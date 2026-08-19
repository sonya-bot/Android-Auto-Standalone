@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

open class AudioTrackWrapper(
    private val sampleRate: Int,
    private val channelConfig: Int,
    private val audioFormat: Int,
    private val usage: Int = AudioAttributes.USAGE_MEDIA,
    private val contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
) {
    private var audioTrack: AudioTrack? = null

    open fun start() {
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize > 0) {
            val bufferSize = maxOf(minBufferSize * 4, BUFFER_SIZE_BYTES)
            audioTrack =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(usage)
                            .setContentType(contentType)
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

            audioTrack?.play()
        }
    }

    open fun write(
        data: ByteArray,
        offset: Int,
        length: Int,
    ) {
        audioTrack?.write(data, offset, length)
    }

    open fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    companion object {
        private const val BUFFER_SIZE_BYTES = 32768
    }
}
