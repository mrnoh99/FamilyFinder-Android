package com.familyfinder.data

import android.content.Context
import java.io.File
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 정답/오답 피드백 앞에 붙일 짧은 효과음(WAV)을 생성·캐시한다.
 * - 정답: "딩동"(높은 음 → 낮은 음으로 내려가는 두 음)
 * - 오답: "삑"(낮은 음 짧게 두 번)
 */
object CueSounds {

    private const val SAMPLE_RATE = 22_050

    fun dingFile(context: Context): File {
        val f = File(context.filesDir, "cue_ding.wav")
        if (!f.exists() || f.length() < 64L) {
            writeTones(f, listOf(988.0 to 170, 0.0 to 40, 784.0 to 330))
        }
        return f
    }

    fun buzzFile(context: Context): File {
        val f = File(context.filesDir, "cue_buzz.wav")
        if (!f.exists() || f.length() < 64L) {
            writeTones(f, listOf(165.0 to 150, 0.0 to 70, 165.0 to 260))
        }
        return f
    }

    /** segments: (주파수Hz, 길이ms). 주파수 0이면 무음. */
    private fun writeTones(file: File, segments: List<Pair<Double, Int>>) {
        val out = ArrayList<Short>()
        for ((freq, ms) in segments) {
            val n = SAMPLE_RATE * ms / 1000
            val fade = min(n / 6, SAMPLE_RATE / 80)
            for (i in 0 until n) {
                val env = when {
                    freq <= 0.0 -> 0.0
                    i < fade -> i.toDouble() / fade
                    i > n - fade -> (n - i).toDouble() / fade
                    else -> 1.0
                }
                val sample = if (freq <= 0.0) 0.0 else sin(2.0 * PI * freq * i / SAMPLE_RATE) * 0.5 * env
                out.add(
                    (sample * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                )
            }
        }
        WavWriter.write16Mono(file, out.toShortArray(), SAMPLE_RATE)
    }
}
