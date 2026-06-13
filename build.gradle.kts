buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Reproducible builds: the R8 version bundled by AGP 8.5.2 emits the DEX
        // in a non-deterministic byte order. R8 8.6.33+/8.8+ makes emission
        // deterministic. See https://f-droid.org/docs/Reproducible_Builds/
        classpath("com.android.tools:r8:8.8.34")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
