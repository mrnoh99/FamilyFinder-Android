package com.familyfinder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 첫 실행 시 예시 가족(엄마·아빠·할아버지·할머니)을 만들어 넣는다.
 * - 사진: 관계별 이모지 아바타를 그려 JPEG로 저장
 * - 음성: TTS(기계음)로 질문/정답/오답을 합성해 WAV로 저장 (불가 시 비프음 폴백)
 * 이후 사용자가 편집에서 실제 사진/목소리로 바꿀 수 있다.
 */
object SampleSeeder {

    private data class Seed(
        val relationship: String,
        val emoji: String,
        val bgColor: Int,
        val question: String,
        val freq: Double,
    )

    private val seeds = listOf(
        Seed("엄마", "👩", 0xFFFFCDD2.toInt(), "엄마는 어디 있을까요?", 523.0),
        Seed("아빠", "👨", 0xFFBBDEFB.toInt(), "아빠는 어디 있을까요?", 392.0),
        Seed("할아버지", "👴", 0xFFC8E6C9.toInt(), "할아버지는 어디 있을까요?", 330.0),
        Seed("할머니", "👵", 0xFFFFF9C4.toInt(), "할머니는 어디 있을까요?", 440.0),
    )

    suspend fun seedIfNeeded(context: Context, repository: FamilyRepository) {
        val prefs = context.getSharedPreferences("familyfinder", Context.MODE_PRIVATE)
        if (prefs.getBoolean("sample_seeded", false)) return
        if (repository.getCount() > 0) {
            prefs.edit().putBoolean("sample_seeded", true).apply()
            return
        }

        val filesDir = context.filesDir
        val correctFile = File(filesDir, "global_correct.wav")
        val incorrectFile = File(filesDir, "global_incorrect.wav")

        val tts = initTts(context)
        try {
            synthesizeOrBeep(tts, "잘했어요!", correctFile, 880.0)
            synthesizeOrBeep(tts, "다시 해 볼까요?", incorrectFile, 247.0)

            seeds.forEachIndexed { index, seed ->
                val photo = File(filesDir, "photo_seed_$index.jpg")
                withContext(Dispatchers.IO) {
                    saveAvatar(photo, seed.emoji, seed.relationship, seed.bgColor)
                }
                val question = File(filesDir, "audio_question_seed_$index.wav")
                synthesizeOrBeep(tts, seed.question, question, seed.freq)

                repository.insert(
                    FamilyMember(
                        relationship = seed.relationship,
                        photoPath = photo.absolutePath,
                        questionAudioPath = question.absolutePath,
                        correctAudioPath = correctFile.absolutePath,
                        incorrectAudioPath = incorrectFile.absolutePath,
                    )
                )
            }
        } finally {
            tts?.shutdown()
        }
        prefs.edit().putBoolean("sample_seeded", true).apply()
    }

    // ── TTS ───────────────────────────────────────────────────────────────

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

    private suspend fun synthesizeOrBeep(
        tts: TextToSpeech?,
        text: String,
        file: File,
        freq: Double,
    ) {
        val ok = tts != null && synthesizeToFile(tts, text, file)
        if (!ok || !file.exists() || file.length() < 64L) {
            withContext(Dispatchers.IO) { writeBeepWav(file, freq) }
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

    // ── 아바타 그림 ─────────────────────────────────────────────────────────

    private fun saveAvatar(file: File, emoji: String, label: String, bgColor: Int) {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bgColor)

        // 일러스트 느낌: 흰 원판 위에 이모지 얼굴, 아래 이름표
        val cx = size / 2f
        val cy = size * 0.42f
        val radius = size * 0.30f
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(cx, cy, radius, circlePaint)

        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = radius * 1.5f
        }
        val emojiBaseline = cy - (emojiPaint.descent() + emojiPaint.ascent()) / 2f
        canvas.drawText(emoji, cx, emojiBaseline, emojiPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 76f
            color = Color.rgb(50, 50, 50)
            isFakeBoldText = true
        }
        canvas.drawText(label, cx, size - 64f, labelPaint)

        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
    }

    // ── 비프음(기계음) 폴백 ─────────────────────────────────────────────────

    private fun writeBeepWav(file: File, freq: Double, durationMs: Int = 600, sampleRate: Int = 22_050) {
        val total = sampleRate * durationMs / 1000
        val fade = min(total / 8, sampleRate / 50) // 짧은 페이드로 클릭음 방지
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
        writeWav16Mono(file, data, sampleRate)
    }

    private fun writeWav16Mono(file: File, pcm16: ShortArray, sampleRate: Int) {
        val dataSize = pcm16.size * 2
        val header = ByteArray(44)
        var idx = 0
        fun putString(s: String) { for (b in s.toByteArray(Charsets.US_ASCII)) header[idx++] = b }
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
        putString("RIFF"); putIntLE(36 + dataSize); putString("WAVE")
        putString("fmt "); putIntLE(16); putShortLE(1); putShortLE(1)
        putIntLE(sampleRate); putIntLE(sampleRate * 2); putShortLE(2); putShortLE(16)
        putString("data"); putIntLE(dataSize)

        if (file.exists()) file.delete()
        FileOutputStream(file).use { fos ->
            fos.write(header)
            val bb = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm16) bb.putShort(s)
            fos.write(bb.array())
            fos.flush()
        }
    }
}
