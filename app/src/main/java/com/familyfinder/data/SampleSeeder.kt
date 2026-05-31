package com.familyfinder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 첫 실행 시 예시 가족(엄마·아빠·할아버지·할머니)을 만들어 넣는다.
 * - 사진: 관계별 이모지 아바타를 그려 JPEG로 저장(이름 텍스트는 넣지 않음)
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

        TtsSynthesizer.synthesizeOrBeep(context, "잘했어요!", correctFile, 880.0)
        TtsSynthesizer.synthesizeOrBeep(context, "다시 해 볼까요?", incorrectFile, 247.0)

        seeds.forEachIndexed { index, seed ->
            val photo = File(filesDir, "photo_seed_$index.jpg")
            withContext(Dispatchers.IO) {
                saveAvatar(photo, seed.emoji, seed.bgColor)
            }
            val question = File(filesDir, "audio_question_seed_$index.wav")
            TtsSynthesizer.synthesizeOrBeep(context, seed.question, question, seed.freq)

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
        prefs.edit().putBoolean("sample_seeded", true).apply()
    }

    /** 흰 원판 위에 이모지 얼굴만 그린 일러스트 아바타(이름 텍스트 없음). */
    private fun saveAvatar(file: File, emoji: String, bgColor: Int) {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(bgColor)

        val cx = size / 2f
        val cy = size / 2f
        val radius = size * 0.34f
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(cx, cy, radius, circlePaint)

        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = radius * 1.5f
        }
        val emojiBaseline = cy - (emojiPaint.descent() + emojiPaint.ascent()) / 2f
        canvas.drawText(emoji, cx, emojiBaseline, emojiPaint)

        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
    }
}
