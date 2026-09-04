package com.example.androidautoselfheadunit.video

import java.nio.ByteBuffer

class FragmentReconstructor {
    companion object {
        private const val MAX_BUFFER_SIZE = 1024 * 1024 * 8 // 8MB max
        private const val FLAG_SINGLE = 11
        private const val FLAG_FIRST = 9
        private const val FLAG_MIDDLE = 8
        private const val FLAG_LAST = 10
    }

    private var buffer = ByteBuffer.allocate(MAX_BUFFER_SIZE)
    private var isAssembling = false

    fun processFragment(
        flags: Int,
        data: ByteArray,
    ): ByteArray? {
        var result: ByteArray? = null
        when (flags and 0x03) {
            0x03 -> {
                isAssembling = false
                buffer.clear()
                result = data
            }
            0x01 -> {
                isAssembling = true
                buffer.clear()
                if (!append(data)) isAssembling = false
            }
            0x00 -> {
                if (isAssembling && !append(data)) isAssembling = false
            }
            0x02 -> {
                if (isAssembling && append(data)) {
                    buffer.flip()
                    val assembled = ByteArray(buffer.limit())
                    buffer.get(assembled)
                    result = assembled
                }
                buffer.clear()
                isAssembling = false
            }
        }
        return result
    }

    fun reset() {
        buffer.clear()
        isAssembling = false
    }

    private fun append(data: ByteArray): Boolean {
        if (buffer.position() + data.size > buffer.capacity()) {
            buffer.clear()
            return false
        }
        buffer.put(data)
        return true
    }
}
