# McpJsonDefaults locates these providers via the SDK's service descriptors.
-keepnames interface io.modelcontextprotocol.json.McpJsonMapperSupplier
-keep class io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier {
    public <init>();
}
-keepnames interface io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier
-keep class io.modelcontextprotocol.json.schema.jackson3.JacksonJsonSchemaValidatorSupplier {
    public <init>();
}
