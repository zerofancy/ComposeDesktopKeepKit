package smoke;

import java.time.Duration;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

public final class Main {
    public static void main(String[] args) throws Exception {
        Probe.renamed();
        if (args[0].equals("one-sink")) {
            verifyOneSink();
            return;
        }
        if (args[0].equals("multicast")) {
            verifyMulticast();
            return;
        }
        var scheduler = Schedulers.newSingle("keepkit-probe");
        try {
            var result = Mono.<Integer>create(sink -> sink.success(41))
                    .publishOn(scheduler)
                    .flatMap(number -> Mono.fromCallable(() -> number + 1))
                    .block(Duration.ofSeconds(3));
            Probe.require(Integer.valueOf(42).equals(result), "Reactor signal lost");
            var sink = Sinks.many().unicast().<Integer>onBackpressureBuffer();
            sink.emitNext(43, Sinks.EmitFailureHandler.FAIL_FAST);
            sink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
            Probe.require(Integer.valueOf(43).equals(sink.asFlux().next().block(Duration.ofSeconds(3))),
                    "Reactor signal lost");
        } finally {
            scheduler.dispose();
        }
    }

    private static void verifyOneSink() throws Exception {
        Sinks.One<Integer> sink = Sinks.one();
        var result = sink.asMono().toFuture();
        var emit = new FutureTask<>(() -> sink.tryEmitValue(42));
        // A broken CAS loop must not outlive the assertion.
        Thread.ofVirtual().start(emit);
        Probe.require(emit.get(3, TimeUnit.SECONDS) == Sinks.EmitResult.OK, "Reactor one-shot emission failed");
        Probe.require(result.get(3, TimeUnit.SECONDS).equals(42), "Reactor one-shot signal lost");
    }

    private static void verifyMulticast() throws Exception {
        Sinks.Many<Integer> sink = Sinks.many().multicast().onBackpressureBuffer();
        var result = sink.asFlux().next().toFuture();
        var emit = new FutureTask<>(() -> sink.tryEmitNext(43));
        Thread.ofVirtual().start(emit);
        Probe.require(emit.get(3, TimeUnit.SECONDS) == Sinks.EmitResult.OK, "Reactor multicast emission failed");
        Probe.require(result.get(3, TimeUnit.SECONDS).equals(43), "Reactor multicast signal lost");
    }
}
