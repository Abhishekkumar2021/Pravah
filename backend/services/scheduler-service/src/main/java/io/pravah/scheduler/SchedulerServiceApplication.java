package io.pravah.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduler Service Application Entry Point.
 *
 * <p>Manages cron-based pipeline schedules and triggers runs via Execution Service.
 *
 * @see <a href="../../../docs/lld/02-database-erd.md">Database ERD - Scheduler Domain</a>
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(
    basePackages = {
      "io.pravah.scheduler",
      "io.pravah.spring.multitenancy",
      "io.pravah.spring.security"
    })
public class SchedulerServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(SchedulerServiceApplication.class, args);
  }
}
