// Copyright (c) 2026 Shubhan Punde · https://github.com/Drakeion2532/Rylaide · All rights reserved · sp2532-19f472e3b826
package com.rylaide.app.mesh

/**
 * When to ask for a connection again after one fails or drops.
 *
 * Both phones used to retry at the same moment, so their requests crossed and
 * Nearby failed them with 8012. Now only one phone (the one with the smaller
 * rider id) asks first; the other waits a head start and only asks if that
 * did not work. The wait grows a little with each failed try, with a small
 * random spread so two phones never stay in step.
 */
object ReconnectPolicy {
    const val FIRST_MS = 1000L
    const val STEP_MS = 500L
    const val MAX_MS = 4000L
    const val OTHER_PHONE_HEAD_START_MS = 3000L
    const val JITTER_MS = 400L

    /** [attempt] counts failed tries so far (0 for the first retry). [jitter] is a number from 0 to 1. */
    fun delayMs(attempt: Int, iAmInitiator: Boolean, jitter: Double): Long {
        val backoff = minOf(FIRST_MS + attempt.coerceAtLeast(0) * STEP_MS, MAX_MS)
        val wait = if (iAmInitiator) backoff else backoff + OTHER_PHONE_HEAD_START_MS
        return wait + (jitter.coerceIn(0.0, 1.0) * JITTER_MS).toLong()
    }
}
