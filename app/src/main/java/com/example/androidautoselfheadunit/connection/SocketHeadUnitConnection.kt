package com.example.androidautoselfheadunit.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class SocketHeadUnitConnection : HeadUnitConnection {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    companion object {
        private const val TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 15000
    }

    override suspend fun connect(
        host: String,
        port: Int,
    ) {
        withContext(Dispatchers.IO) {
            socket =
                Socket().apply {
                    connect(InetSocketAddress(host, port), TIMEOUT_MS)
                    soTimeout = READ_TIMEOUT_MS
                    keepAlive = true
                }
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                inputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            try {
                outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            try {
                socket?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            inputStream = null
            outputStream = null
            socket = null
        }
    }

    override suspend fun read(buffer: ByteArray): Int {
        return withContext(Dispatchers.IO) {
            val inStream = checkNotNull(inputStream) { "Socket is not connected" }
            inStream.read(buffer)
        }
    }

    override suspend fun write(data: ByteArray) {
        withContext(Dispatchers.IO) {
            val outStream = checkNotNull(outputStream) { "Socket is not connected" }
            outStream.write(data)
            outStream.flush()
        }
    }
}
