// Copyright (c) 2026 Shubhan Punde · https://github.com/Drakeion2532/Rylaide · All rights reserved · sp2532-19f472e3b826
package com.rylaide.app.ui

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import com.rylaide.app.RylaideApplication

/** The single [MeshTestViewModel] shared by the Home and Diagnostics screens. */
object SharedViewModel {
    fun get(activity: ComponentActivity): MeshTestViewModel {
        val app = activity.application as RylaideApplication
        return ViewModelProvider(app, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[MeshTestViewModel::class.java]
    }
}
