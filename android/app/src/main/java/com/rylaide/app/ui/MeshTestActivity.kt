package com.rylaide.app.ui

import com.rylaide.app.mesh.MeshDiscoveryManager
import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Sprint 1 test screen: proves two phones can talk over Nearby Connections
 * with no internet. This is a developer tool, not the final Rylaide UI.
 */
class MeshTestActivity : ComponentActivity() {

    private val viewModel: MeshTestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Test screen only: keep the display on so nothing about the screen turning off
        // can disturb a test. The ride service keeps things alive when you switch apps.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val colors = if (isSystemInDarkTheme()) {
                darkColorScheme(primary = Color(0xFFE8A33D))
            } else {
                lightColorScheme(primary = Color(0xFFB3261E))
            }
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MeshTestScreen(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.noteAppVisible(true)
    }

    override fun onStop() {
        viewModel.noteAppVisible(false)
        super.onStop()
    }
}

private fun requiredPermissions(): Array<String> {
    val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= 31) {
        list += Manifest.permission.BLUETOOTH_ADVERTISE
        list += Manifest.permission.BLUETOOTH_CONNECT
        list += Manifest.permission.BLUETOOTH_SCAN
    }
    // Nearby Connections reports error 8034 unless COARSE location is granted,
    // and older Android versions also need FINE. Ask for both on every version.
    list += Manifest.permission.ACCESS_COARSE_LOCATION
    list += Manifest.permission.ACCESS_FINE_LOCATION
    if (Build.VERSION.SDK_INT >= 33) {
        list += Manifest.permission.NEARBY_WIFI_DEVICES
        list += Manifest.permission.POST_NOTIFICATIONS
    }
    return list.toTypedArray()
}

