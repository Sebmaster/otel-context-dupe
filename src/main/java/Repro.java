import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.lang.ref.Reference;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class Repro {
    public static void main(String[] args) throws Exception {
        // The underlying pools are equivalent; single adds a forwarding facade.
        var executor = args[0].equals("single")
                ? Executors.newSingleThreadExecutor() : Executors.newFixedThreadPool(1);
        var finished = new CompletableFuture<Void>();
        var key = ContextKey.<String>named("example");
        System.out.println("executor=" + args[0]);
        try {
            try (var scope = Context.root().with(key, "value").makeCurrent()) {
                executor.execute(() -> {
                    try {
                        if (!"value".equals(Context.current().get(key))) {
                            throw new AssertionError("Context was not propagated");
                        }
                        printExtraAttachments();
                        finished.complete(null);
                    } catch (Throwable error) {
                        finished.completeExceptionally(error);
                    }
                });
            }
            finished.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
        }
    }

    private static void printExtraAttachments() throws Exception {
        int entries = 0;
        int identicalContexts = 0;
        for (var entry : rawRunnableContextMap().entrySet()) {
            Object task = ((Reference<?>) entry.getKey()).get();
            if (task != null && task.getClass().getName().equals(
                    "io.opentelemetry.javaagent.bootstrap.executors.ContextPropagatingRunnable")) {
                entries++;
                Object context = field(task, "context");
                if (context != null && context == field(entry.getValue(), "context")) identicalContexts++;
            }
        }
        System.out.println("extraEntries=" + entries + " identicalContexts=" + identicalContexts);
    }

    // Diagnostic reflection only: inspect the unmodified agent's raw map without triggering cleanup.
    private static Map<?, ?> rawRunnableContextMap() throws Exception {
        Class<?> type = Class.forName(
                "io.opentelemetry.javaagent.bootstrap.field.VirtualFieldImpl$java$lang$Runnable$"
                        + "io$opentelemetry$javaagent$bootstrap$executors$PropagatedContext", true, null);
        var instance = type.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        return (Map<?, ?>) field(field(field(instance.get(null), "map"), "delegate"), "target");
    }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) {
                // Search the weak-map superclass too.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
