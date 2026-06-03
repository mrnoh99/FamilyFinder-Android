package com.familyfinder.data

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 텍스트를 TTS(기계음)로 합성해 WAV 파일로 저장한다. TTS를 쓸 수 없으면 비프음으로 폴백한다.
 * 예시 시드 생성과 "TTS로 되돌리기" 기능에서 공용으로 사용한다.
 */
object TtsSynthesizer {

    /** [text]를 [outFile]에 합성한다. 실패하면 [beepFreq] 비프음을 쓴다. 파일이 생기면 true. */
    suspend fun synthesizeOrBeep(
        context: Context,
        text: String,
        outFile: File,
        beepFreq: Double,
    ): Boolean {
        val ok = synthesize(context, text, outFile)
        if (!ok || !outFile.exists() || outFile.length() < 64L) {
            withContext(Dispatchers.IO) { writeBeepWav(outFile, beepFreq) }
        }
        return outFile.exists()
    }

    private suspend fun synthesize(context: Context, text: String, outFile: File): Boolean {
        val tts = initTts(context) ?: return false
        return try {
            synthesizeToFile(tts, text, outFile)
        } finally {
            tts.shutdown()
        }
    }

    private suspend fun initTts(context: Context): TextToSpeech? = withTimeoutOrNull(5000) {
        suspendCancellableCoroutine<TextToSpeech?> { cont ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try { engine?.language = Locale.KOREAN } catch (_: Exception) {}
                    if (cont.isActive) cont.resume(engine)
                } else {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private suspend fun synthesizeToFile(tts: TextToSpeech, text: String, file: File): Boolean =
        withTimeoutOrNull(8000) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val id = "utt_${file.name}"
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(true)
                    }
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id && cont.isActive) cont.resume(false)
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == id && cont.isActive) cont.resume(false)
                    }
                })
                val res = tts.synthesizeToFile(text, Bundle(), file, id)
                if (res != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
            }
        } ?: false

    private fun writeBeepWav(file: File, freq: Double, durationMs: Int = 600, sampleRate: Int = 22_050) {
        val total = sampleRate * durationMs / 1000
        val fade = min(total / 8, sampleRate / 50)
        val data = ShortArray(total) { i ->
            val env = when {
                i < fade -> i.toDouble() / fade
                i > total - fade -> (total - i).toDouble() / fade
                else -> 1.0
            }
            val sample = sin(2.0 * PI * freq * i / sampleRate) * 0.5 * env
            (sample * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        WavWriter.write16Mono(file, data, sampleRate)
    }
}
