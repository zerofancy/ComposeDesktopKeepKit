package smoke;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class Main {
    public static void main(String[] args) {
        Probe.renamed();
        Probe.require(JsonInclude.Include.class.getEnumConstants().length != 0, "Jackson annotation enum missing");
    }
}
