package com.rylaide.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JitterBufferTest {

    private fun data(n: Int) = byteArrayOf(n.toByte())

    private fun JitterBuffer.takeValue(): Int? = when (val r = take()) {
        is JitterBuffer.Result.Packet -> r.data[0].toInt() and 0xFF
        JitterBuffer.Result.Missing -> -1
        JitterBuffer.Result.Nothing -> null
    }

    @Test
    fun waitsForPrebufferBeforePlaying() {
        val b = JitterBuffer(prebufferFrames = 4)
        for (i in 0..2) b.put(i, data(i))
        assertEquals(null, b.takeValue())
        b.put(3, data(3))
        assertEquals(0, b.takeValue())
    }

    @Test
    fun putsOutOfOrderPacketsBackInOrder() {
        val b = JitterBuffer(prebufferFrames = 4)
        listOf(0, 2, 1, 3).forEach { b.put(it, data(it)) }
        assertEquals(listOf(0, 1, 2, 3), (0..3).map { b.takeValue() })
    }

    @Test
    fun reportsAMissingPacketInTheMiddleOfSpeech() {
        val b = JitterBuffer(prebufferFrames = 4)
        listOf(0, 1, 3, 4).forEach { b.put(it, data(it)) }
        assertEquals(listOf(0, 1, -1, 3, 4), (0..4).map { b.takeValue() })
        assertEquals(1L, b.concealed)
    }

    @Test
    fun handlesSequenceNumberWrapAround() {
        val b = JitterBuffer(prebufferFrames = 4)
        listOf(65534, 65535, 0, 1).forEach { b.put(it, data(it and 0xFF)) }
        assertEquals(listOf(254, 255, 0, 1), (0..3).map { b.takeValue() })
    }

    @Test
    fun dropsPacketsThatArriveTooLate() {
        val b = JitterBuffer(prebufferFrames = 2)
        b.put(0, data(0)); b.put(1, data(1)); b.put(2, data(2))
        b.takeValue(); b.takeValue()   // plays 0 and 1
        b.put(0, data(0))               // 0 again, too late
        assertEquals(1L, b.lateDropped)
    }

    @Test
    fun goesIdleAfterSilenceAndPrebuffersAgain() {
        val b = JitterBuffer(prebufferFrames = 2)
        b.put(0, data(0)); b.put(1, data(1))
        b.takeValue(); b.takeValue()
        repeat(7) { assertEquals(null, b.takeValue()) }   // rider stopped talking
        b.put(5, data(5))
        assertEquals(null, b.takeValue())                  // still prebuffering
        b.put(6, data(6))
        assertEquals(5, b.takeValue())
    }

    @Test
    fun neverHoldsMoreThanTheMaximum() {
        val b = JitterBuffer(prebufferFrames = 2, maxFrames = 5)
        for (i in 0 until 12) b.put(i, data(i))
        assertTrue(b.bufferedFrames <= 5)
    }

    @Test
    fun aBurstOfPacketsIsNotKeptAsPermanentDelay() {
        val b = JitterBuffer(prebufferFrames = 3, maxFrames = 12)
        for (i in 0 until 10) b.put(i, data(i))   // ten arrive at once
        val played = ArrayList<Int>()
        repeat(4) { b.takeValue()?.let { v -> played.add(v) } }
        assertTrue("delay should drain, still buffered ${b.bufferedFrames}", b.bufferedFrames <= 5)
        assertTrue("some frames were skipped", b.skipped >= 2)
        assertEquals("what is played stays in order", played.sorted(), played)
    }

    @Test
    fun steadyFlowIsNeverSkipped() {
        val b = JitterBuffer(prebufferFrames = 3)
        for (i in 0 until 3) b.put(i, data(i))
        for (i in 3 until 200) {
            b.put(i, data(i and 0xFF))
            assertTrue(b.takeValue() != null)
        }
        assertEquals(0L, b.skipped)
        assertEquals(0L, b.concealed)
    }

    @Test
    fun catchUpDoesNotJumpOverAGap() {
        val b = JitterBuffer(prebufferFrames = 3, maxFrames = 12)
        // 0,1,2 then a gap at 3, then 4..9 queued.
        for (i in listOf(0, 1, 2, 4, 5, 6, 7, 8, 9)) b.put(i, data(i))
        val got = (0 until 6).map { b.takeValue() }
        assertTrue("the missing packet 3 is reported, not skipped silently", got.contains(-1))
    }

    @Test
    fun adaptiveBufferStartsShallowThenGrowsWhenPacketsGoMissing() {
        val b = JitterBuffer(prebufferFrames = 2, adaptive = true)
        assertEquals(2, b.prebufferNow)
        for (i in listOf(0, 1, 3, 4)) b.put(i, data(i))
        repeat(5) { b.takeValue() }                      // packet 2 is missing
        assertTrue("grew to ${b.prebufferNow}", b.prebufferNow >= 3)
    }

    @Test
    fun adaptiveBufferShrinksAgainAfterALongCleanStretch() {
        val b = JitterBuffer(prebufferFrames = 2, adaptive = true)
        for (i in listOf(0, 1, 3, 4)) b.put(i, data(i))
        repeat(5) { b.takeValue() }
        val grown = b.prebufferNow
        var seq = 5
        for (i in 0 until 4)  { b.put(seq, data(seq and 0xFF)); seq++ }
        repeat(700) { b.put(seq, data(seq and 0xFF)); seq++; b.takeValue() }
        assertTrue("shrank from $grown to ${b.prebufferNow}", b.prebufferNow < grown)
        assertTrue(b.prebufferNow >= 2)
    }

    @Test
    fun nonAdaptiveBufferNeverChangesDepth() {
        val b = JitterBuffer(prebufferFrames = 3)
        for (i in listOf(0, 1, 2, 4, 5, 6)) b.put(i, data(i))
        repeat(7) { b.takeValue() }
        assertEquals(3, b.prebufferNow)
    }
}
