package io.pravah.playground.keda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JobConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobConsumerApplication.class, args);
    }
}