@Composable
fun MeshTestScreen(vm: MeshTestViewModel) {
    var groupCode by remember { mutableStateOf("TEST") }
    var name by remember { mutableStateOf(Build.MODEL ?: "Rider") }
    var permissionMessage by remember { mutableStateOf("") }
    var sent by remember { mutableLongStateOf(0L) }
    var received by remember { mutableLongStateOf(0L) }
    var level by remember { mutableStateOf(0.0) }
    var concealed by remember { mutableLongStateOf(0L) }
    var late by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableStateOf(0) }
    var rtt by remember { mutableStateOf(-1) }
    var rttWorst by remember { mutableStateOf(-1) }
    var drops by remember { mutableStateOf(0) }
    var reachText by remember { mutableStateOf("none yet") }
    var micBlocked by remember { mutableStateOf(false) }
    var internetUp by remember { mutableStateOf(false) }
    var delayMs by remember { mutableStateOf(0) }
    var gate by remember { mutableStateOf(0.0) }
    var boost by remember { mutableStateOf(1.0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            permissionMessage = ""
            vm.start(groupCode, name)
        } else {
            val denied = results.filterValues { !it }.keys.joinToString { it.substringAfterLast('.') }
            permissionMessage = "Permission needed: $denied. Allow it in Settings > Apps > Rylaide > Permissions."
        }
    }

    // Refresh the counters twice a second instead of on every audio frame.
    LaunchedEffect(Unit) {
        while (true) {
            sent = vm.framesSent.get()
            received = vm.framesReceived.get()
            level = vm.currentLevel
            concealed = vm.concealedFrames
            late = vm.lateFrames
            buffered = vm.bufferedMs
            rtt = vm.linkRttMs
            rttWorst = vm.linkRttWorstMs
            drops = vm.linkDrops
            reachText = vm.reachableText
            vm.checkMicBlocked()
            micBlocked = vm.micBlocked
            internetUp = vm.internetUp
            delayMs = vm.estimatedDelayMs
            gate = vm.gateLevel
            boost = vm.micBoost
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Rylaide · Step 2 · offline intercom test", style = MaterialTheme.typography.titleLarge)
        Text(
            "Turn Bluetooth, Wi-Fi and Location ON on both phones. Mobile data can be off. " +
                "Use a headset or earphones, or keep the phones in different rooms, or the speaker will squeal.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = groupCode,
            onValueChange = { groupCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
            label = { Text("Group code (same on both phones)") },
            singleLine = true,
            enabled = !vm.running,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = { Text("Your name") },
            singleLine = true,
            enabled = !vm.running,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (vm.running) vm.stop() else permissionLauncher.launch(requiredPermissions())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(if (vm.running) "Stop" else "Start")
        }
        if (permissionMessage.isNotEmpty()) {
            Text(permissionMessage, color = MaterialTheme.colorScheme.error)
        }

        // Big mic button: sized for gloves.
        Button(
            onClick = { vm.setMic(!vm.micOpen) },
            enabled = vm.running,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (vm.micOpen) Color(0xFF1DAA5C) else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (vm.micOpen) Color.White else MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            Text(if (vm.micOpen) "YOU'RE LIVE · tap to mute" else "Muted · tap to talk", style = MaterialTheme.typography.titleMedium)
        }

        Text("Status: ${vm.status}")
        Text("Audio route: ${vm.audioRoute}")
        Text(
            if (vm.peers.isEmpty()) "Connected riders: none yet"
            else "Connected riders: " + vm.peers.joinToString { it.name }
        )
        Text("Voice packets sent: $sent   ·   received: $received")
        if (micBlocked) {
            Text(
                "MIC BLOCKED: the phone is giving the app a silent mic, so nobody can hear you.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Text(
            "Internet path: " + when {
                vm.relayUrl.isBlank() -> "off (no relay server set)"
                internetUp -> "connected"
                else -> "not connected (offline link still works)"
            }
        )
        Text("Riders you can hear: $reachText")
        Text("Estimated delay: about $delayMs ms (your headset adds more: Bluetooth about 100 to 200 ms)")
        Text(if (rtt < 0) "Link delay: not measured yet   ·   dropped links: $drops" else "Link delay (round trip): $rtt ms, worst of last 10: $rttWorst ms   ·   dropped links: $drops   ·   voice skipped while link backed up: ${vm.audioDropped}")
        Text("Lost and patched: $concealed   ·   too late: $late   ·   buffered: $buffered ms")
        Text("Mic level: ${level.toInt()}   (your voice is sent above ${gate.toInt()}, boost x${"%.1f".format(boost)})")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Auto mic level")
            androidx.compose.material3.Switch(
                checked = vm.autoLevel,
                onCheckedChange = { vm.autoLevel = it }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Phone noise filter (turn OFF if only close talk is heard)")
            androidx.compose.material3.Switch(
                checked = vm.phoneNoiseSuppressor,
                onCheckedChange = { vm.phoneNoiseSuppressor = it }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("2-phone fast link (same on both phones, then Stop and Start)")
            androidx.compose.material3.Switch(
                checked = vm.fastMode,
                onCheckedChange = { vm.fastMode = it }
            )
        }
        androidx.compose.material3.OutlinedButton(
            onClick = {
                val all = com.rylaide.app.audio.NoiseSuppressor.Level.values()
                vm.noiseLevel = all[(vm.noiseLevel.ordinal + 1) % all.size]
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Wind and noise reducer: ${vm.noiseLevel.label}" +
                (if (vm.noiseLevel != com.rylaide.app.audio.NoiseSuppressor.Level.OFF && vm.windDetected) "  (wind-like sound)" else "") +
                "  · tap to change")
        }
        OutlinedTextField(
            value = vm.relayUrl,
            onValueChange = { vm.updateRelayUrl(it) },
            label = { Text("Relay server (optional, for the internet path). Stop and Start after changing") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        androidx.compose.material3.OutlinedButton(
            onClick = {
                val all = MeshTestViewModel.VoiceGate.values()
                vm.voiceGate = all[(vm.voiceGate.ordinal + 1) % all.size]
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Voice detection: ${vm.voiceGate.label}  · tap to change")
        }
        androidx.compose.material3.OutlinedButton(
            onClick = {
                val options = listOf(1.0, 2.0, 3.0, 4.0)
                vm.incomingBoost = options[(options.indexOf(vm.incomingBoost) + 1) % options.size]
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Incoming voice boost: x${"%.0f".format(vm.incomingBoost)}  · tap to change (helps over wind at your ears)")
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.material3.OutlinedButton(
            onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (t: Throwable) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Battery: set Rylaide to Not optimised / No restrictions")
        }
        androidx.compose.material3.OutlinedButton(
            onClick = {
                val all = MeshDiscoveryManager.LinkType.values()
                vm.linkType = all[(vm.linkType.ordinal + 1) % all.size]
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Link type: ${vm.linkType.label}  · tap to change, then Stop and Start")
        }
        Text("Mic sensitivity: x${"%.1f".format(vm.micSensitivity)}   (raise it if you must hold the mic close to talk)")
        Slider(
            value = vm.micSensitivity.toFloat(),
            onValueChange = { vm.micSensitivity = it.toDouble() },
            valueRange = 1f..8f,
            modifier = Modifier.fillMaxWidth()
        )
        if (!vm.autoLevel) {
            Slider(
                value = vm.vadThreshold.toFloat(),
                onValueChange = { vm.vadThreshold = it.toDouble() },
                valueRange = 100f..4000f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text("Log", style = MaterialTheme.typography.titleSmall)
        vm.log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
