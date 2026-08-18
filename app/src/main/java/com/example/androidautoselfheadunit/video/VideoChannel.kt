@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.video

import com.example.androidautoselfheadunit.aap.AapMessage
import com.example.androidautoselfheadunit.decoder.VideoDecoder

class VideoChannel(
    private val reconstructor: FragmentReconstructor,
    private val videoDecoder: VideoDecoder,
) {
    fun handleMessage(message: AapMessage) {
        // Strip the offset if it is an unfragmented or first fragment.
        // Assuming AapMessage.data here is the raw payload, and AapTransport handles the rest.
        // In this mock architecture, we just pass the raw data.
        val flags = message.flags.toInt()
        val assembled = reconstructor.processFragment(flags, message.payload)

        if (assembled != null) {
            // Find NAL start code.
            val startCodeOffset = findStartCode(assembled)
            if (startCodeOffset >= 0) {
                videoDecoder.decode(assembled, startCodeOffset, assembled.size - startCodeOffset)
            }
        }
    }

    private fun findStartCode(data: ByteArray): Int {
        for (i in 0 until data.size - NAL_START_CODE_LEN) {
            if (isStartCodeAt(data, i)) {
                return i
            }
        }
        return -1
    }

    private fun isStartCodeAt(
        data: ByteArray,
        index: Int,
    ): Boolean {
        val b0 = data[index]
        val b1 = data[index + OFFSET_1]
        val b2 = data[index + OFFSET_2]
        val b3 = data[index + OFFSET_3]
        return b0 == 0.toByte() && b1 == 0.toByte() && b2 == 0.toByte() && b3 == 1.toByte()
    }

    companion object {
        private const val NAL_START_CODE_LEN = 3
        private const val OFFSET_1 = 1
        private const val OFFSET_2 = 2
        private const val OFFSET_3 = 3
    }
}
