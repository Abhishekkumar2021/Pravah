package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StagePlannerTest {

  @Test
  void transitiveUpstream_returnsDependsOnClosure() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "a", "name", "A"),
                Map.of("id", "b", "name", "B", "dependsOn", List.of("a")),
                Map.of("id", "c", "name", "C", "dependsOn", List.of("b"))));

    assertThat(StagePlanner.transitiveUpstream(definition, "c"))
        .containsExactlyInAnyOrder("a", "b");
    assertThat(StagePlanner.transitiveUpstream(definition, "b")).containsExactly("a");
    assertThat(StagePlanner.transitiveUpstream(definition, "a")).isEmpty();
  }

  @Test
  void transitiveUpstream_unknownStage_throws() {
    Map<String, Object> definition = Map.of("stages", List.of(Map.of("id", "a", "name", "A")));
    assertThatThrownBy(() -> StagePlanner.transitiveUpstream(definition, "missing"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stagesReadyToQueueAfterSuccesses_skipsAlreadySucceeded() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "a", "name", "A"),
                Map.of("id", "b", "name", "B", "dependsOn", List.of("a"))));

    assertThat(StagePlanner.stagesReadyToQueueAfterSuccesses(definition, Set.of("a")))
        .containsExactly("b");
    assertThat(StagePlanner.stagesReadyToQueueAfterSuccesses(definition, Set.of("a", "b")))
        .isEmpty();
  }
}
