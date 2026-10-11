# TransportBuilder discovers transports with ServiceLoader.
-keep,allowoptimization class org.freedesktop.dbus.transport.jre.NativeTransportProvider { public <init>(); }
-keepnames interface org.freedesktop.dbus.spi.transport.ITransportProvider
