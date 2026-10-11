package isolated;

import java.util.*;

public class FilekitSmoke {
    public static void main(String[] args) throws Exception {
        // FileKit's implementation dependencies are runtime-only to Java consumers.
        Class.forName("com.sun.jna.Native"); // Initialize the real JNI dispatch library.
        Class<?> transport = Class.forName("org.freedesktop.dbus.spi.transport.ITransportProvider");
        if (!ServiceLoader.load(transport).iterator().hasNext()) throw new AssertionError("transport");
        String name = System.getProperty("fixture.portal", "io.github.vinceglb.filekit.dialogs.platform.xdg.FileChooserDbusInterface");
        if (Arrays.stream(Class.forName(name).getDeclaredMethods()).noneMatch(m -> m.getName().equals("OpenFile"))) throw new AssertionError("portal interface");
        System.out.println("PASS filekit isolated");
    }
}
