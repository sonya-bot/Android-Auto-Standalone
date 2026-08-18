package com.example.androidautoselfheadunit.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlinx.coroutines.test.runTest

class ConnectionManagerTest {
    private class FakeHeadUnitConnection(
        var shouldFailConnect: Boolean = false,
        var shouldFailHandshake: Boolean = false,
    ) : HeadUnitConnection {
        var isConnected = false

        override suspend fun connect(
            host: String,
            port: Int,
        ) {
            if (shouldFailConnect) {
                throw IOException("Connection failed")
            }
            isConnected = true
        }

        override suspend fun disconnect() {
            isConnected = false
        }

        override suspend fun read(buffer: ByteArray): Int {
            if (shouldFailHandshake) return -1
            return if (shouldFailHandshake) {
                -1
            } else {
                // Mock version response and service discovery response
                buffer[0] = 0
                buffer[4] = 0
                buffer[5] = 2
                8
            }
        }

        override suspend fun write(data: ByteArray) {
            // Ignored in tests for now to avoid empty block
            data.size
        }
    }

    @Test
    fun `startConnection updates state to Connected on success`() =
        runTest {
            val fakeConnection = FakeHeadUnitConnection()
            val manager =
                ConnectionManager(
                    fakeConnection,
                    performHandshake = {
                        if (fakeConnection.shouldFailHandshake) {
                            throw java.io.IOException("Handshake failed")
                        }
                    },
                )

            assertEquals(ConnectionState.Idle, manager.connectionState.value)

            manager.startConnection()

            assertEquals(ConnectionState.Connected, manager.connectionState.value)
            assertTrue(fakeConnection.isConnected)
        }

    @Test
    fun `startConnection updates state to Error on connect failure`() =
        runTest {
            val fakeConnection = FakeHeadUnitConnection(shouldFailConnect = true)
            val manager =
                ConnectionManager(
                    fakeConnection,
                    performHandshake = {
                        if (fakeConnection.shouldFailHandshake) {
                            throw java.io.IOException("Handshake failed")
                        }
                    },
                )

            manager.startConnection()

            val state = manager.connectionState.value
            assertTrue(state is ConnectionState.Error)
            assertEquals("Connection failed", (state as ConnectionState.Error).cause.message)
        }

    @Test
    fun `startConnection updates state to Error on handshake failure`() =
        runTest {
            val fakeConnection = FakeHeadUnitConnection(shouldFailHandshake = true)
            val manager =
                ConnectionManager(
                    fakeConnection,
                    performHandshake = {
                        if (fakeConnection.shouldFailHandshake) {
                            throw java.io.IOException("Handshake failed")
                        }
                    },
                )

            manager.startConnection()

            val state = manager.connectionState.value
            assertTrue(state is ConnectionState.Error)
            assertEquals(
                "Handshake failed",
                (state as ConnectionState.Error).cause.message,
            )
        }

    @Test
    fun `disconnect updates state to Disconnected`() =
        runTest {
            val fakeConnection = FakeHeadUnitConnection()
            val manager =
                ConnectionManager(
                    fakeConnection,
                    performHandshake = {
                        if (fakeConnection.shouldFailHandshake) {
                            throw java.io.IOException("Handshake failed")
                        }
                    },
                )

            manager.startConnection()
            manager.disconnect()

            assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
            assertTrue(!fakeConnection.isConnected)
        }
}
