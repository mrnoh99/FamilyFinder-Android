package com.familyfinder.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.familyfinder.R

/**
 * 숫자세기(NumberCount) 앱과 동일한 방식의 배경 음악 컨트롤러.
 *
 * 루프 재생되는 [MediaPlayer]를 하나 두고, 켜짐 여부와 볼륨을 [SharedPreferences]에 저장한다.
 * 앱이 포그라운드일 때만 재생하도록 [pauseBgm]/[resumeBgm]을 액티비티 생명주기에서 호출한다.
 */
class BgmController(private val context: Context) {

    private val prefs =
        context.getSharedPreferences("familyfinder", Context.MODE_PRIVATE)

    private val bgmEnabledKey = "bgmEnabled"
    private val bgmVolumeKey = "bgmVolume"

    @RawRes
    private val bgmRes: Int = R.raw.bgm_waltz

    private val audioAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    private var bgmPlayer: MediaPlayer? = null

    init {
        bgmPlayer = createBgmPlayer()
    }

    private fun createBgmPlayer(): MediaPlayer? {
        return try {
            MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                context.resources.openRawResourceFd(bgmRes).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                isLooping = true
                val volume = getBgmVolume()
                setVolume(volume, volume)
                setOnErrorListener { player, _, _ ->
                    try {
                        player.release()
                    } catch (_: Exception) {
                    }
                    if (bgmPlayer === player) bgmPlayer = null
                    true
                }
                prepare()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureBgmPlayer(): MediaPlayer? {
        val existing = bgmPlayer
        if (existing != null) return existing
        val created = createBgmPlayer()
        bgmPlayer = created
        return created
    }

    private fun releaseBgmPlayer() {
        try {
            bgmPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
        } catch (_: Exception) {
        }
        bgmPlayer = null
    }

    fun isBgmEnabled(): Boolean = prefs.getBoolean(bgmEnabledKey, true)

    fun getBgmVolume(): Float = prefs.getFloat(bgmVolumeKey, 0.12f).coerceIn(0f, 1f)

    fun setBgmEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(bgmEnabledKey, enabled).apply()
        if (enabled) resumeBgm() else pauseBgm()
    }

    fun setBgmVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        prefs.edit().putFloat(bgmVolumeKey, v).apply()
        try {
            ensureBgmPlayer()?.setVolume(v, v)
        } catch (_: Exception) {
        }
    }

    fun pauseBgm() {
        try {
            bgmPlayer?.pause()
        } catch (_: Exception) {
        }
    }

    fun resumeBgm() {
        if (!isBgmEnabled()) return
        val volume = getBgmVolume()
        val player = ensureBgmPlayer() ?: return
        try {
            player.setVolume(volume, volume)
            if (!player.isPlaying) player.start()
        } catch (_: Exception) {
            releaseBgmPlayer()
            val rebuilt = ensureBgmPlayer() ?: return
            try {
                rebuilt.setVolume(volume, volume)
                rebuilt.start()
            } catch (_: Exception) {
                releaseBgmPlayer()
            }
        }
    }

    fun release() {
        releaseBgmPlayer()
    }
}
