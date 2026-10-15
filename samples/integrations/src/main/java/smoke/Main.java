package smoke;

import com.sun.jna.*;
import com.llamatik.library.platform.LlamaBridge;
import com.llamatik.library.platform.GenStream;
import java.sql.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.freedesktop.dbus.spi.transport.ITransportProvider;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.messages.ExportedObject;
import retrofit2.Retrofit;
import retrofit2.Call;
import retrofit2.http.GET;

public class Main {
    public interface LibC extends Library { int getpid(); }
    public interface WindowsLibC extends Library { int _getpid(); }
    public interface IntCallback extends Callback { int apply(int value); }
    @Structure.FieldOrder({"number"})
    public static class NativeStruct extends Structure { public int number; }
    public static class Stream implements GenStream {
        public String last;
        public void onDelta(String value) { last = value; }
        public void onComplete() { last = "complete"; }
        public void onError(String value) { last = value; }
    }
    @DBusInterfaceName("org.keepkit.Smoke")
    public interface Bus extends DBusInterface { String Echo(String value); }
    public static class BusImpl implements Bus {
        public String Echo(String value) { return value; }
        public String getObjectPath() { return "/org/keepkit/Smoke"; }
        public boolean isRemote() { return false; }
    }
    public interface Api { @GET("smoke") Call<okhttp3.ResponseBody> get(); }
    public static void main(String[] args) throws Exception {
        String only = System.getProperty("smoke.only", "all");
        if (only.equals("all") || only.equals("serialization")) SerializationSmoke.run();
        if (only.equals("all") || only.equals("retrofit")) {
            Api api = new Retrofit.Builder().baseUrl("http://127.0.0.1/").build().create(Api.class);
            if (!api.get().request().url().encodedPath().equals("/smoke")) throw new AssertionError("Retrofit");
            System.out.println("PASS retrofit");
        }
        if (only.equals("all") || only.equals("okhttp")) {
            if (!okhttp3.HttpUrl.get("https://www.example.co.uk").topPrivateDomain().equals("example.co.uk")) throw new AssertionError("suffix");
            System.out.println("PASS okhttp");
        }
        if (only.equals("all") || only.equals("logging")) {
            ch.qos.logback.classic.LoggerContext context = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.core.Appender<?> appender = context.getLogger("ROOT").getAppender("STDOUT");
            if (appender == null || !appender.isStarted()) throw new AssertionError("XML appender");
            if (!"loaded".equals(context.getProperty("fixture.spi"))) throw new AssertionError("Configurator SPI");
            LoggerFactory.getLogger("smoke").info("PASS logging");
        }
        if (only.equals("all") || only.equals("jna")) {
            // Windows CRT exports _getpid; Unix libc exports getpid.
            int pid = Platform.isWindows()
                    ? Native.load(Platform.C_LIBRARY_NAME, WindowsLibC.class)._getpid()
                    : Native.load(Platform.C_LIBRARY_NAME, LibC.class).getpid();
            if (pid <= 0) throw new AssertionError("JNA");
            NativeStruct struct = new NativeStruct();
            struct.number = 42;
            struct.write();
            struct.number = 0;
            struct.read();
            if (struct.number != 42) throw new AssertionError("JNA structure");
            IntCallback callback = value -> value + 1;
            Pointer pointer = CallbackReference.getFunctionPointer(callback);
            if (Function.getFunction(pointer).invokeInt(new Object[]{41}) != 42) throw new AssertionError("JNA callback");
            System.out.println("PASS jna");
        }
        if (only.equals("all") || only.equals("dbus")) {
            if (!ServiceLoader.load(ITransportProvider.class).iterator().hasNext()) throw new AssertionError("D-Bus transport");
            ExportedObject object = new ExportedObject(new BusImpl(), false);
            if (!object.getIntrospectiondata().contains("Echo")) throw new AssertionError("D-Bus interface");
            System.out.println("PASS dbus");
        }
        if (only.equals("all") || only.equals("sqlite")) {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("select 42")) {
                if (!result.next() || result.getInt(1) != 42) throw new AssertionError("SQLite");
            }
            System.out.println("PASS sqlite");
        }
        if (only.equals("all") || only.equals("llamatik")) {
            // No model download: exercise native library extraction and exported JNI symbol.
            LlamaBridge.INSTANCE.shutdown();
            // Match the named callback lookup performed by JNI, without a model file.
            Stream callback = new Stream();
            String method = System.getProperty("callback.method", "onDelta");
            callback.getClass().getMethod(method, String.class).invoke(callback, "delta");
            if (!"delta".equals(callback.last)) throw new AssertionError("JNI callback");
            System.out.println("PASS llamatik");
        }
        System.out.println("PASS integrations");
        System.exit(0);
    }
}
