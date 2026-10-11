package isolated;

import org.slf4j.*;
import org.slf4j.spi.*;
import org.slf4j.helpers.*;
public class Slf4jSmoke {
 public static class Provider implements SLF4JServiceProvider {
  public ILoggerFactory getLoggerFactory() { return new NOPLoggerFactory(); }
  public IMarkerFactory getMarkerFactory() { return new BasicMarkerFactory(); }
  public MDCAdapter getMDCAdapter() { return new NOPMDCAdapter(); }
  public String getRequestedApiVersion() { return "2.0.99"; }
  public void initialize() { System.setProperty("fixture.provider", "loaded"); }
 }
 public static void main(String[] args) {
  LoggerFactory.getLogger("isolated").info("init");
  if (!"loaded".equals(System.getProperty("fixture.provider"))) throw new AssertionError("provider SPI");
  System.out.println("PASS slf4j isolated");
 }
}
