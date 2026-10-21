plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.shavei.mapa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shavei.mapa"
        minSdk = 26
        targetSdk = 35
        // Minutes from 2025 to the commit built, so every build of a newer commit installs as an
        // update over the last one, whichever workflow built it. (A workflow's run number is
        // not enough: it restarted at 1 when publishing moved to release.yml, below the builds
        // already installed, and phones refused the update as "invalid".)
        versionCode = commitVersionCode()
        versionName = "0.1.0"
    }

    signingConfigs {
        // A fixed debug key committed to the repo (debug keys are not secret). Without it,
        // every CI machine signs with its own random key and phones refuse to update.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:learning"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Robolectric screen tests save screenshots here for review.
tasks.withType<Test>().configureEach {
    systemProperty("screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
}

/** Minutes from 2025 to the checked out commit; 1 where there is no git to ask. */
fun commitVersionCode(): Int = runCatching {
    val seconds = providers.exec { commandLine("git", "log", "-1", "--format=%ct") }.standardOutput.asText.get().trim().toLong()
    ((seconds - 1_735_689_600L) / 60).toInt().coerceAtLeast(1)
}.getOrDefault(1)
