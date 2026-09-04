@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation", "TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth")

package com.example.androidautoselfheadunit.video

import com.example.androidautoselfheadunit.aap.AapMessage
import com.example.androidautoselfheadunit.decoder.VideoFrameDecoder

class VideoChannel(
    private val reconstructor: FragmentReconstructor,
) {
    private var videoDecoder: VideoFrameDecoder? = null
    private var codecConfig: ByteArray? = null
    private var assemblingConfig = false
    private var waitingForIdr = true

    @Synchronized
    fun attachDecoder(decoder: VideoFrameDecoder) {
        videoDecoder = decoder
        waitingForIdr = true
        codecConfig?.let { decoder.decode(it, 0, it.size, isConfig = true) }
    }

    @Synchronized
    fun detachDecoder() {
        videoDecoder = null
        waitingForIdr = true
        reconstructor.reset()
    }

    @Synchronized
    fun stopStream() {
        waitingForIdr = true
        assemblingConfig = false
        reconstructor.reset()
    }

    @Synchronized
    fun resetSession() {
        codecConfig = null
        stopStream()
    }

    @Synchronized
    fun handleMessage(
        message: AapMessage,
        isConfig: Boolean = false,
    ): Boolean {
        // Strip the offset if it is an unfragmented or first fragment.
        // Assuming AapMessage.data here is the raw payload, and AapTransport handles the rest.
        // In this mock architecture, we just pass the raw data.
        val flags = message.flags.toInt()
        if (flags == FLAG_FIRST || flags == FLAG_SINGLE) {
            assemblingConfig = isConfig
        }
        val assembled = reconstructor.processFragment(flags, message.payload) ?: return false
        val assembledIsConfig = assemblingConfig
        assemblingConfig = false

        val startCodeOffset = findStartCode(assembled)
        if (startCodeOffset < 0) return false
        val accessUnit = assembled.copyOfRange(startCodeOffset, assembled.size)
        if (assembledIsConfig) {
            codecConfig = accessUnit
        }
        val decoder = videoDecoder ?: return true
        if (waitingForIdr && !assembledIsConfig && !containsNalType(accessUnit, NAL_TYPE_IDR)) {
            return true
        }
        val accepted = decoder.decode(accessUnit, 0, accessUnit.size, assembledIsConfig)
        if (accepted && containsNalType(accessUnit, NAL_TYPE_IDR)) waitingForIdr = false
        return accepted
    }

    companion object {
        private const val FLAG_SINGLE = 11
        private const val FLAG_FIRST = 9
        private const val NAL_TYPE_IDR = 5
    }

    private fun containsNalType(
        data: ByteArray,
        targetType: Int,
    ): Boolean {
        var index = 0
        while (index < data.size - 3) {
            val start = findStartCode(data, index)
            if (start < 0) return false
            val header = start + if (data.getOrNull(start + 2) == 1.toByte()) 3 else 4
            if (data.getOrNull(header)?.toInt()?.and(0x1f) == targetType) return true
            index = header + 1
        }
        return false
    }

    private fun findStartCode(
        data: ByteArray,
        fromIndex: Int = 0,
    ): Int {
        for (i in fromIndex until data.size - 2) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) return i
                if (i + 3 < data.size && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) return i
            }
        }
        return -1
    }
}
