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
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.roundToInt

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
    private val mainHandler: Handler? by lazy {
        try {
            Handler(Looper.getMainLooper())
        } catch (_: Exception) {
            null
        }
    }
    private var currentTargetDeviceId: Int? = null

    private val routingChangeRunnable = Runnable {
        applyDeviceRouting()
    }

    private val audioDeviceCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    Log.i(TAG, "Audio devices added: ${addedDevices?.joinToString { "${it.productName}(type=${it.type})" }}")
                    scheduleDeviceRoutingChange()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    Log.i(TAG, "Audio devices removed: ${removedDevices?.joinToString { "${it.productName}(type=${it.type})" }}")
                    scheduleDeviceRoutingChange()
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
                    scheduleDeviceRoutingChange()
                }
            }
        }

    private fun scheduleDeviceRoutingChange() {
        mainHandler?.removeCallbacks(routingChangeRunnable)
        mainHandler?.postDelayed(routingChangeRunnable, DEBOUNCE_DELAY_MS)
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
        mainHandler?.removeCallbacks(routingChangeRunnable)
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
    private fun applyDeviceRouting() {
        val track = audioTrack ?: return
        val target = findBestOutputDevice()
        val targetId = target?.id
        if (targetId == currentTargetDeviceId && track.state == AudioTrack.STATE_INITIALIZED) {
            Log.i(TAG, "Device routing unchanged (id=$targetId, name=${target?.productName}). Ignoring.")
            return
        }
        currentTargetDeviceId = targetId
        Log.i(TAG, "Applying new device routing: ${target?.productName} (type=${target?.type}, id=$targetId)")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val ok = track.setPreferredDevice(target)
            Log.i(TAG, "setPreferredDevice(${target?.productName}) result: $ok")
            if (!ok && target != null) {
                Log.w(TAG, "setPreferredDevice returned false, recreating AudioTrack")
                stop()
                start()
                return
            }
        }
        ensureAudibleVolume(target)
    }

    private fun ensureAudibleVolume(device: AudioDeviceInfo?) {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                Log.i(TAG, "STREAM_MUSIC is muted, unmuting...")
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            }
            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            Log.i(TAG, "STREAM_MUSIC volume: $currentVol / $maxVol for ${device?.productName ?: "default"}")
            if (currentVol == 0 && maxVol > 0) {
                val safeVol = (maxVol * 0.5f).roundToInt().coerceAtLeast(1)
                Log.i(TAG, "Volume is 0. Automatically setting STREAM_MUSIC volume to $safeVol")
                am.setStreamVolume(AudioManager.STREAM_MUSIC, safeVol, AudioManager.FLAG_SHOW_UI)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check or set audio volume", e)
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

            val preferred = findBestOutputDevice()
            currentTargetDeviceId = preferred?.id
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
                ensureAudibleVolume(preferred)
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
        mainHandler?.removeCallbacks(routingChangeRunnable)
        val track = audioTrack ?: return
        audioTrack = null
        currentTargetDeviceId = null
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
        private const val DEBOUNCE_DELAY_MS = 250L
    }
}
