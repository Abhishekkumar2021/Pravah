package io.pravah.pipeline.domain;

import io.pravah.common.domain.PipelineId;
import io.pravah.common.domain.ProjectId;
import io.pravah.common.domain.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pipeline aggregate root - the main domain entity for workflow definitions.
 *
 * <p>A Pipeline represents a workflow definition that can be versioned and executed. It follows the
 * state machine defined in docs/lld/03-state-machines.md.
 *
 * <p>This is the aggregate root for the Pipeline bounded context. All modifications to pipeline
 * state must go through this class.
 *
 * <p>Note: tenantId is stored as UUID to match the database schema (UUID foreign key to tenant).
 *
 * @see <a href="../../../../../../docs/lld/01-design-patterns.md">Design Patterns - Aggregate
 *     Root</a>
 * @see <a href="../../../../../../docs/lld/03-state-machines.md">State Machines - Pipeline</a>
 */
public class Pipeline {

  private final PipelineId id;
  private final UUID tenantId;
  private final ProjectId projectId;
  private String name;
  private String description;
  private int currentVersion;
  private PipelineState state;
  private final Instant createdAt;
  private Instant updatedAt;
  private final UserId createdBy;

  private final List<DomainEventData> domainEvents = new ArrayList<>();

  private Pipeline(Builder builder) {
    this.id = builder.id;
    this.tenantId = Objects.requireNonNull(builder.tenantId, "Tenant ID is required");
    this.projectId = Objects.requireNonNull(builder.projectId, "Project ID is required");
    this.name = Objects.requireNonNull(builder.name, "Pipeline name is required");
    this.description = builder.description;
    this.currentVersion = builder.currentVersion;
    this.state = builder.state != null ? builder.state : PipelineState.DRAFT;
    this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
    this.updatedAt = builder.updatedAt != null ? builder.updatedAt : this.createdAt;
    this.createdBy = Objects.requireNonNull(builder.createdBy, "Created by is required");
  }

  /**
   * Creates a new Pipeline in DRAFT state.
   *
   * @param tenantId the tenant this pipeline belongs to (UUID)
   * @param projectId the project this pipeline belongs to
   * @param name the pipeline name
   * @param description optional description
   * @param createdBy the user creating the pipeline
   * @return a new Pipeline instance
   */
  public static Pipeline create(
      UUID tenantId, ProjectId projectId, String name, String description, UserId createdBy) {
    Pipeline pipeline =
        builder()
            .id(PipelineId.generate())
            .tenantId(tenantId)
            .projectId(projectId)
            .name(name)
            .description(description)
            .currentVersion(0)
            .state(PipelineState.DRAFT)
            .createdBy(createdBy)
            .build();

    pipeline.domainEvents.add(
        new DomainEventData(
            PipelineEventTypes.PIPELINE_CREATED,
            pipeline.id.value(),
            pipeline.tenantId,
            pipeline.createdAt,
            pipeline.createdBy.value()));

    return pipeline;
  }

  /**
   * Updates the pipeline name.
   *
   * @param newName the new name
   * @param updatedBy the user making the change
   * @return true if updated, false if no change
   */
  public boolean updateName(String newName, UserId updatedBy) {
    Objects.requireNonNull(newName, "Name cannot be null");
    if (!newName.equals(this.name)) {
      this.name = newName;
      this.updatedAt = Instant.now();
      registerUpdateEvent(updatedBy);
      return true;
    }
    return false;
  }

  /**
   * Updates the pipeline description.
   *
   * @param newDescription the new description
   * @param updatedBy the user making the change
   * @return true if updated, false if no change
   */
  public boolean updateDescription(String newDescription, UserId updatedBy) {
    if (!Objects.equals(newDescription, this.description)) {
      this.description = newDescription;
      this.updatedAt = Instant.now();
      registerUpdateEvent(updatedBy);
      return true;
    }
    return false;
  }

