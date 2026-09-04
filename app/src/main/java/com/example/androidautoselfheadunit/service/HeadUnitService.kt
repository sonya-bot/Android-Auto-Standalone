@file:Suppress("TooManyFunctions", "MaxLineLength")

package com.example.androidautoselfheadunit.service

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import com.example.androidautoselfheadunit.aap.AapMessageRouter
import com.example.androidautoselfheadunit.aap.AapSession
import com.example.androidautoselfheadunit.aap.AapTransport
import com.example.androidautoselfheadunit.aap.ControlChannel
import com.example.androidautoselfheadunit.aap.protocol.Channel
import com.example.androidautoselfheadunit.aap.protocol.ProjectionDisplayProfile
import com.example.androidautoselfheadunit.audio.AudioChannel
import com.example.androidautoselfheadunit.audio.AudioTrackWrapper
import com.example.androidautoselfheadunit.connection.ConnectionManager
import com.example.androidautoselfheadunit.connection.ConnectionState
import com.example.androidautoselfheadunit.connection.SocketHeadUnitConnection
import com.example.androidautoselfheadunit.decoder.VideoDecoder
import com.example.androidautoselfheadunit.input.InputChannel
import com.example.androidautoselfheadunit.input.TouchEventMapper
import com.example.androidautoselfheadunit.video.FragmentReconstructor
import com.example.androidautoselfheadunit.video.VideoChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class HeadUnitService : Service() {
    inner class LocalBinder : Binder() {
        val state: StateFlow<ConnectionState>
            get() = connectionState

        fun configure(profile: ProjectionDisplayProfile) = configureSession(profile)

        fun retry() = retryConnection()

        fun attachSurface(surface: Surface) = this@HeadUnitService.attachSurface(surface)

        fun detachSurface() = this@HeadUnitService.detachSurface()

        fun sendTouchEvent(
            event: MotionEvent,
            width: Int,
            height: Int,
        ) = this@HeadUnitService.sendTouchEvent(event, width, height, 0, 0)

        fun sendTouchEvent(
            event: MotionEvent,
            viewportWidth: Int,
            viewportHeight: Int,
            surfaceLeft: Int,
            surfaceTop: Int,
        ) = this@HeadUnitService.sendTouchEvent(event, viewportWidth, viewportHeight, surfaceLeft, surfaceTop)
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    private val connectionState = _connectionState.asStateFlow()
    private val videoChannel = VideoChannel(FragmentReconstructor())

    private var displayProfile: ProjectionDisplayProfile? = null
    private var touchMapper: TouchEventMapper? = null
    private var connectionManager: ConnectionManager? = null
    private var transport: AapTransport? = null
    private var router: AapMessageRouter? = null
    private var inputChannel: InputChannel? = null
    private var mediaAudioChannel: AudioChannel? = null
    private var sysAudioChannel: AudioChannel? = null
    private var messageLoopJob: Job? = null
    private var stateCollectionJob: Job? = null
    private var decoder: VideoDecoder? = null
    private var surface: Surface? = null
    private var recreatingDecoder = false

    override fun onBind(intent: Intent?): IBinder = binder

    private fun configureSession(profile: ProjectionDisplayProfile) {
        if (connectionManager != null) return
        displayProfile = profile
        touchMapper = TouchEventMapper(projectionWidth = profile.contentWidthPx, projectionHeight = profile.contentHeightPx)
        val socketConnection = SocketHeadUnitConnection()
        val manager =
            ConnectionManager(socketConnection) { connection ->
                AapSession(applicationContext, connection).startHandshake().also { transport = it }
            }
        connectionManager = manager
        stateCollectionJob =
            serviceScope.launch {
                manager.connectionState.collect { state ->
                    _connectionState.value = state
                    if (state is ConnectionState.Connected) startProtocolSession()
                }
            }
        serviceScope.launch { manager.startConnection() }
    }

    private fun startProtocolSession() {
        val currentTransport = transport ?: return
        val profile = displayProfile ?: return
        stopAudioChannels()
        mediaAudioChannel = createAudioChannel(MEDIA_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioAttributes.USAGE_UNKNOWN)
        sysAudioChannel = createAudioChannel(SPEECH_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        inputChannel =
            InputChannel(currentTransport, requireNotNull(touchMapper), serviceScope) { error ->
                handleTransportFailure(error)
            }
        router =
            AapMessageRouter(
                currentTransport,
                ControlChannel(currentTransport, profile),
                videoChannel,
                mapOf(
                    Channel.ID_AUD to requireNotNull(mediaAudioChannel),
                    Channel.ID_AU2 to requireNotNull(sysAudioChannel),
                ),
            ) { handlePeerDisconnect() }
        messageLoopJob?.cancel()
        messageLoopJob =
            serviceScope.launch(Dispatchers.IO) {
                try {
                    while (true) router?.handleMessage(currentTransport.receiveEncrypted())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: java.io.IOException) {
                    Log.e(TAG, "AAP receive loop failed", error)
                    handleTransportFailure(error)
                } catch (error: IllegalStateException) {
                    Log.e(TAG, "AAP receive loop failed", error)
                    handleTransportFailure(error)
                }
            }
    }

    private fun retryConnection() {
        serviceScope.launch {
            teardownProtocolSession()
            connectionManager?.disconnect()
            connectionManager?.startConnection()
        }
    }

    private suspend fun handleTransportFailure(error: Throwable) {
        teardownProtocolSession()
        connectionManager?.reportFailure(error)
    }

    private suspend fun handlePeerDisconnect() {
        teardownProtocolSession()
        connectionManager?.disconnect()
    }

    private fun attachSurface(newSurface: Surface) {
        surface = newSurface
        createDecoder(newSurface)
    }

    private fun createDecoder(target: Surface) {
        val profile = displayProfile ?: return
        decoder?.stop()
        val newDecoder =
            VideoDecoder(
                target,
                profile,
                onFirstFrameRendered = { _connectionState.value = ConnectionState.Projecting },
                onDecoderFailure = { scheduleDecoderRecovery() },
            )
        decoder = newDecoder
        if (newDecoder.start()) videoChannel.attachDecoder(newDecoder)
    }

    private fun scheduleDecoderRecovery() {
        if (recreatingDecoder) return
        recreatingDecoder = true
        serviceScope.launch {
            val target = surface
            if (target?.isValid == true) createDecoder(target)
            recreatingDecoder = false
        }
    }

    private fun detachSurface() {
        surface = null
        videoChannel.detachDecoder()
        decoder?.stop()
        decoder = null
    }

    private fun sendTouchEvent(
        event: MotionEvent,
        viewportWidth: Int,
        viewportHeight: Int,
        surfaceLeft: Int = 0,
        surfaceTop: Int = 0,
    ) {
        touchMapper?.updateGeometry(viewportWidth, viewportHeight, surfaceLeft, surfaceTop)
        inputChannel?.sendTouchEvent(event)
    }

    private fun createAudioChannel(
        sampleRate: Int,
        channelMask: Int,
        usage: Int,
    ): AudioChannel =
        AudioChannel(
            AudioTrackWrapper(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT,
                usage,
                if (usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_UNKNOWN) {
                    AudioAttributes.CONTENT_TYPE_MUSIC
                } else {
                    AudioAttributes.CONTENT_TYPE_SPEECH
                },
                this,
            ),
        )

    private fun teardownProtocolSession() {
        messageLoopJob?.cancel()
        messageLoopJob = null
        inputChannel = null
        router = null
        transport = null
        videoChannel.resetSession()
        stopAudioChannels()
    }

    private fun stopAudioChannels() {
        mediaAudioChannel?.stop()
        sysAudioChannel?.stop()
        mediaAudioChannel = null
        sysAudioChannel = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "onTaskRemoved: Task cleared by user from Recents. Auto-stopping server.")
        AutoStartAccessibilityService.stopServerAutomatically()
        stopSelf()
    }

    override fun onDestroy() {
        detachSurface()
        teardownProtocolSession()
        stateCollectionJob?.cancel()
        runBlocking(Dispatchers.IO) { connectionManager?.disconnect() }
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "HeadUnitService"
        const val MEDIA_SAMPLE_RATE = 48000
        const val SPEECH_SAMPLE_RATE = 16000
    }
}
