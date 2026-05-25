package io.pravah.connect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Connect Service Application Entry Point.
 *
 * <p>Provides a unified connector framework for data sources and destinations:
 *
 * <ul>
 *   <li>Database connectors (PostgreSQL, MySQL, SQL Server, Oracle, MongoDB)
 *   <li>File storage connectors (S3, GCS, Azure Blob, Local FS)
 *   <li>Protocol connectors (FTP, SFTP, REST API)
 *   <li>Streaming connectors (Kafka)
 *   <li>CDC connectors (Debezium)
 * </ul>
 *
 * @see <a href="../../../docs/adr/ADR-029-connect-service-kafka-connect.md">ADR-029</a>
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {
      "io.pravah.connect",
      "io.pravah.spring.security",
      "io.pravah.spring.multitenancy"
    })
public class ConnectServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ConnectServiceApplication.class, args);
  }
}
