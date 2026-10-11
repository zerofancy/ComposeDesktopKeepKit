package isolated;

public class OkhttpSmoke {
 public static void main(String[] args) {
  if (!okhttp3.HttpUrl.get("https://www.example.co.uk").topPrivateDomain().equals("example.co.uk")) throw new AssertionError("suffix");
  System.out.println("PASS okhttp isolated");
 }
}
