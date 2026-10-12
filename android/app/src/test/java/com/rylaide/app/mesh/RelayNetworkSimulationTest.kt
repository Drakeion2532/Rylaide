package com.rylaide.app.mesh

import java.util.ArrayDeque
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the real relay rules over pretend groups of riders, with pretend radio
 * links between them. Not the radio itself, but it proves the logic: voice
 * crosses several riders, never loops forever, never plays twice, and uses a
 * second path when one path loses packets.
 */
class RelayNetworkSimulationTest {

    private class Sim(val n: Int, links: List<Pair<Int, Int>>, private val loss: Double = 0.0, seed: Long = 1) {
        val neighbours = Array(n) { mutableSetOf<Int>() }
        val routers = Array(n) { RelayRouter(origin(it)) }
        val played = Array(n) { HashMap<Long, Int>() }   // (origin, seq) -> times played
        val down = mutableSetOf<Int>()
        var transmissions = 0
        private val rng = Random(seed)

        init { for ((a, b) in links) { neighbours[a].add(b); neighbours[b].add(a) } }

        fun origin(i: Int) = 1000 + i

        private class Hop(val from: Int, val to: Int, val bytes: ByteArray)

        fun send(from: Int, seq: Int, type: Byte = RelayPacket.TYPE_AUDIO) {
            val msg = RelayPacket.encode(type, origin(from), RelayPacket.DEFAULT_TTL, seq, byteArrayOf(1, 2, 3))
            val queue = ArrayDeque<Hop>()
            for (nb in neighbours[from]) queue.add(Hop(from, nb, msg))
            while (queue.isNotEmpty()) {
                val h = queue.poll()
                if (h.to in down || h.from in down) continue
                if (loss > 0 && rng.nextDouble() < loss) continue
                transmissions++
                val p = RelayPacket.parse(h.bytes)!!
                val d = routers[h.to].accept(p.type, p.origin, p.seq, p.ttl)
                if (d.forward) {
                    val passed = RelayPacket.withTtl(h.bytes, p.ttl - 1)
                    for (nb in neighbours[h.to]) if (nb != h.from) queue.add(Hop(h.to, nb, passed))
                }
                if (d.deliver) {
                    val key = (p.origin.toLong() shl 20) or p.seq.toLong()
                    played[h.to][key] = (played[h.to][key] ?: 0) + 1
                }
            }
        }

        fun timesPlayed(at: Int, from: Int, seq: Int): Int =
            played[at][(origin(from).toLong() shl 20) or seq.toLong()] ?: 0

        fun reaches(at: Int, from: Int, seq: Int) = timesPlayed(at, from, seq) > 0
    }

    private fun line(n: Int) = (0 until n - 1).map { it to it + 1 }
    private fun ring(n: Int) = (0 until n).map { it to (it + 1) % n }
    private fun full(n: Int) = (0 until n).flatMap { a -> (a + 1 until n).map { b -> a to b } }

    @Test
    fun lineOfFiveRidersAllHearTheFirstOne() {
        val sim = Sim(5, line(5))
        sim.send(from = 0, seq = 1)
        for (i in 1..4) assertTrue("rider $i should hear rider 0", sim.reaches(i, 0, 1))
    }

    @Test
    fun hopLimitStopsVoiceAfterFourRelays() {
        val sim = Sim(6, line(6))
        sim.send(0, 1)
        assertTrue(sim.reaches(4, 0, 1))
        assertFalse("rider 5 is five hops away, beyond the limit", sim.reaches(5, 0, 1))
    }

    @Test
    fun middleRiderIsHeardBothWays() {
        val sim = Sim(5, line(5))
        sim.send(2, 7)
        for (i in listOf(0, 1, 3, 4)) assertTrue(sim.reaches(i, 2, 7))
    }

    @Test
    fun ringDeliversExactlyOncePerRiderAndNeverLoops() {
        val sim = Sim(6, ring(6))
        sim.send(0, 1)
        for (i in 1..5) assertEquals("rider $i plays it once", 1, sim.timesPlayed(i, 0, 1))
        assertEquals("sender must not hear itself", 0, sim.timesPlayed(0, 0, 1))
        assertTrue("traffic stays bounded: ${sim.transmissions}", sim.transmissions <= 2 * 6 * 2)
    }

