plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.bestie.tvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.bestie.tvlauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "2.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Pure Android framework app (no AndroidX) to stay lightweight on TV hardware.
    // ZXing core is used only to render the phone-pairing QR code locally.
    implementation("com.google.zxing:core:3.5.3")
}
