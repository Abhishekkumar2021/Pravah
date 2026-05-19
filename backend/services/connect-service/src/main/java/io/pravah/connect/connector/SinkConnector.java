package io.pravah.connect.connector;

import java.util.List;
import java.util.Map;

/** Interface for sink connectors that write data. */
public interface SinkConnector extends Connector {

  /**
   * Writes a batch of records to the destination.
   *
   * @param config the connection configuration
   * @param streamName the target stream/table
   * @param records the records to write
   * @param writeOptions write options (mode, batch settings)
   * @return write result
   */
  WriteResult write(
      Map<String, Object> config,
      String streamName,
      List<SourceConnector.Record> records,
      WriteOptions writeOptions);

  /**
   * Creates or updates the schema in the destination.
   *
   * @param config the connection configuration
   * @param streamName the target stream/table
   * @param schema the schema to apply
   */
  void ensureSchema(
      Map<String, Object> config, String streamName, SourceConnector.StreamInfo schema);

  /** Write mode options. */
  enum WriteMode {
    APPEND,
    OVERWRITE,
    UPSERT,
    MERGE
  }

  /** Options for writing data. */
  record WriteOptions(
      WriteMode mode, List<String> primaryKeys, int batchSize, boolean createTableIfNotExists) {
    public static WriteOptions append() {
      return new WriteOptions(WriteMode.APPEND, List.of(), 1000, true);
    }

    public static WriteOptions upsert(List<String> primaryKeys) {
      return new WriteOptions(WriteMode.UPSERT, primaryKeys, 1000, true);
    }
  }

  /** Result of a write operation. */
  record WriteResult(
      long recordsWritten, long bytesWritten, long durationMs, Map<String, Object> metadata) {}
}
