@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

open class AudioTrackWrapper(
    private val sampleRate: Int,
    private val channelConfig: Int,
    private val audioFormat: Int,
    private val usage: Int = AudioAttributes.USAGE_MEDIA,
    private val contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
) {
    private var audioTrack: AudioTrack? = null

    @Synchronized
    open fun start() {
        if (audioTrack != null && audioTrack?.state == AudioTrack.STATE_INITIALIZED) return
        stop()
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize > 0) {
            val bufferSize = maxOf(minBufferSize * 4, BUFFER_SIZE_BYTES)
            audioTrack =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(contentType)
                            .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .setEncoding(audioFormat)
                            .build(),
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

            try {
                audioTrack?.setVolume(1.0f)
                audioTrack?.play()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to start AudioTrack", e)
            }
        }
    }

    @Synchronized
    open fun write(
        data: ByteArray,
        offset: Int,
        length: Int,
    ) {
        val track = audioTrack ?: return
        if (track.state != AudioTrack.STATE_INITIALIZED) return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
            try {
                track.play()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Failed to play track before write", e)
            }
        }
        val written = track.write(data, offset, length)
        if (written < 0) {
            Log.w(TAG, "AudioTrack.write returned $written, recovering AudioTrack...")
            try {
                stop()
                start()
                val retrack = audioTrack
                if (retrack != null && retrack.state == AudioTrack.STATE_INITIALIZED) {
                    retrack.write(data, offset, length)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recover AudioTrack", e)
            }
        }
    }

    @Synchronized
    open fun stop() {
        val track = audioTrack ?: return
        audioTrack = null
        try {
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.stop()
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to stop AudioTrack", e)
        } finally {
            track.release()
        }
    }

    companion object {
        private const val TAG = "AudioTrackWrapper"
        private const val BUFFER_SIZE_BYTES = 32768
    }
}
