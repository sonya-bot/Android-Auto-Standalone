@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation", "TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth")

package com.example.androidautoselfheadunit.ui

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.androidautoselfheadunit.aap.AapSession
import com.example.androidautoselfheadunit.aap.AapTransport
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    companion object {
        private const val TAG = "MainActivity"
        private const val VIDEO_CHANNEL_ID = 5
        private const val AUDIO_MEDIA_CHANNEL_ID = 2
        private const val AUDIO_NAV_CHANNEL_ID = 4
        private const val AUDIO_SYS_CHANNEL_ID = 6
        private const val AUDIO_SAMPLE_RATE = 48000
    }

    private var videoDecoder: VideoDecoder? = null
    private val touchMapper = TouchEventMapper()
    private var inputChannel: InputChannel? = null
    private var aapMessageRouter: com.example.androidautoselfheadunit.aap.AapMessageRouter? = null
    private var videoChannel: VideoChannel? = null
    private var mediaAudioChannel: AudioChannel? = null
    private var sysAudioChannel: AudioChannel? = null
    private lateinit var statusView: TextView
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private lateinit var connectionManager: ConnectionManager
    private var transport: AapTransport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupFullBleed()

        val surfaceView =
            SurfaceView(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                holder.addCallback(this@MainActivity)
                setOnTouchListener { _, event ->
                    touchMapper.updateScreenSize(width, height)
                    inputChannel?.sendTouchEvent(event)
                    true
                }
            }

        val frameLayout =
            FrameLayout(this).apply {
                addView(surfaceView)
                statusView =
                    TextView(this@MainActivity).apply {
                        text = getString(com.example.androidautoselfheadunit.R.string.status_connecting)
                        setTextColor(android.graphics.Color.WHITE)
                        setBackgroundColor(0xB3000000.toInt())
                        textSize = 18f
                        gravity = android.view.Gravity.CENTER
                        setPadding(32, 24, 32, 24)
                    }
                addView(
                    statusView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.Gravity.CENTER,
                    ),
                )
            }

        setContentView(frameLayout)

        setupConnection()
    }

    private fun setupConnection() {
        val socketConnection = SocketHeadUnitConnection()
        connectionManager =
            ConnectionManager(socketConnection) { conn ->
                val t = AapSession(applicationContext, conn).startHandshake()
                transport = t
                t
            }

        lifecycleScope.launch {
            connectionManager.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        Log.i(TAG, "Connected to Head Unit Server")
                        statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_starting_projection)
                        transport?.let { t ->
                            inputChannel = InputChannel(t, touchMapper, uiScope)
                            val controlChannel =
                                com.example.androidautoselfheadunit.aap.ControlChannel(
                                    t,
                                )
                            aapMessageRouter =
                                com.example.androidautoselfheadunit.aap.AapMessageRouter(
                                    t,
                                    controlChannel,
                                    videoChannel,
                                    mediaAudioChannel,
                                    sysAudioChannel,
                                )
                            startMessageLoop(t, aapMessageRouter!!)
                        }
                    }
                    is ConnectionState.Error -> {
                        Log.e(TAG, "Connection Error", state.cause)
                        statusView.visibility = android.view.View.VISIBLE
                        statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_connection_failed)
                        showErrorDialog()
                    }
                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            connectionManager.startConnection()
        }
    }

    private fun showErrorDialog() {
        ErrorDialogHelper.showHeadUnitServerDownDialog(
            context = this,
            onRetry = {
                statusView.visibility = android.view.View.VISIBLE
                statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_connecting)
                lifecycleScope.launch {
                    connectionManager.startConnection()
                }
            },
            onCancel = {
                finish()
            },
        )
    }

    private fun startMessageLoop(
        t: AapTransport,
        router: com.example.androidautoselfheadunit.aap.AapMessageRouter,
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val msg = t.receiveEncrypted()
                    router.handleMessage(msg)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Message loop error", e)
                runOnUiThread {
                    statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_connection_failed)
                    showErrorDialog()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
        lifecycleScope.launch {
            connectionManager.disconnect()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val decoder =
            VideoDecoder(holder.surface) {
                runOnUiThread { statusView.visibility = android.view.View.GONE }
            }
        decoder.start()
        videoDecoder = decoder
        videoChannel = VideoChannel(FragmentReconstructor(), decoder)

        val mediaAudioWrapper = AudioTrackWrapper(48000, android.media.AudioFormat.CHANNEL_OUT_STEREO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        val sysAudioWrapper = AudioTrackWrapper(16000, android.media.AudioFormat.CHANNEL_OUT_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        mediaAudioWrapper.start()
        sysAudioWrapper.start()
        mediaAudioChannel = AudioChannel(mediaAudioWrapper)
        sysAudioChannel = AudioChannel(sysAudioWrapper)
        aapMessageRouter?.videoChannel = videoChannel
        aapMessageRouter?.mediaAudioChannel = mediaAudioChannel
        aapMessageRouter?.sysAudioChannel = sysAudioChannel
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        // No-op
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        videoDecoder?.stop()
        videoDecoder = null
        videoChannel = null

        mediaAudioChannel?.stop()
        sysAudioChannel?.stop()
        mediaAudioChannel = null
        sysAudioChannel = null
        aapMessageRouter?.videoChannel = null
        aapMessageRouter?.mediaAudioChannel = null
        aapMessageRouter?.sysAudioChannel = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun setupFullBleed() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
