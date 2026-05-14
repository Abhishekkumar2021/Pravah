package io.pravah.execution.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Hibernate to use a clean Jackson ObjectMapper for JSONB serialization.
 *
 * <p>Without this, Hibernate auto-discovers jackson-module-scala (from Kafka test dependencies) and
 * deserializes JSON arrays as Scala collections instead of Java Lists. This causes serialization
 * issues when the payload is sent through Kafka.
 */
@Configuration(proxyBeanMethods = false)
public class HibernateJsonConfiguration {

  @Bean
  public HibernatePropertiesCustomizer hibernateJsonFormatMapperCustomizer() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    return properties ->
        properties.put(
            AvailableSettings.JSON_FORMAT_MAPPER, new JacksonJsonFormatMapper(objectMapper));
  }
}
