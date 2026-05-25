package io.pravah.metadata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Metadata Service Application Entry Point.
 *
 * <p>Manages artifact storage and provides run analytics using DuckDB.
 *
 * @see <a href="../../../docs/adr/ADR-009-duckdb-analytics.md">ADR-009: DuckDB for Analytics</a>
 */
@SpringBootApplication
@org.springframework.context.annotation.ComponentScan(
    basePackages = {"io.pravah.metadata", "io.pravah.spring.security"})
public class MetadataServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(MetadataServiceApplication.class, args);
  }
}
