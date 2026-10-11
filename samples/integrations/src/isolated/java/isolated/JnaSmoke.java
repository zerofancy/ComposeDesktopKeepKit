package isolated;

import com.sun.jna.*;
public class JnaSmoke {
 public interface ProcessAPI extends Library { int getpid(); }
 public interface LibC extends ProcessAPI { }
 public interface WithOptions extends Library {
  java.util.Map<String,Object> OPTIONS = java.util.Collections.singletonMap("fixture-option", "yes");
  int getpid();
  @Structure.FieldOrder({"number"})
  class Nested extends Structure { public int number; }
 }
 public interface IntCallback extends Callback { int apply(int value); }
 @Structure.FieldOrder({"number"})
 public static class NativeStruct extends Structure { public int number; }
 public static void main(String[] args) {
  if (Native.load(Platform.C_LIBRARY_NAME, LibC.class).getpid() <= 0) throw new AssertionError("libc");
  if (Native.load(Platform.C_LIBRARY_NAME, WithOptions.class, WithOptions.OPTIONS).getpid() <= 0) throw new AssertionError("options libc");
  if (!"yes".equals(Native.getLibraryOptions(WithOptions.Nested.class).get("fixture-option"))) throw new AssertionError("enclosing OPTIONS");
  NativeStruct struct = new NativeStruct(); struct.number=42; struct.write(); struct.number=0; struct.read();
  if (struct.number != 42) throw new AssertionError("Structure");
  IntCallback callback = value -> value + 1;
  if (Function.getFunction(CallbackReference.getFunctionPointer(callback)).invokeInt(new Object[]{41}) != 42) throw new AssertionError("callback");
  System.out.println("PASS jna isolated");
 }
}
