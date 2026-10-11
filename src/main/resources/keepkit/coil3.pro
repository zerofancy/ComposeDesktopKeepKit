# Coil 3 discovers fetchers and decoders dynamically. Keep their binary names
# and members; service declarations remain valid when obfuscation is enabled.
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
# ServiceLoader locates META-INF/services by the interface binary name.
# Preserve the interfaces too; keeping implementations alone fails with obfuscation.
-keep interface coil3.util.FetcherServiceLoaderTarget { *; }
-keep interface coil3.util.DecoderServiceLoaderTarget { *; }
