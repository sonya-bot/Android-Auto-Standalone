package com.example.androidautoselfheadunit.decoder

interface VideoFrameDecoder {
    fun decode(
        data: ByteArray,
        offset: Int,
        length: Int,
        isConfig: Boolean,
    ): Boolean
}
