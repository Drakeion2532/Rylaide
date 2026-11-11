// Copyright (c) 2026 Shubhan Punde · https://github.com/Drakeion2532/Rylaide · All rights reserved · sp2532-19f472e3b826
package com.rylaide.app.mesh

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stopwatch for the offline link: how long the first connection took, and how
 * long each outage lasted before the link came back. Shown at the top of the
 * Diagnostics screen so a test can be read without scrolling a long log.
 */
class LinkTimings(private val clock: () -> Long = { System.currentTimeMillis() }) {
    private var startedAt = 0L
    private var downAt = 0L
    @Volatile var firstConnectSeconds = -1.0
        private set
    private val recoveries = CopyOnWriteArrayList<Double>()

    /** Seconds each earlier outage lasted, oldest first. */
    val recoverySeconds: List<Double> get() = recoveries.toList()

    fun reset() {
        startedAt = clock()
        downAt = 0L
        firstConnectSeconds = -1.0
        recoveries.clear()
    }

    /** A rider has just connected. */
    fun onLinked() {
        val now = clock()
        if (firstConnectSeconds < 0) firstConnectSeconds = (now - startedAt) / 1000.0
        if (downAt > 0L) {
            recoveries.add((now - downAt) / 1000.0)
            downAt = 0L
        }
    }

    /** The last rider has just dropped. */
    fun onAllLost() {
        if (downAt == 0L) downAt = clock()
    }

    /** How long the link has been down right now, or -1 when it is up. */
    fun currentOutageSeconds(): Double = if (downAt > 0L) (clock() - downAt) / 1000.0 else -1.0

    fun summary(drops: Int): String {
        val first = if (firstConnectSeconds < 0) "not yet" else "${f1(firstConnectSeconds)} s"
        val back = if (recoveries.isEmpty()) "none yet" else recoveries.joinToString(", ") { f1(it) } + " s"
        val now = currentOutageSeconds()
        val outage = if (now >= 0) "  ·  down for ${f1(now)} s right now" else ""
        return "First connect: $first\nDrops: $drops  ·  time to reconnect: $back$outage"
    }

    private fun f1(x: Double) = String.format(java.util.Locale.US, "%.1f", x)
}
