package isolated;

import ch.qos.logback.core.Appender;
public class LogbackCoreSmoke {
 public static void main(String[] args) throws Exception {
  String name = System.getProperty("fixture.appender", "ch.qos.logback.core.ConsoleAppender");
  Object appender = Class.forName(name).getConstructor().newInstance();
  appender.getClass().getMethod(System.getProperty("fixture.setter", "setName"), String.class).invoke(appender, "smoke");
  if (!"smoke".equals(((Appender<?>)appender).getName())) throw new AssertionError("XML bean");
  System.out.println("PASS logback-core isolated");
 }
}
