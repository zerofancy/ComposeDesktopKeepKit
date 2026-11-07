pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral(); google() } }
rootProject.name = "keepkit-kzagent-dependencies"
include("release-rules")
