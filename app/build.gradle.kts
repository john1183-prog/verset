plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.johndev.verset"
    compileSdk = 35

    signingConfigs {
        getByName("debug") {
            // Committed keystore (app/debug.keystore) so every build — including every
            // CI run on a fresh GitHub Actions VM — produces the same SHA-1/SHA-256.
            // Without this, AGP auto-generates a new random debug key per machine/run,
            // which breaks Google Sign-In every time since Firebase only trusts
            // fingerprints you've explicitly registered.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release signing is intentionally NOT hardcoded. It's read from Gradle
        // properties that only exist when explicitly passed in (via -P flags), which
        // release.yml does using GitHub Secrets (RELEASE_STORE_FILE points at a keystore
        // decoded from RELEASE_KEYSTORE_BASE64 at CI time). If these properties are
        // absent — e.g. a plain local `gradle assembleRelease` — this config is simply
        // not created, and the release build type below falls back to unsigned rather
        // than crashing the build with a null keystore path.
        val releaseStoreFile = project.findProperty("RELEASE_STORE_FILE") as String?
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
                keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
                keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    defaultConfig {
        applicationId = "com.johndev.verset"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Baked in at build time so every user's installed APK already has it —
        // no runtime setup screen needed for end users. Sourced from a Gradle
        // property (set via CI from the FIREBASE_WEB_CLIENT_ID secret, or locally
        // via -PWEB_CLIENT_ID=... / gradle.properties). Empty string if unset,
        // which the app treats as "not configured yet" and falls back to the
        // developer-only manual-entry screen in Settings.
        val webClientId = (project.findProperty("WEB_CLIENT_ID") as String?) ?: ""
        buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Firebase (Auth + Firestore for sync)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Google Sign-In (Credential Manager, modern replacement for old GoogleSignInClient)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // JSON parsing for the bundled KJV asset uses org.json, built into the Android SDK — no extra dependency needed.

    // WorkManager for background auto-sync
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Glance for home-screen widget
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
