val gitCommitHash: String = try {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().readText().trim()
} catch (e: Exception) {
    "unknown"
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.cellrecorder.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cellrecorder.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 7
        versionName = "1.2.0"

        buildConfigField("String", "GIT_HASH", "\"$gitCommitHash\"")

        testInstrumentationRunner = "com.cellrecorder.app.HiltTestRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE.md,LICENSE-notice.md,NOTICE.md}"
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }

    lint {
        // targetSdk intentionally stays at 34 to avoid runtime behavior changes
        // (Android 14+ FGS restrictions, Android 15 edge-to-edge, etc.); bumping it
        // to match compileSdk requires a runtime audit. Suppress the lint nudge.
        disable += "OldTargetApi"
    }

    }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    debugImplementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // NetMonster Core
    implementation("app.netmonster:core:1.3.0")

    // osmdroid
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:5.4.0")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    // Testing
    testImplementation(platform("org.junit:junit-bom:6.1.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    // Instrumented testing
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.59.2")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.59.2")
    androidTestImplementation("io.mockk:mockk-android:1.14.11")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

// Configure KSP for Room
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<Test> {
    useJUnitPlatform()
}