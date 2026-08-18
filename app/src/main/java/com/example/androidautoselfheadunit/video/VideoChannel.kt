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
        for (i in 0 until data.size - 2) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) {
                    return i // 3-byte start code
                }
                if (i + 3 < data.size && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                    return i // 4-byte start code
                }
            }
        }
        return -1
    }

    companion object {
        private const val NAL_START_CODE_LEN = 3
    }
}
