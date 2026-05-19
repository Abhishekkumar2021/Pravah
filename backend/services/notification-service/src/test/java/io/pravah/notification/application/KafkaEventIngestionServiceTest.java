package io.pravah.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.pravah.notification.infrastructure.persistence.repository.ProcessedEventRepository;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaEventIngestionServiceTest {

  @Mock private ProcessedEventRepository processedEventRepository;

  @Test
  void processIfNew_skipsDuplicateEventId() {
    UUID eventId = UUID.randomUUID();
    when(processedEventRepository.existsById(eventId)).thenReturn(true);

    KafkaEventIngestionService service = new KafkaEventIngestionService(processedEventRepository);
    AtomicBoolean handled = new AtomicBoolean(false);

    boolean processed =
        service.processIfNew(
            Map.of("eventId", eventId.toString(), "eventType", "execution.failed"),
            event -> handled.set(true));

    assertThat(processed).isFalse();
    assertThat(handled).isFalse();
    verify(processedEventRepository, never()).save(any());
  }

  @Test
  void processIfNew_runsHandlerForNewEvent() {
    UUID eventId = UUID.randomUUID();
    when(processedEventRepository.existsById(eventId)).thenReturn(false);

    KafkaEventIngestionService service = new KafkaEventIngestionService(processedEventRepository);
    AtomicBoolean handled = new AtomicBoolean(false);

    boolean processed =
        service.processIfNew(
            Map.of("eventId", eventId.toString(), "eventType", "execution.failed"),
            event -> handled.set(true));

    assertThat(processed).isTrue();
    assertThat(handled).isTrue();
    verify(processedEventRepository).save(any());
  }
}
