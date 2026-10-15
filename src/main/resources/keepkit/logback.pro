# XML names extension classes and configures JavaBean properties reflectively.
# Match extension roles, including application-provided components, not whole packages.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
# Joran converts XML sizes via parameterType.getMethod("valueOf", String.class).
-keepclassmembers class ch.qos.logback.core.util.FileSize {
    public static ch.qos.logback.core.util.FileSize valueOf(java.lang.String);
}
# Other standard XML property types use the same reflective conversion contract.
-keepclassmembers class ch.qos.logback.core.util.Duration {
    public static ch.qos.logback.core.util.Duration valueOf(java.lang.String);
}
-keepclassmembers enum ch.qos.logback.core.spi.FilterReply {
    public static **[] values();
}
# Runtime annotation default values resolve enum constants by name.
-keepclassmembers enum ch.qos.logback.core.model.processor.ProcessingPhase {
    public static ch.qos.logback.core.model.processor.ProcessingPhase *;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep,allowoptimization class * implements ch.qos.logback.core.Appender { public <init>(); }
-keep,allowoptimization class * implements ch.qos.logback.core.Layout { public <init>(); }
-keep,allowoptimization class * implements ch.qos.logback.core.encoder.Encoder { public <init>(); }
-keep,allowoptimization class * extends ch.qos.logback.core.filter.Filter { public <init>(); }
-keep,allowoptimization class * implements ch.qos.logback.core.rolling.RollingPolicy { public <init>(); }
-keep,allowoptimization class * implements ch.qos.logback.core.rolling.TriggeringPolicy { public <init>(); }
-keep,allowoptimization class * implements ch.qos.logback.core.status.StatusListener { public <init>(); }
-keep,allowoptimization class * extends ch.qos.logback.core.pattern.Converter { public <init>(); }
-keepclassmembers class * implements ch.qos.logback.core.spi.ContextAware {
    public void set*(...);
    public void add*(...);
    public *** get*();
    public boolean is*();
}
# Optional servlet, mail, console coloring and compressed archive integrations.
-dontwarn jakarta.servlet.**
-dontwarn jakarta.mail.**
-dontwarn org.jline.jansi.**
-dontwarn org.tukaani.xz.**
