package smoke

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.Files

fun main() = runBlocking {
    val directory = Files.createTempDirectory("keepkit-preferences").toFile()
    val file = directory.resolve("settings.preferences_pb")
    val key = stringPreferencesKey("clock-format")
    suspend fun open(write: Boolean) {
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
            if (write) store.edit { it[key] = "HH:mm:ss" }
            check(store.data.first()[key] == "HH:mm:ss")
        } finally { job.cancelAndJoin() }
    }
    try { open(true); check(file.length() > 0); open(false) }
    finally { directory.deleteRecursively() }
    println("PASS datastore persisted preferences round trip")
}

// Deliberately unreachable: verification checks that Release shrinking removes it.
private class UnusedSmokeMarker
