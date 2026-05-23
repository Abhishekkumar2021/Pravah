package io.pravah.runnerservice.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.UUID;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerGrpcIdentityInterceptorTest {

  private static final UUID RUNNER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

  @Test
  void connectWithMtls_rejectsMissingCertificateIdentity(@TempDir Path tempDir) throws Exception {
    Path ca = tempDir.resolve("ca.crt");
    Files.writeString(ca, "dummy-ca");

    RunnerGrpcTlsProperties tls = new RunnerGrpcTlsProperties();
    tls.setEnabled(true);
    tls.setCertChain(tempDir.resolve("tls.crt").toString());
    tls.setPrivateKey(tempDir.resolve("tls.key").toString());
    Files.writeString(tempDir.resolve("tls.crt"), "cert");
    Files.writeString(tempDir.resolve("tls.key"), "key");
    tls.setClientCa(ca.toString());

    RunnerGrpcIdentityInterceptor interceptor = new RunnerGrpcIdentityInterceptor(tls);

    @SuppressWarnings("unchecked")
    ServerCall<Object, Object> call = mock(ServerCall.class);
    when(call.getMethodDescriptor()).thenReturn(connectMethod());
    when(call.getAttributes()).thenReturn(Attributes.EMPTY);

    @SuppressWarnings("unchecked")
    ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

    ServerCall.Listener<Object> listener = interceptor.interceptCall(call, new Metadata(), next);

    assertThat(listener).isNotNull();
    verify(call)
        .close(
            argThat(
                status ->
                    status.getCode() == Status.Code.UNAUTHENTICATED
                        && "Client certificate with runner SPIFFE identity is required"
                            .equals(status.getDescription())),
            any(Metadata.class));
  }

  @Test
  void connectWithMtls_attachesIdentityToContext(@TempDir Path tempDir) throws Exception {
    Path ca = tempDir.resolve("ca.crt");
    Files.writeString(ca, "dummy-ca");

    RunnerGrpcTlsProperties tls = new RunnerGrpcTlsProperties();
    tls.setEnabled(true);
    tls.setCertChain(tempDir.resolve("tls.crt").toString());
    tls.setPrivateKey(tempDir.resolve("tls.key").toString());
    Files.writeString(tempDir.resolve("tls.crt"), "cert");
    Files.writeString(tempDir.resolve("tls.key"), "key");
    tls.setClientCa(ca.toString());

    RunnerGrpcIdentityInterceptor interceptor = new RunnerGrpcIdentityInterceptor(tls);

    X509Certificate cert = mock(X509Certificate.class);
    when(cert.getSubjectAlternativeNames())
        .thenReturn(List.of(List.of(6, "spiffe://pravah.local/runner/" + RUNNER_ID)));

    SSLSession session = mock(SSLSession.class);
    when(session.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[] {cert});

    @SuppressWarnings("unchecked")
    ServerCall<Object, Object> call = mock(ServerCall.class);
    when(call.getMethodDescriptor()).thenReturn(connectMethod());
    when(call.getAttributes())
        .thenReturn(Attributes.newBuilder().set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session).build());

    @SuppressWarnings("unchecked")
    ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
    when(next.startCall(any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(RunnerGrpcIdentityContext.current()).isPresent();
              assertThat(RunnerGrpcIdentityContext.current().get().runnerId()).isEqualTo(RUNNER_ID);
              return new ServerCall.Listener<>() {};
            });

    interceptor.interceptCall(call, new Metadata(), next);

    verify(next).startCall(any(), any());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static MethodDescriptor<Object, Object> connectMethod() {
    MethodDescriptor.Marshaller<Object> marshaller = mock(MethodDescriptor.Marshaller.class);
    return MethodDescriptor.<Object, Object>newBuilder()
        .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
        .setFullMethodName("pravah.runner.v1.RunnerService/Connect")
        .setRequestMarshaller(marshaller)
        .setResponseMarshaller(marshaller)
        .build();
  }
}
