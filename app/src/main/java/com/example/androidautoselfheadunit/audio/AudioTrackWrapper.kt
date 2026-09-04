@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Build
import android.util.Log

open class AudioTrackWrapper(
    private val sampleRate: Int,
    private val channelConfig: Int,
    private val audioFormat: Int,
    private val usage: Int = AudioAttributes.USAGE_UNKNOWN,
    private val contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
    private val context: Context? = null,
) {
    private var audioTrack: AudioTrack? = null
    private val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var isCallbacksRegistered = false

    private val audioDeviceCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    Log.i(TAG, "Audio devices added: ${addedDevices?.joinToString { "${it.productName}(type=${it.type})" }}")
                    handleDeviceRoutingChange()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    Log.i(TAG, "Audio devices removed: ${removedDevices?.joinToString { "${it.productName}(type=${it.type})" }}")
                    handleDeviceRoutingChange()
                }
            }
        } else {
            null
        }

    private val headsetReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_HEADSET_PLUG) {
                    val state = intent.getIntExtra("state", -1)
                    Log.i(TAG, "ACTION_HEADSET_PLUG received: state=$state")
                    handleDeviceRoutingChange()
                }
            }
        }

    private fun registerCallbacks() {
        if (isCallbacksRegistered || context == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
                audioManager?.registerAudioDeviceCallback(audioDeviceCallback, null)
            }
            val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
            context.registerReceiver(headsetReceiver, filter)
            isCallbacksRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register audio device callbacks", e)
        }
    }

    private fun unregisterCallbacks() {
        if (!isCallbacksRegistered || context == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
                audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
            }
            context.unregisterReceiver(headsetReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister audio device callbacks", e)
        } finally {
            isCallbacksRegistered = false
        }
    }

    private fun findBestOutputDevice(): AudioDeviceInfo? {
        val am = audioManager ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // Priority 1: Wired / USB devices (Headset, Headphones, USB DAC, AUX line)
        val wired =
            devices.firstOrNull { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_LINE_DIGITAL -> true
                    else -> false
                }
            }
        if (wired != null) return wired

        // Priority 2: Bluetooth devices (A2DP, BLE)
        val bt =
            devices.firstOrNull { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
                    else -> false
                }
            }
        if (bt != null) return bt

        // Priority 3: Built-in speaker
        return devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    @Synchronized
    private fun handleDeviceRoutingChange() {
        val target = findBestOutputDevice()
        Log.i(TAG, "Device routing changed. Target device: ${target?.productName} (type=${target?.type})")
        if (audioTrack != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && target != null) {
                audioTrack?.setPreferredDevice(target)
            }
            // Re-creating AudioTrack ensures Android HAL migrates immediately from speaker to wired/USB DAC
            stop()
            start()
        }
    }

    @Synchronized
    open fun start() {
        registerCallbacks()
        if (audioTrack != null && audioTrack?.state == AudioTrack.STATE_INITIALIZED) return
        stop()
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize > 0) {
            val bufferSize = maxOf(minBufferSize * 4, BUFFER_SIZE_BYTES)
            val track =
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val preferred = findBestOutputDevice()
                if (preferred != null) {
                    val ok = track.setPreferredDevice(preferred)
                    Log.i(TAG, "AudioTrack.setPreferredDevice(${preferred.productName}, type=${preferred.type}) = $ok")
                }
                track.addOnRoutingChangedListener(
                    AudioRouting.OnRoutingChangedListener { router ->
                        val routed = router.routedDevice
                        Log.i(TAG, "AudioTrack actual routed device: ${routed?.productName} (type=${routed?.type})")
                    },
                    null,
                )
            }

            audioTrack = track
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

    open fun release() {
        stop()
        unregisterCallbacks()
    }

    companion object {
        private const val TAG = "AudioTrackWrapper"
        private const val BUFFER_SIZE_BYTES = 32768
    }
}
