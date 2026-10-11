package io.github.zerofancy.keepkit

import org.junit.jupiter.api.Test
import kotlin.test.*

class RuleCatalogTest {
    @Test fun `KZAgent missing adapters use resolved coordinates and scoped rules`() {
        val modules = listOf(
            "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0",
            "org.jsoup:jsoup:1.22.2"
        )
        val catalog = RuleCatalog.load()
        val result = catalog.generate(modules, emptySet())
        assertEquals(3, result.report.applications.size)
        assertTrue(result.report.applications.all { it.selection == "exact" })
        assertContains(result.rules, "implements kotlinx.coroutines.internal.MainDispatcherFactory")
        assertContains(result.rules, "implements kotlinx.coroutines.CoroutineExceptionHandler")
        assertContains(result.rules, "class kotlinx.coroutines.swing.SwingDispatcherFactory")
        assertContains(result.rules, "volatile <fields>;")
        assertContains(result.rules, "class kotlinx.coroutines.flow.ReadonlyStateFlow")
        assertContains(result.rules, "class kotlinx.coroutines.flow.ReadonlySharedFlow")
        assertContains(result.rules, "-dontwarn com.google.re2j.**")
        assertContains(result.rules, "class org.jsoup.helper.HttpClientExecutor")
        assertContains(result.rules, "class org.jsoup.helper.RequestAuthHandler")
        assertContains(result.rules, "enum org.jsoup.Connection\$Method")
        assertContains(result.rules, "class kotlinx.coroutines.flow.FlowKt__*")
        assertContains(result.rules, "invokeOnCompletion(boolean, boolean, kotlin.jvm.functions.Function1)")
        assertFalse(result.rules.contains("-keep class org.jsoup.**"))
        assertEquals("", catalog.generate(modules, modules.map { it.substringBeforeLast(':') }.toSet()).rules)
        // Existing adapters already match the versions actually selected in KZAgent.
        val existing = catalog.generate(listOf(
            "io.coil-kt.coil3:coil-core-jvm:3.5.0",
            "io.coil-kt.coil3:coil-network-okhttp-jvm:3.5.0",
            "io.coil-kt.coil3:coil-svg-jvm:3.5.0",
            "com.squareup.okio:okio-jvm:3.17.0",
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0"
        ), emptySet())
        assertEquals(5, existing.report.applications.size)
        assertEquals(4, existing.report.applications.count { it.selection == "lower" })
    }
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
    @Test fun `Logback legacy version adds optional Janino rules without affecting modern baseline`() {
        val catalog = RuleCatalog.load()
        val legacy = catalog.generate(listOf("ch.qos.logback:logback-core:1.5.16", "ch.qos.logback:logback-classic:1.5.16"), emptySet())
        assertEquals(2, legacy.report.applications.size)
        assertTrue(legacy.report.applications.all { it.selection == "exact" && it.ruleVersion == "1.5.16" })
        assertContains(legacy.rules, "-dontwarn org.codehaus.janino.ClassBodyEvaluator")
        assertContains(legacy.rules, "-dontwarn org.codehaus.commons.compiler.CompileException")
        assertContains(legacy.rules, "public static ch.qos.logback.core.util.FileSize valueOf(java.lang.String);")
        val modern = catalog.generate(listOf("ch.qos.logback:logback-core:1.6.5"), emptySet())
        assertFalse(modern.rules.contains("org.codehaus.janino"))
        assertContains(modern.rules, "public static ch.qos.logback.core.util.FileSize valueOf(java.lang.String);")
    }
    @Test fun `JNA JPMS coordinates match independently and reuse deduplicated rules`() {
        val catalog = RuleCatalog.load()
        val jpms = listOf("net.java.dev.jna:jna-jpms:5.16.0", "net.java.dev.jna:jna-platform-jpms:5.16.0")
        val result = catalog.generate(jpms, emptySet())
        assertEquals(2, result.report.applications.size)
        assertTrue(result.report.applications.all { it.selection == "exact" && it.ruleVersion == "5.16.0" })
        val mixed = catalog.generate(jpms + listOf("net.java.dev.jna:jna:5.19.1", "net.java.dev.jna:jna-platform:5.19.1"), emptySet())
        assertEquals(result.rules, mixed.rules)
        assertEquals(4, mixed.report.applications.size)
        val excluded = catalog.generate(jpms, setOf("net.java.dev.jna:jna-jpms"))
        assertFalse(excluded.rules.contains("class com.sun.jna.Native {"))
        assertTrue(excluded.rules.contains("interface com.sun.jna.platform.unix.LibCAPI"))
        assertEquals("excluded", excluded.report.applications.first { it.dependency == "net.java.dev.jna:jna-jpms" }.selection)
    }
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
