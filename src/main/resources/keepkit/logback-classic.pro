# Configurator SPI is loaded independently of the SLF4J provider.
-keep,allowoptimization class * implements ch.qos.logback.classic.spi.Configurator { public <init>(); }
-keepnames interface ch.qos.logback.classic.spi.Configurator
# LevelFilter's level property is converted through valueOf, unlike root logger level.
-keepclassmembers class ch.qos.logback.classic.Level {
    public static ch.qos.logback.classic.Level valueOf(java.lang.String);
}
