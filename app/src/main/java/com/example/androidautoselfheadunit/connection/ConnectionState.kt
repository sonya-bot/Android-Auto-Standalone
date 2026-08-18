package com.example.androidautoselfheadunit.connection

sealed interface ConnectionState {
    data object Idle : ConnectionState

    data object Connecting : ConnectionState

    data object TcpConnected : ConnectionState

    data object Handshaking : ConnectionState

    data object Connected : ConnectionState

    data object StartingProjection : ConnectionState

    data object Projecting : ConnectionState

    data class Error(val cause: Throwable) : ConnectionState

    data object Disconnected : ConnectionState
}
