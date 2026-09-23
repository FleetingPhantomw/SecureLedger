package com.secureledger.core

import com.secureledger.core.protocol.QrFrameAssembler
import com.secureledger.core.protocol.QrSyncFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QrSyncFramesTest {
    @Test
    fun shortPackageUsesCompatibleSingleQr() {
        val payload = "SL1:abc123"
        assertEquals(listOf(payload), QrSyncFrames.encode(payload))
    }

    @Test
    fun largePackageReassemblesOutOfOrderAndIgnoresDuplicateScans() {
        val payload = "SL1:" + "Abc123-_".repeat(390)
        val frames = QrSyncFrames.encode(payload)
        assertTrue(frames.size > 2)
        assertTrue(frames.all { it.length <= QrSyncFrames.MAX_FRAME_CHARS })
        val assembler = QrFrameAssembler()
        assertNull(assembler.accept(frames.last()).completePackage)
        assertNull(assembler.accept(frames.last()).completePackage)
        val rest = frames.dropLast(1).reversed()
        rest.dropLast(1).forEach { assertNull(assembler.accept(it).completePackage) }
        assertEquals(payload, assembler.accept(rest.last()).completePackage)
    }

    @Test
    fun alteredOrMixedFramesNeverComplete() {
        val first = QrSyncFrames.encode("SL1:" + "A".repeat(2200))
        val other = QrSyncFrames.encode("SL1:" + "B".repeat(2200))
        val assembler = QrFrameAssembler()
        assembler.accept(first[0])
        assertThrows(IllegalArgumentException::class.java) { assembler.accept(other[1]) }
        val altered = first[1].dropLast(1) + "C"
        assembler.accept(altered)
        assertThrows(IllegalArgumentException::class.java) { assembler.accept(first[2]) }
    }
}
