# kotlinx-coroutines-reactive discovers the Reactor context bridge by SPI.
-keepnames interface kotlinx.coroutines.reactive.ContextInjector
-keep class kotlinx.coroutines.reactor.ReactorContextInjector {
    public <init>();
}
