package isolated;

import retrofit2.*;
import retrofit2.http.GET;
public class RetrofitSmoke {
 public interface Api { @GET("smoke") Call<okhttp3.ResponseBody> get(); }
 public static void main(String[] args) {
  Api api = new Retrofit.Builder().baseUrl("http://127.0.0.1/").build().create(Api.class);
  if (!api.get().request().url().encodedPath().equals("/smoke")) throw new AssertionError("Retrofit");
  System.out.println("PASS retrofit isolated");
 }
}
