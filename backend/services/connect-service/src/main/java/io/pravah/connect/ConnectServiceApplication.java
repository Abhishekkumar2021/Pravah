package io.pravah.connect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Connect Service Application Entry Point.
 *
 * <p>Provides a unified connector framework for data sources and destinations:
 * <ul>
 *   <li>Database connectors (PostgreSQL, MySQL, SQL Server, Oracle, MongoDB)</li>
 *   <li>File storage connectors (S3, GCS, Azure Blob, Local FS)</li>
 *   <li>Protocol connectors (FTP, SFTP, REST API)</li>
 *   <li>Streaming connectors (Kafka)</li>
 *   <li>CDC connectors (Debezium)</li>
 * </ul>
 *
 * @see <a href="../../../docs/adr/ADR-029-connect-service-kafka-connect.md">ADR-029</a>
 */
@SpringBootApplication
public class ConnectServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ConnectServiceApplication.class, args);
  }
}
