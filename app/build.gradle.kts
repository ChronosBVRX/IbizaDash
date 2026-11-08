import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma release real: la llave vive fuera del repo y se referencia desde
// local.properties (no versionado). Sin esas claves, el release se firma con
// debug como antes.
val releaseProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.chronosbvrx.ibizadash"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chronosbvrx.ibizadash"
        minSdk = 23
        targetSdk = 33
        versionCode = 18
        versionName = "1.4.1"
        ndk {
            abiFilters += listOf("armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = releaseProps.getProperty("release.storeFile")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = releaseProps.getProperty("release.storePassword")
                keyAlias = releaseProps.getProperty("release.keyAlias", "ibizadash")
                keyPassword = releaseProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (releaseProps.getProperty("release.storeFile").isNullOrBlank()) {
                    signingConfigs.getByName("debug")
                } else {
                    signingConfigs.getByName("release")
                }
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
