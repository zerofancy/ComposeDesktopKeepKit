package smoke;

import ai.rever.bossterm.compose.ui.ProperTerminalKt;

public final class Main {
    public static void main(String[] args) {
        Probe.renamed();
        Probe.require(ProperTerminalKt.class.getDeclaredMethods().length > 0, "BossTerm renderer methods missing");
    }
}
