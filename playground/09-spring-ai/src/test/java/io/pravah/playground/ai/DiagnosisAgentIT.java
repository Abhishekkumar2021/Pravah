package io.pravah.playground.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for the Diagnosis Agent.
 * 
 * These tests require a valid GEMINI_API_KEY environment variable.
 * They are skipped if the API key is not set.
 *
 * To run:
 *   export GEMINI_API_KEY=AIzaSy...
 *   ./gradlew test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = "AIzaSy.*")
class DiagnosisAgentIT {

    @Autowired
    DiagnosisAgent agent;

    /**
     * Test basic chat without tools.
     */
    @Test
    void simpleChat() {
        String response = agent.chat("What is the capital of France? Answer in one word.");

        assertThat(response).containsIgnoringCase("Paris");
    }

    /**
     * Test full ReAct diagnosis flow.
     * The agent should:
     * 1. Call getExecutionLogs to see the error
     * 2. Call getPipelineDefinition to see the expected schema
     * 3. Call getColumnLineage to see what changed
     * 4. Propose a fix or explain the root cause
     */
    @Test
    void diagnoseSchemaError() {
        String diagnosis = agent.diagnoseFailure(
                "orders-pipeline",
                "exec-005",
                "transform",
                "SchemaError"
        );

        System.out.println("=== DIAGNOSIS ===");
        System.out.println(diagnosis);
        System.out.println("=================");

        // The agent should identify the schema drift issue
        assertThat(diagnosis).satisfiesAnyOf(
                d -> assertThat(d).containsIgnoringCase("loyalty_tier"),
                d -> assertThat(d).containsIgnoringCase("schema"),
                d -> assertThat(d).containsIgnoringCase("column")
        );

        // The agent should propose a fix or mention COALESCE
        assertThat(diagnosis).satisfiesAnyOf(
                d -> assertThat(d).containsIgnoringCase("fix"),
                d -> assertThat(d).containsIgnoringCase("coalesce"),
                d -> assertThat(d).containsIgnoringCase("removed"),
                d -> assertThat(d).containsIgnoringCase("drift")
        );
    }
}
