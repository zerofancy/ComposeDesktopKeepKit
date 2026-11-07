# These sites call the signature-polymorphic MethodHandle.invokeExact.
-dontwarn tools.jackson.databind.deser.impl.MethodProperty,tools.jackson.databind.introspect.AnnotatedConstructor,tools.jackson.databind.introspect.AnnotatedMethod,tools.jackson.databind.ser.BeanPropertyWriter

# ConfigFeature and enum deserialization read constants through Class.getEnumConstants().
-keepclassmembers enum tools.jackson.databind.** {
    public static **[] values();
}
