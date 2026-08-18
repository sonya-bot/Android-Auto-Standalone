package com.example.androidautoselfheadunit.connection

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ConnectionManagerTest {
    private class FakeHeadUnitConnection(
        var shouldFailConnect: Boolean = false,
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

        override suspend fun read(buffer: ByteArray): Int = -1

        override suspend fun write(data: ByteArray) {
            // Ignored in tests for now to avoid empty block
            data.size
        }
    }

    @Test
    fun `startConnection updates state to TcpConnected on success`() =
        runTest {
            val fakeConnection = FakeHeadUnitConnection()
            val manager = ConnectionManager(fakeConnection)

            assertEquals(ConnectionState.Idle, manager.connectionState.value)

            manager.startConnection()

            assertEquals(ConnectionState.TcpConnected, manager.connectionState.value)
            assertTrue(fakeConnection.isConnected)
        }

    @Test
    fun `startConnection updates state to Error on failure`() =
        runTest {
            val fakeConnection = FakeHeadUnitConnection(shouldFailConnect = true)
            val manager = ConnectionManager(fakeConnection)

            manager.startConnection()

            val state = manager.connectionState.value
            assertTrue(state is ConnectionState.Error)
            assertEquals("Connection failed", (state as ConnectionState.Error).cause.message)
        }

    @Test
    fun `disconnect updates state to Disconnected`() =
        runTest {
            val fakeConnection = FakeHeadUnitConnection()
            val manager = ConnectionManager(fakeConnection)

            manager.startConnection()
            manager.disconnect()

            assertEquals(ConnectionState.Disconnected, manager.connectionState.value)
            assertTrue(!fakeConnection.isConnected)
        }
}
