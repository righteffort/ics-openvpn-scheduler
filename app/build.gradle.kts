plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.guava)
    implementation(libs.androidx.appcompat)  // for theme in AndroidManifest.xml
    implementation(libs.androidx.core)  // fixes jar version conflicts
    implementation(libs.androidx.work)
}

kotlin {
    jvmToolchain(17)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

android {
    namespace = "org.righteffort.vpnscheduler"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        aidl = true
	buildConfig = true
    }

    buildTypes {
        getByName("release") {
            // TODO: Use real signing keys
	    logger.warn("Using debug signing for release")
	    signingConfig = android.signingConfigs.getByName("debug")
        }
    }
}
