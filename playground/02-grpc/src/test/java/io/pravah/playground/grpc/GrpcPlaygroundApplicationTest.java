package io.pravah.playground.grpc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "grpc.server.port=0",
        "playground.runner.demo-on-startup=false"
})
class GrpcPlaygroundApplicationTest {

    @Test
    void contextLoads() {
    }
}
