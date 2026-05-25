package io.pravah.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PythonStageOutputParserTest {

  @Test
  void parseStdout_jsonLine_returnsMap() {
    String stdout = "log line\n{\"row_count\": 42}\n";
    assertThat(PythonStageOutputParser.parseStdout(stdout))
        .hasValueSatisfying(m -> assertThat(m).containsEntry("row_count", 42));
  }

  @Test
  void parseStdout_pravahPrefix_returnsMap() {
    String stdout = PythonStageOutputParser.OUTPUT_LINE_PREFIX + "{\"ok\": true}\n";
    assertThat(PythonStageOutputParser.parseStdout(stdout))
        .hasValueSatisfying(m -> assertThat(m).containsEntry("ok", true));
  }

  @Test
  void parseStdout_noStructuredOutput_returnsEmpty() {
    assertThat(PythonStageOutputParser.parseStdout("hello\nworld\n")).isEmpty();
  }

  @Test
  void parseStdout_usesLastJsonLine() {
    String stdout = "{\"first\": 1}\nnoise\n{\"last\": 2}\n";
    assertThat(PythonStageOutputParser.parseStdout(stdout))
        .hasValueSatisfying(m -> assertThat(m).containsEntry("last", 2));
  }
}
