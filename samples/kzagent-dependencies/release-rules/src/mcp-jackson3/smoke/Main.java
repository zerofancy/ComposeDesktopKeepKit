package smoke;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;

public final class Main {
    public static void main(String[] args) throws java.io.IOException {
        Probe.renamed();
        switch (args[0]) {
            case "mapper" -> {
                var mapper = McpJsonDefaults.getMapper();
                var empty = mapper.readValue("{}", new TypeRef<McpSchema.CallToolResult>() {});
                Probe.require(empty.content().isEmpty(), "MCP default content missing");
                var result = mapper.readValue("""
                        {"content":[{"type":"text","text":"hello"}],"structuredContent":{"ok":true}}
                        """, new TypeRef<McpSchema.CallToolResult>() {});
                Probe.require(((McpSchema.TextContent) result.content().getFirst()).text().equals("hello"),
                        "MCP wire payload was not decoded");
                Probe.require(mapper.writeValueAsString(result).contains("\"type\":\"text\""),
                        "MCP wire discriminator was not encoded");
            }
            case "enum" -> {
                var mapper = McpJsonDefaults.getMapper();
                var role = mapper.readValue("\"user\"", new TypeRef<McpSchema.Role>() {});
                Probe.require(role == McpSchema.Role.USER, "MCP enum wire name missing");
                Probe.require(mapper.writeValueAsString(role).equals("\"user\""), "MCP enum wire name changed");
                var reason = mapper.readValue("\"futureReason\"",
                        new TypeRef<McpSchema.CreateMessageResult.StopReason>() {});
                Probe.require(reason == McpSchema.CreateMessageResult.StopReason.UNKNOWN,
                        "MCP enum creator missing");
            }
            case "validator" -> {
                Probe.require(McpJsonDefaults.getMapper() != null, "mapper SPI should still work");
                var validator = McpJsonDefaults.getSchemaValidator();
                var schema = Map.<String, Object>of("type", "string", "pattern", "^hello$");
                Probe.require(validator.validate(schema, "\"hello\"").valid(), "valid MCP output rejected");
                Probe.require(!validator.validate(schema, "\"other\"").valid(), "invalid MCP output accepted");
            }
            default -> throw new AssertionError("Unknown probe");
        }
    }
}
