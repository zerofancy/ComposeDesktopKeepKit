package isolated;

public class JnaPlatformSmoke {
 public static void main(String[] args) {
  com.sun.jna.platform.linux.LibC.Sysinfo info = new com.sun.jna.platform.linux.LibC.Sysinfo();
  if (com.sun.jna.platform.linux.LibC.INSTANCE.sysinfo(info) != 0 || info.uptime.longValue() <= 0) throw new AssertionError("platform libc");
  if (com.sun.jna.platform.linux.LibC.INSTANCE.getuid() < 0) throw new AssertionError("inherited native method");
  System.out.println("PASS jna-platform isolated");
 }
}
