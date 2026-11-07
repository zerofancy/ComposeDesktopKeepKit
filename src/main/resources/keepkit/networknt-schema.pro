# The built-in JDK regex engine does not need the optional Joni and GraalJS engines.
-dontwarn com.networknt.schema.regex.JoniRegularExpression**
-dontwarn com.networknt.schema.regex.GraalJS**

# SpecificationVersionRange uses EnumSet, which needs the enum constants at runtime.
-keepclassmembers enum com.networknt.schema.SpecificationVersion {
    public static **[] values();
}
