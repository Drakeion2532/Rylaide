package com.rylaide.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

enum class Screen {
    HOME,
    IN_RIDE
}

// Both schemes share the same amber "signal" accent so the app still feels
// like the same product whether it's showing light or dark.
private val RiderLightColors = lightColorScheme(
    primary = Color(0xFFB3261E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171715),
    surfaceVariant = Color(0xFFF1F0EB),
    outline = Color(0xFF171715)
)

private val RiderDarkColors = darkColorScheme(
    primary = Color(0xFFE8A33D),
    surface = Color(0xFF17140F),
    onSurface = Color(0xFFF2EEE4),
    surfaceVariant = Color(0xFF241F17),
    outline = Color(0xFFE8A33D)
)

/**
 * Root composable. Holds two independent rider preferences:
 *  - themeMode: Auto (follows real clock time) / Light / Dark - see Settings.kt
 *  - gloveMode: whether touch targets use the larger, glove-friendly sizing
 *
 * TODO: persist both of these (e.g. via DataStore) so they survive an app
 * restart - right now they reset to defaults every time the app opens.
 */
@Composable
fun RylaideApp() {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    var themeMode by remember { mutableStateOf(ThemeMode.AUTO) }
    var gloveMode by remember { mutableStateOf(true) }

    val isDark = resolveIsDarkTheme(themeMode)
    val colors = if (isDark) RiderDarkColors else RiderLightColors

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                Screen.HOME -> HomeScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { themeMode = it },
                    gloveMode = gloveMode,
                    onGloveModeChange = { gloveMode = it },
                    onCreateGroup = { currentScreen = Screen.IN_RIDE },
                    onJoinGroup = { currentScreen = Screen.IN_RIDE }
                )
                Screen.IN_RIDE -> InRideScreen(
                    gloveMode = gloveMode,
                    onEndRide = { currentScreen = Screen.HOME }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Day")
@Composable
fun RylaideAppPreviewDay() {
    MaterialTheme(colorScheme = RiderLightColors) {
        Surface {
            HomeScreen(
                themeMode = ThemeMode.LIGHT,
                onThemeModeChange = {},
                gloveMode = true,
                onGloveModeChange = {},
                onCreateGroup = {},
                onJoinGroup = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Night")
@Composable
fun RylaideAppPreviewNight() {
    MaterialTheme(colorScheme = RiderDarkColors) {
        Surface {
            HomeScreen(
                themeMode = ThemeMode.DARK,
                onThemeModeChange = {},
                gloveMode = true,
                onGloveModeChange = {},
                onCreateGroup = {},
                onJoinGroup = {}
            )
        }
    }
}
