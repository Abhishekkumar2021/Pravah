package io.pravah.playground.ai;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * ReAct agent for diagnosing pipeline failures.
 * Demonstrates ADR-020: Agent Service Architecture.
 *
 * ReAct Pattern:
 *   1. Thought: Agent reasons about what information it needs
 *   2. Action: Agent calls a tool to get that information
 *   3. Observation: Agent receives tool response
 *   4. Repeat until a conclusion is reached
 *
 * Spring AI handles the tool-calling loop automatically when you configure
 * functions (tools) in the ChatClient.
 */
@Service
public class DiagnosisAgent {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisAgent.class);

    private static final String SYSTEM_PROMPT = """
        You are a data pipeline diagnosis agent for Pravah, an intelligent ETL platform.
        
        Your role is to diagnose why a pipeline execution failed and propose a fix.
        
        When diagnosing a failure:
        1. First, get the execution logs to understand the error
        2. Get the pipeline definition to see what the transform expects
        3. If it's a schema issue, check the column lineage to see what changed
        4. Check recent runs to see if this is a new problem
        5. If you can identify the root cause, propose a fix
        
        Be concise and technical. Your audience is data engineers.
        
        Always explain your reasoning before calling tools.
        After gathering information, provide:
        - Root Cause Analysis (RCA)
        - Recommended fix (if applicable)
        - Prevention suggestions
        """;

    private final ChatClient chatClient;

    public DiagnosisAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultFunctions(
                        "getExecutionLogs",
                        "getPipelineDefinition",
                        "getColumnLineage",
                        "getRecentRuns",
                        "proposeFix"
                )
                .build();
    }

    /**
     * Diagnose a failed pipeline execution.
     * The agent will use ReAct pattern: reason → call tools → reason → conclude.
     */
    public String diagnoseFailure(String pipelineId, String executionId, String stepId, String errorType) {
        String userPrompt = String.format("""
                A pipeline execution has failed. Please diagnose the issue.
                
                Pipeline ID: %s
                Execution ID: %s
                Failed Step: %s
                Error Type: %s
                
                Please investigate and provide a diagnosis.
                """, pipelineId, executionId, stepId, errorType);

        log.info("Starting diagnosis for pipeline={}, execution={}", pipelineId, executionId);

        ChatResponse response = chatClient.prompt()
                .user(userPrompt)
                .call()
                .chatResponse();

        String diagnosis = response.getResult().getOutput().getText();
        log.info("Diagnosis complete");

        return diagnosis;
    }

    /**
     * Simple chat without automatic tools — useful for testing the model connection.
     */
    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
