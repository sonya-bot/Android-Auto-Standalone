@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation", "TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth")

package com.example.androidautoselfheadunit.connection

import com.example.androidautoselfheadunit.common.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConnectionManager(
    private val connection: HeadUnitConnection,
    private val performHandshake: suspend (HeadUnitConnection) -> Any?,
) {
    private val stateMutex = Mutex()
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    suspend fun startConnection() =
        stateMutex.withLock {
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
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e)
                try {
                    connection.disconnect()
                } catch (ignored: Exception) {
                    // detekt workaround
                    ignored.printStackTrace()
                }
            }
        }

    suspend fun reportFailure(cause: Throwable) =
        stateMutex.withLock {
            disconnectConnection()
            _connectionState.value = ConnectionState.Error(cause)
        }

    suspend fun disconnect() =
        stateMutex.withLock {
            disconnectConnection()
            _connectionState.value = ConnectionState.Disconnected
        }

    private suspend fun disconnectConnection() {
        try {
            connection.disconnect()
        } catch (_: Exception) {
            // The state transition still has to complete if the peer already closed the socket.
        }
    }
}
