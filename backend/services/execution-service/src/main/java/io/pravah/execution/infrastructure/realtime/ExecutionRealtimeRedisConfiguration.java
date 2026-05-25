package io.pravah.execution.infrastructure.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(
    prefix = "pravah.realtime",
    name = "redis-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ExecutionRealtimeRedisConfiguration {

  @Bean
  ExecutionRealtimeFanout redisExecutionRealtimeFanout(StringRedisTemplate redisTemplate) {
    return (tenantId, jsonPayload) ->
        redisTemplate.convertAndSend(ExecutionRealtimeChannels.topic(tenantId), jsonPayload);
  }

  @Bean
  RedisMessageListenerContainer executionRealtimeRedisListenerContainer(
      RedisConnectionFactory connectionFactory,
      ExecutionRealtimeRedisMessageHandler redisMessageHandler) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    MessageListener listener = redisMessageHandler::onRedisMessage;
    container.addMessageListener(listener, new PatternTopic("pravah:tenant:*:executions"));
    return container;
  }
}
