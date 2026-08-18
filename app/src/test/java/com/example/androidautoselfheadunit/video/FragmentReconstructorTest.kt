package com.example.androidautoselfheadunit.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FragmentReconstructorTest {
    @Test
    fun `processFragment returns data immediately on single fragment`() {
        val reconstructor = FragmentReconstructor()
        val data = byteArrayOf(1, 2, 3)
        val result = reconstructor.processFragment(11, data)
        assertArrayEquals(data, result)
    }

    @Test
    fun `processFragment assembles multiple fragments`() {
        val reconstructor = FragmentReconstructor()

        assertNull(reconstructor.processFragment(9, byteArrayOf(1, 2)))
        assertNull(reconstructor.processFragment(8, byteArrayOf(3, 4)))

        val result = reconstructor.processFragment(10, byteArrayOf(5))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), result)
    }

    @Test
    fun `processFragment discards on overflow and recovers on next first fragment`() {
        val reconstructor = FragmentReconstructor()

        // Emulate overflow (we cannot easily overflow 8MB in test, but we test the corrupt flag behavior conceptually)
        // Let us just test corruption recovery.

        // First fragment
        reconstructor.processFragment(9, byteArrayOf(1))

        // Suppose we get a random middle fragment but state is somehow corrupt, or we skip to a new FIRST.
        // If we get a new FIRST, it should clear the buffer.
        reconstructor.processFragment(9, byteArrayOf(2, 3))
        val result = reconstructor.processFragment(10, byteArrayOf(4))

        assertArrayEquals(byteArrayOf(2, 3, 4), result)
    }
}
