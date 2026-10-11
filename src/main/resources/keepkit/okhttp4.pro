# OkHttp 4.12: the PublicSuffixDatabase loads publicsuffixes.gz relatively.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
# These providers/platforms are optional in this Compose Desktop JVM adapter.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
