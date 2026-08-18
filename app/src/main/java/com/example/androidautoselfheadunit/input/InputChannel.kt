package com.example.androidautoselfheadunit.input

import android.view.MotionEvent
import com.example.androidautoselfheadunit.aap.AapMessage
import com.example.androidautoselfheadunit.aap.AapTransport
import com.example.androidautoselfheadunit.aap.protocol.proto.Input
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InputChannel(
    private val transport: AapTransport,
    private val mapper: TouchEventMapper,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val CHANNEL_ID = 3
        private const val MSG_TYPE_INPUT = 0x8001
        private const val FLAG_UNFRAGMENTED: Byte = 11
    }

    fun sendTouchEvent(event: MotionEvent) {
        val action =
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> Input.TouchEvent.PointerAction.TOUCH_ACTION_DOWN
                MotionEvent.ACTION_MOVE -> Input.TouchEvent.PointerAction.TOUCH_ACTION_MOVE
                MotionEvent.ACTION_UP -> Input.TouchEvent.PointerAction.TOUCH_ACTION_UP
                else -> return
            }

        val mappedX = mapper.mapX(event.x)
        val mappedY = mapper.mapY(event.y)

        val pointer =
            Input.TouchEvent.Pointer.newBuilder()
                .setPointerId(0)
                .setX(mappedX)
                .setY(mappedY)
                .build()

        val touchEvent =
            Input.TouchEvent.newBuilder()
                .setAction(action)
                .addPointerData(pointer)
                .build()

        val inputReport =
            Input.InputReport.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setTouchEvent(touchEvent)
                .build()

        val payload = inputReport.toByteArray()
        val message =
            AapMessage(
                channelId = CHANNEL_ID,
                messageType = MSG_TYPE_INPUT,
                flags = FLAG_UNFRAGMENTED,
                payload = payload,
            )

        scope.launch {
            transport.sendEncrypted(message)
        }
    }
}
