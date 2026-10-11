package io.github.zerofancy.keepkit

import com.google.gson.GsonBuilder
import org.apache.maven.artifact.versioning.ComparableVersion

internal data class RuleVersion(val version: String, val rules: List<String>, val source: String)
internal data class DependencyRules(val dependency: String, val versions: List<RuleVersion>)
internal data class CatalogDocument(val schemaVersion: Int, val dependencies: List<DependencyRules>)
internal data class ApplicationRecord(
    val dependency: String, val actualVersion: String, val ruleVersion: String?,
    val selection: String, val rules: List<String>, val source: String?
)
internal data class RuleReport(val schemaVersion: Int = 1, val pluginVersion: String = PluginMetadata.version, val applications: List<ApplicationRecord>)
internal data class GeneratedRules(val rules: String, val report: RuleReport)

internal class RuleCatalog(private val document: CatalogDocument, private val readRule: (String) -> String) {
    val coordinates: Set<String> get() = document.dependencies.map { it.dependency }.toSet()
    init {
        require(document.schemaVersion == 1) { "Unsupported KeepKit catalog schema" }
        require(document.dependencies.map { it.dependency }.distinct().size == document.dependencies.size) { "Duplicate dependency in catalog" }
        document.dependencies.forEach { entry ->
            validateCoordinate(entry.dependency)
            require(entry.versions.isNotEmpty()) { "Empty rule versions: ${entry.dependency}" }
            val versions = entry.versions.map { ComparableVersion(it.version) }
            require(versions.distinct().size == versions.size) { "Equivalent rule versions: ${entry.dependency}" }
            entry.versions.forEach { version ->
                require(version.version.isNotBlank() && version.rules.isNotEmpty() && version.source.isNotBlank()) { "Incomplete catalog entry" }
                version.rules.forEach { name ->
                    require(name.matches(Regex("[a-zA-Z0-9_-]+\\.pro"))) { "Invalid rule resource: $name" }
                    require(readRule(name).isNotBlank()) { "Empty rule resource: $name" }
                }
            }
        }
    }

    fun generate(modules: List<String>, excluded: Set<String>): GeneratedRules {
        excluded.forEach(::validateCoordinate)
        val records = mutableListOf<ApplicationRecord>()
        val snippets = linkedSetOf<String>()
        val entries = document.dependencies.associateBy { it.dependency }
        modules.distinct().sorted().forEach { module ->
            val parts = module.split(':', limit = 3)
            require(parts.size == 3 && parts[2].isNotBlank()) { "Invalid resolved module: $module" }
            val coordinate = parts.take(2).joinToString(":")
            val entry = entries[coordinate] ?: return@forEach
            if (coordinate in excluded) {
                records += ApplicationRecord(coordinate, parts[2], null, "excluded", emptyList(), null)
                return@forEach
            }
            val actual = ComparableVersion(parts[2])
            val sorted = entry.versions.sortedWith(compareBy { ComparableVersion(it.version) })
            val selected = sorted.lastOrNull { ComparableVersion(it.version) <= actual } ?: sorted.first()
            val comparison = ComparableVersion(selected.version).compareTo(actual)
            val selection = when { comparison == 0 -> "exact"; comparison < 0 -> "lower"; else -> "higher" }
            records += ApplicationRecord(coordinate, parts[2], selected.version, selection, selected.rules.sorted(), selected.source)
            selected.rules.sorted().forEach { snippets += readRule(it).replace("\r\n", "\n").replace('\r', '\n').trim() }
        }
        val text = if (snippets.isEmpty()) "" else snippets.joinToString("\n\n", postfix = "\n")
        return GeneratedRules(text, RuleReport(applications = records))
    }

    companion object {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        fun load(): RuleCatalog {
            val json = resource("catalog.json")
            return RuleCatalog(gson.fromJson(json, CatalogDocument::class.java), ::resource)
        }
        private fun resource(name: String): String = RuleCatalog::class.java.getResourceAsStream("/keepkit/$name")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("Missing KeepKit resource: $name")
        fun validateCoordinate(value: String) {
            require(value.matches(Regex("[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+"))) { "KeepKit excluded dependency must be group:module: $value" }
        }
    }
}
