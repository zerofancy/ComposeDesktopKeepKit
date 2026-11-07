# The servlet server transports are optional for stdio and JDK HTTP clients.
-dontwarn io.modelcontextprotocol.server.transport.HttpServlet**

# Jackson reads the protocol records and their wire annotations at runtime.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations,AnnotationDefault,Record,MethodParameters
-keep class io.modelcontextprotocol.spec.McpSchema$** extends java.lang.Record {
    <init>(...);
    @com.fasterxml.jackson.annotation.JsonCreator <methods>;
    !static <fields>;
    !static <methods>;
}
# Jackson maps annotated enum fields to protocol values and discovers enum factories.
-keepclassmembers enum io.modelcontextprotocol.spec.McpSchema$** {
    public static **[] values();
    @com.fasterxml.jackson.annotation.JsonProperty <fields>;
    @com.fasterxml.jackson.annotation.JsonCreator public static <methods>;
}

# TypeRef reads the generic superclass of its concrete subclass.
-keepattributes Signature
