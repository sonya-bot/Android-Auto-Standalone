package com.example.androidautoselfheadunit.connection

interface HeadUnitConnection {
    suspend fun connect(
        host: String,
        port: Int,
    )

    suspend fun disconnect()

    suspend fun read(buffer: ByteArray): Int

    suspend fun write(data: ByteArray)
}
