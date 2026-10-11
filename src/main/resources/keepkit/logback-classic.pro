# Configurator SPI is loaded independently of the SLF4J provider.
-keep,allowoptimization class * implements ch.qos.logback.classic.spi.Configurator { public <init>(); }
-keepnames interface ch.qos.logback.classic.spi.Configurator
