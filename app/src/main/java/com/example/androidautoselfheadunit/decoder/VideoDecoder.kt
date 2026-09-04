@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation", "TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth")

package com.example.androidautoselfheadunit.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import com.example.androidautoselfheadunit.aap.protocol.ProjectionDisplayProfile
import java.nio.ByteBuffer

class VideoDecoder(
    private val surface: Surface,
    private val displayProfile: ProjectionDisplayProfile,
    private val onFirstFrameRendered: () -> Unit = {},
    private val onDecoderFailure: () -> Unit = {},
) : VideoFrameDecoder {
    companion object {
        private const val TAG = "VideoDecoder"
        private const val TIMEOUT_US = 50000L
        private const val MAX_CONSECUTIVE_BACKPRESSURE = 5
    }

    private var mediaCodec: MediaCodec? = null
    private var isConfigured = false
    private var hasRenderedFrame = false
    private var consecutiveBackpressure = 0

    @Synchronized
    fun start(): Boolean {
        if (isConfigured) return true
        try {
            val format =
                MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    displayProfile.widthPx,
                    displayProfile.heightPx,
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
            return true
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to start MediaCodec", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to start MediaCodec", e)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "Failed to start MediaCodec", e)
        }
        releaseCodec()
        onDecoderFailure()
        return false
    }

    @Suppress("MagicNumber")
    @Synchronized
    override fun decode(
        data: ByteArray,
        offset: Int,
        length: Int,
        isConfig: Boolean,
    ): Boolean {
        if (!isConfigured) return false
        val codec = mediaCodec ?: return false

        try {
            drainOutput(codec)
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer: ByteBuffer? = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(data, offset, length)
                val flags = if (isConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                val pts = System.nanoTime() / 1000
                codec.queueInputBuffer(inputBufferIndex, 0, length, pts, flags)
                consecutiveBackpressure = 0
            } else {
                consecutiveBackpressure++
                Log.w(TAG, "No decoder input buffer available; applying backpressure")
                if (consecutiveBackpressure >= MAX_CONSECUTIVE_BACKPRESSURE) {
                    Log.e(TAG, "Decoder stopped consuming input; recreating codec")
                    releaseCodec()
                    onDecoderFailure()
                }
                return false
            }
            drainOutput(codec)
            return true
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to decode", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to decode", e)
        }
        releaseCodec()
        onDecoderFailure()
        return false
    }

    @Synchronized
    fun stop() {
        releaseCodec()
    }

    private fun drainOutput(codec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Log.i(TAG, "Output format changed: ${codec.outputFormat}")
            outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
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
    }

    private fun releaseCodec() {
        val codec = mediaCodec
        mediaCodec = null
        isConfigured = false
        hasRenderedFrame = false
        consecutiveBackpressure = 0
        if (codec == null) return
        try {
            codec.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to stop MediaCodec", e)
        } finally {
            try {
                codec.release()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to release MediaCodec", e)
            }
        }
    }
}
