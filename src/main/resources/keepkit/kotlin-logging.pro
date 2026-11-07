# Optional Logback adapter and GraalVM native-image substitutions.
# Suspendable MDC helpers are only used with kotlinx-coroutines-slf4j.
-dontwarn io.github.oshai.kotlinlogging.coroutines.KotlinLoggingAsyncMDCKt**
-dontwarn io.github.oshai.kotlinlogging.logback.**
-dontwarn com.oracle.svm.core.annotate.Substitute,com.oracle.svm.core.annotate.TargetElement,com.oracle.svm.core.annotate.TargetClass
