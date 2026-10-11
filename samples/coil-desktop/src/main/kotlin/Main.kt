import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Base64

/** Headless Release smoke: network fetcher and SVG decoder are discovered by Coil. */
fun main() {
    val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aP1sAAAAASUVORK5CYII=")
    val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="2" height="2"><rect width="2" height="2" fill="red"/></svg>""".toByteArray()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/image.png") { exchange ->
        exchange.responseHeaders.set("Content-Type", "image/png")
        exchange.sendResponseHeaders(200, png.size.toLong())
        exchange.responseBody.use { it.write(png) }
    }
    server.createContext("/image.svg") { exchange ->
        exchange.responseHeaders.set("Content-Type", "image/svg+xml")
        exchange.sendResponseHeaders(200, svg.size.toLong())
        exchange.responseBody.use { it.write(svg) }
    }
    server.start()
    val loader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
    try {
        runBlocking {
            for (name in listOf("image.png", "image.svg")) {
                val request = ImageRequest.Builder(PlatformContext.INSTANCE)
                    .data("http://127.0.0.1:${server.address.port}/$name").size(2, 2).build()
                val result = loader.execute(request)
                check(result is SuccessResult) { "Coil failed to fetch/decode $name: $result" }
                check(result.image.width > 0 && result.image.height > 0)
                println("KEEPKIT_COIL_SUCCESS $name ${result.image.width}x${result.image.height}")
            }
        }
    } finally {
        loader.shutdown()
        server.stop(0)
    }
    // The auto-discovered OkHttp client owns non-daemon idle threads. This is a
    // finite smoke executable; exit only after every assertion and cleanup passed.
    kotlin.system.exitProcess(0)
}
