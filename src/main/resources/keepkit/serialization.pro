# The default companion is looked up by the fixed field name "Companion".
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** Companion;
}

# Named companions are found by the field type's runtime annotation in 1.11.
# Their field and class names may still be obfuscated.
-if @kotlinx.serialization.internal.NamedCompanion class *
-keepclassmembers,allowobfuscation class * {
    static <1> *;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
# ProGuard also needs the enclosing-class pattern: its conditional field-type
# capture can stop matching companion classes after optimization.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$* {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Don't print notes about potential mistakes or omissions in the configuration for kotlinx-serialization classes
# See also https://github.com/Kotlin/kotlinx.serialization/issues/1900
-dontnote kotlinx.serialization.**
# disable optimisation for descriptor field because in some versions of ProGuard, optimization generates incorrect bytecode that causes a verification error
# see https://github.com/Kotlin/kotlinx.serialization/issues/2719
-keepclassmembers,allowobfuscation public class **$$serializer {
    private ** descriptor;
}
