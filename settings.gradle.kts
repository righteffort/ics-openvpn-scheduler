pluginManagement {  
    repositories {
        google()
        mavenCentral()
	gradlePluginPortal()
    }
}

plugins {
    // Apply the foojay-resolver plugin to allow automatic download of JDKs
    // TODO: relevant?
    // id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "ics-openvpn-scheduler"
include("app")
