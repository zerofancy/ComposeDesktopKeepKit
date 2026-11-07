package smoke;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import java.lang.reflect.ParameterizedType;
import java.util.List;

public final class Main {
    public static void main(String[] args) {
        Probe.renamed();
        switch (args[0]) {
            case "records" -> {
                var components = McpSchema.CallToolResult.class.getRecordComponents();
                Probe.require(components != null && components.length == 4, "MCP record components missing");
                Probe.require(components[0].getAccessor() != null, "MCP record accessor missing");
                var constructors = McpSchema.CallToolResult.class.getConstructors();
                Probe.require(constructors.length != 0, "MCP canonical constructor missing");
                Probe.require(constructors[0].getParameters()[0].isAnnotationPresent(JsonProperty.class),
                        "MCP constructor wire annotations missing");
            }
            case "type" -> {
                TypeRef<List<String>> reference = new TypeRef<>() {};
                Probe.require(reference.getType() instanceof ParameterizedType, "MCP generic type missing");
            }
            case "enum" -> Probe.require(McpSchema.Role.class.getEnumConstants().length == 2,
                    "MCP role constants missing");
            default -> throw new AssertionError("Unknown probe");
        }
    }
}
