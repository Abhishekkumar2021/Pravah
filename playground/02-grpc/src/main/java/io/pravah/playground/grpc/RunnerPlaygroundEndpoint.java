package io.pravah.playground.grpc;

import io.grpc.stub.StreamObserver;
import io.pravah.playground.grpc.v1.RunnerMessageKind;
import io.pravah.playground.grpc.v1.RunnerPlaygroundGrpc;
import io.pravah.playground.grpc.v1.RunnerToServer;
import io.pravah.playground.grpc.v1.ServerCommandKind;
import io.pravah.playground.grpc.v1.ServerToRunner;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Control-plane side of the playground RPC (like Pravah’s runner-facing service).
 *
 * ADR-005: runner initiates {@code Connect}; both directions stream on one logical connection.
 * This class implements the server half — you extend behaviour in the tasks in README.
 */
@GrpcService
public class RunnerPlaygroundEndpoint extends RunnerPlaygroundGrpc.RunnerPlaygroundImplBase {

    private static final Logger log = LoggerFactory.getLogger(RunnerPlaygroundEndpoint.class);

    @Override
    public StreamObserver<RunnerToServer> connect(StreamObserver<ServerToRunner> responseObserver) {
        return new StreamObserver<>() {

            @Override
            public void onNext(RunnerToServer message) {
                log.info("[control-plane] <- runner_id={} tenant_id={} kind={} body={}",
                        message.getRunnerId(), message.getTenantId(), message.getKind(), message.getBody());

                switch (message.getKind()) {
                    case REGISTRATION -> {
                        responseObserver.onNext(ServerToRunner.newBuilder()
                                .setCorrelationId("reg-ack")
                                .setKind(ServerCommandKind.ACK)
                                .setBody("registered")
                                .build());
                        responseObserver.onNext(ServerToRunner.newBuilder()
                                .setCorrelationId("job-1")
                                .setKind(ServerCommandKind.JOB_ASSIGNMENT)
                                .setBody("extract-orders")
                                .build());
                    }
                    case HEARTBEAT -> responseObserver.onNext(ServerToRunner.newBuilder()
                            .setCorrelationId("hb")
                            .setKind(ServerCommandKind.PING)
                            .setBody("")
                            .build());
                    case LOG_LINE -> responseObserver.onNext(ServerToRunner.newBuilder()
                            .setCorrelationId("log-ack")
                            .setKind(ServerCommandKind.ACK)
                            .setBody("ok")
                            .build());
                    default -> log.warn("Unhandled kind: {}", message.getKind());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("[control-plane] runner stream error", t);
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                log.info("[control-plane] runner closed stream");
                responseObserver.onCompleted();
            }
        };
    }

}
