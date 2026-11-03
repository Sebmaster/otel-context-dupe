package repro;

import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;

public final class IgnoreAgentWrapper implements IgnoredTypesConfigurer {
    @Override
    public void configure(IgnoredTypesBuilder builder) {
        builder.ignoreTaskClass(
                "io.opentelemetry.javaagent.bootstrap.executors.ContextPropagatingRunnable");
    }
}
