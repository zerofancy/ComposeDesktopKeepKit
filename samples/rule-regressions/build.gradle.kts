import groovy.json.JsonOutput

plugins { java }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }

val scenario = providers.gradleProperty("scenario").orElse("jna").get()
val dependencyVersion = providers.gradleProperty("dependencyVersion").orElse(
    when (scenario) { "jna" -> "5.19.1"; "logback" -> "1.6.5"; "dbus" -> "5.2.0"; else -> error("Unknown scenario: $scenario") }
).get()
val jnaModule = providers.gradleProperty("jnaModule").orElse("jna").get()
require(jnaModule in listOf("jna", "jna-jpms"))
val omit = providers.gradleProperty("omit").orElse("").get()
val proguard by configurations.creating
dependencies {
    implementation(when (scenario) {
        "jna" -> "net.java.dev.jna:$jnaModule:$dependencyVersion"
        "logback" -> "ch.qos.logback:logback-classic:$dependencyVersion"
        else -> "com.github.hypfvieh:dbus-java-core:$dependencyVersion"
    })
    proguard("com.guardsquare:proguard-base:7.7.0")
}
sourceSets.main { java.setSrcDirs(listOf("src/$scenario")) }
val fragments = when (scenario) {
    "jna" -> listOf("jna.pro")
    "logback" -> listOf("logback.pro", "logback-classic.pro", "slf4j2.pro") +
        if (dependencyVersion == "1.5.16") listOf("logback-janino.pro") else emptyList()
    else -> listOf("dbus.pro", "slf4j2.pro")
}.map { file("../../src/main/resources/keepkit/$it") }
val fixtureMain = "audit.${scenario.replaceFirstChar { it.uppercase() }}Regression"
val directory = layout.buildDirectory.dir("$scenario-$jnaModule-$dependencyVersion-${omit.ifEmpty { "positive" }}")
val javaLauncher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }

// Remove one protection at a time. The assertions are runtime contracts, not text checks.
// Fail closed if a rule is renamed, rather than silently running a positive as a negative.
val mutations = mapOf(
    "lifecycle" to "    private static final java.lang.Object finalizer;",
    "pointer-constructor" to "    public <init>(com.sun.jna.Pointer);",
    "native-mapped-enum" to "-keepclassmembers enum * implements com.sun.jna.NativeMapped { public static **[] values(); }",
    "byreference-string" to "-keepclassmembers class * extends com.sun.jna.ptr.ByReference { public *** getValue(); }",
    "direct-names" to "-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }",
    "direct-stack" to """-keepclassmembers,allowshrinking,allowobfuscation class com.sun.jna.Native {
    public static void register(...);
    public static void unregister(...);
    static java.lang.Class getCallingClass();
}""",
    "logback-enum" to """-keepclassmembers enum ch.qos.logback.core.spi.FilterReply {
    public static **[] values();
}""",
    "logback-duration" to """-keepclassmembers class ch.qos.logback.core.util.Duration {
    public static ch.qos.logback.core.util.Duration valueOf(java.lang.String);
}""",
    "logback-level" to """-keepclassmembers class ch.qos.logback.classic.Level {
    public static ch.qos.logback.classic.Level valueOf(java.lang.String);
}""",
    "dbus-property" to """-keepclassmembers enum org.freedesktop.dbus.annotations.DBusProperty${'$'}Access,org.freedesktop.dbus.annotations.PropertiesEmitsChangedSignal${'$'}EmitChangeSignal {
    public static **[] values();
    public static ** *;
}""",
    "dbus-annotation" to "-keep @org.freedesktop.dbus.annotations.DBusInterfaceName @interface * { public <methods>; }"
)
val prepare = tasks.register("prepareRegression") {
    dependsOn(tasks.jar)
    inputs.files(configurations.runtimeClasspath, tasks.jar.flatMap { it.archiveFile }, fragments)
    inputs.property("omit", omit)
    outputs.files(directory.map { it.file("config.pro") }, directory.map { it.file("rules.pro") }, directory.map { it.file("runtime.json") })
    doLast {
        val output = directory.get().asFile.apply { mkdirs() }
        var rules = fragments.joinToString("\n") { it.readText().replace("\r\n", "\n") }
        if (omit.isNotEmpty()) {
            val removed = mutations.getValue(omit)
            require(rules.indexOf(removed) >= 0 && rules.indexOf(removed) == rules.lastIndexOf(removed)) {
                "Mutation must match exactly once: $omit"
            }
            rules = rules.replace(removed, "")
        }
        output.resolve("rules.pro").writeText(rules)
        val javaHome = javaLauncher.get().metadata.installationPath.asFile
        output.resolve("config.pro").writeText(buildString {
            appendLine("-injars '${tasks.jar.get().archiveFile.get().asFile}'")
            configurations.runtimeClasspath.get().files.forEach {
                appendLine("-injars '$it'(!META-INF/versions/**,!module-info.class)")
            }
            listOf("java.base", "java.desktop", "java.logging", "java.xml", "java.naming", "java.net.http", "java.management", "jdk.security.auth").forEach {
                appendLine("-libraryjars '$javaHome/jmods/$it.jmod'(!**.jar;!module-info.class)")
            }
            appendLine("-outjars '${output.resolve("output.jar")}'")
            appendLine("-printmapping '${output.resolve("mapping.txt")}'")
            appendLine("-printusage '${output.resolve("usage.txt")}'")
            appendLine("-keep class $fixtureMain { public static void main(java.lang.String[]); }")
            appendLine("-include '${output.resolve("rules.pro")}'")
        })
        output.resolve("runtime.json").writeText(JsonOutput.toJson(mapOf(
            "java" to javaLauncher.get().executablePath.asFile.absolutePath,
            "jar" to output.resolve("output.jar").absolutePath, "main" to fixtureMain,
            "plainClasspath" to (listOf(tasks.jar.get().archiveFile.get().asFile) + configurations.runtimeClasspath.get().files).joinToString(File.pathSeparator)
        )))
    }
}
tasks.register<JavaExec>("shrinkRegression") {
    dependsOn(prepare)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    classpath = proguard
    mainClass.set("proguard.ProGuard")
    inputs.files(directory.map { it.file("config.pro") }, directory.map { it.file("rules.pro") }, configurations.runtimeClasspath, tasks.jar.flatMap { it.archiveFile })
    outputs.files(directory.map { it.file("output.jar") }, directory.map { it.file("mapping.txt") }, directory.map { it.file("usage.txt") })
    doFirst { args("@${directory.get().file("config.pro").asFile.absolutePath}") }
}

extra["ciRuleFragments"] = fragments.map { it.name }
apply(from = "../ci-manual-inputs.gradle.kts")
