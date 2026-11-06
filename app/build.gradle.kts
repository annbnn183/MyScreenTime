plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.atharok.screentime"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.atharok.screentime"
        minSdk = 24
        targetSdk = 37
        versionCode = 17
        versionName = "1.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A signingConfig on a productFlavor is overridden by the buildType's default debug
    // signingConfig, which is why earlier CI builds all carried a different ephemeral certificate.
    // Attach it to the buildTypes instead, and only when a real keystore file is available.
    val fixedSigning = signingConfigs.create("release") {
        System.getenv("ANDROID_KEY_STORE_FILE")
            ?.takeIf { it.isNotBlank() }
            ?.let { file(it) }
            ?.takeIf { it.isFile && it.length() > 0 }
            ?.let { storeFile = it }
        System.getenv("ANDROID_KEY_STORE_PASSWORD")?.takeIf { it.isNotBlank() }?.let { storePassword = it }
        System.getenv("ANDROID_KEY_ALIAS")?.takeIf { it.isNotBlank() }?.let { keyAlias = it }
        System.getenv("ANDROID_KEY_PASSWORD")?.takeIf { it.isNotBlank() }?.let { keyPassword = it }
    }
    val useFixedSigning = fixedSigning.storeFile != null

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            if (useFixedSigning) signingConfig = fixedSigning
        }
        release {
            isDebuggable = false
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (useFixedSigning) signingConfig = fixedSigning
        }
    }

    flavorDimensions += "version"
    productFlavors {
        create("default") {
            dimension = "version"
        }
        create("gplay") {
            dimension = "version"
            applicationIdSuffix = ".gplay"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.koin.androidx.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}