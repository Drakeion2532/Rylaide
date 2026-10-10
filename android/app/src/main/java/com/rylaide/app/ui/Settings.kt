package com.rylaide.app.ui

import java.time.LocalTime

/**
 * Auto follows the actual clock, not just the phone's system dark-mode
 * setting - a rider heading out at 6am with their phone still in "light
 * mode" system-wide should still get a sensible theme here. Light/Dark let
 * the rider override that and lock in one look regardless of time.
 */
enum class ThemeMode {
    AUTO,
    LIGHT,
    DARK
}

/**
 * Simple day/night boundary - dark from 7pm to 6am, light the rest of the
 * day. Good enough for v1; a later version could use actual sunrise/sunset
 * for the rider's location instead of a fixed clock boundary.
 */
fun isNightTimeNow(currentTime: LocalTime = LocalTime.now()): Boolean {
    return currentTime.isAfter(LocalTime.of(19, 0)) || currentTime.isBefore(LocalTime.of(6, 0))
}

fun resolveIsDarkTheme(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.AUTO -> isNightTimeNow()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
