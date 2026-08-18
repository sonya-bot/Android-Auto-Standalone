package com.example.androidautoselfheadunit.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

open class AudioTrackWrapper(
    private val sampleRate: Int,
    private val channelConfig: Int,
    private val audioFormat: Int,
) {
    private var audioTrack: AudioTrack? = null

    open fun start() {
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize > 0) {
            audioTrack =
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .setEncoding(audioFormat)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBufferSize)
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
}
