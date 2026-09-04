@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation", "TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth")

package com.example.androidautoselfheadunit.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.androidautoselfheadunit.aap.protocol.ProjectionDisplayConfig
import com.example.androidautoselfheadunit.aap.protocol.ProjectionDisplayProfile
import com.example.androidautoselfheadunit.connection.ConnectionState
import com.example.androidautoselfheadunit.service.HeadUnitService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.View

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    companion object {
        private const val TAG = "MainActivity"
        private const val MAX_AUTOSTART_RETRIES = 5
        private const val RETRY_DELAY_MS = 1000L
        private const val SPLASH_FADE_DURATION_MS = 400L
        private const val VIDEO_WIDTH = 1280
        private const val VIDEO_HEIGHT = 720
    }

    private lateinit var displayProfile: ProjectionDisplayProfile
    private lateinit var statusView: TextView
    private lateinit var surfaceView: SurfaceView
    private var splashVideoContainer: FrameLayout? = null
    private var splashTextureView: TextureView? = null
    private var splashMediaPlayer: MediaPlayer? = null
    private var splashSurface: Surface? = null
    private var splashAfd: android.content.res.AssetFileDescriptor? = null
    private var isOpeningVideoFinished = false
    private var isProjectionReady = false
    private var serviceBinder: HeadUnitService.LocalBinder? = null
    private var serviceBound = false
    private var stateCollectionJob: Job? = null
    private var errorDialogVisible = false
    private var activeDialog: androidx.appcompat.app.AlertDialog? = null
    private var isAutoStarting = false
    private var isStoppingServer = false
    private var retryCount = 0
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                val headUnitBinder = binder as HeadUnitService.LocalBinder
                serviceBinder = headUnitBinder
                serviceBound = true
                headUnitBinder.configure(displayProfile)
                if (surfaceView.holder.surface.isValid) {
                    headUnitBinder.attachSurface(surfaceView.holder.surface)
                }
                collectConnectionState(headUnitBinder)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceBound = false
                serviceBinder = null
                stateCollectionJob?.cancel()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupFullBleed()

        val windowBounds = windowManager.currentWindowMetrics.bounds
        displayProfile =
            ProjectionDisplayConfig.forViewport(
                windowBounds.width(),
                windowBounds.height(),
            )
        surfaceView =
            SurfaceView(this).apply {
                holder.addCallback(this@MainActivity)
                setOnTouchListener { v, event ->
                    val parent = v.parent as? android.view.View
                    val viewportW = parent?.width?.takeIf { it > 0 } ?: v.width
                    val viewportH = parent?.height?.takeIf { it > 0 } ?: v.height
                    serviceBinder?.sendTouchEvent(
                        event = event,
                        viewportWidth = viewportW,
                        viewportHeight = viewportH,
                        surfaceLeft = v.left,
                        surfaceTop = v.top,
                    )
                    true
                }
            }

        val frameLayout =
            FrameLayout(this).apply {
                addView(surfaceView)
                addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                    layoutCroppedSurface(surfaceView, right - left, bottom - top)
                }
                statusView =
                    TextView(this@MainActivity).apply {
                        text = getString(com.example.androidautoselfheadunit.R.string.status_connecting)
                        setTextColor(android.graphics.Color.WHITE)
                        setBackgroundColor(Color.BLACK)
                        textSize = 24f
                        gravity = android.view.Gravity.CENTER
                        setPadding(32, 24, 32, 24)
                    }
                addView(
                    statusView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.Gravity.CENTER,
                    ),
                )
                setupSplashVideo(this)
            }

        setContentView(frameLayout)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.i(TAG, "handleOnBackPressed invoked")
                performCleanExit()
            }
        })
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
            Log.i(TAG, "dispatchKeyEvent KEYCODE_BACK intercepted")
            performCleanExit()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        startService(Intent(this, HeadUnitService::class.java))
        if (!serviceBound) {
            bindService(Intent(this, HeadUnitService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "MainActivity onResume called")
        isStoppingServer = false
        if (surfaceView.holder.surface.isValid) {
            serviceBinder?.attachSurface(surfaceView.holder.surface)
        }
        try {
            if (splashMediaPlayer != null && !isOpeningVideoFinished && splashMediaPlayer?.isPlaying == false) {
                Log.i(TAG, "Resuming splashMediaPlayer in onResume")
                splashMediaPlayer?.start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resume splashMediaPlayer", e)
        }
    }

    private fun collectConnectionState(binder: HeadUnitService.LocalBinder) {
        stateCollectionJob?.cancel()
        stateCollectionJob =
            lifecycleScope.launch {
                binder.state.collect { state ->
                    when (state) {
                        is ConnectionState.Connected -> {
                            Log.i(TAG, "Connected to Head Unit Server")
                            isAutoStarting = false
                            isStoppingServer = false
                            retryCount = 0
                            activeDialog?.dismiss()
                            activeDialog = null
                            errorDialogVisible = false
                            if (surfaceView.holder.surface.isValid) {
                                binder.attachSurface(surfaceView.holder.surface)
                            }
                            statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_starting_projection)
                            statusView.visibility = android.view.View.VISIBLE
                        }
                        is ConnectionState.Projecting -> {
                            isAutoStarting = false
                            isStoppingServer = false
                            retryCount = 0
                            activeDialog?.dismiss()
                            activeDialog = null
                            errorDialogVisible = false
                            statusView.visibility = android.view.View.GONE
                            isProjectionReady = true
                            if (isOpeningVideoFinished) {
                                finishSplashVideo()
                            }
                        }
                        is ConnectionState.Error -> {
                            Log.e(TAG, "Connection Error: ${state.cause.message}")
                            if (retryCount >= MAX_AUTOSTART_RETRIES) {
                                finishSplashVideo()
                            }
                            handleConnectionError()
                        }
                        is ConnectionState.Connecting,
                        is ConnectionState.TcpConnected,
                        is ConnectionState.Handshaking,
                        -> {
                            statusView.visibility = android.view.View.VISIBLE
                            statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_connecting)
                        }
                        else -> Unit
                    }
                }
            }
    }

    private fun handleConnectionError() {
        if (isAutoStarting) {
            if (retryCount < MAX_AUTOSTART_RETRIES) {
                retryCount++
                Log.i(TAG, "Server not yet ready after auto-start, retry $retryCount/$MAX_AUTOSTART_RETRIES in ${RETRY_DELAY_MS}ms...")
                statusView.visibility = android.view.View.VISIBLE
                statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_connecting)
                lifecycleScope.launch {
                    delay(RETRY_DELAY_MS)
                    serviceBinder?.retry()
                }
                return
            }
            Log.w(TAG, "Auto-start retry limit reached. Displaying connection error dialog.")
            isAutoStarting = false
            retryCount = 0
            statusView.visibility = android.view.View.VISIBLE
            statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_connection_failed)
            showErrorDialog()
            return
        }

        if (com.example.androidautoselfheadunit.service.AutoStartAccessibilityService.isServiceEnabled(this)) {
            Log.i(TAG, "Accessibility service is enabled. Arming and opening Android Auto settings to auto-start server.")
            isAutoStarting = true
            retryCount = 0
            statusView.visibility = android.view.View.VISIBLE
            statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_auto_starting_server)

            com.example.androidautoselfheadunit.service.AutoStartAccessibilityService.armAutoStart()

            try {
                val intent = Intent("com.google.android.projection.gearhead.SETTINGS").apply {
                    setPackage("com.google.android.projection.gearhead")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch Android Auto settings", e)
                isAutoStarting = false
                showErrorDialog()
            }
        } else {
            Log.i(TAG, "Accessibility service not enabled, showing permission dialog")
            showAccessibilityDialog()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i(TAG, "onNewIntent received, delaying and retrying connection")
        activeDialog?.dismiss()
        activeDialog = null
        errorDialogVisible = false
        statusView.visibility = android.view.View.VISIBLE
        statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_connecting)
        lifecycleScope.launch {
            delay(RETRY_DELAY_MS)
            serviceBinder?.retry()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isAutoStarting && !isStoppingServer) {
            activeDialog?.dismiss()
            activeDialog = null
            errorDialogVisible = false
            retryCount = 0
        }
    }

    override fun onDestroy() {
        try {
            splashMediaPlayer?.stop()
            splashMediaPlayer?.release()
        } catch (_: Exception) {}
        splashMediaPlayer = null
        splashTextureView = null
        splashVideoContainer = null
        if (isFinishing && !isStoppingServer) {
            com.example.androidautoselfheadunit.service.AutoStartAccessibilityService.stopServerAutomatically()
        }
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroy()
    }

    private fun performCleanExit() {
        if (isStoppingServer) return
        if (com.example.androidautoselfheadunit.service.AutoStartAccessibilityService.isServiceEnabled(this)) {
            Log.i(TAG, "Exiting app: Requesting accessibility service to stop Head Unit Server")
            isStoppingServer = true
            statusView.visibility = android.view.View.VISIBLE
            statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_stopping_server)

            window.decorView.postDelayed({
                if (isStoppingServer) {
                    finishAndRemoveTask()
                }
            }, 4000)

            com.example.androidautoselfheadunit.service.AutoStartAccessibilityService.stopServerAutomatically {
                runOnUiThread {
                    finishAndRemoveTask()
                }
            }
        } else {
            finishAndRemoveTask()
        }
    }

    private fun showAccessibilityDialog() {
        if (errorDialogVisible || isFinishing || isDestroyed) return
        activeDialog?.dismiss()
        errorDialogVisible = true
        activeDialog = ErrorDialogHelper.showAccessibilityPermissionDialog(
            context = this,
            onOpenSettings = {
                errorDialogVisible = false
                activeDialog = null
                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            },
            onCancel = {
                errorDialogVisible = false
                activeDialog = null
                performCleanExit()
            },
        )
    }

    private fun showErrorDialog() {
        if (errorDialogVisible || isFinishing || isDestroyed) return
        activeDialog?.dismiss()
        errorDialogVisible = true
        activeDialog = ErrorDialogHelper.showHeadUnitServerDownDialog(
            context = this,
            onRetry = {
                errorDialogVisible = false
                activeDialog = null
                isAutoStarting = false
                retryCount = 0
                statusView.visibility = android.view.View.VISIBLE
                statusView.text = getString(com.example.androidautoselfheadunit.R.string.status_connecting)
                serviceBinder?.retry()
            },
            onCancel = {
                errorDialogVisible = false
                activeDialog = null
                performCleanExit()
            },
        )
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        serviceBinder?.attachSurface(holder.surface)
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
        serviceBinder?.detachSurface()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun setupFullBleed() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun layoutCroppedSurface(
        surfaceView: SurfaceView,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return

        val widthScale = viewportWidth.toDouble() / displayProfile.contentWidthPx
        val heightScale = viewportHeight.toDouble() / displayProfile.contentHeightPx
        val scale = maxOf(widthScale, heightScale)
        val surfaceWidth = (displayProfile.widthPx * scale).roundToInt()
        val surfaceHeight = (displayProfile.heightPx * scale).roundToInt()
        val current = surfaceView.layoutParams
        if (current?.width == surfaceWidth && current.height == surfaceHeight) return

        surfaceView.layoutParams =
            FrameLayout.LayoutParams(surfaceWidth, surfaceHeight, android.view.Gravity.CENTER)
    }

    private fun setupSplashVideo(parent: FrameLayout) {
        val videoUri = Uri.parse("android.resource://$packageName/${com.example.androidautoselfheadunit.R.raw.opening_copen}")
        Log.i(TAG, "Setting up splash video with TextureView: $videoUri")

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnClickListener {
                Log.i(TAG, "Opening video tapped - skipping splash")
                finishSplashVideo()
            }
        }

        val textureView = TextureView(this).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    Log.i(TAG, "TextureView surface available: ${width}x${height}")
                    adjustAspectRatio(this@apply, width, height)
                    try {
                        val surfaceObj = Surface(surface)
                        splashSurface = surfaceObj
                        val afd = resources.openRawResourceFd(com.example.androidautoselfheadunit.R.raw.opening_copen)
                        splashAfd = afd
                        val mp = MediaPlayer().apply {
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                            setSurface(surfaceObj)
                            setOnPreparedListener { player ->
                                Log.i(TAG, "MediaPlayer prepared, starting playback")
                                player.start()
                            }
                            setOnInfoListener { _, what, extra ->
                                Log.i(TAG, "MediaPlayer info: what=$what extra=$extra")
                                false
                            }
                            setOnCompletionListener {
                                Log.i(TAG, "Opening video playback completed")
                                isOpeningVideoFinished = true
                                if (isProjectionReady) {
                                    finishSplashVideo()
                                }
                            }
                            setOnErrorListener { _, what, extra ->
                                Log.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                                finishSplashVideo()
                                true
                            }
                            prepareAsync()
                        }
                        splashMediaPlayer = mp
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize MediaPlayer", e)
                        finishSplashVideo()
                    }
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    adjustAspectRatio(this@apply, width, height)
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    cleanupSplashResources()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }

        container.addView(
            textureView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.Gravity.CENTER,
            ),
        )

        splashVideoContainer = container
        splashTextureView = textureView

        parent.addView(
            container,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun cleanupSplashResources() {
        try {
            splashMediaPlayer?.stop()
            splashMediaPlayer?.release()
        } catch (_: Exception) {}
        splashMediaPlayer = null
        try {
            splashSurface?.release()
        } catch (_: Exception) {}
        splashSurface = null
        try {
            splashAfd?.close()
        } catch (_: Exception) {}
        splashAfd = null
    }

    private fun adjustAspectRatio(textureView: TextureView, viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0) return
        val videoAspect = VIDEO_WIDTH.toFloat() / VIDEO_HEIGHT.toFloat()
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val matrix = android.graphics.Matrix()
        if (viewAspect > videoAspect) {
            val scaleX = (viewHeight * videoAspect) / viewWidth
            matrix.setScale(scaleX, 1.0f, viewWidth / 2f, viewHeight / 2f)
        } else {
            val scaleY = (viewWidth / videoAspect) / viewHeight
            matrix.setScale(1.0f, scaleY, viewWidth / 2f, viewHeight / 2f)
        }
        textureView.setTransform(matrix)
    }

    private fun finishSplashVideo() {
        val container = splashVideoContainer ?: return
        if (container.visibility == View.GONE) return
        runOnUiThread {
            container.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_DURATION_MS)
                .withEndAction {
                    cleanupSplashResources()
                    container.visibility = View.GONE
                }
                .start()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "MainActivity onPause called")
        try {
            if (splashMediaPlayer?.isPlaying == true) {
                splashMediaPlayer?.pause()
            }
        } catch (_: Exception) {}
    }
}
