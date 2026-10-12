package com.rylaide.app.mesh

/**
 * Decides what a phone does with a message it just received, so riders who are
 * out of direct range can still be heard through the riders between them.
 *
 *  - A message that is new gets played and passed on to every other phone we
 *    are connected to (never back to the one it came from).
 *  - A message we have already seen (it reached us by another path) is dropped.
 *    The first copy to arrive wins, so two paths also give a second chance when
 *    one path loses a packet.
 *  - A message that has no hops left is played but not passed on.
 *  - Our own messages coming back around a loop are dropped.
 *
 * Plain Kotlin with no Android parts, so it can be tested on any computer.
 */
class RelayRouter(private val myOrigin: Int, private val window: Int = 128) {

    class Decision(val deliver: Boolean, val forward: Boolean) {
        companion object {
            val DROP = Decision(deliver = false, forward = false)
        }
    }

    /** What we have seen from one origin: newest sequence number and a ring of recent ones. */
    private class History(window: Int) {
        var newest = -1L
        val seen = BooleanArray(window)
    }

    private val histories = HashMap<Long, History>()

    @Synchronized
    fun accept(type: Byte, origin: Int, seq: Int, ttl: Int): Decision {
        if (origin == myOrigin) return Decision.DROP
        val key = (type.toLong() shl 32) or (origin.toLong() and 0xFFFFFFFFL)
        val h = histories.getOrPut(key) { History(window) }

        val candidate: Long
        if (h.newest < 0) {
            candidate = seq.toLong()
            h.newest = candidate
            h.seen[slot(candidate)] = true
        } else {
            // Work out the real position even when the 16-bit counter wraps around.
            val diff = ((seq - (h.newest and 0xFFFF).toInt()) shl 16) shr 16
            candidate = h.newest + diff
            if (candidate > h.newest) {
                val advance = candidate - h.newest
                if (advance >= window) {
                    h.seen.fill(false)
                } else {
                    for (v in h.newest + 1..candidate) h.seen[slot(v)] = false
                }
                h.newest = candidate
                h.seen[slot(candidate)] = true
            } else if (h.newest - candidate >= window) {
                return Decision.DROP // far too old to matter
            } else if (h.seen[slot(candidate)]) {
                return Decision.DROP // already seen by another path
            } else {
                h.seen[slot(candidate)] = true // late but never seen: still useful
            }
        }
        return Decision(deliver = true, forward = ttl > 1)
    }

    private fun slot(v: Long): Int = (((v % window) + window) % window).toInt()
}
