plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21"
    id("org.jetbrains.compose") version "1.9.3"
    id("io.github.zerofancy.compose-desktop-keep-kit")
}
kotlin { jvmToolchain(21) }
val isolated = providers.gradleProperty("isolated").orNull
val logbackVersion = providers.gradleProperty("logbackVersion").orElse("1.6.5").get()
val isolatedDependencies = mapOf(
    "retrofit" to "com.squareup.retrofit2:retrofit:3.0.0",
    "okhttp" to "com.squareup.okhttp3:okhttp:5.5.0",
    "serialization" to "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0",
    "serialization-core" to "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0",
    "slf4j" to "org.slf4j:slf4j-api:2.0.20",
    "logback-core" to "ch.qos.logback:logback-core:$logbackVersion",
    "logback-classic" to "ch.qos.logback:logback-classic:$logbackVersion",
    "jna" to "net.java.dev.jna:jna:5.19.1",
    "jna-platform" to "net.java.dev.jna:jna-platform:5.19.1",
    "jna-jpms" to "net.java.dev.jna:jna-jpms:5.16.0",
    "jna-platform-jpms" to "net.java.dev.jna:jna-platform-jpms:5.16.0",
    "dbus-core" to "com.github.hypfvieh:dbus-java-core:5.2.0",
    "dbus-transport" to "com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.0",
    "sqlite" to "org.xerial:sqlite-jdbc:3.53.4.0",
    "llamatik" to "com.llamatik:library-jvm:1.10.1",
    "filekit" to "io.github.vinceglb:filekit-dialogs:0.16.0",
    "sqldelight" to "app.cash.sqldelight:sqlite-driver:2.4.1",
    "retrofit-converter" to "com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0"
)
val isolatedMain = mapOf(
    "retrofit" to "RetrofitSmoke", "okhttp" to "OkhttpSmoke",
    "slf4j" to "Slf4jSmoke", "logback-core" to "LogbackCoreSmoke",
    "logback-classic" to "LogbackClassicSmoke", "jna" to "JnaSmoke",
    "jna-platform" to "JnaPlatformSmoke", "dbus-core" to "DbusCoreSmoke",
    "jna-jpms" to "JnaSmoke", "jna-platform-jpms" to "JnaPlatformSmoke",
    "dbus-transport" to "DbusTransportSmoke", "sqlite" to "SqliteSmoke",
    "llamatik" to "LlamatikSmoke", "filekit" to "FilekitSmoke",
    "sqldelight" to "SqldelightSmoke", "retrofit-converter" to "ConverterSmoke"
)
dependencies {
    implementation(compose.desktop.currentOs)
    if (isolated != null) {
        implementation(isolatedDependencies.getValue(isolated))
    } else {
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("ch.qos.logback:logback-classic:1.6.5")
    implementation("org.slf4j:slf4j-api:2.0.20")
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    implementation("com.github.hypfvieh:dbus-java-core:5.2.0")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.0")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.llamatik:library-jvm:1.10.1")
    }
}
if (isolated != null) {
    sourceSets.main {
        java.setSrcDirs(listOf("src/isolated/java"))
        java.include("isolated/${isolatedMain[isolated]}.java")
        resources.setSrcDirs(listOf("src/isolated/resources/$isolated"))
    }
    when (isolated) {
        "serialization" -> Unit
        "retrofit-converter" -> kotlin.sourceSets.main { kotlin.setSrcDirs(listOf("src/isolated/converter")) }
        "serialization-core" -> kotlin.sourceSets.main { kotlin.setSrcDirs(listOf("src/isolated/kotlin")) }
        else -> kotlin.sourceSets.main { kotlin.setSrcDirs(emptyList<String>()) }
    }
}
compose.desktop {
    application {
        mainClass = when (isolated) {
            null -> "smoke.Main"
            "serialization" -> "smoke.SerializationSmoke"
            "serialization-core" -> "smoke.CoreSerializationSmoke"
            else -> "isolated.${isolatedMain.getValue(isolated)}"
        }
        jvmArgs += "-Dsmoke.only=${providers.gradleProperty("smokeOnly").orElse("all").get()}"
        buildTypes.release.proguard {
            obfuscate.set(providers.gradleProperty("smokeObfuscate").orElse("true").get().toBooleanStrict())
            optimize.set(true)
            // Negative controls remove keeps, while optional integrations stay absent.
            if (providers.gradleProperty("excludeRules").isPresent) {
                configurationFiles.from(project.file("optional-warnings.pro"))
            }
        }
    }
}
// Each comma-separated coordinate removes only that adapter for negative controls.
providers.gradleProperty("excludeRules").orNull?.let {
    keepKit { excludedDependencies.addAll(it.split(',')) }
}

