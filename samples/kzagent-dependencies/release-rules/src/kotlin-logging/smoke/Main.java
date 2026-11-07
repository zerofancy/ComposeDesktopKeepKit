package smoke;

import io.github.oshai.kotlinlogging.KotlinLogging;

public final class Main {
    public static void main(String[] args) {
        Probe.renamed();
        Probe.require(KotlinLogging.INSTANCE.logger("keepkit-probe").getName() != null,
                "SLF4J logger initialization failed");
    }
}
