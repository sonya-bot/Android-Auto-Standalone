package com.example.androidautoselfheadunit.aap

import java.io.IOException

class ControlChannel(
    private val transport: AapTransport,
) {
    suspend fun doServiceDiscovery() {
        // 1. Send Service Discovery Request
        val request = AapMessage(0, 1, 11.toByte(), byteArrayOf())
        transport.sendEncrypted(request)

        // 2. Receive Service Discovery Response
        val response = transport.receiveEncrypted()
        if (response.payload.isEmpty()) {
            throw IOException("Empty Service Discovery Response")
        }

        // TODO(#1): Parse Protobuf and set up channels
    }
}