// Compose 1.9.3 already ships broad serialization keeps. Verify this adapter
// separately so that its negative control is not masked by Compose defaults.
val smokeProguard by configurations.creating
dependencies { smokeProguard("com.guardsquare:proguard-base:7.7.0") }
val serializationJar = layout.buildDirectory.file("serialization-smoke/output.jar")
val serializationBaseline = providers.gradleProperty("serializationNegative").orNull != "true"
val codecMain = if (isolated == "serialization-core") "CoreSerializationSmoke" else "SerializationSmoke"
val codecModels = if (isolated == "serialization-core") "Core" else ""
val shrinkSerializationSmoke = tasks.register<JavaExec>("shrinkSerializationSmoke") {
    dependsOn(tasks.jar)
    classpath = smokeProguard
    mainClass.set("proguard.ProGuard")
    inputs.files(configurations.runtimeClasspath, tasks.jar.flatMap { it.archiveFile })
    inputs.file("../../src/main/resources/keepkit/serialization.pro")
    inputs.property("withRules", serializationBaseline)
    outputs.file(serializationJar)
    doFirst {
        val runtime = configurations.runtimeClasspath.get().files
        val stdlib = runtime.filter { it.name.startsWith("kotlin-stdlib") || it.name.startsWith("annotations-") }
        val codec = runtime.filter { it.name.startsWith("kotlinx-serialization-") }
        val directory = serializationJar.get().asFile.parentFile.apply { mkdirs() }
        val config = directory.resolve("config.pro")
        config.writeText(buildString {
            appendLine("-injars '${tasks.jar.get().archiveFile.get().asFile}'(smoke/$codecMain.class,smoke/${codecModels}Payload*.class,smoke/${codecModels}NamedPayload*.class,smoke/${codecModels}SingletonPayload*.class)")
            codec.forEach { appendLine("-injars '$it'") }
            stdlib.forEach { appendLine("-libraryjars '$it'") }
            appendLine("-libraryjars '${System.getProperty("java.home")}/jmods/java.base.jmod'(!**.jar;!module-info.class)")
            appendLine("-outjars '${serializationJar.get().asFile}'")
            appendLine("-printmapping '${directory.resolve("mapping.txt")}'")
            appendLine("-keep class smoke.$codecMain { public static void main(java.lang.String[]); }")
            // Compiler-injected Compose marker has no role in this standalone codec test.
            appendLine("-dontwarn androidx.compose.runtime.internal.StabilityInferred")
            if (serializationBaseline) appendLine("-include '${file("../../src/main/resources/keepkit/serialization.pro")}'")
        })
        args("@${config.absolutePath}")
    }
}
tasks.register<JavaExec>("verifySerializationSmoke") {
    dependsOn(shrinkSerializationSmoke)
    mainClass.set("smoke.$codecMain")
    classpath = files(serializationJar) + files(provider {
        configurations.runtimeClasspath.get().files.filter { it.name.startsWith("kotlin-stdlib") }
    })
}

apply(from = "../ci-rule-inputs.gradle.kts")
