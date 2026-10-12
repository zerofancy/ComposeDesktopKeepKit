package smoke

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.*
import okio.Path.Companion.toPath
import java.nio.file.Files

private object ClockSerializer : OkioSerializer<String> {
    override val defaultValue = ""
    override suspend fun readFrom(source: BufferedSource) = source.readUtf8()
    override suspend fun writeTo(t: String, sink: BufferedSink) { sink.writeUtf8(t) }
}

fun main() = runBlocking {
    val directory = Files.createTempDirectory("keepkit-okio").toFile()
    val path = directory.resolve("clock.txt").absolutePath.toPath()
    suspend fun open(write: Boolean) {
        val job = SupervisorJob()
        try {
            val storage = OkioStorage(FileSystem.SYSTEM, ClockSerializer, producePath = { path })
            val store = DataStoreFactory.create(storage = storage, scope = CoroutineScope(Dispatchers.IO + job))
            if (write) store.updateData { "HH:mm:ss" }
            check(store.data.first() == "HH:mm:ss")
        } finally { job.cancelAndJoin() }
    }
    try { open(true); open(false) } finally { directory.deleteRecursively() }
    println("PASS datastore Okio storage persisted round trip")
}

// Deliberately unreachable: verification checks that Release shrinking removes it.
private class UnusedSmokeMarker
