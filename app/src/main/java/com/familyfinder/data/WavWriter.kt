package com.familyfinder.data

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Single source of truth for writing 16-bit mono PCM WAV files.
 * Used by TtsSynthesizer, CueSounds, and WavRecorder.
 */
internal object WavWriter {

    fun write16Mono(file: File, pcm16: ShortArray, sampleRate: Int) {
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
        putString("fmt "); putIntLE(16)
        putShortLE(1)             // PCM
        putShortLE(1)             // mono
        putIntLE(sampleRate)
        putIntLE(sampleRate * 2)  // byteRate = sampleRate * channels * bitsPerSample/8
        putShortLE(2)             // blockAlign = channels * bitsPerSample/8
        putShortLE(16)            // bitsPerSample
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
