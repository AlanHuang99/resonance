import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.resonance.music"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tabataflow.music"
        minSdk = 31
        targetSdk = 34
        versionCode = 15
        versionName = "0.8.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val hasReleaseSigning = rootProject.file("signing.properties").exists() ||
        System.getenv("SIGNING_KEYSTORE") != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val props = rootProject.file("signing.properties")
                if (props.exists()) {
                    val signingProps = Properties()
                    props.inputStream().use { signingProps.load(it) }
                    storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                    storePassword = signingProps.getProperty("storePassword") ?: ""
                    keyAlias = signingProps.getProperty("keyAlias") ?: "tabataflow"
                    keyPassword = signingProps.getProperty("keyPassword") ?: ""
                } else {
                    storeFile = file(System.getenv("SIGNING_KEYSTORE"))
                    storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
                    keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "tabataflow"
                    keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            if (providers.gradleProperty("deviceTest").isPresent) {
                applicationIdSuffix = ".debug"
                signingConfig = signingConfigs.getByName("debug")
            }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

tasks.configureEach {
    if (name.contains("ArtProfile") || name.contains("StartupProfile")) {
        enabled = false
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.work)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)

    implementation(libs.kotlinx.coroutines)

    implementation(libs.reorderable)

    testImplementation(libs.junit)
    androidTestImplementation("androidx.test.runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
