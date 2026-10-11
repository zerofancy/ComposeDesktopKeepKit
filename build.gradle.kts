plugins {
    kotlin("jvm") version "2.2.0"
    `java-gradle-plugin`
    `maven-publish`
}
val onJitPack = providers.environmentVariable("JITPACK").orNull == "true"
group = if (onJitPack) providers.environmentVariable("GROUP").orElse("com.github.zerofancy").get() else "io.github.zerofancy"
version = if (onJitPack) providers.environmentVariable("VERSION").orElse("v0.2.1").get() else "0.2.1"

tasks.processResources {
    val tokens = mapOf("version" to project.version.toString())
    inputs.property("pluginVersion", tokens.getValue("version"))
    filesMatching("keepkit/version.properties") { expand(tokens) }
}
repositories { mavenCentral(); gradlePluginPortal(); google() }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("org.apache.maven:maven-artifact:3.9.9")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
gradlePlugin {
    plugins {
        create("keepKit") {
            id = "io.github.zerofancy.compose-desktop-keep-kit"
            implementationClass = "io.github.zerofancy.keepkit.KeepKitPlugin"
            displayName = "Compose Desktop Keep Kit"
            description = "Automatically applies versioned dependency keep rules to Compose Desktop ProGuard."
        }
    }
}
tasks.test { useJUnitPlatform(); maxParallelForks = 1 }
publishing { repositories { maven { name = "localBuild"; url = layout.buildDirectory.dir("repository").get().asFile.toURI() } } }
java { withSourcesJar() }
publishing.publications.withType<MavenPublication>().configureEach {
    pom {
        name.set("Compose Desktop Keep Kit")
        description.set("Automatically applies versioned dependency keep rules to Compose Desktop ProGuard.")
        url.set("https://github.com/zerofancy/ComposeDesktopKeepKit")
        licenses { license { name.set("Apache License, Version 2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0") } }
        scm {
            url.set("https://github.com/zerofancy/ComposeDesktopKeepKit")
            connection.set("scm:git:https://github.com/zerofancy/ComposeDesktopKeepKit.git")
        }
    }
}
