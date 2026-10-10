plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rylaide.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rylaide.app"
        minSdk = 26 // Nearby Connections + modern Bluetooth APIs need this floor
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Jetpack Compose - lets us preview screens right inside Android Studio
    // without needing to run on a phone or emulator at all.
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Mesh peer discovery over Wi-Fi Direct/Bluetooth, wrapped by Google Play Services
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // Voice does not use WebRTC any more: offline audio goes over Nearby
    // Connections (see audio/IntercomAudioEngine.kt) and online voice will use
    // LiveKit later. The old WebRTC dependency was removed to keep builds light.

    // TODO: add MapLibre Native (free, no key) when the map screen is built
}