  /**
   * Publishes a new version of the pipeline.
   *
   * <p>Valid transitions: DRAFT → ACTIVE, ACTIVE → ACTIVE
   *
   * @param publishedBy the user publishing
   * @return the new version number
   * @throws IllegalStateException if the current state doesn't allow publishing
   */
  public int publish(UserId publishedBy) {
    this.state = this.state.onPublish();
    this.currentVersion++;
    this.updatedAt = Instant.now();

    domainEvents.add(
        new DomainEventData(
            PipelineEventTypes.PIPELINE_PUBLISHED,
            this.id.value(),
            this.tenantId,
            this.updatedAt,
            publishedBy.value()));

    return this.currentVersion;
  }

  /**
   * Archives the pipeline.
   *
   * <p>Valid transition: ACTIVE → ARCHIVED
   *
   * @param archivedBy the user archiving
   * @throws IllegalStateException if the current state doesn't allow archiving
   */
  public void archive(UserId archivedBy) {
    this.state = this.state.onArchive();
    this.updatedAt = Instant.now();

    domainEvents.add(
        new DomainEventData(
            PipelineEventTypes.PIPELINE_ARCHIVED,
            this.id.value(),
            this.tenantId,
            this.updatedAt,
            archivedBy.value()));
  }

  /**
   * Restores an archived pipeline.
   *
   * <p>Valid transition: ARCHIVED → ACTIVE
   *
   * @param restoredBy the user restoring
   * @throws IllegalStateException if the current state doesn't allow restoring
   */
  public void restore(UserId restoredBy) {
    this.state = this.state.onRestore();
    this.updatedAt = Instant.now();

    domainEvents.add(
        new DomainEventData(
            PipelineEventTypes.PIPELINE_RESTORED,
            this.id.value(),
            this.tenantId,
            this.updatedAt,
            restoredBy.value()));
  }

  private void registerUpdateEvent(UserId updatedBy) {
    domainEvents.add(
        new DomainEventData(
            PipelineEventTypes.PIPELINE_UPDATED,
            this.id.value(),
            this.tenantId,
            this.updatedAt,
            updatedBy.value()));
  }

  /**
   * Returns and clears pending domain events.
   *
   * @return list of domain events to be published
   */
  public List<DomainEventData> collectDomainEvents() {
    List<DomainEventData> events = new ArrayList<>(domainEvents);
    domainEvents.clear();
    return Collections.unmodifiableList(events);
  }

  public PipelineId getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public ProjectId getProjectId() {
    return projectId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getCurrentVersion() {
    return currentVersion;
  }

  public PipelineState getState() {
    return state;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UserId getCreatedBy() {
    return createdBy;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Pipeline pipeline)) return false;
    return Objects.equals(id, pipeline.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Pipeline{id=%s, name='%s', state=%s, version=%d}"
        .formatted(id, name, state, currentVersion);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Simple domain event data holder for internal use. The application service translates this to
   * proper event store entries and outbox messages.
   */
  public record DomainEventData(
      String eventType, UUID pipelineId, UUID tenantId, Instant occurredAt, UUID actorId) {}

  /** Builder for reconstructing Pipeline from persistence. */
  public static class Builder {
    private PipelineId id;
    private UUID tenantId;
    private ProjectId projectId;
    private String name;
    private String description;
    private int currentVersion;
    private PipelineState state;
    private Instant createdAt;
    private Instant updatedAt;
    private UserId createdBy;

    public Builder id(PipelineId id) {
      this.id = id;
      return this;
    }

    public Builder tenantId(UUID tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder projectId(ProjectId projectId) {
      this.projectId = projectId;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder currentVersion(int currentVersion) {
      this.currentVersion = currentVersion;
      return this;
    }

    public Builder state(PipelineState state) {
      this.state = state;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder updatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    public Builder createdBy(UserId createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Pipeline build() {
      return new Pipeline(this);
    }
  }
}
