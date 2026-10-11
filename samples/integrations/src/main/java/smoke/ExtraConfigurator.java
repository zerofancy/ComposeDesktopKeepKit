package smoke;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.core.spi.ContextAwareBase;

public class ExtraConfigurator extends ContextAwareBase implements Configurator {
    public ExecutionStatus configure(LoggerContext context) {
        context.putProperty("fixture.spi", "loaded");
        return ExecutionStatus.INVOKE_NEXT_IF_ANY;
    }
}
