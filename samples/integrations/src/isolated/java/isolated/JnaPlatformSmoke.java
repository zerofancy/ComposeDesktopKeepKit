package isolated;

public class JnaPlatformSmoke {
 public static void main(String[] args) throws Exception {
  // LibCAPI does not extend Library; its symbol names need the platform fragment.
  // A runtime name prevents ProGuard's constant-reflection recognition from
  // preserving the method independently of the rule under test.
  com.sun.jna.platform.unix.LibCAPI.class.getMethod(System.getProperty("smoke.libc.method", "getuid"));
  if (com.sun.jna.Platform.isWindows()) {
   if (com.sun.jna.platform.win32.Kernel32.INSTANCE.GetCurrentProcessId() <= 0) throw new AssertionError("platform kernel32");
  } else if (com.sun.jna.Platform.isLinux()) {
  com.sun.jna.platform.linux.LibC.Sysinfo info = new com.sun.jna.platform.linux.LibC.Sysinfo();
  if (com.sun.jna.platform.linux.LibC.INSTANCE.sysinfo(info) != 0 || info.uptime.longValue() <= 0) throw new AssertionError("platform libc");
  if (com.sun.jna.platform.linux.LibC.INSTANCE.getuid() < 0) throw new AssertionError("inherited native method");
  }
  System.out.println("PASS jna-platform isolated");
 }
}
