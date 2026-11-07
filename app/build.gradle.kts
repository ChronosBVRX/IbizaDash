plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chronosbvrx.ibizadash"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chronosbvrx.ibizadash"
        minSdk = 23
        targetSdk = 33
        versionCode = 5
        versionName = "0.5.0"
        ndk {
            abiFilters += listOf("armeabi-v7a")
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
            signingConfig = signingConfigs.getByName("debug")
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
