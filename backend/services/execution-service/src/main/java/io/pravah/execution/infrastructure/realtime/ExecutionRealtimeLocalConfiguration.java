package io.pravah.execution.infrastructure.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "pravah.realtime", name = "redis-enabled", havingValue = "false")
public class ExecutionRealtimeLocalConfiguration {

  @Bean
  ExecutionRealtimeFanout localExecutionRealtimeFanout(ExecutionWebSocketSessionRegistry registry) {
    return registry::broadcast;
  }
}
