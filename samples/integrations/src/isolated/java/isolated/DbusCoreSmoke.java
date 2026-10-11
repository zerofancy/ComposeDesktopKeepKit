package isolated;

import org.freedesktop.dbus.annotations.*;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.ExportedObject;
public class DbusCoreSmoke {
 @DBusInterfaceName("org.keepkit.Smoke")
 public interface Bus extends DBusInterface { String Echo(String value); }
 public static class BusImpl implements Bus {
  public String Echo(String value) { return value; }
  public String getObjectPath() { return "/org/keepkit/Smoke"; }
  public boolean isRemote() { return false; }
 }
 public static void main(String[] args) throws Exception {
  if (!new ExportedObject(new BusImpl(), false).getIntrospectiondata().contains("Echo")) throw new AssertionError("D-Bus reflection");
  System.out.println("PASS dbus-core isolated");
 }
}
