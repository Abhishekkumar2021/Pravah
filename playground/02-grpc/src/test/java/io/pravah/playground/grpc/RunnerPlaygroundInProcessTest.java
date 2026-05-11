package io.pravah.playground.grpc;

import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.pravah.playground.grpc.v1.RunnerMessageKind;
import io.pravah.playground.grpc.v1.RunnerPlaygroundGrpc;
import io.pravah.playground.grpc.v1.RunnerToServer;
import io.pravah.playground.grpc.v1.ServerToRunner;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In-process gRPC — no TCP, no Spring: proves the proto + bidi handler behave correctly.
 * Same pattern Spring’s tests use for fast, deterministic gRPC unit tests.
 */
class RunnerPlaygroundInProcessTest {

    @Test
    void connectBidirectionalStream_registrationThenHeartbeat() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        RunnerPlaygroundEndpoint service = new RunnerPlaygroundEndpoint();

        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .build()
                .start();

        try {
            var channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            RunnerPlaygroundGrpc.RunnerPlaygroundStub stub = RunnerPlaygroundGrpc.newStub(channel);

            CountDownLatch latch = new CountDownLatch(3);

            StreamObserver<ServerToRunner> responses = new StreamObserver<>() {
                @Override
                public void onNext(ServerToRunner cmd) {
                    latch.countDown();
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                }
            };

            StreamObserver<RunnerToServer> requests = stub.connect(responses);

            requests.onNext(RunnerToServer.newBuilder()
                    .setRunnerId("r1")
                    .setTenantId("tenant-a")
                    .setKind(RunnerMessageKind.REGISTRATION)
                    .setBody("v1")
                    .build());

            requests.onNext(RunnerToServer.newBuilder()
                    .setRunnerId("r1")
                    .setTenantId("tenant-a")
                    .setKind(RunnerMessageKind.HEARTBEAT)
                    .setBody("")
                    .build());

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            requests.onCompleted();
        } finally {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
