# D-Bus wire names and signatures are derived from reflected interfaces.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-keep interface * extends org.freedesktop.dbus.interfaces.DBusInterface { public <methods>; }
# Property annotation defaults are resolved by Enum.valueOf inside the JVM.
-keepclassmembers enum org.freedesktop.dbus.annotations.DBusProperty$Access,org.freedesktop.dbus.annotations.PropertiesEmitsChangedSignal$EmitChangeSignal {
    public static **[] values();
    public static ** *;
}
# ExportedObject discovers extension annotations dynamically and reflects value().
-keep @org.freedesktop.dbus.annotations.DBusInterfaceName @interface * { public <methods>; }
# Marshalling reflects positioned fields and constructors, including user structs.
-keepclassmembers class * extends org.freedesktop.dbus.Container {
    @org.freedesktop.dbus.annotations.Position <fields>;
    <init>(...);
}
-keepclassmembers class * implements org.freedesktop.dbus.interfaces.DBusSerializable {
    public <init>();
    public *** deserialize(...);
}
-keep,allowoptimization class * extends org.freedesktop.dbus.messages.DBusSignal { <init>(...); }
-keep,allowoptimization class * extends org.freedesktop.dbus.exceptions.DBusExecutionException { public <init>(java.lang.String); }
