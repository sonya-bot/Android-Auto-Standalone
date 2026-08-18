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
    private var isCorrupt = false

    fun processFragment(
        flags: Int,
        data: ByteArray,
    ): ByteArray? {
        var result: ByteArray? = null
        when (flags) {
            FLAG_SINGLE -> {
                isCorrupt = false
                buffer.clear()
                result = data
            }
            FLAG_FIRST -> {
                isCorrupt = false
                buffer.clear()
                buffer.put(data)
            }
            FLAG_MIDDLE -> {
                if (!isCorrupt) {
                    if (buffer.position() + data.size > buffer.capacity()) {
                        isCorrupt = true
                        buffer.clear()
                    } else {
                        buffer.put(data)
                    }
                }
            }
            FLAG_LAST -> {
                if (!isCorrupt) {
                    if (buffer.position() + data.size > buffer.capacity()) {
                        isCorrupt = true
                        buffer.clear()
                    } else {
                        buffer.put(data)
                        buffer.flip()
                        val assembled = ByteArray(buffer.limit())
                        buffer.get(assembled)
                        buffer.clear()
                        result = assembled
                    }
                }
            }
        }
        return result
    }
}
