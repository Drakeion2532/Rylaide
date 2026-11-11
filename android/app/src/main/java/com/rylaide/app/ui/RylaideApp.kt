// Copyright (c) 2026 Shubhan Punde · https://github.com/Drakeion2532/Rylaide · All rights reserved · sp2532-19f472e3b826
package com.rylaide.app.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
 * The screen follows the engine: while a ride is running ([MeshTestViewModel.running])
 * the in-ride screen shows, otherwise Home. Create makes a random ride code; Join asks
 * for one. The code is the ride's secret, so it is never stored or sent anywhere except
 * to the riders you share it with.
 *
 * TODO: persist theme and glove mode (e.g. via DataStore).
 */
@Composable
fun RylaideApp(vm: MeshTestViewModel, onOpenDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rylaide", Context.MODE_PRIVATE) }
    var themeMode by remember { mutableStateOf(ThemeMode.AUTO) }
    var gloveMode by remember { mutableStateOf(true) }
    var riderName by remember { mutableStateOf(prefs.getString("riderName", null) ?: (Build.MODEL ?: "Rider")) }
    var pendingCode by remember { mutableStateOf("") }
    var rideCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showJoin by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            message = ""
            rideCode = pendingCode
            vm.start(pendingCode, riderName.trim().ifEmpty { "Rider" })
        } else {
            val denied = results.filterValues { !it }.keys.joinToString { it.substringAfterLast('.') }
            message = "Permission needed: $denied. Allow it in Settings > Apps > Rylaide > Permissions."
        }
    }

    fun beginRide(code: String) {
        pendingCode = code
        prefs.edit().putString("riderName", riderName.trim()).apply()
        permissionLauncher.launch(requiredPermissions())
    }

    val isDark = resolveIsDarkTheme(themeMode)
    val colors = if (isDark) RiderDarkColors else RiderLightColors

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (vm.running) {
                InRideScreen(
                    vm = vm,
                    rideCode = rideCode,
                    gloveMode = gloveMode,
                    onEndRide = { vm.stop() }
                )
            } else {
                HomeScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { themeMode = it },
                    gloveMode = gloveMode,
                    onGloveModeChange = { gloveMode = it },
                    onCreateGroup = { beginRide(RideCodes.random()) },
                    onJoinGroup = { joinCode = ""; showJoin = true },
                    riderName = riderName,
                    onRiderNameChange = { riderName = it.take(20) },
                    message = message,
                    onOpenDiagnostics = onOpenDiagnostics
                )
            }
            if (showJoin && !vm.running) {
                AlertDialog(
                    onDismissRequest = { showJoin = false },
                    title = { Text("Join a group") },
                    text = {
                        Column {
                            Text("Enter the ride code the group leader shared with you.")
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = joinCode,
                                onValueChange = { joinCode = RideCodes.clean(it) },
                                label = { Text("Ride code") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = RideCodes.isUsable(joinCode),
                            onClick = { showJoin = false; beginRide(joinCode) }
                        ) { Text("Join") }
                    },
                    dismissButton = { TextButton(onClick = { showJoin = false }) { Text("Cancel") } }
                )
            }
        }
    }
}

/** Ride codes: the ride's shared secret. Letters and digits that are hard to mix up. */
object RideCodes {
    const val LENGTH = 10
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun random(): String {
        val r = java.security.SecureRandom()
        return String(CharArray(LENGTH) { ALPHABET[r.nextInt(ALPHABET.length)] })
    }

    fun clean(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }.take(LENGTH)

    /** At least 8 characters; shorter codes are too easy to guess. */
    fun isUsable(code: String): Boolean = code.length >= 8
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
