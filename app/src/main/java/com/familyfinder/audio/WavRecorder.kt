package com.familyfinder.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.annotation.WorkerThread
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Records microphone audio as 16-bit mono PCM WAV files.
 *
 * Ported from the NumberCount app's FeedbackRecorder: captures raw PCM through
 * [AudioRecord], trims leading/trailing non-speech, applies a high-pass filter
 * and peak normalization, then writes a self-contained WAV file. Unlike that
 * recorder this one carries no background-music / language coupling and writes
 * to any caller-supplied [File], so each family member can own its own clips.
 */
class WavRecorder(@Suppress("unused") private val context: Context) {

    private val tag = "WavRecorder"

    private val sampleRate = 44_100
    // Per-sample floor used as a minimum speech threshold.
    private val minSpeechPeak = 550
    private val padFrames = (sampleRate * 0.045f).toInt().coerceAtLeast(1)
    private val minFrames = (sampleRate * 0.08f).toInt()
    // ~25 ms windows: short taps/clicks usually occupy one window; speech spans several.
    private val windowFrames = (sampleRate * 0.025f).toInt().coerceAtLeast(256)
    private val minSpeechWindows = 2
    private val speechThresholdMultiplier = 4.0

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var recordBufferSize: Int = 0

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val isRecordingInternal = AtomicBoolean(false)
    private val pcmBytesBuffer = ByteArrayOutputStream()
    private val pcmLock = Any()

    /** Caller must ensure RECORD_AUDIO permission is granted. Returns false if the mic is unavailable. */
    fun startRecording(scope: CoroutineScope): Boolean {
        if (_isRecording.value) return false

        synchronized(pcmLock) {
            pcmBytesBuffer.reset()
        }

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val rawMinBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        val minBuf = if (rawMinBuf > 0) rawMinBuf else sampleRate * 2 // 1s fallback
        recordBufferSize = minBuf

        val record = try {
            AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                encoding,
                minBuf,
            )
        } catch (e: Exception) {
            android.util.Log.e(tag, "AudioRecord creation failed", e)
            null
        }

