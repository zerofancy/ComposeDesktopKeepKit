package io.github.zerofancy.keepkit

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.io.File
import java.util.jar.JarOutputStream
import java.util.jar.JarEntry
import kotlin.test.*

class KeepKitPluginTest {
    @TempDir lateinit var temporary: Path
    private fun fixture(directory: File, disabled: Boolean = false, reversed: Boolean = false) {
        directory.mkdirs()
        File(directory, "settings.gradle").writeText("""
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
            rootProject.name = 'fixture'
            buildCache { local { directory = new File('${temporary.toFile().invariantSeparatorsPath}', 'build-cache') } }
        """.trimIndent())
        val repo = File(temporary.toFile(), "repo")
        module(repo, "io.coil-kt.coil3", "coil-core-jvm", "3.0.0")
        module(repo, "io.coil-kt.coil3", "coil-core-jvm", "3.4.0")
        module(repo, "unknown", "parent", "1.0", "<dependencies><dependency><groupId>io.coil-kt.coil3</groupId><artifactId>coil-core-jvm</artifactId><version>3.4.0</version></dependency></dependencies>")
        val plugins = if (reversed) "id 'io.github.zerofancy.compose-desktop-keep-kit'\nid 'org.jetbrains.compose' version '1.9.3'" else "id 'org.jetbrains.compose' version '1.9.3'\nid 'io.github.zerofancy.compose-desktop-keep-kit'"
        File(directory, "build.gradle").writeText("""
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '2.2.0'
                id 'org.jetbrains.kotlin.plugin.compose' version '2.2.0'
                $plugins
            }
            repositories { maven { url = uri('${repo.invariantSeparatorsPath}') }; mavenCentral(); google() }
            dependencies {
                implementation 'io.coil-kt.coil3:coil-core-jvm:3.0.0'
                implementation 'unknown:parent:1.0'
            }
            compose.desktop.application { mainClass = 'UnusedKt' }
            ${if (disabled) "keepKit { excludedDependencies.add('io.coil-kt.coil3:coil-core-jvm') }" else ""}
        """.trimIndent())
    }
    private fun module(repo: File, group: String, name: String, version: String, extra: String = "") {
        val root = File(repo, "${group.replace('.', '/')}/$name/$version").apply { mkdirs() }
        File(root, "$name-$version.pom").writeText("<project><modelVersion>4.0.0</modelVersion><groupId>$group</groupId><artifactId>$name</artifactId><version>$version</version>$extra</project>")
        JarOutputStream(File(root, "$name-$version.jar").outputStream()).use {
            it.putNextEntry(JarEntry("META-INF/services/not-valid")); it.write("BROKEN SERVICE FILE".toByteArray()); it.closeEntry()
            it.putNextEntry(JarEntry("META-INF/proguard/should-not-load.pro")); it.write("-keep class should.not.be.Loaded { *; }".toByteArray()); it.closeEntry()
        }
    }
    private fun run(directory: File, vararg arguments: String) = GradleRunner.create()
        .withProjectDir(directory).withPluginClasspath()
        .withArguments(*arguments, "--stacktrace", "--console=plain", "--max-workers=2", "-Dorg.gradle.jvmargs=-Xmx768m")
        .build()
    @Test fun `resolved transitive conflict logs even with cache and configuration replay`() {
        val directory = File(temporary.toFile(), "project")
        fixture(directory)
        val first = run(directory, "announceKeepKitRules", "keepKitReport", "--configuration-cache", "--build-cache")
        assertContains(first.output, "coil-core-jvm:3.4.0 添加了 keep 规则")
        assertContains(first.output, "规则版本：3.3.0，向下选择")
        assertFalse(first.output.contains("自动为依赖 unknown:"))
        val second = run(directory, "announceKeepKitRules", "keepKitReport", "--configuration-cache", "--build-cache")
        assertContains(second.output, "Reusing configuration cache")
        assertContains(second.output, "coil-core-jvm:3.4.0 添加了 keep 规则")
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateKeepKitRules")?.outcome)
        val htmlPath = Regex("\\[KeepKit] 报告：(.*)").find(second.output)!!.groupValues[1].trim()
        val report = File(htmlPath)
        assertFalse(report.toPath().startsWith(directory.toPath()))
        val rules = File(report.parentFile, "generated.pro")
        assertContains(rules.readText(), "FetcherServiceLoaderTarget")
        assertFalse(rules.readText().contains("should.not.be.Loaded"))
        assertFalse(directory.walkTopDown().any { it.extension == "pro" })
        assertTrue(rules.delete(), rules.absolutePath)
        val recovered = run(directory, "announceKeepKitRules", "keepKitReport", "--configuration-cache", "--build-cache")
        assertTrue(rules.exists())
        assertContains(recovered.output, "添加了 keep 规则")
        rules.writeText("CORRUPTED")
        val repaired = run(directory, "announceKeepKitRules", "keepKitReport", "--configuration-cache", "--build-cache")
        assertContains(rules.readText(), "FetcherServiceLoaderTarget")
        assertContains(repaired.output, "添加了 keep 规则")
        assertTrue(rules.delete())
        val restored = run(directory, "announceKeepKitRules", "keepKitReport", "--configuration-cache", "--build-cache", "--no-watch-fs")
        assertEquals(TaskOutcome.FROM_CACHE, restored.task(":generateKeepKitRules")?.outcome)
        assertTrue(rules.exists())
        assertContains(restored.output, "coil-core-jvm:3.4.0 添加了 keep 规则")
    }
    @Test fun `optional exclusion suppresses rules and changes invalidate generation`() {
        val directory = File(temporary.toFile(), "excluded")
        fixture(directory, disabled = true, reversed = true)
        val disabled = run(directory, "announceKeepKitRules", "keepKitReport", "--configuration-cache")
        assertFalse(disabled.output.contains("添加了 keep 规则"))
        val path = Regex("\\[KeepKit] 报告：(.*)").find(disabled.output)!!.groupValues[1].trim()
        assertEquals("", File(File(path).parentFile, "generated.pro").readText())
        fixture(directory, disabled = false, reversed = true)
        val enabled = run(directory, "announceKeepKitRules", "--configuration-cache")
        assertContains(enabled.output, "添加了 keep 规则")
        assertEquals(TaskOutcome.SUCCESS, enabled.task(":generateKeepKitRules")?.outcome)
    }
    @Test fun `KMP discovers desktop runtime and custom source set uses selected inputs`() {
        val kmp = File(temporary.toFile(), "kmp")
        fixture(kmp)
        val build = File(kmp, "build.gradle")
        build.writeText(build.readText().replace("org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.multiplatform")
            .replace("repositories { maven", "kotlin { jvm('desktop') }\nrepositories { maven")
            .replace("implementation 'io.coil-kt.coil3:coil-core-jvm:3.0.0'", "add('desktopMainImplementation', 'io.coil-kt.coil3:coil-core-jvm:3.0.0')")
            .replace("implementation 'unknown:parent:1.0'", "add('desktopMainImplementation', 'unknown:parent:1.0')"))
        val result = run(kmp, "announceKeepKitRules", "--configuration-cache")
        assertContains(result.output, "coil-core-jvm:3.4.0 添加了 keep 规则")
        val sourceSet = File(temporary.toFile(), "selected-source-set")
        fixture(sourceSet)
        File(sourceSet, "build.gradle").appendText("\nsourceSets { other }\ncompose.desktop.application.disableDefaultConfiguration()\ncompose.desktop.application.from(sourceSets.other)\n")
        val selected = run(sourceSet, "announceKeepKitRules")
        assertFalse(selected.output.contains("添加了 keep 规则"))
    }

}
