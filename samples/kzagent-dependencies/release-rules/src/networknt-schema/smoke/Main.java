package smoke;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

public final class Main {
    public static void main(String[] args) {
        Probe.renamed();
        var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        var schema = registry.getSchema("{\"type\":\"string\",\"pattern\":\"^[a-z]+$\"}");
        Probe.require(schema.validate("\"hello\"", InputFormat.JSON).isEmpty(), "valid schema input rejected");
        Probe.require(!schema.validate("\"123\"", InputFormat.JSON).isEmpty(), "invalid schema input accepted");
    }
}
