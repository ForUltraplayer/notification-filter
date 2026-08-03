plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
}

// Signing credentials come from the environment so that no key material is ever committed.
// Read through the providers API to stay compatible with the configuration cache.
val envKeystore = "PUSHFILTER_KEYSTORE"
val envStorePassword = "PUSHFILTER_KEYSTORE_PASSWORD"
val envKeyAlias = "PUSHFILTER_KEY_ALIAS"
val envKeyPassword = "PUSHFILTER_KEY_PASSWORD"

val releaseKeystore: String? = providers.environmentVariable(envKeystore).orNull

android {
    namespace = "ru.krestnikov.pushfilter"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.krestnikov.pushfilter"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"
    }

    androidResources {
        localeFilters += setOf("en", "ru")
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable(envStorePassword).orNull
                keyAlias = providers.environmentVariable(envKeyAlias).orNull
                keyPassword = providers.environmentVariable(envKeyPassword).orNull
            }
        }
    }

    buildTypes {
        release {
            // Without a keystore in the environment the release build falls back to the debug
            // key, so anyone cloning the repo still gets an installable APK. Only the artifacts
            // published from CI carry the real signature.
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
