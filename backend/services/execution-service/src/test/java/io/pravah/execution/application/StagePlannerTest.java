package io.pravah.execution.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StagePlannerTest {

  @Test
  void plan_emptyStages_returnsEmpty() {
    assertThat(StagePlanner.plan(Map.of("stages", List.of()))).isEmpty();
  }

  @Test
  void plan_missingStagesKey_returnsEmpty() {
    assertThat(StagePlanner.plan(Map.of("other", List.of()))).isEmpty();
  }

  @Test
  void plan_mapsStageIdAndName() {
    List<StagePlanner.PlannedJob> jobs =
        StagePlanner.plan(
            Map.of(
                "stages",
                List.of(
                    Map.of("id", "extract", "name", "Extract"),
                    Map.of("id", "load", "type", "sql"))));

    assertThat(jobs).hasSize(2);
    assertThat(jobs.get(0).stageId()).isEqualTo("extract");
    assertThat(jobs.get(0).stageName()).isEqualTo("Extract");
    assertThat(jobs.get(1).stageId()).isEqualTo("load");
    assertThat(jobs.get(1).stageName()).isEqualTo("load");
  }

  @Test
  void rootStageIds_whenNoDependsOn_returnsAllStageIds() {
    assertThat(
            StagePlanner.rootStageIds(
                Map.of(
                    "stages",
                    List.of(Map.of("id", "a", "name", "A"), Map.of("id", "b", "name", "B")))))
        .containsExactly("a", "b");
  }

  @Test
  void stagesReadyToQueueAfterSuccesses_respectsDependsOn() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "extract", "name", "Extract"),
                Map.of("id", "load", "name", "Load", "dependsOn", List.of("extract"))));

    assertThat(StagePlanner.stagesReadyToQueueAfterSuccesses(definition, Set.of()))
        .containsExactly("extract");
    assertThat(StagePlanner.stagesReadyToQueueAfterSuccesses(definition, Set.of("extract")))
        .containsExactly("load");
    assertThat(StagePlanner.stagesReadyToQueueAfterSuccesses(definition, Set.of("extract", "load")))
        .isEmpty();
  }

  @Test
  void stagesReadyToQueueAfterSuccesses_parallelRootsWhenNoneSucceeded() {
    Map<String, Object> definition =
        Map.of("stages", List.of(Map.of("id", "a", "name", "A"), Map.of("id", "b", "name", "B")));
    assertThat(StagePlanner.stagesReadyToQueueAfterSuccesses(definition, Set.of()))
        .containsExactly("a", "b");
  }

  @Test
  void stagesReadyToQueueAfterSuccesses_respectsDependsOnSnakeCase() {
    Map<String, Object> definition =
        Map.of(
            "stages",
            List.of(
                Map.of("id", "extract", "name", "Extract"),
                Map.of("id", "load", "name", "Load", "depends_on", List.of("extract"))));

    assertThat(StagePlanner.stagesReadyToQueueAfterSuccesses(definition, Set.of("extract")))
        .containsExactly("load");
  }

  @Test
  void rootStageIds_whenDependsOn_returnsOnlyRoots() {
    assertThat(
            StagePlanner.rootStageIds(
                Map.of(
                    "stages",
                    List.of(
                        Map.of("id", "extract", "name", "Extract"),
                        Map.of("id", "load", "name", "Load", "dependsOn", List.of("extract"))))))
        .containsExactly("extract");
  }
}
