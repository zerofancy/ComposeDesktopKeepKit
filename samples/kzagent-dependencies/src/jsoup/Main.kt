package smoke

import org.jsoup.Jsoup
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

fun main() {
    val document = Jsoup.parse("<a href='/hello'>Hello 42</a><p>Other</p>")
    check(document.select("a:matches(Hello\\s+\\d+)").single().attr("href") == "/hello")
    check(document.select("p").text() == "Other")
    // Assert both dynamically loaded implementations survive obfuscation.
    val helperPackage = listOf("org", "jsoup", "helper").joinToString(".")
    Class.forName("$helperPackage.RequestAuthHandler").getConstructor().apply { isAccessible = true }.newInstance()
    val executor = Class.forName("$helperPackage.HttpClientExecutor")
    check(executor.constructors.single().parameterCount == 2)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/") { exchange ->
        val body = "<p>Network 42</p>".toByteArray()
        exchange.responseHeaders.add("Content-Type", "text/html")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }
    server.start()
    try {
        val page = Jsoup.connect("http://127.0.0.1:${server.address.port}/").get()
        check(page.select("p:matches(Network\\s+\\d+)").text() == "Network 42")
    } finally { server.stop(0) }
    println("PASS jsoup regex fallback, HTTP and reflected Java 11 implementations")
}
