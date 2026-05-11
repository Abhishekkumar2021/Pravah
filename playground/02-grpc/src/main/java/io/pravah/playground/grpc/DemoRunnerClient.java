package io.pravah.playground.grpc;

import io.grpc.stub.StreamObserver;
import io.pravah.playground.grpc.v1.RunnerMessageKind;
import io.pravah.playground.grpc.v1.RunnerPlaygroundGrpc;
import io.pravah.playground.grpc.v1.RunnerToServer;
import io.pravah.playground.grpc.v1.ServerToRunner;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Minimal runner-side client: opens {@code Connect}, sends REGISTRATION + HEARTBEAT, reads commands.
 * Enable with {@code playground.runner.demo-on-startup=true} to see traffic without grpcurl.
 */
@Component
@ConditionalOnProperty(name = "playground.runner.demo-on-startup", havingValue = "true")
public class DemoRunnerClient {

    private static final Logger log = LoggerFactory.getLogger(DemoRunnerClient.class);

    @GrpcClient("runner-playground")
    private RunnerPlaygroundGrpc.RunnerPlaygroundStub stub;

    @EventListener(ApplicationReadyEvent.class)
    public void runDemo() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(3);

        StreamObserver<ServerToRunner> responses = new StreamObserver<>() {
            @Override
            public void onNext(ServerToRunner cmd) {
                log.info("[demo-runner] <- kind={} correlation_id={} body={}",
                        cmd.getKind(), cmd.getCorrelationId(), cmd.getBody());
                done.countDown();
            }

            @Override
            public void onError(Throwable t) {
                log.error("[demo-runner] stream error", t);
            }

            @Override
            public void onCompleted() {
                log.info("[demo-runner] server completed stream");
            }
        };

        StreamObserver<RunnerToServer> requests = stub.connect(responses);
        requests.onNext(RunnerToServer.newBuilder()
                .setRunnerId("demo-runner-1")
                .setTenantId("acme")
                .setKind(RunnerMessageKind.REGISTRATION)
                .setBody("version=0.0.1")
                .build());

        requests.onNext(RunnerToServer.newBuilder()
                .setRunnerId("demo-runner-1")
                .setTenantId("acme")
                .setKind(RunnerMessageKind.HEARTBEAT)
                .setBody("")
                .build());

        // Third command expected from server (e.g. second part of registration flow): wait briefly
        done.await(5, TimeUnit.SECONDS);
        requests.onCompleted();
    }
}
