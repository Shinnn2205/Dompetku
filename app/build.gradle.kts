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
        versionName = "3.0.$runNum"
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
