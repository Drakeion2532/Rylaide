// Copyright (c) 2026 Shubhan Punde · https://github.com/Drakeion2532/Rylaide · All rights reserved · sp2532-19f472e3b826
package com.rylaide.app.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rylaide.app.mesh.SessionState
import kotlinx.coroutines.delay

/** What the rider sees for each connection state, in plain words. */
private fun stateLabel(s: SessionState): String = when (s) {
    SessionState.CONNECTING -> "Looking for riders…"
    SessionState.BOTH -> "Connected · internet + offline"
    SessionState.INTERNET -> "Connected · internet"
    SessionState.MESH -> "Connected · offline link"
    SessionState.DEGRADED -> "Weak connection"
    SessionState.DISCONNECTED -> "No riders reachable"
}

private fun stateColors(s: SessionState): Pair<Color, Color> = when (s) {
    SessionState.BOTH, SessionState.INTERNET, SessionState.MESH -> Color(0xFFDDF3E4) to Color(0xFF1D9E75)
    SessionState.CONNECTING, SessionState.DEGRADED -> Color(0xFFFFF0D6) to Color(0xFF9A6200)
    SessionState.DISCONNECTED -> Color(0xFFFBE3E3) to Color(0xFFB3261E)
}

/**
 * The screen riders spend the most time on, now driven by the real voice engine:
 * live connection state, real riders, the tap-to-talk button and the ride code to share.
 * Touch targets follow gloveMode (a rider preference set on Home).
 *
 * Colors come from MaterialTheme.colorScheme; the only hardcoded colors are semantic ones
 * (red for live, green/amber/red for connection) that keep their meaning in any theme.
 *
 * The map and SOS are not built yet. SOS is shown as unavailable on purpose: a button that
 * looks live but does nothing would be worse than none.
 */
@Composable
fun InRideScreen(vm: MeshTestViewModel, rideCode: String, gloveMode: Boolean, onEndRide: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline

    // Keep the screen on during a ride (the ride service keeps audio alive in the background).
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var state by remember { mutableStateOf(SessionState.CONNECTING) }
    var riders by remember { mutableStateOf(emptyList<Pair<String, Int>>()) }
    var distance by remember { mutableStateOf("") }
    var farthest by remember { mutableStateOf("") }
    var delayMs by remember { mutableStateOf(0) }
    var micBlocked by remember { mutableStateOf(false) }
    var encrypted by remember { mutableStateOf(true) }

    // The engine keeps its counters in plain fields, so read them twice a second.
    LaunchedEffect(Unit) {
        while (true) {
            state = vm.sessionState
            riders = vm.reachableRiders
            vm.checkMicBlocked()
            vm.refreshDistance()
            micBlocked = vm.micBlocked
            distance = vm.distanceLine
            farthest = vm.farthestLine
            delayMs = vm.estimatedDelayMs
            encrypted = vm.isEncrypted
            delay(500)
        }
    }

    val isLive = vm.micOpen
    val avatarSize = if (gloveMode) 56.dp else 40.dp
    val micSize = if (gloveMode) 100.dp else 72.dp
    val (chipBg, chipFg) = stateColors(state)

    Column(modifier = Modifier.fillMaxSize()) {

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ride code: $rideCode", style = MaterialTheme.typography.titleMedium, color = onSurface)
                Text(
                    when (riders.size) {
                        0 -> "No riders yet"
                        1 -> "1 rider connected"
                        else -> "${riders.size} riders connected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                "Share",
                modifier = Modifier.clickable {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join my Rylaide ride. Ride code: $rideCode")
                    }
                    context.startActivity(Intent.createChooser(send, "Share ride code"))
                }.padding(end = 16.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "End ride",
                modifier = Modifier.clickable { onEndRide() },
                fontSize = 13.sp,
                color = onSurface.copy(alpha = 0.7f)
            )
        }

        // Connection state
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.clip(CircleShape).background(chipBg).padding(horizontal = 12.dp, vertical = 5.dp)
            ) { Text(stateLabel(state), fontSize = 12.sp, color = chipFg) }
            Spacer(modifier = Modifier.width(8.dp))
            if (encrypted) {
                Text("Encrypted", fontSize = 12.sp, color = onSurface.copy(alpha = 0.6f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Warnings and the live banner
        if (micBlocked) {
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF0D6)).padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Android is blocking the microphone. Close other apps that use it, then tap the mic again.",
                    color = Color(0xFF9A6200), fontSize = 12.sp, textAlign = TextAlign.Center
                )
            }
        }
        if (isLive) {
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFFBE3E3)).padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("You're live, the group can hear you", color = Color(0xFFB3261E), fontSize = 12.sp)
            }
        }

        // Middle: ride details until the map is built
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(surfaceVariant)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Map goes here later", color = onSurface.copy(alpha = 0.4f), fontSize = 12.sp)
            if (distance.isNotEmpty()) Text(distance, color = onSurface, fontSize = 13.sp)
            if (farthest.isNotEmpty()) Text(farthest, color = onSurface, fontSize = 13.sp)
            Text(
                "Delay: about $delayMs ms" + if (vm.audioRoute.contains("BLUETOOTH", true)) " (Bluetooth adds more)" else "",
                color = onSurface, fontSize = 13.sp
            )
            Text("Audio: ${vm.audioRoute.lowercase().replace('_', ' ')}", color = onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
            if (state == SessionState.DEGRADED || state == SessionState.DISCONNECTED) {
                Text(
                    "Riders are out of reach. Voice resumes by itself when a link comes back.",
                    color = onSurface.copy(alpha = 0.7f), fontSize = 12.sp
                )
            }
        }

        // Rider strip: the riders we can hear right now
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (riders.isEmpty()) {
                Text("Waiting for riders to join with this code…", color = onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            riders.forEach { (name, hops) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RiderAvatar(
                        initials = name.take(2).uppercase(),
                        size = avatarSize, surfaceVariant = surfaceVariant, outline = outline
                    )
                    Text(
                        if (hops <= 1) name.take(8) else "${name.take(6)} · $hops",
                        fontSize = 10.sp, color = onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Bottom controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(micSize)
                    .clip(CircleShape)
                    .background(if (isLive) Color(0xFFB3261E) else surfaceVariant)
                    .clickable { vm.setMic(!isLive) },
                contentAlignment = Alignment.Center
            ) {
                Text(if (isLive) "🔴" else "🎙️", fontSize = if (gloveMode) 34.sp else 26.sp)
            }
            Spacer(modifier = Modifier.width(32.dp))
            Box(
                modifier = Modifier
                    .size(if (gloveMode) 52.dp else 40.dp)
                    .clip(CircleShape)
                    .background(surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("SOS\nsoon", fontSize = 9.sp, color = onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center)
            }
        }
        Text(
            if (isLive) "Tap to mute" else "Tap to talk",
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = TextAlign.Center,
            color = onSurface.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun RiderAvatar(
    initials: String,
    isTalking: Boolean = false,
    size: androidx.compose.ui.unit.Dp,
    surfaceVariant: Color,
    outline: Color
) {
    val ringModifier = if (isTalking) {
        Modifier.border(BorderStroke(2.dp, outline), CircleShape)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(size)
            .then(ringModifier)
            .padding(2.dp)
            .clip(CircleShape)
            .background(surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, fontSize = 14.sp)
    }
}
