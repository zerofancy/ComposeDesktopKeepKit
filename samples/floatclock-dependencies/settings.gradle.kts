pluginManagement {
    val remoteVersion = providers.gradleProperty("keepKitRemoteVersion").orNull
    if (remoteVersion == null) {
        includeBuild("../..")
    } else {
        plugins {
            id("io.github.zerofancy.compose-desktop-keep-kit") version remoteVersion
        }
        resolutionStrategy {
            eachPlugin {
                if (requested.id.id == "io.github.zerofancy.compose-desktop-keep-kit") {
                    useModule("com.github.zerofancy:ComposeDesktopKeepKit:${requested.version}")
                }
            }
        }
    }
    repositories {
        maven("https://jitpack.io") { content { includeGroup("com.github.zerofancy") } }
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
dependencyResolutionManagement { repositories { mavenCentral(); google() } }
rootProject.name = "keepkit-floatclock-dependencies"
