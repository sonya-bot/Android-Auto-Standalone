package com.example.androidautoselfheadunit.input

import android.util.Log
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
    private val onSendFailure: suspend (Throwable) -> Unit = {},
) {
    companion object {
        private const val TAG = "InputChannel"
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
                MotionEvent.ACTION_CANCEL -> Input.TouchEvent.PointerAction.TOUCH_ACTION_CANCEL
                MotionEvent.ACTION_POINTER_DOWN -> Input.TouchEvent.PointerAction.TOUCH_ACTION_POINTER_DOWN
                MotionEvent.ACTION_POINTER_UP -> Input.TouchEvent.PointerAction.TOUCH_ACTION_POINTER_UP
                else -> return
            }

        val touchEventBuilder =
            Input.TouchEvent.newBuilder()
                .setAction(action)
                .setActionIndex(event.actionIndex)
        repeat(event.pointerCount) { pointerIndex ->
            val mappedX = mapper.mapX(event.getX(pointerIndex))
            val mappedY = mapper.mapY(event.getY(pointerIndex))
            if (action == Input.TouchEvent.PointerAction.TOUCH_ACTION_DOWN || action == Input.TouchEvent.PointerAction.TOUCH_ACTION_UP) {
                Log.d(TAG, "Touch $action pointer=${event.getPointerId(pointerIndex)} raw=(${event.getX(pointerIndex)}, ${event.getY(pointerIndex)}) -> mapped=($mappedX, $mappedY)")
            }
            touchEventBuilder.addPointerData(
                Input.TouchEvent.Pointer.newBuilder()
                    .setPointerId(event.getPointerId(pointerIndex))
                    .setX(mappedX)
                    .setY(mappedY)
                    .build(),
            )
        }

        val inputReport =
            Input.InputReport.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setTouchEvent(touchEventBuilder.build())
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
            try {
                transport.sendEncrypted(message)
            } catch (error: java.io.IOException) {
                onSendFailure(error)
            } catch (error: IllegalStateException) {
                onSendFailure(error)
            }
        }
    }
}
