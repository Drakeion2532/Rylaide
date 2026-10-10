package com.rylaide.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Create/join choice screen, plus the two rider-facing preferences: theme
 * mode and glove mode. Touch target sizing here follows gloveMode the same
 * way InRideScreen does, so the setting affects the whole app consistently.
 */
@Composable
fun HomeScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    gloveMode: Boolean,
    onGloveModeChange: (Boolean) -> Unit,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit
) {
    val iconCircleSize = if (gloveMode) 52.dp else 44.dp

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "Start riding",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(20.dp))

        ThemeModeRow(themeMode = themeMode, onThemeModeChange = onThemeModeChange)
        Spacer(modifier = Modifier.height(8.dp))
        GloveModeRow(gloveMode = gloveMode, onGloveModeChange = onGloveModeChange)

        Spacer(modifier = Modifier.height(24.dp))

        OptionCard(
            emoji = "➕",
            title = "Create a group",
            subtitle = "Start a ride and invite others",
            iconCircleSize = iconCircleSize,
            onClick = onCreateGroup
        )
        Spacer(modifier = Modifier.height(12.dp))
        OptionCard(
            emoji = "👥",
            title = "Join a group",
            subtitle = "Enter a code or find nearby riders",
            iconCircleSize = iconCircleSize,
            onClick = onJoinGroup
        )
    }
}

@Composable
private fun ThemeModeRow(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Theme",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(70.dp)
        )
        ThemeChip("Auto", themeMode == ThemeMode.AUTO) { onThemeModeChange(ThemeMode.AUTO) }
        Spacer(modifier = Modifier.width(8.dp))
        ThemeChip("Light", themeMode == ThemeMode.LIGHT) { onThemeModeChange(ThemeMode.LIGHT) }
        Spacer(modifier = Modifier.width(8.dp))
        ThemeChip("Dark", themeMode == ThemeMode.DARK) { onThemeModeChange(ThemeMode.DARK) }
    }
}

@Composable
private fun ThemeChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, color = fg)
    }
}

@Composable
private fun GloveModeRow(gloveMode: Boolean, onGloveModeChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Glove mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Bigger buttons for riding gloves",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Switch(checked = gloveMode, onCheckedChange = onGloveModeChange)
    }
}

@Composable
private fun OptionCard(
    emoji: String,
    title: String,
    subtitle: String,
    iconCircleSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(iconCircleSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(
            themeMode = ThemeMode.AUTO,
            onThemeModeChange = {},
            gloveMode = true,
            onGloveModeChange = {},
            onCreateGroup = {},
            onJoinGroup = {}
        )
    }
}
