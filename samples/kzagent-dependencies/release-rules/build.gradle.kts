import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins { java }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
val javaLauncher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
val scenario = providers.gradleProperty("releaseScenario").orElse("mcp-core").get()
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("release-rules/$scenario"))
val casesFile = layout.projectDirectory.file("cases.json")
val cases = JsonSlurper().parse(casesFile.asFile) as Map<*, *>
val setup = cases[scenario] as Map<*, *>
val fragments = (setup["fragments"] as List<*>).map { file("../../../src/main/resources/keepkit/$it") }
val proguard by configurations.creating

dependencies {
    implementation(requireNotNull(setup["dependency"]).toString())
    // The facade is selected by the application and is optional in kotlin-logging metadata.
    if (scenario == "kotlin-logging") runtimeOnly("org.slf4j:slf4j-api:2.0.20")
    // Match the transitive Reactor selected alongside MCP in KZAgent.
    if (scenario == "coroutines-reactor") constraints.add("implementation", "io.projectreactor:reactor-core:3.7.0")
    proguard("com.guardsquare:proguard-base:7.8.0")
}
sourceSets.main { java.setSrcDirs(listOf("src/common", "src/$scenario")) }

val output = rootProject.layout.buildDirectory.dir("verification/release-rules/$scenario")
val prepare = tasks.register("prepareReleaseProbes") {
    dependsOn(tasks.jar)
    inputs.files(configurations.runtimeClasspath, proguard, tasks.jar.flatMap { it.archiveFile }, fragments, casesFile)
    inputs.property("launcher", javaLauncher.map { it.executablePath.asFile.absolutePath })
    outputs.files(output.map { it.file("input.pro") }, output.map { it.file("runtime.json") })
    doLast {
        val directory = output.get().asFile.apply { mkdirs() }
        val jar = tasks.jar.get().archiveFile.get().asFile
        val dependencies = configurations.runtimeClasspath.get().files
        val javaHome = javaLauncher.get().metadata.installationPath.asFile
        val bossTerm = if (scenario == "bossterm") {
            configurations.runtimeClasspath.get().incoming.artifacts.artifacts.single {
                val id = it.id.componentIdentifier as? ModuleComponentIdentifier
                id?.group == "com.risaboss" && id.module == "bossterm-compose-desktop"
            }.file
        } else null
        directory.resolve("input.pro").writeText(buildString {
            appendLine("-injars '$jar'")
            val renderer = "ai/rever/bossterm/compose/ui/ProperTerminalKt*.class"
            val standardFilter = "!META-INF/versions/**,!module-info.class"
            dependencies.forEach {
                if (it == bossTerm) {
                    appendLine("-injars '$it'($renderer)")
                    appendLine("-libraryjars '$it'(!$renderer,$standardFilter)")
                } else {
                    val option = if (bossTerm == null) "-injars" else "-libraryjars"
                    appendLine("$option '$it'($standardFilter)")
                }
            }
            javaHome.resolve("jmods").listFiles()!!.filter { it.extension == "jmod" }.sortedBy { it.name }.forEach {
                appendLine("-libraryjars '$it'(!**.jar;!module-info.class)")
            }
            appendLine("-keep class smoke.Main { public static void main(java.lang.String[]); }")
            appendLine("-keep,allowobfuscation class smoke.Probe")
            if (bossTerm != null) {
                appendLine("-dontskipnonpubliclibraryclassmembers")
                // Retain the problematic pointer handler in the negative, with optimization enabled.
                appendLine("-keepclassmembers,allowoptimization,allowobfuscation class ai.rever.bossterm.compose.ui.ProperTerminalKt { private static kotlin.Unit ProperTerminal\$lambda\$139\$2\$0(...); }")
            }
        })
        directory.resolve("runtime.json").writeText(JsonOutput.toJson(mapOf(
            "java" to javaLauncher.get().executablePath.asFile.absolutePath,
            "proguard" to proguard.asPath,
            "plainClasspath" to (listOf(jar) + dependencies).joinToString(File.pathSeparator),
            "fragments" to fragments.map { it.absolutePath },
            "releaseLibraries" to if (bossTerm == null) "" else dependencies.joinToString(File.pathSeparator)
        )))
    }
}
