package smoke

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.ServiceLoader

// A fixture provider also exercises application-defined implementations.
class Handler : kotlin.coroutines.AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
    override fun handleException(context: kotlin.coroutines.CoroutineContext, exception: Throwable) = Unit
}

fun main() = runBlocking {
    check(ServiceLoader.load(CoroutineExceptionHandler::class.java).single() is Handler)
    val result = async(Dispatchers.Default) { delay(10); 42 }.await()
    check(result == 42)
    val state = MutableStateFlow(0)
    coroutineScope {
        val shared = state.stateIn(this, SharingStarted.Eagerly, -1)
        check(shared.javaClass.getDeclaredField("job").type == Job::class.java)
        val events = state.shareIn(this, SharingStarted.Eagerly, replay = 1)
        check(events.javaClass.getDeclaredField("job").type == Job::class.java)
        coroutineContext.cancelChildren()
    }
    println("PASS coroutines-core SPI, async, stateIn/shareIn GC anchors")
}
