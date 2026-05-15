package io.pravah.pipeline.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.pravah.common.domain.PipelineId;
import io.pravah.common.domain.ProjectId;
import io.pravah.common.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PipelineTest {

  private UUID tenantId;
  private ProjectId projectId;
  private UserId userId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    projectId = ProjectId.generate();
    userId = UserId.generate();
  }

  @Nested
  class Creation {

    @Test
    void create_withValidParams_returnsPipelineInDraftState() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "test-pipeline", "desc", userId);

      assertThat(pipeline.getId()).isNotNull();
      assertThat(pipeline.getTenantId()).isEqualTo(tenantId);
      assertThat(pipeline.getProjectId()).isEqualTo(projectId);
      assertThat(pipeline.getName()).isEqualTo("test-pipeline");
      assertThat(pipeline.getDescription()).isEqualTo("desc");
      assertThat(pipeline.getCurrentVersion()).isEqualTo(0);
      assertThat(pipeline.getState()).isEqualTo(PipelineState.DRAFT);
      assertThat(pipeline.getCreatedBy()).isEqualTo(userId);
      assertThat(pipeline.getCreatedAt()).isNotNull();
      assertThat(pipeline.getUpdatedAt()).isEqualTo(pipeline.getCreatedAt());
    }

    @Test
    void create_emitsCreatedEvent() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "test-pipeline", null, userId);

      List<Pipeline.DomainEventData> events = pipeline.collectDomainEvents();
      assertThat(events).hasSize(1);
      assertThat(events.get(0).eventType()).isEqualTo(PipelineEventTypes.PIPELINE_CREATED);
      assertThat(events.get(0).pipelineId()).isEqualTo(pipeline.getId().value());
      assertThat(events.get(0).tenantId()).isEqualTo(tenantId);
      assertThat(events.get(0).actorId()).isEqualTo(userId.value());
    }

    @Test
    void create_withNullTenantId_throws() {
      assertThatThrownBy(() -> Pipeline.create(null, projectId, "name", null, userId))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Tenant ID");
    }

    @Test
    void create_withNullProjectId_throws() {
      assertThatThrownBy(() -> Pipeline.create(tenantId, null, "name", null, userId))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Project ID");
    }

    @Test
    void create_withNullName_throws() {
      assertThatThrownBy(() -> Pipeline.create(tenantId, projectId, null, null, userId))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("name");
    }

    @Test
    void create_withNullCreatedBy_throws() {
      assertThatThrownBy(() -> Pipeline.create(tenantId, projectId, "name", null, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Created by");
    }

    @Test
    void create_withNullDescription_isAllowed() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);
      assertThat(pipeline.getDescription()).isNull();
    }
  }

  @Nested
  class Updates {

    @Test
    void updateName_changesNameAndEmitsEvent() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "old-name", null, userId);
      pipeline.collectDomainEvents();

      boolean updated = pipeline.updateName("new-name", userId);

      assertThat(updated).isTrue();
      assertThat(pipeline.getName()).isEqualTo("new-name");
      List<Pipeline.DomainEventData> events = pipeline.collectDomainEvents();
      assertThat(events).hasSize(1);
      assertThat(events.get(0).eventType()).isEqualTo(PipelineEventTypes.PIPELINE_UPDATED);
    }

    @Test
    void updateName_sameName_returnsFalseAndNoEvent() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "same-name", null, userId);
      pipeline.collectDomainEvents();

      boolean updated = pipeline.updateName("same-name", userId);

      assertThat(updated).isFalse();
      assertThat(pipeline.collectDomainEvents()).isEmpty();
    }

    @Test
    void updateDescription_changesDescriptionAndEmitsEvent() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", "old", userId);
      pipeline.collectDomainEvents();

      boolean updated = pipeline.updateDescription("new", userId);

      assertThat(updated).isTrue();
      assertThat(pipeline.getDescription()).isEqualTo("new");
      List<Pipeline.DomainEventData> events = pipeline.collectDomainEvents();
      assertThat(events).hasSize(1);
      assertThat(events.get(0).eventType()).isEqualTo(PipelineEventTypes.PIPELINE_UPDATED);
    }

    @Test
    void updateDescription_sameDescription_returnsFalseAndNoEvent() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", "same", userId);
      pipeline.collectDomainEvents();

      boolean updated = pipeline.updateDescription("same", userId);

      assertThat(updated).isFalse();
      assertThat(pipeline.collectDomainEvents()).isEmpty();
    }

    @Test
    void updateDescription_nullToNonNull_emitsEvent() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);
      pipeline.collectDomainEvents();

      boolean updated = pipeline.updateDescription("new desc", userId);

      assertThat(updated).isTrue();
      assertThat(pipeline.getDescription()).isEqualTo("new desc");
    }

    @Test
    void updateDescription_nonNullToNull_emitsEvent() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", "old", userId);
      pipeline.collectDomainEvents();

      boolean updated = pipeline.updateDescription(null, userId);

      assertThat(updated).isTrue();
      assertThat(pipeline.getDescription()).isNull();
    }
  }

  @Nested
  class StateTransitions {

    @Test
    void publish_fromDraft_transitionsToActiveAndIncrementsVersion() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);
      pipeline.collectDomainEvents();

      int newVersion = pipeline.publish(userId);

      assertThat(newVersion).isEqualTo(1);
      assertThat(pipeline.getCurrentVersion()).isEqualTo(1);
      assertThat(pipeline.getState()).isEqualTo(PipelineState.ACTIVE);
    }

    @Test
    void publish_fromDraft_emitsPublishedEvent() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);
      pipeline.collectDomainEvents();

      pipeline.publish(userId);

      List<Pipeline.DomainEventData> events = pipeline.collectDomainEvents();
      assertThat(events).hasSize(1);
      assertThat(events.get(0).eventType()).isEqualTo(PipelineEventTypes.PIPELINE_PUBLISHED);
    }

    @Test
    void publish_fromActive_staysActiveAndIncrementsVersion() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);
      pipeline.publish(userId);
      pipeline.collectDomainEvents();

      int newVersion = pipeline.publish(userId);

      assertThat(newVersion).isEqualTo(2);
      assertThat(pipeline.getCurrentVersion()).isEqualTo(2);
      assertThat(pipeline.getState()).isEqualTo(PipelineState.ACTIVE);
    }

    @Test
    void publish_fromArchived_throws() {
      Pipeline pipeline = createArchivedPipeline();

      assertThatThrownBy(() -> pipeline.publish(userId))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot publish");
    }

    @Test
    void archive_fromActive_transitionsToArchived() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);
      pipeline.publish(userId);
      pipeline.collectDomainEvents();

      pipeline.archive(userId);

      assertThat(pipeline.getState()).isEqualTo(PipelineState.ARCHIVED);
    }

    @Test
    void archive_fromActive_emitsArchivedEvent() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);
      pipeline.publish(userId);
      pipeline.collectDomainEvents();

      pipeline.archive(userId);

      List<Pipeline.DomainEventData> events = pipeline.collectDomainEvents();
      assertThat(events).hasSize(1);
      assertThat(events.get(0).eventType()).isEqualTo(PipelineEventTypes.PIPELINE_ARCHIVED);
    }

    @Test
    void archive_fromDraft_throws() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);

      assertThatThrownBy(() -> pipeline.archive(userId))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot archive");
    }

    @Test
    void archive_fromArchived_throws() {
      Pipeline pipeline = createArchivedPipeline();

      assertThatThrownBy(() -> pipeline.archive(userId))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot archive");
    }

    @Test
    void restore_fromArchived_transitionsToActive() {
      Pipeline pipeline = createArchivedPipeline();
      pipeline.collectDomainEvents();

      pipeline.restore(userId);

      assertThat(pipeline.getState()).isEqualTo(PipelineState.ACTIVE);
    }

    @Test
    void restore_fromArchived_emitsRestoredEvent() {
      Pipeline pipeline = createArchivedPipeline();
      pipeline.collectDomainEvents();

      pipeline.restore(userId);

      List<Pipeline.DomainEventData> events = pipeline.collectDomainEvents();
      assertThat(events).hasSize(1);
      assertThat(events.get(0).eventType()).isEqualTo(PipelineEventTypes.PIPELINE_RESTORED);
    }

    @Test
    void restore_fromDraft_throws() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);

      assertThatThrownBy(() -> pipeline.restore(userId))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot restore");
    }

    @Test
    void restore_fromActive_throws() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);
      pipeline.publish(userId);

      assertThatThrownBy(() -> pipeline.restore(userId))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot restore");
    }

    private Pipeline createArchivedPipeline() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);
      pipeline.publish(userId);
      pipeline.archive(userId);
      return pipeline;
    }
  }

  @Nested
  class DomainEvents {

    @Test
    void collectDomainEvents_clearsEventsAfterCollection() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);

      List<Pipeline.DomainEventData> firstCollection = pipeline.collectDomainEvents();
      List<Pipeline.DomainEventData> secondCollection = pipeline.collectDomainEvents();

      assertThat(firstCollection).hasSize(1);
      assertThat(secondCollection).isEmpty();
    }

    @Test
    void collectDomainEvents_returnsUnmodifiableList() {
      Pipeline pipeline = Pipeline.create(tenantId, projectId, "name", null, userId);

      List<Pipeline.DomainEventData> events = pipeline.collectDomainEvents();

      assertThatThrownBy(() -> events.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  class Builder {

    @Test
    void builder_canReconstructFromPersistence() {
      PipelineId id = PipelineId.generate();
      Instant createdAt = Instant.now().minusSeconds(3600);
      Instant updatedAt = Instant.now();

      Pipeline pipeline =
          Pipeline.builder()
              .id(id)
              .tenantId(tenantId)
              .projectId(projectId)
              .name("reconstructed")
              .description("desc")
              .currentVersion(5)
              .state(PipelineState.ACTIVE)
              .createdAt(createdAt)
              .updatedAt(updatedAt)
              .createdBy(userId)
              .build();

      assertThat(pipeline.getId()).isEqualTo(id);
      assertThat(pipeline.getTenantId()).isEqualTo(tenantId);
      assertThat(pipeline.getProjectId()).isEqualTo(projectId);
      assertThat(pipeline.getName()).isEqualTo("reconstructed");
      assertThat(pipeline.getDescription()).isEqualTo("desc");
      assertThat(pipeline.getCurrentVersion()).isEqualTo(5);
      assertThat(pipeline.getState()).isEqualTo(PipelineState.ACTIVE);
      assertThat(pipeline.getCreatedAt()).isEqualTo(createdAt);
      assertThat(pipeline.getUpdatedAt()).isEqualTo(updatedAt);
      assertThat(pipeline.getCreatedBy()).isEqualTo(userId);
    }

    @Test
    void builder_reconstructedPipelineHasNoEvents() {
      Pipeline pipeline =
          Pipeline.builder()
              .id(PipelineId.generate())
              .tenantId(tenantId)
              .projectId(projectId)
              .name("reconstructed")
              .state(PipelineState.ACTIVE)
              .createdBy(userId)
              .build();

      assertThat(pipeline.collectDomainEvents()).isEmpty();
    }
  }

  @Nested
  class Equality {

    @Test
    void equals_sameId_returnsTrue() {
      PipelineId id = PipelineId.generate();
      Pipeline p1 =
          Pipeline.builder()
              .id(id)
              .tenantId(tenantId)
              .projectId(projectId)
              .name("name1")
              .createdBy(userId)
              .build();
      Pipeline p2 =
          Pipeline.builder()
              .id(id)
              .tenantId(tenantId)
              .projectId(projectId)
              .name("name2")
              .createdBy(userId)
              .build();

      assertThat(p1).isEqualTo(p2);
      assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    void equals_differentId_returnsFalse() {
      Pipeline p1 = Pipeline.create(tenantId, projectId, "name", null, userId);
      Pipeline p2 = Pipeline.create(tenantId, projectId, "name", null, userId);

      assertThat(p1).isNotEqualTo(p2);
    }
  }
}
