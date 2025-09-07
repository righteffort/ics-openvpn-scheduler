plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.guava)
    implementation("androidx.appcompat:appcompat:1.7.1")  // for theme in AndroidManifest.xml
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
}

android {
    namespace = "org.righteffort.vpnscheduler"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.righteffort.vpnscheduler"
        minSdk = 26
        targetSdk  = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}
