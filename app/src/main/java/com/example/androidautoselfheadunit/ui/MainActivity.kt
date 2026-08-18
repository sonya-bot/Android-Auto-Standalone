package com.example.androidautoselfheadunit.ui

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.androidautoselfheadunit.decoder.VideoDecoder
import com.example.androidautoselfheadunit.input.InputChannel
import com.example.androidautoselfheadunit.input.TouchEventMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private var videoDecoder: VideoDecoder? = null
    private val touchMapper = TouchEventMapper()
    private var inputChannel: InputChannel? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            }

        setContentView(frameLayout)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        videoDecoder = VideoDecoder(holder.surface)
        videoDecoder?.start()
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
    }
}
