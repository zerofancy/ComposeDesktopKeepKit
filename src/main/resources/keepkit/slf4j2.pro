# ServiceLoader reads provider names from META-INF/services. Keep only SPI entries.
-keep,allowoptimization class * implements org.slf4j.spi.SLF4JServiceProvider {
    public <init>();
}
-keepnames interface org.slf4j.spi.SLF4JServiceProvider
