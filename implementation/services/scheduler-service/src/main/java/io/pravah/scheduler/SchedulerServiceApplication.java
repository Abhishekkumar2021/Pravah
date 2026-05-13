package io.pravah.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Scheduler Service Application Entry Point.
 * <p>
 * Manages cron-based pipeline schedules and triggers runs via Execution Service.
 *
 * @see <a href="../../../docs/lld/02-database-erd.md">Database ERD - Scheduler Domain</a>
 */
@SpringBootApplication
public class SchedulerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerServiceApplication.class, args);
    }
}