        // Bail out cleanly if the mic could not be acquired (e.g. permission revoked
        // or device busy) instead of silently leaving the UI in a recording state.
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            android.util.Log.w(tag, "AudioRecord not initialized (state=${record?.state})")
            try { record?.release() } catch (e: Exception) { android.util.Log.w(tag, "release failed", e) }
            audioRecord = null
            return false
        }
        audioRecord = record

        try {
            record.startRecording()
        } catch (e: Exception) {
            android.util.Log.e(tag, "startRecording failed", e)
            try { record.release() } catch (re: Exception) { android.util.Log.w(tag, "release failed", re) }
            audioRecord = null
            return false
        }

        isRecordingInternal.set(true)
        _isRecording.value = true

        recordJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(minBuf)
            try {
                while (isActive && isRecordingInternal.get()) {
                    val readBytes = record.read(buf, 0, buf.size)
                    if (readBytes > 0) {
                        synchronized(pcmLock) {
                            pcmBytesBuffer.write(buf, 0, readBytes)
                        }
                    }
                }
            } catch (_: CancellationException) {
                // ignore
            }
        }
        return true
    }

    /** Stops capture, processes the PCM and writes it to [outFile]. Returns false if nothing usable was recorded. */
    suspend fun stopAndSave(outFile: File): Boolean {
        if (!_isRecording.value) return false

        isRecordingInternal.set(false)
        _isRecording.value = false

        val job = recordJob
        val record = audioRecord
        recordJob = null

        try {
            job?.join()
        } catch (e: Exception) {
            android.util.Log.w(tag, "recordJob join interrupted", e)
        }

        withContext(Dispatchers.IO) {
            if (record != null) {
                try {
                    record.stop()
                } catch (e: Exception) {
                    android.util.Log.w(tag, "AudioRecord stop failed", e)
                }
                val buf = ByteArray(recordBufferSize.coerceAtLeast(1))
                while (true) {
                    val readBytes = try {
                        record.read(buf, 0, buf.size)
                    } catch (e: Exception) {
                        android.util.Log.w(tag, "Drain read failed", e)
                        break
                    }
                    if (readBytes <= 0) break
                    synchronized(pcmLock) {
                        pcmBytesBuffer.write(buf, 0, readBytes)
                    }
                }
                try {
                    record.release()
                } catch (e: Exception) {
                    android.util.Log.w(tag, "AudioRecord release failed", e)
                }
            }
            audioRecord = null
        }

        val pcmBytes = synchronized(pcmLock) { pcmBytesBuffer.toByteArray() }

        val ok = withContext(Dispatchers.Default) {
            trimAndWriteWavPcm16Mono(pcmBytes = pcmBytes, outFile = outFile)
        }

        if (!ok) outFile.delete()
        return ok
    }

    /** Stops capture without saving — used for lifecycle teardown. */
    fun release() {
        isRecordingInternal.set(false)
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null
        val record = audioRecord
        audioRecord = null
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
    }

    @WorkerThread
    private fun trimAndWriteWavPcm16Mono(pcmBytes: ByteArray, outFile: File): Boolean {
        val sampleCount = pcmBytes.size / 2
        if (sampleCount == 0) return false

        val byteBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val shorts = ShortArray(sampleCount)
        byteBuffer.asShortBuffer().get(shorts)

        // Trim to detected speech when possible. If detection fails (quiet clip,
        // emulator silence, very short hold) keep the whole clip rather than
        // discarding the recording — the user pressed record, so we save something.
        val bounds = findSpeechBounds(shorts)
        val start = if (bounds != null) (bounds.first - padFrames).coerceAtLeast(0) else 0
        // Do not pad the tail; that would re-include trailing clicks and room noise.
        val end = bounds?.second ?: (sampleCount - 1)
        if (end < start) return false

        val trimmed = shorts.copyOfRange(start, end + 1)
        if (trimmed.isEmpty()) return false
        val enhanced = enhanceSpeechClip(trimmed)

        return try {
            writeWav16MonoPcm(file = outFile, pcm16 = enhanced, sampleRate = sampleRate)
            true
        } catch (e: Exception) {
            android.util.Log.e(tag, "WAV write failed: ${outFile.name}", e)
            false
        }
    }

    /**
     * Finds speech bounds using windowed peaks and sustained energy so brief
     * non-speech sounds (finger taps, clicks, breath puffs) at the edges are dropped.
     */
    private fun findSpeechBounds(shorts: ShortArray): Pair<Int, Int>? {
        val sampleCount = shorts.size
        if (sampleCount < minFrames) return null

        val windowCount = (sampleCount + windowFrames - 1) / windowFrames
        if (windowCount == 0) return null

        val windowPeaks = IntArray(windowCount) { window ->
            val windowStart = window * windowFrames
            val windowEnd = minOf((window + 1) * windowFrames, sampleCount)
            var peak = 0
            for (i in windowStart until windowEnd) {
                peak = maxOf(peak, kotlin.math.abs(shorts[i].toInt()))
            }
            peak
        }

        val sortedPeaks = windowPeaks.sorted()
        val noiseFloor = sortedPeaks[(windowCount / 10).coerceIn(0, windowCount - 1)]
            .coerceAtLeast(80)
        val speechThreshold = maxOf(
            minSpeechPeak,
            (noiseFloor * speechThresholdMultiplier).toInt(),
        )

        fun isSpeechWindow(window: Int): Boolean = windowPeaks[window] >= speechThreshold

        val startWindow = findFirstSustainedSpeechWindow(windowCount, ::isSpeechWindow)
        val endWindow = findLastSustainedSpeechWindow(windowCount, ::isSpeechWindow)

        if (startWindow != null && endWindow != null && endWindow >= startWindow) {
            val start = startWindow * windowFrames
            val end = minOf((endWindow + 1) * windowFrames, sampleCount) - 1
            if (end > start) return start to end
        }

        return findSpeechBoundsFallback(shorts)
    }

    private fun findFirstSustainedSpeechWindow(
        windowCount: Int,
        isSpeechWindow: (Int) -> Boolean,
    ): Int? {
        var run = 0
        for (window in 0 until windowCount) {
            if (isSpeechWindow(window)) {
                run++
                if (run >= minSpeechWindows) {
                    return window - minSpeechWindows + 1
                }
            } else {
                run = 0
            }
        }
        return null
    }

    private fun findLastSustainedSpeechWindow(
        windowCount: Int,
        isSpeechWindow: (Int) -> Boolean,
    ): Int? {
        var run = 0
        for (window in windowCount - 1 downTo 0) {
            if (isSpeechWindow(window)) {
                run++
                if (run >= minSpeechWindows) {
                    return window + minSpeechWindows - 1
                }
            } else {
                run = 0
            }
        }
        return null
    }

    /** Simple per-sample trim used when windowed detection cannot find speech. */
    private fun findSpeechBoundsFallback(shorts: ShortArray): Pair<Int, Int>? {
        val sampleCount = shorts.size
        var start = 0
        while (start < sampleCount && kotlin.math.abs(shorts[start].toInt()) < minSpeechPeak) {
            start++
        }
        var end = sampleCount - 1
        while (end > start && kotlin.math.abs(shorts[end].toInt()) < minSpeechPeak) {
            end--
        }
        if (end <= start) return null
        return start to end
    }

    /** High-pass + peak normalize so saved clips sound clearer and louder on playback. */
    private fun enhanceSpeechClip(input: ShortArray): ShortArray {
        if (input.isEmpty()) return input
        val filtered = applyHighPass(input, cutoffHz = 90f)
        return normalizePeak(filtered)
    }

    /** Removes low-frequency rumble that makes speech sound muffled on phone mics. */
    private fun applyHighPass(input: ShortArray, cutoffHz: Float): ShortArray {
        val dt = 1.0 / sampleRate
        val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
        val alpha = rc / (rc + dt)
        val out = ShortArray(input.size)
        var prevIn = 0.0
        var prevOut = 0.0
        for (i in input.indices) {
            val sample = input[i].toDouble()
            val filtered = alpha * (prevOut + sample - prevIn)
            prevIn = sample
            prevOut = filtered
            out[i] = filtered.toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }

    /** Boost quiet recordings toward a consistent playback level without hard clipping. */
    private fun normalizePeak(input: ShortArray): ShortArray {
        var peak = 0
        for (sample in input) {
            peak = maxOf(peak, kotlin.math.abs(sample.toInt()))
        }
        if (peak == 0) return input

        val targetPeak = (Short.MAX_VALUE * 0.92).toInt()
        val gain = (targetPeak.toDouble() / peak).coerceIn(1.25, 4.0)
        return ShortArray(input.size) { index ->
            (input[index] * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private fun writeWav16MonoPcm(file: File, pcm16: ShortArray, sampleRate: Int) {
        val dataSize = pcm16.size * 2
        val riffSize = 36 + dataSize

        val header = ByteArray(44)
        var idx = 0

        fun putString(s: String) {
            val bytes = s.toByteArray(Charsets.US_ASCII)
            for (b in bytes) header[idx++] = b
        }

        fun putIntLE(v: Int) {
            header[idx++] = (v and 0xFF).toByte()
            header[idx++] = ((v shr 8) and 0xFF).toByte()
            header[idx++] = ((v shr 16) and 0xFF).toByte()
            header[idx++] = ((v shr 24) and 0xFF).toByte()
        }

        fun putShortLE(v: Int) {
            header[idx++] = (v and 0xFF).toByte()
            header[idx++] = ((v shr 8) and 0xFF).toByte()
        }

        putString("RIFF")
        putIntLE(riffSize)
        putString("WAVE")
        putString("fmt ")
        putIntLE(16) // PCM chunk size
        putShortLE(1) // AudioFormat PCM=1
        putShortLE(1) // channels mono
        putIntLE(sampleRate)
        val byteRate = sampleRate * 1 * 16 / 8
        putIntLE(byteRate)
        val blockAlign = 1 * 16 / 8
        putShortLE(blockAlign)
        putShortLE(16) // bits per sample
        putString("data")
        putIntLE(dataSize)

        if (file.exists()) file.delete()

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val bb = ByteBuffer.allocate(pcm16.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm16) bb.putShort(s)
            fos.write(bb.array())
            fos.flush()
        }
    }
}
