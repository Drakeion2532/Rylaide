// Copyright (c) 2026 Shubhan Punde · https://github.com/Drakeion2532/Rylaide · All rights reserved · sp2532-19f472e3b826
package com.rylaide.app

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Holds the one voice engine for the whole app. Before this, the Home screen and
 * the Diagnostics screen each had their own copy, so leaving Diagnostics (or a
 * stray back press) stopped the ride and threw the log away.
 */
class RylaideApplication : Application(), ViewModelStoreOwner {
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store
}
