# JDBC discovery and NativeDB.c JNI_OnLoad class/member lookups.
-keep,allowoptimization class org.sqlite.JDBC { public <init>(); }
-keepclasseswithmembernames,includedescriptorclasses class org.sqlite.core.NativeDB { native <methods>; }
-keep class org.sqlite.core.NativeDB {
    long pointer;
    long busyHandler;
    long commitListener;
    long updateListener;
    long progressHandler;
    static byte[] stringToUtf8ByteArray(java.lang.String);
    static void throwex(java.lang.String);
}
-keepclassmembers class org.sqlite.core.DB {
    void throwex();
    void throwex(int);
    void onUpdate(int,java.lang.String,java.lang.String,long);
    void onCommit(boolean);
}
-keep class org.sqlite.Function { long context; long value; int args; void xFunc(); }
-keep class org.sqlite.Function$Aggregate { void xStep(); void xFinal(); java.lang.Object clone(); }
-keep class org.sqlite.Function$Window { void xInverse(); void xValue(); }
-keep class org.sqlite.Collation { int xCompare(java.lang.String,java.lang.String); }
-keep interface org.sqlite.core.DB$ProgressObserver { void progress(int,int); }
-keep class org.sqlite.ProgressHandler { int progress(); }
-keep class org.sqlite.BusyHandler { int callback(int); }
# EnumMap and Enum.valueOf invoke values() reflectively for these config types.
-keep,allowshrinking,allowobfuscation enum org.sqlite.SQLiteConfig$TransactionMode,org.sqlite.SQLiteConfig$DateClass,org.sqlite.SQLiteConfig$DatePrecision,org.sqlite.SQLiteConfig$Encoding
-keepclassmembers enum org.sqlite.SQLiteConfig$TransactionMode,org.sqlite.SQLiteConfig$DateClass,org.sqlite.SQLiteConfig$DatePrecision,org.sqlite.SQLiteConfig$Encoding {
    public static **[] values();
}
# Optional GraalVM native-image substitutions.
-dontwarn org.graalvm.nativeimage.**
-dontwarn com.oracle.svm.core.annotate.**
# LoggerFactory falls back to java.util.logging when SLF4J is absent.
-dontwarn org.slf4j.Logger
-dontwarn org.slf4j.LoggerFactory
