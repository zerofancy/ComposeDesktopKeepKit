# Adapted from kotlinx-coroutines-core-jvm 1.11.0 META-INF/proguard/coroutines.pro.

# ServiceLoader support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Most of volatile fields are updated with AFU and should not be mangled
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Same story for the standard library's SafeContinuation that also uses AtomicReferenceFieldUpdater
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

# These classes are only required by kotlinx.coroutines.debug.internal.AgentPremain, which is only loaded when
# kotlinx-coroutines-core is used as a Java agent, so these are not needed in contexts where ProGuard is used.
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.Signal

# Only used in `kotlinx.coroutines.internal.ExceptionsConstructor`.
# The case when it is not available is hidden in a `try`-`catch`, as well as a check for Android.
-dontwarn java.lang.ClassValue

# An annotation used for build tooling, won't be directly accessed.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# AgentPremain carries this Android-only tooling annotation in 1.11.0.
-dontwarn android.annotation.SuppressLint

# Keep GC anchor fields that prevent the sharing coroutine from being collected.
-keepclassmembers class kotlinx.coroutines.flow.ReadonlySharedFlow {
    kotlinx.coroutines.Job job;
}
-keepclassmembers class kotlinx.coroutines.flow.ReadonlyStateFlow {
    kotlinx.coroutines.Job job;
}

# Desktop ProGuard does not consume packaged Android consumer rules.
# Preserve service providers (including application providers) and constructors.
-keep class * implements kotlinx.coroutines.internal.MainDispatcherFactory {
    public <init>();
}
-keep class * implements kotlinx.coroutines.CoroutineExceptionHandler {
    public <init>();
}

# ProGuard 7.7.0 specializes this handler parameter to the synthetic adapter
# inside JobKt__JobKt, while another call still passes Function1 (VerifyError).
# Preserve just the completion-handler contract; shrinking/renaming stay enabled.
-keepclassmembers,allowshrinking,allowobfuscation interface kotlinx.coroutines.Job {
    kotlinx.coroutines.DisposableHandle invokeOnCompletion(boolean, boolean, kotlin.jvm.functions.Function1);
}

# The same optimizer also specializes FlowKt multifile return types incorrectly.
-keep,allowshrinking,allowobfuscation class kotlinx.coroutines.flow.FlowKt__* { *; }
