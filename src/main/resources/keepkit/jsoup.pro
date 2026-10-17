# jsoup 1.22.2 optionally uses RE2/J and falls back to java.util.regex.
# Adapted from META-INF/proguard/org.jsoup_jsoup.pro in the upstream JAR.
# Warning suppression and reflection keeps have separate negative controls:
# missing optional types fail shrinking; missing reflection targets fail at runtime.
-dontwarn com.google.re2j.**
# jsoup's compile-only JSpecify nullability annotations are not runtime APIs.
-dontwarn org.jspecify.annotations.Nullable
-dontwarn org.jspecify.annotations.NullMarked

# Java 11 multi-release implementations are instantiated by name and constructor.
-keep,includedescriptorclasses class org.jsoup.helper.HttpClientExecutor {
    public <init>(org.jsoup.helper.HttpConnection$Request, org.jsoup.helper.HttpConnection$Response);
}
-keep class org.jsoup.helper.RequestAuthHandler {
    public <init>();
}

# HttpClientExecutor converts request method names through Method.valueOf.
-keepclassmembers enum org.jsoup.Connection$Method {
    public static org.jsoup.Connection$Method *;
    public static org.jsoup.Connection$Method[] values();
    public static org.jsoup.Connection$Method valueOf(java.lang.String);
}
