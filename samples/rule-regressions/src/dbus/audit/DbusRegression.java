package audit;

import java.lang.annotation.*;
import org.freedesktop.dbus.annotations.*;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.ExportedObject;

public final class DbusRegression {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @DBusInterfaceName("org.keepkit.Note")
    public @interface Note { String value(); }

    @Note("marker")
    @DBusProperty(name = "Count", type = String.class, access = DBusProperty.Access.READ)
    @DBusInterfaceName("org.keepkit.Regression")
    public interface Bus extends DBusInterface { String Echo(String text); }

    public static class Impl implements Bus {
        public String Echo(String text) { return text; }
        public String getObjectPath() { return "/org/keepkit/Regression"; }
        public boolean isRemote() { return false; }
    }

    public static void main(String[] args) throws Exception {
        String xml = new ExportedObject(new Impl(), false).getIntrospectiondata();
        switch (args[0]) {
            case "dbus-property":
                if (!xml.contains("<property name=\"Count\" type=\"s\" access=\"read\"")) {
                    throw new AssertionError("D-Bus property lost: " + xml);
                }
                break;
            case "dbus-annotation":
                if (!xml.contains("<annotation name=\"org.keepkit.Note\" value=\"marker\"")) {
                    throw new AssertionError("D-Bus annotation lost: " + xml);
                }
                break;
            default: throw new IllegalArgumentException(args[0]);
        }
        System.out.println("PASS " + args[0]);
    }
}

class UnusedRegressionMarker { }
