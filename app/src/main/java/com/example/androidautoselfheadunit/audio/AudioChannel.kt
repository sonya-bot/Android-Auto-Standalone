@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.audio

import android.util.Log
import com.example.androidautoselfheadunit.aap.AapMessage
import com.example.androidautoselfheadunit.video.FragmentReconstructor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class AudioChannel(
    private val audioTrackWrapper: AudioTrackWrapper,
    private val reconstructor: FragmentReconstructor = FragmentReconstructor(),
) {
    companion object {
        private const val TAG = "AudioChannel"
        private const val HEADER_OFFSET = 8
        private const val AUDIO_QUEUE_CAPACITY = 64
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioQueue = Channel<ByteArray>(capacity = AUDIO_QUEUE_CAPACITY)

    init {
        audioTrackWrapper.start()
        scope.launch {
            for (pcm in audioQueue) {
                audioTrackWrapper.write(pcm, 0, pcm.size)
            }
        }
    }

    fun handleMessage(message: AapMessage): Boolean {
        if (message.messageType != 0) return false
        val assembled = reconstructor.processFragment(message.flags.toInt(), message.payload) ?: return false
        val offset = HEADER_OFFSET
        if (assembled.size <= offset) return false
        val pcm = assembled.copyOfRange(offset, assembled.size)
        val result = audioQueue.trySend(pcm)
        if (!result.isSuccess) {
            Log.w(TAG, "Audio queue full, dropping pcm packet (${pcm.size} bytes)")
        }
        return result.isSuccess
    }

    fun stop() {
        audioQueue.close()
        scope.cancel()
        audioTrackWrapper.stop()
    }
}
