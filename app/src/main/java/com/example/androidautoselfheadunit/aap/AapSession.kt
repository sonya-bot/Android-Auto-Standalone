@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation")

package com.example.androidautoselfheadunit.aap

import android.content.Context
import android.util.Log
import com.example.androidautoselfheadunit.aap.security.AapSslContext
import com.example.androidautoselfheadunit.connection.HeadUnitConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class AapSession(
    context: Context,
    private val connection: HeadUnitConnection,
) {
    companion object {
        private const val TAG = "AapSession"
        private val VERSION = byteArrayOf(0, 1, 0, 7)
        private val STATUS_OK = byteArrayOf(8, 0)
    }

    private val sslContext = AapSslContext(context)

    suspend fun startHandshake(): AapTransport {
        return withContext(Dispatchers.IO) {
            // 1. Send Version Request
            connection.write(
                AapFrameCodec.encodePlainControl(
                    AapFrameCodec.MESSAGE_VERSION_REQUEST,
                    VERSION,
                ),
            )

            // 2. Read Version Response
            val response = AapFrameCodec.readPlainControl(connection)
            Log.i(TAG, "Version response payload=${response.payload.toHexString()}")
            if (response.channelId != AapFrameCodec.CHANNEL_CONTROL ||
                response.messageType != AapFrameCodec.MESSAGE_VERSION_RESPONSE
            ) {
                throw IOException(
                    "Unexpected AAP version response: channel=${response.channelId}, type=${response.messageType}",
                )
            }

            // 3. TLS Handshake
            sslContext.performHandshake(connection)
            Log.i(TAG, "TLS handshake completed")

            // 4. Send Status OK
            connection.write(
                AapFrameCodec.encodePlainControl(
                    AapFrameCodec.MESSAGE_AUTH_COMPLETE,
                    STATUS_OK,
                ),
            )
            Log.i(TAG, "Authentication complete sent")

            // 5. Control Channel & Service Discovery
            return@withContext AapTransport(connection, sslContext)
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { "%02x".format(it) }
}
