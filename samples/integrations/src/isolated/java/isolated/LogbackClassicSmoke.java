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
 public static void main(String[] args) throws Exception {
  java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("keepkit-logback-");
  System.setProperty("fixture.log.dir", directory.toString().replace('\\', '/'));
  // Scanning requires a watchable file URL; a configuration inside the JAR
  // cannot exercise reconfiguration scheduling on recent Logback versions.
  java.nio.file.Path configuration = directory.resolve("logback.xml");
  try (java.io.InputStream xml = LogbackClassicSmoke.class.getResourceAsStream("/logback.xml")) {
   java.nio.file.Files.copy(java.util.Objects.requireNonNull(xml), configuration);
  }
  System.setProperty("logback.configurationFile", configuration.toString());
  LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
  try {
  for (ch.qos.logback.core.status.Status status : context.getStatusManager().getCopyOfStatusList()) {
   if (status.getLevel() >= ch.qos.logback.core.status.Status.ERROR) throw new AssertionError("Logback configuration error: " + status);
  }
  ch.qos.logback.core.Appender<?> fileAppender = context.getLogger("ROOT").getAppender("FILE");
  if (fileAppender == null || !fileAppender.isStarted()) throw new AssertionError("XML rolling file appender");
  ch.qos.logback.core.Appender<?> appender = context.getLogger("ROOT").getAppender("STDOUT");
  if (appender == null || !appender.isStarted()) throw new AssertionError("XML appender");
  if (!"loaded".equals(context.getProperty("fixture.spi"))) throw new AssertionError("Configurator SPI");
  LoggerFactory.getLogger("isolated").info("PASS logback-classic isolated");
  if (!java.nio.file.Files.readString(directory.resolve("smoke.log")).contains("PASS logback-classic isolated")) throw new AssertionError("file logging");
  if (!context.getCopyOfScheduledFutures().stream().anyMatch(task -> !task.isCancelled())) throw new AssertionError("configuration scan scheduler");
  } finally {
   context.stop();
   System.clearProperty("fixture.log.dir");
   System.clearProperty("logback.configurationFile");
   try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(directory)) {
    for (java.nio.file.Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
   }
  }
 }
}
