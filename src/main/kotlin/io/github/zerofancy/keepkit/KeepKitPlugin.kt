package io.github.zerofancy.keepkit

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.file.ConfigurableFileCollection
import java.io.File
import java.security.MessageDigest

class KeepKitPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("keepKit", KeepKitExtension::class.java)
        val supported = RuleCatalog.load().coordinates
        val scope = sha256(project.rootDir.canonicalPath + "\n" + project.path)
        val output = File(project.gradle.gradleUserHomeDir, "caches/compose-desktop-keep-kit/${PluginMetadata.version}/work/$scope")
        val generate = project.tasks.register("generateKeepKitRules", GenerateKeepKitRules::class.java) {
            it.group = "keep kit"
            it.description = "Selects built-in dependency keep rules into the Gradle user cache."
            it.excludedDependencies.set(extension.excludedDependencies)
        }
        val ensure = project.tasks.register("ensureKeepKitRules", EnsureKeepKitRules::class.java) {
            it.dependsOn(generate)
            it.modules.set(generate.flatMap { task -> task.modules })
            it.excludedDependencies.set(extension.excludedDependencies)
            it.rulesFile.set(generate.flatMap { task -> task.rulesFile })
            it.reportFile.set(generate.flatMap { task -> task.reportFile })
        }
        val announce = project.tasks.register("announceKeepKitRules", AnnounceKeepKitRules::class.java) {
            it.dependsOn(ensure)
            it.reportFile.set(generate.flatMap { task -> task.reportFile })
        }
        val render = project.tasks.register("renderKeepKitReport", KeepKitReport::class.java) {
            it.dependsOn(ensure)
            it.reportFile.set(generate.flatMap { task -> task.reportFile })
            it.htmlFile.set(project.layout.file(generate.flatMap { task -> task.reportFile }.map { file -> File(file.asFile.parentFile, "report.html") }))
        }
        project.tasks.register("keepKitReport", ShowKeepKitReport::class.java) {
            it.group = "keep kit"
            it.description = "Renders a dependency rule report outside the consuming project."
            it.htmlFile.set(render.flatMap { task -> task.htmlFile })
        }
        project.pluginManager.withPlugin("org.jetbrains.compose") {
            val compose = project.extensions.getByName("compose") as ExtensionAware
            val desktop = compose.extensions.getByName("desktop")
            val application = property(desktop, "getApplication")
            val settings = property(property(property(application, "getBuildTypes"), "getRelease"), "getProguard")
            val rules = property(settings, "getConfigurationFiles") as ConfigurableFileCollection
            rules.from(generate.flatMap { it.rulesFile })
            project.tasks.matching { it.name == "proguardReleaseJars" }.configureEach {
                it.dependsOn(announce)
            }
        }
        project.afterEvaluate {
            if (!project.pluginManager.hasPlugin("org.jetbrains.compose")) {
                throw GradleException("KeepKit requires org.jetbrains.compose in a Compose Desktop application module.")
            }
            val configuration = runtimeConfiguration(project)
            // ResolutionResult's root provider is lazy and supported by Gradle's configuration cache.
            val modules = configuration.incoming.resolutionResult.rootComponent.map { root ->
                resolvedModules(root, supported)
            }
            // Immutable, input-specific paths prevent concurrent builds with different
            // exclusions/dependency selections from replacing files ProGuard is using.
            val directory = modules.zip(extension.excludedDependencies) { selected, excluded ->
                val result = RuleCatalog.load().generate(selected, excluded)
                File(output, sha256(result.rules + "\n" + RuleCatalog.gson.toJson(result.report)))
            }
            generate.configure {
                it.modules.set(modules)
                it.rulesFile.set(project.layout.file(directory.map { dir -> File(dir, "generated.pro") }))
                it.reportFile.set(project.layout.file(directory.map { dir -> File(dir, "report.json") }))
            }
        }
    }

    private fun runtimeConfiguration(project: Project): Configuration {
        val compose = project.extensions.getByName("compose") as ExtensionAware
        val app = property(compose.extensions.getByName("desktop"), "getApplication")
        // Compose 1.9.3's selected application source is not exposed in its public DSL.
        // This small, fail-closed adapter reads the selected provider at configuration
        // time, rather than silently analysing main when application.from(...) differs.
        val dataGetter = app.javaClass.methods.singleOrNull { it.name.startsWith("getData") && it.parameterCount == 0 }
            ?: throw GradleException("KeepKit cannot inspect this Compose application model.")
        val data = dataGetter.invoke(app)
        val extra = property(data, "getFromFiles") as ConfigurableFileCollection
        if (extra.from.isNotEmpty()) throw GradleException("KeepKit v0.1 does not support Compose application.fromFiles custom inputs.")
        val runtime = data.javaClass.getMethod("getJvmApplicationRuntimeFilesProvider").invoke(data)
        if (runtime != null) {
            when (runtime.javaClass.simpleName) {
                "FromGradleSourceSet" -> {
                    val sourceSet = field(runtime, "sourceSet") as org.gradle.api.tasks.SourceSet
                    return project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName)
                }
                "FromKotlinMppTarget" -> {
                    val target = field(runtime, "target")
                    val compilations = property(target, "getCompilations") as org.gradle.api.NamedDomainObjectCollection<*>
                    val name = property(compilations.getByName("main"), "getRuntimeDependencyConfigurationName") as String
                    return project.configurations.getByName(name)
                }
                else -> throw GradleException("KeepKit v0.1 does not support custom Compose runtime inputs: ${runtime.javaClass.simpleName}.")
            }
        }
        val kotlin = project.extensions.findByName("kotlin")
        if (project.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform") && kotlin != null) {
            val targets = (property(kotlin, "getTargets") as Iterable<*>).filterNotNull()
                .filter { property(it, "getPlatformType").toString() == "jvm" }
            if (targets.size != 1) throw GradleException("KeepKit v0.1 requires exactly one KMP JVM desktop target; found ${targets.size}.")
            val compilations = property(targets.single(), "getCompilations") as org.gradle.api.NamedDomainObjectCollection<*>
            val main = compilations.getByName("main")
            val name = property(main, "getRuntimeDependencyConfigurationName") as String
            return project.configurations.getByName(name)
        }
        val sourceSets = project.extensions.findByType(SourceSetContainer::class.java)
            ?: throw GradleException("KeepKit requires a JVM or single-JVM-target KMP application.")
        return project.configurations.getByName(sourceSets.getByName("main").runtimeClasspathConfigurationName)
    }
}

