plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "fr.xpert.guitar"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.xpert.guitar"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Clé perso (secrets GitHub XPERT_KEYSTORE_B64 / XPERT_KEYSTORE_PASSWORD, alias « xpertguitar ») :
    // même signature à chaque build, donc les mises à jour s'installent par-dessus.
    // Sans clé, on signe avec la clé de debug.
    val keystorePath = System.getenv("XPERT_KEYSTORE")
    val keystorePassword = System.getenv("XPERT_KEYSTORE_PASSWORD")
    signingConfigs {
        if (keystorePath != null && keystorePassword != null) {
            create("perso") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = System.getenv("XPERT_KEY_ALIAS") ?: "xpertguitar"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("perso") ?: signingConfigs.getByName("debug")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    testImplementation("junit:junit:4.13.2")
}
