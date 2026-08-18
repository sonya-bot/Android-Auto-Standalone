package com.example.androidautoselfheadunit.connection

import com.example.androidautoselfheadunit.aap.AapSession
import com.example.androidautoselfheadunit.common.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

class ConnectionManager(
    private val connection: HeadUnitConnection,
    private val performHandshake: suspend (HeadUnitConnection) -> Unit = {
        AapSession(it).startHandshake()
    },
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    suspend fun startConnection() {
        val currentState = _connectionState.value
        if (currentState !is ConnectionState.Idle &&
            currentState !is ConnectionState.Disconnected &&
            currentState !is ConnectionState.Error
        ) {
            return
        }

        _connectionState.value = ConnectionState.Connecting
        try {
            connection.connect(Constants.HEAD_UNIT_HOST, Constants.HEAD_UNIT_PORT)
            _connectionState.value = ConnectionState.TcpConnected

            _connectionState.value = ConnectionState.Handshaking
            performHandshake(connection)

            _connectionState.value = ConnectionState.Connected
        } catch (e: IOException) {
            _connectionState.value = ConnectionState.Error(e)
            try {
                connection.disconnect()
            } catch (ignored: Exception) {
                // detekt workaround
                ignored.printStackTrace()
            }
        }
    }

    suspend fun disconnect() {
        try {
            connection.disconnect()
        } finally {
            _connectionState.value = ConnectionState.Disconnected
        }
    }
}
