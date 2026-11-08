import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Propiedades locales (no versionadas): API key de YouTube y firma release.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.chronosbvrx.ibizacompanion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chronosbvrx.ibizacompanion"
        minSdk = 26
        targetSdk = 34
        versionCode = 22
        versionName = "1.7.0"
        manifestPlaceholders["redirectSchemeName"] = "spotify-sdk"
        manifestPlaceholders["redirectHostName"] = "auth"

        // La API key de YouTube NO se versiona. Se puede definir en
        // local.properties (YOUTUBE_API_KEY=...) o con -PYOUTUBE_API_KEY=...
        val youtubeKey = (project.findProperty("YOUTUBE_API_KEY") as? String)
            ?: localProps.getProperty("YOUTUBE_API_KEY", "")
        buildConfigField(
            "String",
            "YOUTUBE_API_KEY",
            "\"${youtubeKey.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )

        // Clave de tr?fico (Fase C). Igual que la de YouTube, nunca se versiona.
        // Se define en local.properties: TOMTOM_API_KEY=...
        val tomtomKey = localProps.getProperty("TOMTOM_API_KEY", "")
        buildConfigField(
            "String",
            "TOMTOM_API_KEY",
            "\"${tomtomKey.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
    }

    // Firma release real: la llave vive fuera del repo y se referencia desde
    // local.properties (no versionado). Sin esas claves, el release se firma
    // con debug como antes.
    signingConfigs {
        create("release") {
            val storeFilePath = localProps.getProperty("release.storeFile")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = localProps.getProperty("release.storePassword")
                keyAlias = localProps.getProperty("release.keyAlias", "ibizadash")
                keyPassword = localProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig =
                if (localProps.getProperty("release.storeFile").isNullOrBlank()) {
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
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Spotify App Remote SDK (local IPC, no Web API REST)
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation("com.spotify.android:auth:2.1.2")
    implementation("com.google.code.gson:gson:2.10.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
