package io.github.zerofancy.keepkit

import java.util.Properties

internal object PluginMetadata {
    val version: String by lazy {
        val properties = Properties()
        val stream = PluginMetadata::class.java.getResourceAsStream("/keepkit/version.properties")
            ?: error("Missing KeepKit version metadata")
        stream.use { properties.load(it) }
        properties.getProperty("version").also {
            require(it != null && it.matches(Regex("[A-Za-z0-9_.-]+"))) { "Invalid KeepKit version metadata" }
        }
    }
}
