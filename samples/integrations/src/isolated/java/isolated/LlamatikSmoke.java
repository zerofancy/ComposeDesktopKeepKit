package isolated;

import com.llamatik.library.platform.*;
public class LlamatikSmoke {
 public static class Stream implements GenStream {
  public String last;
  public void onDelta(String value) { last=value; }
  public void onComplete() { last="complete"; }
  public void onError(String value) { last=value; }
 }
 public static void main(String[] args) throws Exception {
  LlamaBridge.INSTANCE.shutdown();
  Stream callback=new Stream();
  callback.getClass().getMethod(System.getProperty("callback.method", "onDelta"), String.class).invoke(callback,"delta");
  if (!"delta".equals(callback.last)) throw new AssertionError("JNI callback");
  System.out.println("PASS llamatik isolated");
 }
}
