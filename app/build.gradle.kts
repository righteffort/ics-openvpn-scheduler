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
    implementation("androidx.core:core-ktx:1.17.0")  // fixes jar version conflicts
}

android {
    namespace = "org.righteffort.openvpnscheduler"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.righteffort.openvpnscheduler"
        minSdk = 24
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
