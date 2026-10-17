plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("org.jetbrains.compose") version "1.8.2"
    id("io.github.zerofancy.compose-desktop-keep-kit")
}
kotlin { jvmToolchain(21) }
val scenario = providers.gradleProperty("scenario").orElse("datastore").get()
dependencies {
    implementation(compose.desktop.currentOs)
    // Match FloatClock's resolved versions without directly adding unrelated libraries.
    constraints {
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
        implementation("org.slf4j:slf4j-api:2.0.19")
    }
    implementation(when (scenario) {
        "datastore" -> "androidx.datastore:datastore-preferences-core-jvm:1.1.7"
        "datastore-okio" -> "androidx.datastore:datastore-core-okio-jvm:1.1.7"
        "oshi" -> "com.github.oshi:oshi-core:6.6.5"
        else -> error("Unknown scenario: $scenario")
    })
}
kotlin.sourceSets.main { kotlin.setSrcDirs(listOf("src/$scenario")) }
// Test diagnostics prove that the successful runs actually used renamed/shrunk jars.
val diagnosticsFile = layout.buildDirectory.file("verification/$scenario-diagnostics.pro")
val prepareSmokeDiagnostics = tasks.register("prepareSmokeDiagnostics") {
    outputs.file(diagnosticsFile)
    doLast {
        val directory = diagnosticsFile.get().asFile.parentFile.apply { mkdirs() }
        diagnosticsFile.get().asFile.writeText(
            "-printmapping '${directory.resolve("$scenario-mapping.txt")}'\n" +
            "-printusage '${directory.resolve("$scenario-usage.txt")}'\n"
        )
    }
}
compose.desktop {
    application {
        mainClass = "smoke.MainKt"
        buildTypes.release.proguard {
            obfuscate.set(true)
            optimize.set(true)
            configurationFiles.from(prepareSmokeDiagnostics.map { diagnosticsFile.get().asFile })
        }
    }
}
providers.gradleProperty("excludeRules").orNull?.let {
    keepKit { excludedDependencies.addAll(it.split(',')) }
}

apply(from = "../ci-rule-inputs.gradle.kts")
