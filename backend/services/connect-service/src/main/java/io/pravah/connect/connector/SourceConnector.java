package io.pravah.connect.connector;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Interface for source connectors that read data.
 */
public interface SourceConnector extends Connector {

    /**
     * Discovers available streams/tables/objects in the source.
     *
     * @param config the connection configuration
     * @return list of discovered streams
     */
    List<StreamInfo> discoverStreams(Map<String, Object> config);

    /**
     * Reads data from a stream.
     *
     * @param config the connection configuration
     * @param streamName the stream to read from
     * @param readOptions read options (batch size, offset, filters)
     * @return iterator over records
     */
    RecordIterator read(Map<String, Object> config, String streamName, ReadOptions readOptions);

    /**
     * Information about a discovered stream.
     */
    record StreamInfo(
            String name,
            String namespace,
            List<FieldInfo> fields,
            List<String> primaryKeys,
            Map<String, Object> metadata
    ) {}

    /**
     * Information about a field in a stream.
     */
    record FieldInfo(
            String name,
            String type,
            boolean nullable,
            String description
    ) {}

    /**
     * Options for reading data.
     */
    record ReadOptions(
            int batchSize,
            String cursor,
            Map<String, Object> filters,
            List<String> selectedFields
    ) {
        public static ReadOptions defaults() {
            return new ReadOptions(1000, null, Map.of(), List.of());
        }
    }

    /**
     * Iterator over records with cursor support.
     */
    interface RecordIterator extends Iterator<Record>, AutoCloseable {
        /**
         * Returns the current cursor position for resumption.
         */
        String getCursor();

        /**
         * Returns the total count if known.
         */
        default Long getTotalCount() {
            return null;
        }

        /**
         * Closes the iterator and releases resources.
         */
        @Override
        void close();
    }

    /**
     * A single data record.
     */
    record Record(
            Map<String, Object> data,
            String cursor,
            long emittedAt
    ) {}
}