internal fun resolvedModules(root: ResolvedComponentResult, supported: Set<String>): List<String> {
    val queue = ArrayDeque<ResolvedComponentResult>()
    val seen = hashSetOf<org.gradle.api.artifacts.component.ComponentIdentifier>()
    val modules = sortedSetOf<String>()
    queue.add(root)
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        if (!seen.add(component.id)) continue
        val id = component.id
        if (id is ModuleComponentIdentifier && "${id.group}:${id.module}" in supported) {
            modules += "${id.group}:${id.module}:${id.version}"
        }
        component.dependencies.forEach { dependency ->
            when (dependency) {
                is ResolvedDependencyResult -> if (!dependency.isConstraint) queue.add(dependency.selected)
                is UnresolvedDependencyResult -> throw GradleException("KeepKit could not resolve ${dependency.attempted.displayName}", dependency.failure)
            }
        }
    }
    return modules.toList()
}

internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

// Only public model getters are reflected, at configuration time. This avoids forcing
// a Compose/Kotlin plugin version or classloader onto the consuming build.
internal fun property(model: Any, getter: String): Any = try {
    model.javaClass.getMethod(getter).invoke(model)
        ?: throw GradleException("KeepKit received null from $getter")
} catch (error: ReflectiveOperationException) {
    throw GradleException("KeepKit does not support this Compose/Kotlin model: ${model.javaClass.name}.$getter", error)
}

private fun field(model: Any, name: String): Any = try {
    model.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(model)
        ?: throw GradleException("KeepKit received a null Compose $name")
} catch (error: ReflectiveOperationException) {
    throw GradleException("KeepKit cannot inspect the selected Compose application input ($name).", error)
}
