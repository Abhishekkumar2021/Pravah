package io.pravah.runnerservice.service;

import io.pravah.common.runner.RemoteJobSpecPayload;
import io.pravah.proto.runner.JobSpec;
import io.pravah.proto.runner.ResourceRequirements;

/** Maps {@link RemoteJobSpecPayload} to gRPC {@link JobSpec}. */
public final class JobSpecMapper {

  private JobSpecMapper() {}

  public static JobSpec toProto(RemoteJobSpecPayload payload) {
    JobSpec.Builder builder =
        JobSpec.newBuilder()
            .setExecutor(payload.executor())
            .setTimeoutSeconds(payload.timeoutSeconds());
    if (payload.image() != null && !payload.image().isBlank()) {
      builder.setImage(payload.image());
    }
    if (payload.commands() != null && !payload.commands().isEmpty()) {
      builder.addAllCommands(payload.commands());
    }
    if (payload.environment() != null && !payload.environment().isEmpty()) {
      builder.putAllEnvironment(payload.environment());
    }
    if (payload.memoryBytes() != null || payload.cpuCores() != null) {
      ResourceRequirements.Builder resources = ResourceRequirements.newBuilder();
      if (payload.memoryBytes() != null) {
        resources.setMemoryBytes(payload.memoryBytes());
      }
      if (payload.cpuCores() != null) {
        resources.setCpuCores(payload.cpuCores());
      }
      builder.setResources(resources.build());
    }
    return builder.build();
  }
}
