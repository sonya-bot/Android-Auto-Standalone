@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.audio

import com.example.androidautoselfheadunit.aap.AapMessage

class AudioChannel(
    private val audioTrackWrapper: AudioTrackWrapper,
) {
    companion object {
        private const val HEADER_OFFSET = 8
    }

    init {
        audioTrackWrapper.start()
    }

    fun handleMessage(message: AapMessage) {
        val offset = HEADER_OFFSET
        val length = message.payload.size - offset
        if (length > 0) {
            audioTrackWrapper.write(message.payload, offset, length)
        }
    }

    fun stop() {
        audioTrackWrapper.stop()
    }
}
