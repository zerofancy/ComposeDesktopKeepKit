# ResourcePublicSuffixList builds the .list resource path from this simple name.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
# Guarded optional TLS providers and Android platform implementations.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn android.**
-dontwarn javax.annotation.**
# GraalVM substitutions are only used by native-image, not the JVM client.
-dontwarn org.graalvm.nativeimage.**
-dontwarn com.oracle.svm.core.annotate.**
