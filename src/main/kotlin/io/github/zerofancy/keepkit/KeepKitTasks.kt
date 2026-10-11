package io.github.zerofancy.keepkit

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

abstract class KeepKitExtension {
    abstract val excludedDependencies: SetProperty<String>
    init { excludedDependencies.convention(emptySet()) }
}

@CacheableTask
abstract class GenerateKeepKitRules : DefaultTask() {
    @get:Input abstract val modules: ListProperty<String>
    @get:Input abstract val excludedDependencies: SetProperty<String>
    @get:OutputFile abstract val rulesFile: RegularFileProperty
    @get:OutputFile abstract val reportFile: RegularFileProperty
    @TaskAction fun generate() {
        val result = RuleCatalog.load().generate(modules.get(), excludedDependencies.get())
        val rules = rulesFile.get().asFile
        withCacheLock(rules.parentFile) {
            atomicWrite(rules, result.rules)
            atomicWrite(reportFile.get().asFile, RuleCatalog.gson.toJson(result.report) + "\n")
        }
    }
}

@DisableCachingByDefault(because = "Verifies external user-cache files on every invocation, even if Gradle retains a stale file snapshot")
abstract class EnsureKeepKitRules : DefaultTask() {
    @get:Input abstract val modules: ListProperty<String>
    @get:Input abstract val excludedDependencies: SetProperty<String>
    @get:Internal abstract val rulesFile: RegularFileProperty
    @get:Internal abstract val reportFile: RegularFileProperty
    @TaskAction fun ensure() {
        val result = RuleCatalog.load().generate(modules.get(), excludedDependencies.get())
        val rules = rulesFile.get().asFile
        val report = reportFile.get().asFile
        val json = RuleCatalog.gson.toJson(result.report) + "\n"
        withCacheLock(rules.parentFile) {
            // GRADLE_USER_HOME is outside the project's filesystem watch roots.
            // Verify actual bytes rather than relying solely on daemon snapshots.
            if (!rules.isFile || rules.readText(Charsets.UTF_8) != result.rules) atomicWrite(rules, result.rules)
            if (!report.isFile || report.readText(Charsets.UTF_8) != json) atomicWrite(report, json)
        }
    }
}

@DisableCachingByDefault(because = "Logs selected rules on every Release invocation, including cache hits")
abstract class AnnounceKeepKitRules : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val reportFile: RegularFileProperty
    @TaskAction fun announce() {
        readReport(reportFile.get().asFile).applications.filter { it.selection != "excluded" }.forEach {
            val mode = when (it.selection) { "exact" -> "精确匹配"; "lower" -> "向下选择"; else -> "向上选择" }
            logger.lifecycle("[KeepKit] 自动为依赖 ${it.dependency}:${it.actualVersion} 添加了 keep 规则（规则版本：${it.ruleVersion}，$mode）")
        }
    }
}

@CacheableTask
abstract class KeepKitReport : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val reportFile: RegularFileProperty
    @get:OutputFile abstract val htmlFile: RegularFileProperty
    init { outputs.upToDateWhen { htmlFile.get().asFile.isFile } }
    @TaskAction fun render() {
        val report = readReport(reportFile.get().asFile)
        val rows = report.applications.joinToString("\n") {
            "<tr><td>${html(it.dependency)}</td><td>${html(it.actualVersion)}</td><td>${html(it.ruleVersion ?: "—")}</td><td>${html(it.selection)}</td><td>${html(it.rules.joinToString(", "))}</td><td>${html(it.source ?: "—")}</td></tr>"
        }
        val output = htmlFile.get().asFile
        withCacheLock(output.parentFile) {
            atomicWrite(output, """<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>KeepKit Report</title><style>body{font:16px/1.7 system-ui;max-width:1100px;margin:40px auto;padding:20px}table{border-collapse:collapse;width:100%}td,th{padding:12px;border-bottom:1px solid #ddd;text-align:left}</style><h1>KeepKit 依赖规则</h1><p>插件 ${html(report.pluginVersion)} · exact 精确匹配 / lower 向下选择 / higher 向上选择 / excluded 已禁用</p><table><tr><th>依赖</th><th>实际版本</th><th>规则版本</th><th>选择</th><th>规则文件</th><th>来源</th></tr>$rows</table></html>""")
        }
    }
}

@DisableCachingByDefault(because = "Prints report location even when the report renderer is up-to-date")
abstract class ShowKeepKitReport : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val htmlFile: RegularFileProperty
    @TaskAction fun show() { logger.lifecycle("[KeepKit] 报告：${htmlFile.get().asFile.absolutePath}") }
}

internal fun readReport(file: File): RuleReport = file.reader(Charsets.UTF_8).use {
    RuleCatalog.gson.fromJson(it, RuleReport::class.java)
}
internal fun html(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
internal fun atomicWrite(file: File, content: String) {
    Files.createDirectories(file.parentFile.toPath())
    val temporary = Files.createTempFile(file.parentFile.toPath(), ".keepkit-", ".tmp")
    try {
        Files.writeString(temporary, content, Charsets.UTF_8)
        try { Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    } finally { Files.deleteIfExists(temporary) }
}
private fun withCacheLock(directory: File, block: () -> Unit) {
    Files.createDirectories(directory.toPath())
    // In-process monitor also avoids OverlappingFileLockException from parallel Gradle tasks.
    synchronized(directory.canonicalPath.intern()) {
        FileChannel.open(directory.toPath().resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { block() }
        }
    }
}
