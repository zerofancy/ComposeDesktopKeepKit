import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins { kotlin("jvm") version "2.4.0" }
kotlin { jvmToolchain(17) }
val scenario = providers.gradleProperty("scenario").orElse("coroutines-swing").get()
val negative = providers.gradleProperty("negative").orNull == "true"
val warningsOnly = scenario != "jsoup" || providers.gradleProperty("warningsOnly").orNull == "true"
val proguard by configurations.creating
dependencies {
    implementation(when (scenario) {
        "coroutines-core" -> "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0"
        "coroutines-swing" -> "org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0"
        "jsoup" -> "org.jsoup:jsoup:1.22.2"
        else -> error("Unknown scenario: $scenario")
    })
    proguard("com.guardsquare:proguard-base:7.7.0")
}
kotlin.sourceSets.main { kotlin.setSrcDirs(listOf("src/$scenario")) }
sourceSets.main { resources.setSrcDirs(if (scenario == "coroutines-core") listOf("src/main/resources") else emptyList<String>()) }
val fragments = when (scenario) {
    "coroutines-core" -> listOf("coroutines.pro")
    "coroutines-swing" -> listOf("coroutines.pro", "coroutines-swing.pro")
    else -> listOf("jsoup.pro")
}.map { file("../../src/main/resources/keepkit/$it") }
val outputJar = layout.buildDirectory.file("$scenario/output.jar")
val shrink = tasks.register<JavaExec>("shrinkSmoke") {
    dependsOn(tasks.jar)
    classpath = proguard
    mainClass.set("proguard.ProGuard")
    inputs.files(configurations.runtimeClasspath, tasks.jar.flatMap { it.archiveFile }, fragments)
    inputs.property("negative", negative)
    inputs.property("warningsOnly", warningsOnly)
    outputs.file(outputJar)
    doFirst {
        val directory = outputJar.get().asFile.parentFile.apply { mkdirs() }
        val config = directory.resolve("config.pro")
        config.writeText(buildString {
            appendLine("-injars '${tasks.jar.get().archiveFile.get().asFile}'")
            configurations.runtimeClasspath.get().files.forEach { jar ->
                // Normalize jsoup's Java 11 multi-release classes for a JDK 17+
                // standalone fixture, so the runtime reflection paths are tested.
                val input = if (jar.name.startsWith("jsoup-")) {
                    directory.resolve("jsoup-jvm.jar").also { flattened ->
                        ZipFile(jar).use { source ->
                            ZipOutputStream(flattened.outputStream()).use { target ->
                                source.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                                    val name = entry.name.removePrefix("META-INF/versions/11/")
                                    if (name != "module-info.class") {
                                        target.putNextEntry(ZipEntry(name))
                                        source.getInputStream(entry).use { it.copyTo(target) }
                                        target.closeEntry()
                                    }
                                }
                            }
                        }
                    }
                } else jar
                appendLine("-injars '$input'(!META-INF/versions/**,!module-info.class)")
            }
            listOf("java.base", "java.desktop", "java.management", "java.logging", "java.xml", "java.net.http", "jdk.httpserver", "jdk.unsupported").forEach {
                appendLine("-libraryjars '${System.getProperty("java.home")}/jmods/$it.jmod'(!**.jar;!module-info.class)")
            }
            appendLine("-outjars '${outputJar.get().asFile}'")
            appendLine("-printmapping '${directory.resolve("mapping.txt")}'")
            appendLine("-keep class smoke.MainKt { public static void main(java.lang.String[]); }")
            if (!negative) fragments.forEach { appendLine("-include '$it'") }
            else if (warningsOnly) fragments.forEach { fragment ->
                fragment.readLines().filter { it.startsWith("-dontwarn") }.forEach { appendLine(it) }
            }
        })
        args("@${config.absolutePath}")
    }
}
tasks.register<JavaExec>("verifySmoke") {
    dependsOn(shrink)
    mainClass.set("smoke.MainKt")
    classpath = files(outputJar)
    systemProperty("java.awt.headless", "true")
}

extra["ciRuleFragments"] = fragments.map { it.name }
apply(from = "../ci-manual-inputs.gradle.kts")