    @Test
    fun fullyConnectedGroupPlaysOnceAndTrafficIsBounded() {
        val sim = Sim(6, full(6))
        sim.send(3, 9)
        for (i in 0 until 6) if (i != 3) assertEquals(1, sim.timesPlayed(i, 3, 9))
        assertTrue("transmissions ${sim.transmissions}", sim.transmissions <= 6 * 5)
    }

    @Test
    fun longStreamOfVoicePlaysEveryPacketOnceInARing() {
        val sim = Sim(5, ring(5))
        for (s in 0 until 500) sim.send(0, s and 0xFFFF)
        for (i in 1..4) for (s in 0 until 500) assertEquals(1, sim.timesPlayed(i, 0, s))
    }

    @Test
    fun counterWrappingAroundDuringALongTalkStillWorks() {
        val sim = Sim(4, line(4))
        for (k in 0 until 40) sim.send(0, (65520 + k) and 0xFFFF)
        for (k in 0 until 40) assertTrue(sim.reaches(3, 0, (65520 + k) and 0xFFFF))
    }

    @Test
    fun twoPathsDeliverMoreThanOneUnderPacketLoss() {
        val packets = 1000
        fun rate(sim: Sim, at: Int): Double {
            for (s in 0 until packets) sim.send(0, s)
            return (0 until packets).count { sim.reaches(at, 0, it) } / packets.toDouble()
        }
        val lineRate = rate(Sim(4, line(4), loss = 0.2, seed = 5), at = 3)      // one path of 3 hops
        val ringRate = rate(Sim(6, ring(6), loss = 0.2, seed = 5), at = 3)      // two paths of 3 hops
        assertTrue("ring $ringRate should beat line $lineRate", ringRate > lineRate + 0.1)
    }

    @Test
    fun ridersBehindABrokenRiderAreCutOffButTheRestKeepWorking() {
        val sim = Sim(5, line(5))
        sim.down.add(2)
        sim.send(0, 1)
        assertTrue(sim.reaches(1, 0, 1))
        assertFalse(sim.reaches(3, 0, 1))
        assertFalse(sim.reaches(4, 0, 1))
    }

    @Test
    fun ringSurvivesOneRiderDroppingOut() {
        val sim = Sim(6, ring(6))
        sim.down.add(1)
        sim.send(0, 1)
        // Rider 2 is reached the long way round: 0-5-4-3-2 (4 hops).
        for (i in listOf(2, 3, 4, 5)) assertTrue("rider $i should still hear rider 0", sim.reaches(i, 0, 1))
    }

    @Test
    fun presenceMessagesTravelLikeVoice() {
        val sim = Sim(4, line(4))
        sim.send(0, 1, RelayPacket.TYPE_PRESENCE)
        for (i in 1..3) assertTrue(sim.reaches(i, 0, 1))
    }

    @Test
    fun everyoneTalkingAtOnceInAFullGroupStaysCorrect() {
        val sim = Sim(6, full(6))
        for (s in 0 until 100) for (from in 0 until 6) sim.send(from, s)
        for (at in 0 until 6) for (from in 0 until 6) for (s in 0 until 100) {
            assertEquals(if (at == from) 0 else 1, sim.timesPlayed(at, from, s))
        }
    }

    @Test
    fun randomGroupsAreFullyCoveredWithinTheHopLimit() {
        val rng = Random(42)
        repeat(20) {
            val n = 6 + rng.nextInt(4)
            val links = line(n).toMutableList() // always connected
            repeat(3) { links.add(rng.nextInt(n) to rng.nextInt(n)) }
            val clean = links.filter { it.first != it.second }
            val sim = Sim(n, clean)
            sim.send(0, 1)
            // Breadth-first distance from rider 0, to know who should be within 4 hops.
            val dist = IntArray(n) { -1 }; dist[0] = 0
            val q = ArrayDeque<Int>(); q.add(0)
            while (q.isNotEmpty()) { val x = q.poll(); for (y in sim.neighbours[x]) if (dist[y] < 0) { dist[y] = dist[x] + 1; q.add(y) } }
            for (i in 1 until n) {
                if (dist[i] in 1..RelayPacket.DEFAULT_TTL) {
                    assertEquals("rider $i at distance ${dist[i]}", 1, sim.timesPlayed(i, 0, 1))
                } else {
                    assertEquals(0, sim.timesPlayed(i, 0, 1))
                }
            }
        }
    }
}
