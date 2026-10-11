pluginManagement {
    repositories {
        maven("https://jitpack.io") {
            content { includeGroup("com.github.zerofancy") }
        }
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.zerofancy.compose-desktop-keep-kit") {
                useModule("com.github.zerofancy:ComposeDesktopKeepKit:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement { repositories { mavenCentral(); google() } }
rootProject.name = "keepkit-jitpack-consumer"
