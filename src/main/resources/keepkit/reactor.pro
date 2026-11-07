# Optional context propagation, metrics and BlockHound hooks.
# Reactor nullness annotations use JSR-305, a compile-time-only dependency.
-dontwarn reactor.util.annotation.**
# Reactor falls back to its console logger when the SLF4J facade is absent.
-dontwarn reactor.util.Loggers$Slf4JLogger**
-dontwarn io.micrometer.**
-dontwarn reactor.blockhound.**
# StackWalker is used on modern JVMs. The alternate tracing path uses these old JDK internals.
-dontwarn reactor.core.publisher.CallSiteSupplierFactory$SharedSecretsCallSiteSupplierFactory**

# Atomic*FieldUpdater resolves fields by name and writes them outside normal bytecode.
# Protect volatile state on updater owners, including the one-shot and HTTP multicast sinks.
-if class reactor.** {
    static java.util.concurrent.atomic.Atomic*FieldUpdater *;
}
-keepclassmembers class reactor.<1> {
    volatile <fields>;
}
