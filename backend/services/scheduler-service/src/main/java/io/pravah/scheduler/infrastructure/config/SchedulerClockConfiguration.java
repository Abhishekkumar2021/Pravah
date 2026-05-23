package io.pravah.scheduler.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchedulerClockConfiguration {

  @Bean
  public Clock schedulerClock() {
    return Clock.systemUTC();
  }
}
