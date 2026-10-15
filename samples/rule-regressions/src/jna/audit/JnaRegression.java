package audit;

import com.sun.jna.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public final class JnaRegression {
    @Structure.FieldOrder({"number"})
    public static class PointerOnly extends Structure {
        public int number;
        public PointerOnly(Pointer pointer) { super(pointer); read(); }
    }

    public enum Code implements NativeMapped {
        ZERO, ONE;
        public Object toNative() { return ordinal(); }
        public Object fromNative(Object value, FromNativeContext context) { return (Integer) value == 0 ? ZERO : ONE; }
        public Class<?> nativeType() { return Integer.class; }
    }

    public static class Direct {
        static { Native.register(Platform.C_LIBRARY_NAME); }
        public static native int getpid();
    }

    public static class ExplicitDirect {
        static { Native.register(ExplicitDirect.class, Platform.C_LIBRARY_NAME); }
        public static native int getpid();
    }

    // A single declaration is necessary: register resolves ALL declared native methods.
    // Platform-specific classes keep the other OS's symbol out of the mapping.
    public static class WindowsDirect {
        static { Native.register(Platform.C_LIBRARY_NAME); }
        public static native int _getpid();
    }
    public static class WindowsExplicitDirect {
        static { Native.register(WindowsExplicitDirect.class, Platform.C_LIBRARY_NAME); }
        public static native int _getpid();
    }

    public static class CustomRef extends com.sun.jna.ptr.ByReference {
        public CustomRef() { super(4); getPointer().setInt(0, 42); }
        public int getValue() { return getPointer().getInt(0); }
    }

    public interface IntCallback extends Callback { int invoke(int value); }
    public interface Udev extends Library {
        Pointer udev_new();
        Pointer udev_unref(Pointer context);
    }

    @SuppressWarnings("removal")
    private static void collect() throws InterruptedException {
        System.gc();
        System.runFinalization();
        Thread.sleep(20);
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "lifecycle": {
                Class<?> nativeClass = Class.forName("com.sun.jna.Native");
                // libudev is not permanently loaded like libc. Exercise the original
                // Linux crash path, but check disposal first to fail without crashing JNI.
                Udev udev = Platform.isLinux() ? Native.load("udev", Udev.class) : null;
                for (int i = 0; i < 200; i++) {
                    collect();
                    if (!"true".equals(System.getProperty("jna.loaded"))) {
                        throw new AssertionError("JNA disposed while Native class is live");
                    }
                    if (udev != null) {
                        Pointer context = udev.udev_new();
                        if (context == null) throw new AssertionError("udev_new returned null");
                        udev.udev_unref(context);
                    }
                }
                Reference.reachabilityFence(nativeClass);
                Reference.reachabilityFence(udev);
                break;
            }
            case "pointer-constructor": {
                Memory memory = new Memory(4);
                memory.setInt(0, 42);
                if (Structure.newInstance(PointerOnly.class, memory).number != 42) {
                    throw new AssertionError("Pointer constructor was not used");
                }
                break;
            }
            case "native-mapped-enum": {
                if (NativeMappedConverter.getInstance(Code.class).fromNative(1, null) != Code.ONE) {
                    throw new AssertionError("NativeMapped enum conversion");
                }
                break;
            }
            case "byreference-string": {
                String text = new CustomRef().toString();
                if (text.contains("Contract violated") || !text.endsWith("=42")) throw new AssertionError(text);
                break;
            }
            case "direct-mapping": {
                int pid = Platform.isWindows() ? WindowsDirect._getpid() : Direct.getpid();
                if (pid <= 0) throw new AssertionError("direct getpid");
                break;
            }
            case "explicit-direct-mapping": {
                int pid = Platform.isWindows() ? WindowsExplicitDirect._getpid() : ExplicitDirect.getpid();
                if (pid <= 0) throw new AssertionError("explicit getpid");
                break;
            }
            case "callback-gc": {
                IntCallback callback = value -> value + 1;
                Function function = Function.getFunction(CallbackReference.getFunctionPointer(callback));
                for (int i = 0; i < 50; i++) {
                    collect();
                    if (function.invokeInt(new Object[]{41}) != 42) throw new AssertionError("callback");
                }
                Reference.reachabilityFence(callback);
                break;
            }
            case "shared-memory": {
                Memory memory = new Memory(16);
                memory.setInt(4, 42);
                Pointer slice = memory.share(4, 4);
                WeakReference<Memory> owner = new WeakReference<>(memory);
                memory = null;
                for (int i = 0; i < 30; i++) collect();
                if (owner.get() == null || slice.getInt(0) != 42) throw new AssertionError("Memory owner collected");
                Reference.reachabilityFence(slice);
                break;
            }
            default: throw new IllegalArgumentException(args[0]);
        }
        System.out.println("PASS " + args[0]);
    }
}

// Verification requires this class to be absent from the shrunk output.
class UnusedRegressionMarker { }
