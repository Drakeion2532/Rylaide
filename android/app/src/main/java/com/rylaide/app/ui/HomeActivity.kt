package com.rylaide.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Entry point. All actual UI lives in Compose screens (see RylaideApp.kt,
 * HomeScreen.kt, InRideScreen.kt) - this Activity just hosts them.
 */
class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RylaideApp() }
    }
}
