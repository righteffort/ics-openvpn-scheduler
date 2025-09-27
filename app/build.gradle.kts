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
    implementation(libs.androidx.appcompat)  // for theme in AndroidManifest.xml
    implementation(libs.androidx.ktx)  // fixes jar version conflicts
}

android {
    namespace = "org.righteffort.openvpnscheduler"
    compileSdk = 36
    defaultConfig {
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
