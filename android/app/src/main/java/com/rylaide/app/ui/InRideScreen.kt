package com.rylaide.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The screen riders spend the most time on: map, rider strip, open-mic
 * toggle, SOS. When gloveMode is on, touch targets scale up (56dp avatars,
 * 100dp mic, 52dp SOS); when off, they use tighter, standard sizing.
 * gloveMode is a rider preference set on HomeScreen and passed down here,
 * not a fixed choice for everyone.
 *
 * Colors come from MaterialTheme.colorScheme, set once in
 * RylaideApp.kt from the rider's theme mode choice - the only
 * hardcoded colors here are semantic ones (red for live/SOS, green for
 * connection status) that should stay the same meaning regardless of theme.
 *
 * The mic toggle here is local UI state only - wiring it to
 * BluetoothAudioManager/AudioRouteManager/WebRtcAudioEngine is the real
 * work of sprint 1.
 */
@Composable
fun InRideScreen(gloveMode: Boolean, onEndRide: () -> Unit) {
    var isLive by remember { mutableStateOf(false) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline

    val avatarSize = if (gloveMode) 56.dp else 40.dp
    val micSize = if (gloveMode) 100.dp else 72.dp
    val sosSize = if (gloveMode) 52.dp else 40.dp

    Column(modifier = Modifier.fillMaxSize()) {

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Sunday ride", style = MaterialTheme.typography.titleMedium, color = onSurface)
                Text(
                    "5 riders connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.6f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "End ride",
                    modifier = Modifier.clickable { onEndRide() }.padding(end = 12.dp),
                    fontSize = 12.sp,
                    color = onSurface.copy(alpha = 0.6f)
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFDDF3E4))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Internet", fontSize = 12.sp, color = Color(0xFF1D9E75))
                }
            }
        }

        // Live banner - always red, regardless of theme, since "you are
        // broadcasting" needs to read as urgent either way
        if (isLive) {
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFFBE3E3)).padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("You're live, the group can hear you", color = Color(0xFFB3261E), fontSize = 12.sp)
            }
        }

        // Map placeholder - replace with real Mapbox/HERE view later
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Map goes here", color = onSurface.copy(alpha = 0.4f))
        }

        // Rider strip - size follows gloveMode
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            RiderAvatar(initials = "MK", isTalking = true, size = avatarSize, surfaceVariant = surfaceVariant, outline = outline)
            Spacer(modifier = Modifier.width(10.dp))
            RiderAvatar(initials = "AS", size = avatarSize, surfaceVariant = surfaceVariant, outline = outline)
            Spacer(modifier = Modifier.width(10.dp))
            RiderAvatar(initials = "RP", size = avatarSize, surfaceVariant = surfaceVariant, outline = outline)
            Spacer(modifier = Modifier.width(10.dp))
            RiderAvatar(initials = "JD", size = avatarSize, surfaceVariant = surfaceVariant, outline = outline)
        }

        // Bottom controls - sizes follow gloveMode
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
                    .clickable { isLive = !isLive },
                contentAlignment = Alignment.Center
            ) {
                Text(if (isLive) "🔴" else "🎙️", fontSize = if (gloveMode) 34.sp else 26.sp)
            }
            Spacer(modifier = Modifier.width(32.dp))
            Box(
                modifier = Modifier
                    .size(sosSize)
                    .clip(CircleShape)
                    .background(Color(0xFFFBE3E3))
                    .clickable { /* TODO: wire up real SOS broadcast in a later sprint */ },
                contentAlignment = Alignment.Center
            ) {
                Text("SOS", fontSize = 12.sp, color = Color(0xFFB3261E))
            }
        }
        Text(
            if (isLive) "Tap to mute" else "Tap to unmute",
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

@Preview(showBackground = true)
@Composable
fun InRideScreenPreview() {
    MaterialTheme {
        InRideScreen(gloveMode = true, onEndRide = {})
    }
}
