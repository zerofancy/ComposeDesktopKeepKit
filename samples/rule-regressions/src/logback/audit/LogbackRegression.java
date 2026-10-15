package audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.joran.util.StringToObjectConverter;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.spi.FilterAttachable;
import ch.qos.logback.core.util.Duration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public final class LogbackRegression {
    public static void main(String[] args) throws Exception {
        ContextAwareBase context = new ContextAwareBase();
        switch (args[0]) {
            case "logback-enum":
                if (StringToObjectConverter.convertArg(context, "ACCEPT", FilterReply.class) != FilterReply.ACCEPT) {
                    throw new AssertionError("FilterReply conversion");
                }
                break;
            case "logback-level":
                if (StringToObjectConverter.convertArg(context, "DEBUG", Level.class) != Level.DEBUG) {
                    throw new AssertionError("Level conversion failed");
                }
                break;
            case "logback-duration": {
                Object value = StringToObjectConverter.convertArg(context, "10 seconds", Duration.class);
                if (!(value instanceof Duration) || ((Duration) value).getMilliseconds() != 10_000) {
                    throw new AssertionError("Duration conversion failed");
                }
                break;
            }
            case "logback-xml": {
                LoggerContext loggerContext = new LoggerContext();
                try {
                    JoranConfigurator configurator = new JoranConfigurator();
                    configurator.setContext(loggerContext);
                    String xml = "<configuration><appender name='A' class='ch.qos.logback.core.ConsoleAppender'>"
                        + "<filter class='ch.qos.logback.classic.filter.LevelFilter'><level>INFO</level>"
                        + "<onMatch>ACCEPT</onMatch><onMismatch>DENY</onMismatch></filter>"
                        + "<encoder><pattern>%msg%n</pattern></encoder></appender>"
                        + "<root level='INFO'><appender-ref ref='A'/></root></configuration>";
                    configurator.doConfigure(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
                    Filter<?> filter = ((FilterAttachable<?>) loggerContext.getLogger("ROOT").getAppender("A"))
                        .getCopyOfAttachedFiltersList().get(0);
                    @SuppressWarnings("unchecked") Filter<LoggingEvent> eventFilter = (Filter<LoggingEvent>) filter;
                    LoggingEvent event = new LoggingEvent();
                    event.setLevel(Level.INFO);
                    if (eventFilter.decide(event) != FilterReply.ACCEPT) throw new AssertionError("XML LevelFilter failed");
                    LoggingEvent mismatch = new LoggingEvent();
                    mismatch.setLevel(Level.DEBUG);
                    if (eventFilter.decide(mismatch) != FilterReply.DENY) throw new AssertionError("XML LevelFilter mismatch failed");
                } finally { loggerContext.stop(); }
                break;
            }
            default: throw new IllegalArgumentException(args[0]);
        }
        System.out.println("PASS " + args[0]);
    }
}

class UnusedRegressionMarker { }
