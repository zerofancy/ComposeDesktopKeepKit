package smoke;

import io.ktor.server.routing.openapi.SecuritySchemeAnnotationKt;

public final class Main {
    public static void main(String[] args) {
        Probe.renamed();
        Probe.require(!SecuritySchemeAnnotationKt.getAuthSecuritySchemesAttributeKey().getName().isBlank(), "Ktor OpenAPI registry unavailable");
    }
}
