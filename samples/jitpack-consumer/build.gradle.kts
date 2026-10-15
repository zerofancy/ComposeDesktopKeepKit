plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("org.jetbrains.compose") version "1.9.3"
    id("io.github.zerofancy.compose-desktop-keep-kit") version "v0.2.5"
}
kotlin { jvmToolchain(17) }
kotlin.sourceSets.getByName("main").kotlin.srcDir("../coil-desktop/src/main/kotlin")
dependencies {
    implementation(compose.desktop.currentOs)
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-svg:3.3.0")
}
compose.desktop.application {
    mainClass = "MainKt"
    buildTypes.release.proguard {
        obfuscate.set(true)
        optimize.set(true)
    }
}
