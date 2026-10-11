package smoke

import kotlinx.coroutines.*
import javax.swing.SwingUtilities

fun main() = runBlocking {
    withContext(Dispatchers.Main) {
        check(SwingUtilities.isEventDispatchThread())
        delay(10)
        withContext(Dispatchers.Main.immediate) { check(SwingUtilities.isEventDispatchThread()) }
    }
    println("PASS coroutines-swing Main and Main.immediate")
}
