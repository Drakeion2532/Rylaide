// Copyright (c) 2026 Shubhan Punde · https://github.com/Drakeion2532/Rylaide · All rights reserved · sp2532-19f472e3b826
package com.rylaide.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

/**
 * Entry point. All actual UI lives in Compose screens (see RylaideApp.kt,
 * HomeScreen.kt, InRideScreen.kt) - this Activity just hosts them. The voice engine
 * lives in [MeshTestViewModel] (the name is historical), so a ride keeps running
 * when the screen rotates.
 */
class HomeActivity : ComponentActivity() {
    private val viewModel: MeshTestViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RylaideApp(
                vm = viewModel,
                onOpenDiagnostics = { startActivity(Intent(this, MeshTestActivity::class.java)) }
            )
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
