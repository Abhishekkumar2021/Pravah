package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageTimeoutTest {

  @Test
  void resolveForStage_pipelineMinutesAndStageSeconds() {
    Map<String, Object> definition =
        Map.of(
            "timeout_minutes", 60, "stages", List.of(Map.of("id", "slow", "timeout_seconds", 30)));

    StageTimeout pipelineDefault = StageTimeoutParser.resolveForStage(definition, "other");
    StageTimeout stageOverride = StageTimeoutParser.resolveForStage(definition, "slow");

    assertThat(pipelineDefault.timeoutSeconds()).isEqualTo(3600);
    assertThat(stageOverride.timeoutSeconds()).isEqualTo(30);
  }

  @Test
  void isExpired_whenDeadlinePassed() {
    StageTimeout timeout = new StageTimeout(10);
    Instant started = Instant.parse("2026-05-16T10:00:00Z");

    assertThat(timeout.isExpired(started, started.plusSeconds(5))).isFalse();
    assertThat(timeout.isExpired(started, started.plusSeconds(11))).isTrue();
  }

  @Test
  void parseFromMap_rejectsNonPositiveTimeout() {
    assertThatThrownBy(() -> StageTimeoutParser.parseFromMap(Map.of("timeout_seconds", 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseFromMap_rejectsBothSecondsAndMinutes() {
    assertThatThrownBy(
            () ->
                StageTimeoutParser.parseFromMap(
                    Map.of("timeout_seconds", 10, "timeout_minutes", 1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not both");
  }

  @Test
  void parseFromMap_rejectsExcessiveTimeout() {
    assertThatThrownBy(
            () ->
                StageTimeoutParser.parseFromMap(
                    Map.of("timeout_seconds", StageTimeout.MAX_TIMEOUT_SECONDS + 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolveForStage_nestedStageTimeoutBlock() {
    Map<String, Object> definition =
        Map.of(
            "timeout_minutes",
            60,
            "stages",
            List.of(Map.of("id", "slow", "timeout", Map.of("timeout_seconds", 15))));

    assertThat(StageTimeoutParser.resolveForStage(definition, "slow").timeoutSeconds())
        .isEqualTo(15);
  }
}
