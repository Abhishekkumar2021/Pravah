package io.pravah.connect.connector.database;

import static io.pravah.connect.domain.ConfigField.FieldType.*;

import com.mongodb.client.*;
import io.pravah.connect.connector.SinkConnector;
import io.pravah.connect.connector.SourceConnector;
import io.pravah.connect.domain.ConfigField;
import io.pravah.connect.domain.ConnectorMode;
import io.pravah.connect.domain.ConnectorSpec;
import io.pravah.connect.domain.ConnectorType;
import java.util.*;
import org.bson.Document;
import org.springframework.stereotype.Component;

/** MongoDB NoSQL connector. */
@Component
public class MongoDbConnector implements SourceConnector, SinkConnector {

  @Override
  public ConnectorSpec getSpec() {
    return ConnectorSpec.builder("mongodb")
        .name("MongoDB")
        .description(
            "Document-oriented NoSQL database with flexible schema and horizontal scaling.")
        .icon("mongodb")
        .category("Database")
        .type(ConnectorType.DATABASE)
        .mode(ConnectorMode.BIDIRECTIONAL)
        .configFields(
            List.of(
                ConfigField.builder("connectionString")
                    .label("Connection String")
                    .description("MongoDB connection string (mongodb://...)")
                    .type(STRING)
                    .required()
                    .placeholder("mongodb://localhost:27017")
                    .group("Connection")
                    .order(1)
                    .build(),
                ConfigField.builder("database")
                    .label("Database")
                    .description("Database name")
                    .type(STRING)
                    .required()
                    .group("Connection")
                    .order(2)
                    .build(),
                ConfigField.builder("authSource")
                    .label("Auth Source")
                    .description("Authentication database (default: admin)")
                    .type(STRING)
                    .defaultValue("admin")
                    .group("Authentication")
                    .order(3)
                    .build()))
        .capabilities(
            Map.of(
                "discover", true,
                "incremental", true,
                "fullRefresh", true,
                "cdc", true))
        .tags(List.of("database", "nosql", "mongodb", "document"))
        .build();
  }

  @Override
  public ValidationResult validate(Map<String, Object> config) {
    Map<String, String> errors = new LinkedHashMap<>();

    if (isBlank(config.get("connectionString"))) {
      errors.put("connectionString", "Connection string is required");
    }
    if (isBlank(config.get("database"))) {
      errors.put("database", "Database name is required");
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
  }

  @Override
  public TestResult testConnection(Map<String, Object> config) {
    long start = System.currentTimeMillis();
    String connectionString = getString(config, "connectionString");
    String database = getString(config, "database");

    try (MongoClient client = MongoClients.create(connectionString)) {
      MongoDatabase db = client.getDatabase(database);
      Document result = db.runCommand(new Document("ping", 1));

      if (result.getDouble("ok") == 1.0) {
        long latency = System.currentTimeMillis() - start;
        return TestResult.success(
            "Connected to MongoDB database: " + database, latency, Map.of("database", database));
      } else {
        return TestResult.failure("Ping failed");
      }
    } catch (Exception e) {
      return TestResult.failure("Connection failed", e);
    }
  }

  @Override
  public List<StreamInfo> discoverStreams(Map<String, Object> config) {
    String connectionString = getString(config, "connectionString");
    String database = getString(config, "database");
    List<StreamInfo> streams = new ArrayList<>();

    try (MongoClient client = MongoClients.create(connectionString)) {
      MongoDatabase db = client.getDatabase(database);

      for (String collectionName : db.listCollectionNames()) {
        MongoCollection<Document> collection = db.getCollection(collectionName);

        List<FieldInfo> fields = inferSchemaFromSample(collection);

        streams.add(
            new StreamInfo(
                collectionName,
                database,
                fields,
                List.of("_id"),
                Map.of("documentCount", collection.estimatedDocumentCount())));
      }
    }

    return streams;
  }

  private List<FieldInfo> inferSchemaFromSample(MongoCollection<Document> collection) {
    List<FieldInfo> fields = new ArrayList<>();
    Set<String> seenFields = new HashSet<>();

    try (MongoCursor<Document> cursor = collection.find().limit(100).iterator()) {
      while (cursor.hasNext()) {
        Document doc = cursor.next();
        for (String key : doc.keySet()) {
          if (seenFields.add(key)) {
            Object value = doc.get(key);
            fields.add(
                new FieldInfo(
                    key, value != null ? value.getClass().getSimpleName() : "null", true, null));
          }
        }
      }
    }

    return fields;
  }

  @Override
  public RecordIterator read(Map<String, Object> config, String streamName, ReadOptions options) {
    String connectionString = getString(config, "connectionString");
    String database = getString(config, "database");

    MongoClient client = MongoClients.create(connectionString);
    MongoDatabase db = client.getDatabase(database);
    MongoCollection<Document> collection = db.getCollection(streamName);

    FindIterable<Document> iterable = collection.find();
    if (options.batchSize() > 0) {
      iterable.batchSize(options.batchSize());
    }

    MongoCursor<Document> cursor = iterable.iterator();

    return new MongoRecordIterator(client, cursor);
  }

  @Override
  public WriteResult write(
      Map<String, Object> config, String streamName, List<Record> records, WriteOptions options) {
    if (records.isEmpty()) {
      return new WriteResult(0, 0, 0, Map.of());
    }

    String connectionString = getString(config, "connectionString");
    String database = getString(config, "database");
    long start = System.currentTimeMillis();

    try (MongoClient client = MongoClients.create(connectionString)) {
      MongoDatabase db = client.getDatabase(database);
      MongoCollection<Document> collection = db.getCollection(streamName);

      List<Document> documents = records.stream().map(r -> new Document(r.data())).toList();

      if (options.mode() == WriteMode.UPSERT && !options.primaryKeys().isEmpty()) {
        for (Document doc : documents) {
          Document filter = new Document();
          for (String pk : options.primaryKeys()) {
            filter.put(pk, doc.get(pk));
          }
          collection.replaceOne(
              filter, doc, new com.mongodb.client.model.ReplaceOptions().upsert(true));
        }
      } else {
        collection.insertMany(documents);
      }

      long duration = System.currentTimeMillis() - start;
      return new WriteResult(records.size(), 0, duration, Map.of());
    }
  }

  @Override
  public void ensureSchema(Map<String, Object> config, String streamName, StreamInfo schema) {
    // MongoDB is schema-less, no-op
  }

  private static boolean isBlank(Object value) {
    return value == null || value.toString().isBlank();
  }

  private static String getString(Map<String, Object> config, String key) {
    Object value = config.get(key);
    return value != null ? value.toString() : null;
  }

  private static class MongoRecordIterator implements RecordIterator {
    private final MongoClient client;
    private final MongoCursor<Document> cursor;
    private long rowNum = 0;

    MongoRecordIterator(MongoClient client, MongoCursor<Document> cursor) {
      this.client = client;
      this.cursor = cursor;
    }

    @Override
    public boolean hasNext() {
      return cursor.hasNext();
    }

    @Override
    public Record next() {
      Document doc = cursor.next();
      rowNum++;

      Map<String, Object> data = new LinkedHashMap<>(doc);

      return new Record(data, String.valueOf(rowNum), System.currentTimeMillis());
    }

    @Override
    public String getCursor() {
      return String.valueOf(rowNum);
    }

    @Override
    public void close() {
      cursor.close();
      client.close();
    }
  }
}
