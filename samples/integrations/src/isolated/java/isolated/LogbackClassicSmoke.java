package isolated;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.core.spi.ContextAwareBase;
import org.slf4j.LoggerFactory;
public class LogbackClassicSmoke {
 public static class ExtraConfigurator extends ContextAwareBase implements Configurator {
  public ExecutionStatus configure(LoggerContext context) {
   context.putProperty("fixture.spi", "loaded");
   return ExecutionStatus.INVOKE_NEXT_IF_ANY;
  }
 }
 public static void main(String[] args) {
  LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
  ch.qos.logback.core.Appender<?> appender = context.getLogger("ROOT").getAppender("STDOUT");
  if (appender == null || !appender.isStarted()) throw new AssertionError("XML appender");
  if (!"loaded".equals(context.getProperty("fixture.spi"))) throw new AssertionError("Configurator SPI");
  LoggerFactory.getLogger("isolated").info("PASS logback-classic isolated");
 }
}
