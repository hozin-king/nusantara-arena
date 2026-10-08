package com.hozinking.arena.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

/**
 * Efek suara super ringan: nada sintetis via AudioTrack.
 * Full offline, tanpa file audio.
 */
object SoundFX {
    private const val SR = 22050
    private var track: AudioTrack? = null

    private fun ensure(): AudioTrack? {
        if (track == null) {
            try {
                val buf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track?.play()
            } catch (_: Exception) { }
        }
        return track
    }

    /** Nada [freq] Hz selama [ms], dengan sweep opsional ke [freq2]. */
    private fun tone(freq: Float, ms: Int, freq2: Float = freq, vol: Float = 0.25f) {
        try {
            val t = ensure() ?: return
            val n = (SR * ms / 1000)
            val buf = ShortArray(n)
            for (i in 0 until n) {
                val f = freq + (freq2 - freq) * i / n
                val env = 1f - i.toFloat() / n // fade out
                buf[i] = (sin(2.0 * Math.PI * f * i / SR) * 32767 * vol * env).toInt().toShort()
            }
            t.write(buf, 0, n)
        } catch (_: Exception) { }
    }

    fun hit() = tone(700f, 60, 500f, 0.15f)
    fun skill() = tone(400f, 140, 900f, 0.22f)
    fun ult() = tone(180f, 420, 700f, 0.3f)
    fun death() = tone(500f, 200, 120f, 0.25f)
    fun start() = tone(300f, 120, 600f, 0.25f)
    fun win() = tone(500f, 500, 1000f, 0.3f)
    fun lose() = tone(400f, 500, 150f, 0.3f)
}
