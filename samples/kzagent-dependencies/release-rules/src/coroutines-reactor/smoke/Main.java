package smoke;

import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.flow.FlowKt;
import kotlinx.coroutines.reactive.ReactiveFlowKt;
import kotlinx.coroutines.reactor.ReactorContextKt;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public final class Main {
    public static void main(String[] args) throws InterruptedException {
        Probe.renamed();
        var publisher = Mono.deferContextual(context -> Mono.just(context.getOrDefault("probe", "missing")));
        var flow = ReactiveFlowKt.asFlow(publisher);
        var context = ReactorContextKt.asCoroutineContext(Context.of("probe", "bridged"));
        var result = BuildersKt.runBlocking(context, (scope, continuation) -> FlowKt.first(flow, continuation));
        Probe.require("bridged".equals(result), "Reactor context was not injected");
    }
}
