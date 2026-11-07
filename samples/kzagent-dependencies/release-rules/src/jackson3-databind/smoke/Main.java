package smoke;

import tools.jackson.databind.json.JsonMapper;

public final class Main {
    public static void main(String[] args) {
        Probe.renamed();
        var mapper = JsonMapper.builder().build();
        Probe.require(mapper.readTree("{\"value\":42}").get("value").asInt() == 42,
                "Jackson mapper could not read JSON");
    }
}
