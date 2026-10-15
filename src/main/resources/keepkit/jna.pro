# jnidispatch resolves these core types and members by fixed JNI names.
-keepclasseswithmembernames,includedescriptorclasses class com.sun.jna.Native { native <methods>; }
-keep class com.sun.jna.Native {
    # This write-only field anchors the finalizer until Native itself is unloaded.
    private static final java.lang.Object finalizer;
    private static void dispose();
    private static com.sun.jna.NativeMapped fromNative(java.lang.Class,java.lang.Object);
    private static com.sun.jna.NativeMapped fromNative(java.lang.reflect.Method,java.lang.Object);
    private static java.lang.Object fromNative(com.sun.jna.FromNativeConverter,java.lang.Object,java.lang.reflect.Method);
    private static java.lang.Object toNative(com.sun.jna.ToNativeConverter,java.lang.Object);
    private static java.lang.Class nativeType(java.lang.Class);
    static com.sun.jna.Callback$UncaughtExceptionHandler callbackExceptionHandler;
}
# register(String) / unregister() identify their caller by a fixed stack depth.
# Inlining these frames changes which class is registered (notably JNA 5.15/5.16).
-keepclassmembers,allowshrinking,allowobfuscation class com.sun.jna.Native {
    public static void register(...);
    public static void unregister(...);
    static java.lang.Class getCallingClass();
}
# Direct mappings use reflected native method names as library symbol names.
# Unused native methods may still shrink; no blanket keep of JNA or application code.
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keep class com.sun.jna.Pointer { long peer; public <init>(long); }
-keep class com.sun.jna.Structure {
    com.sun.jna.Pointer memory;
    long typeInfo;
    *** getTypeInfo();
    static *** newInstance(java.lang.Class,long);
    public void autoRead();
    public void autoWrite();
}
-keep interface com.sun.jna.Structure$ByValue
-keep class com.sun.jna.Structure$FFIType$FFITypes { static <fields>; }
-keep class com.sun.jna.CallbackReference {
    static com.sun.jna.Callback getCallback(java.lang.Class,com.sun.jna.Pointer,boolean);
    static com.sun.jna.Pointer getFunctionPointer(com.sun.jna.Callback,boolean);
    static com.sun.jna.Pointer getNativeString(java.lang.Object,boolean);
    static java.lang.ThreadGroup initializeThread(com.sun.jna.Callback,com.sun.jna.CallbackReference$AttachOptions);
}
-keep class com.sun.jna.CallbackReference$AttachOptions { public <init>(); }
-keep class com.sun.jna.WString { public <init>(java.lang.String); }
-keep class com.sun.jna.IntegerType { long value; }
-keep class com.sun.jna.PointerType { com.sun.jna.Pointer pointer; }
-keep class com.sun.jna.JNIEnv
-keep interface com.sun.jna.NativeMapped { public <methods>; }
-keep interface com.sun.jna.FromNativeConverter { public <methods>; }
-keep interface com.sun.jna.Native$ffi_callback { public <methods>; }
-keep interface com.sun.jna.Callback
-keep interface com.sun.jna.Callback$UncaughtExceptionHandler { public <methods>; }
# Proxy method names map to native symbols; structures reflect fields and constructors.
# Native.findEnclosingLibrary uses declaring/enclosing classes to inherit OPTIONS.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod
-keepclassmembers interface * extends com.sun.jna.Library { public <methods>; }
-keepclassmembers class * implements com.sun.jna.Library {
    public static <fields>;
}
-keepclassmembers class * extends com.sun.jna.Structure {
    public <fields>;
    public <init>();
    public <init>(com.sun.jna.Pointer);
}
-keepclassmembers class * implements com.sun.jna.Callback { public <methods>; }
-keepclassmembers class * implements com.sun.jna.NativeMapped { public <init>(); }
# NativeMappedConverter.defaultValue calls Class.getEnumConstants().
-keepclassmembers enum * implements com.sun.jna.NativeMapped { public static **[] values(); }
# ByReference.toString reflects getValue on application-defined subclasses.
-keepclassmembers class * extends com.sun.jna.ptr.ByReference { public *** getValue(); }
