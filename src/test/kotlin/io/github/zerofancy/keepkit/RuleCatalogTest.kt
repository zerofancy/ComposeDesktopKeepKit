package io.github.zerofancy.keepkit

import org.junit.jupiter.api.Test
import kotlin.test.*

class RuleCatalogTest {
    private fun catalog(versions: List<String> = listOf("3.1.0", "3.3.0", "3.5.0")) = RuleCatalog(
        CatalogDocument(1, listOf(DependencyRules("example:library", versions.map { RuleVersion(it, listOf("test.pro"), "https://example.org") })))
    ) { "-keep class example.Service { *; }\r\n" }
    @Test fun `selects closest lower or first higher version`() {
        mapOf("3.3.0" to "3.3.0", "3.4.0" to "3.3.0", "3.0.0" to "3.1.0", "4.0.0" to "3.5.0").forEach { (actual, expected) ->
            assertEquals(expected, catalog().generate(listOf("example:library:$actual"), emptySet()).report.applications.single().ruleVersion)
        }
    }
    @Test fun `numeric and prerelease comparison is Maven ordering`() {
        val rules = catalog(listOf("3.9.0", "3.10.0-rc1", "3.10.0"))
        assertEquals("3.10.0-rc1", rules.generate(listOf("example:library:3.10.0-rc2"), emptySet()).report.applications.single().ruleVersion)
        assertEquals("3.10.0", rules.generate(listOf("example:library:3.11.0"), emptySet()).report.applications.single().ruleVersion)
    }
    @Test fun `excluded and unknown modules emit no rules`() {
        val result = catalog().generate(listOf("unknown:library:1.0", "example:library:3.3.0"), setOf("example:library"))
        assertEquals("", result.rules)
        assertEquals("excluded", result.report.applications.single().selection)
        assertEquals(emptyList(), catalog().generate(listOf("unknown:library:1"), emptySet()).report.applications)
    }
    @Test fun `duplicate equivalent versions and invalid coordinates fail`() {
        assertFailsWith<IllegalArgumentException> { catalog(listOf("3.3", "3.3.0")) }
        assertFailsWith<IllegalArgumentException> { catalog().generate(emptyList(), setOf("example:library:1")) }
        assertFailsWith<IllegalArgumentException> { catalog(emptyList()) }
    }
    @Test fun `Coil modules reuse deterministic rule text`() {
        val result = RuleCatalog.load().generate(listOf("io.coil-kt.coil3:coil-network-okhttp-jvm:3.3.0", "io.coil-kt.coil3:coil-core-jvm:3.3.0"), emptySet())
        assertEquals(2, result.report.applications.size)
        assertEquals(1, Regex("-keep class \\* implements coil3.util.FetcherServiceLoaderTarget").findAll(result.rules).count())
        assertFalse(result.rules.contains('\r'))
        assertEquals(result, RuleCatalog.load().generate(listOf("io.coil-kt.coil3:coil-core-jvm:3.3.0", "io.coil-kt.coil3:coil-network-okhttp-jvm:3.3.0"), emptySet()))
    }
    @Test fun `report text is escaped`() { assertEquals("&lt;script&gt;&amp;&quot;&#39;", html("<script>&\"'")) }
    @Test fun `integration catalog uses resolved JVM coordinates and scoped protection`() {
        val modules = listOf(
            "com.squareup.retrofit2:retrofit:3.0.0",
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0",
            "org.slf4j:slf4j-api:2.0.20", "ch.qos.logback:logback-core:1.6.5",
            "ch.qos.logback:logback-classic:1.6.5", "net.java.dev.jna:jna:5.19.1",
            "net.java.dev.jna:jna-platform:5.19.1",
            "com.github.hypfvieh:dbus-java-core:5.2.0",
            "com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.0",
            "org.xerial:sqlite-jdbc:3.53.4.0", "com.llamatik:library-jvm:1.10.1",
            "com.squareup.okhttp3:okhttp-jvm:5.5.0"
        )
        val result = RuleCatalog.load().generate(modules, emptySet())
        assertEquals(modules.size, result.report.applications.size)
        assertTrue(result.report.applications.all { it.selection == "exact" })
        assertFalse(Regex("-keep class [\\w.]+\\.\\*\\* \\{ \\*; }").containsMatchIn(result.rules))
        assertFalse(result.rules.contains("top.ntutn.kica"))
        assertFalse(result.rules.contains("-dontoptimize"))
    }
    @Test fun `OkHttp 5 root component and JVM artifact share modern rules`() {
        val result = RuleCatalog.load().generate(listOf(
            "com.squareup.okhttp3:okhttp:5.5.0", "com.squareup.okhttp3:okhttp-jvm:5.5.0"
        ), emptySet())
        assertTrue(result.report.applications.all { it.ruleVersion == "5.5.0" })
        assertEquals(1, Regex("-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase").findAll(result.rules).count())
        assertFalse(result.rules.contains("-dontwarn okhttp3.internal.platform.**"))
    }
}
