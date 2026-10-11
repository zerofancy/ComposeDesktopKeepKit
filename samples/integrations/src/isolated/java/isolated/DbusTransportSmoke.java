package isolated;

import java.util.ServiceLoader;
import org.freedesktop.dbus.spi.transport.ITransportProvider;
public class DbusTransportSmoke {
 public static void main(String[] args) {
  ITransportProvider provider = ServiceLoader.load(ITransportProvider.class).iterator().next();
  if (provider == null) throw new AssertionError("transport");
  System.out.println("PASS dbus-transport isolated");
 }
}
