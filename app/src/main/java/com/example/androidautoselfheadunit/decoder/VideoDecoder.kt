@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation", "TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth")

package com.example.androidautoselfheadunit.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

class VideoDecoder(
    private val surface: Surface,
    private val onFirstFrameRendered: () -> Unit = {},
) {
    companion object {
        private const val TAG = "VideoDecoder"
        private const val VIDEO_WIDTH = 1920
        private const val VIDEO_HEIGHT = 1080
        private const val TIMEOUT_US = 10000L
    }

    private var mediaCodec: MediaCodec? = null
    private var isConfigured = false
    private var hasRenderedFrame = false

    fun start() {
        if (isConfigured) return
        try {
            val format =
                MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    VIDEO_WIDTH,
                    VIDEO_HEIGHT,
                )
            var codec: android.media.MediaCodec? = null
            try {
                codec = android.media.MediaCodec.createByCodecName("c2.android.avc.decoder")
            } catch (e: Exception) {
                try {
                    codec = android.media.MediaCodec.createByCodecName("OMX.google.h264.decoder")
                } catch (e2: Exception) {
                    codec = android.media.MediaCodec.createDecoderByType(android.media.MediaFormat.MIMETYPE_VIDEO_AVC)
                }
            }
            mediaCodec = codec
            mediaCodec?.configure(format, surface, null, 0)
            mediaCodec?.start()
            isConfigured = true
            Log.i(TAG, "MediaCodec started")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to start MediaCodec", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start MediaCodec", e)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Failed to start MediaCodec", e)
        }
    }

    @Suppress("MagicNumber")
    fun decode(
        data: ByteArray,
        offset: Int,
        length: Int,
        isConfig: Boolean = false,
    ) {
        if (!isConfigured) return
        val codec = mediaCodec ?: return

        try {
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer: ByteBuffer? = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(data, offset, length)
                val flags = if (isConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                val pts = System.nanoTime() / 1000
                codec.queueInputBuffer(inputBufferIndex, 0, length, pts, flags)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.i(TAG, "Output format changed: ${codec.outputFormat}")
            }
            while (outputBufferIndex >= 0) {
                codec.releaseOutputBuffer(outputBufferIndex, true)
                if (!hasRenderedFrame) {
                    hasRenderedFrame = true
                    Log.i(TAG, "First video frame rendered")
                    onFirstFrameRendered()
                }
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to decode", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to decode", e)
        }
    }

    fun stop() {
        if (!isConfigured) return
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
            isConfigured = false
            hasRenderedFrame = false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to stop MediaCodec", e)
        }
    }
}
