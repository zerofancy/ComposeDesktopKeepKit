pluginManagement {
    includeBuild("../..")
    repositories { gradlePluginPortal(); mavenCentral(); google() }
}
dependencyResolutionManagement { repositories { mavenCentral(); google() } }
rootProject.name = "keepkit-coil-kmp"
