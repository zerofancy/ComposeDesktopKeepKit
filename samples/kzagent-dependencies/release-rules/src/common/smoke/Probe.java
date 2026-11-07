package smoke;

final class Probe {
    private volatile int result;

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void renamed() {
        var probe = new Probe();
        probe.result = 1;
        require(probe.result == 1, "fixture sanity check");
    }
}

final class Unused {}
