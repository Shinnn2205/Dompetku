plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNum = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0

android {
    namespace = "com.dompetku.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dompetku.v2"
        minSdk = 24
        targetSdk = 34
        versionCode = 10 + runNum
        versionName = "2.1.$runNum"
    }

    signingConfigs {
        create("dompetku") {
            storeFile = file("dompetku.jks")
            storePassword = System.getenv("KEYSTORE_PASS") ?: "dompetku123"
            keyAlias = "dompetku"
            keyPassword = System.getenv("KEY_PASS") ?: "dompetku123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("dompetku")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dompetku")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
}
