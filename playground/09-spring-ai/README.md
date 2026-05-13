# Playground 09 — Spring AI + ReAct Agent

**ADR**: [ADR-020 Agent Service Architecture](../../docs/adr/ADR-020-agent-service-architecture.md)  
**Concepts**: ReAct pattern, function calling, LLM tool use, autonomous diagnosis

---

## What is ReAct?

**ReAct** = **Rea**soning + **Act**ing. It's a pattern where an LLM:

1. **Thinks** about what information it needs
2. **Acts** by calling a tool (function)
3. **Observes** the tool's response
4. **Repeats** until it can provide a final answer

```
User: "Why did the pipeline fail?"

Agent Thought: "I need to see the execution logs first."
Agent Action: getExecutionLogs(executionId="exec-005", stepId="transform")
Agent Observation: "Column 'loyalty_tier' not found in source table"

Agent Thought: "This is a schema issue. Let me check the pipeline definition."
Agent Action: getPipelineDefinition(pipelineId="orders-pipeline")
Agent Observation: "SELECT customer_id, loyalty_tier, revenue FROM orders"

Agent Thought: "The transform queries loyalty_tier but it's missing. Let me check lineage."
Agent Action: getColumnLineage(datasetId="postgres://prod/orders")
Agent Observation: "loyalty_tier was REMOVED on 2026-05-08"

Agent Final Answer: "Root cause: Schema drift. The 'loyalty_tier' column was removed 
from the source table on May 8th. Recommended fix: Use COALESCE to handle missing column."
```

This is exactly what Pravah's Agent Service does for pipeline failures.

---

## Quick Start

**Prerequisites:**
- A Google Gemini API key (get one free at [aistudio.google.com](https://aistudio.google.com/apikey))

```bash
cd /Users/abhishek/Dev/Pravah/playground/09-spring-ai

# Set your API key
export GEMINI_API_KEY=AIzaSy...your-key-here

# Run the tests (calls real Gemini API)
./gradlew test
```

---

## What's in Here

| File | Purpose |
|------|---------|
| `DiagnosisAgent.java` | ReAct agent that diagnoses pipeline failures |
| `PipelineTools.java` | Tools (functions) the agent can call |
| `DiagnosisAgentIT.java` | Integration test that runs a full diagnosis |
| `application.yml` | Spring AI + Anthropic configuration |

---

## How Spring AI Works

### 1. Define Tools as Spring Beans

```java
@Bean
@Description("Get the last N lines of logs for a pipeline step")
public Function<GetLogsRequest, GetLogsResponse> getExecutionLogs() {
    return request -> {
        // In Pravah: call Execution Service gRPC
        // Here: return mock data
        return new GetLogsResponse(request.executionId(), mockLogs);
    };
}
```

Spring AI automatically:
- Exposes this function to the LLM as a callable tool
- Handles JSON serialization/deserialization
- Manages the tool-calling loop

### 2. Configure ChatClient with Functions

```java
this.chatClient = chatClientBuilder
    .defaultSystem(SYSTEM_PROMPT)
    .defaultFunctions(
        "getExecutionLogs",
        "getPipelineDefinition", 
        "getColumnLineage",
        "proposeFix"
    )
    .build();
```

### 3. Let Spring AI Handle ReAct

```java
ChatResponse response = chatClient.prompt()
    .user("Diagnose this failure...")
    .call()
    .chatResponse();
```

Spring AI automatically:
1. Sends the prompt to Claude
2. If Claude wants to call a tool, executes the function
3. Sends the tool result back to Claude
4. Repeats until Claude has a final answer

---

## Tasks

### 1. Run the integration test

```bash
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew test
```

Watch the logs. You'll see:
- `[Tool] getExecutionLogs called: ...`
- `[Tool] getPipelineDefinition called: ...`
- `[Tool] getColumnLineage called: ...`

The agent autonomously decided which tools to call based on the error.

### 2. Trace the ReAct loop

Add logging to see the LLM's reasoning:

```yaml
logging:
  level:
    org.springframework.ai: TRACE
```

You'll see the full prompt, tool calls, and responses.

### 3. Add a new tool

Create a `getDLQMessages` tool that returns dead-letter queue messages:

```java
@Bean
@Description("Get messages from a Kafka dead-letter queue topic")
public Function<GetDLQRequest, GetDLQResponse> getDLQMessages() {
    return request -> {
        // Return mock DLQ messages
    };
}
```

Add it to the agent's function list and test with a "MessageDeserializationError".

### 4. Implement guardrails

The agent has a `proposeFix` tool. Currently it accepts any SQL. Add validation:
- Reject SQL with `DROP`, `DELETE`, `TRUNCATE`
- Require `SELECT` statements only
- Return validation errors the agent can understand

### 5. Test streaming (advanced)

Modify `DiagnosisAgent` to stream the response:

```java
public Flux<String> diagnoseFailureStreaming(...) {
    return chatClient.prompt()
        .user(prompt)
        .stream()
        .content();
}
```

This is how Pravah streams agent responses to the UI via WebSocket.

---

## How Pravah Uses This

| Agent Mode | Trigger | Behavior |
|------------|---------|----------|
| **Auto-heal** | `JobFailed` Kafka event | Agent diagnoses, proposes fix, retries if `auto_heal_enabled` |
| **Chat** | User message via WebSocket | Agent proposes, user confirms before action |
| **Schema drift** | `schema.drift.detected` event | Agent notifies, suggests transform update |

The tools in this playground are mocked. In real Pravah:
- `getExecutionLogs` → gRPC call to Execution Service
- `getPipelineDefinition` → reads from Pipeline Service event store
- `getColumnLineage` → queries Metadata Service lineage graph
- `proposeFix` → creates draft version in Pipeline Service

---

## Cost Awareness

Gemini 1.5 Flash has a generous **free tier**:
- 15 requests per minute (RPM)
- 1 million tokens per minute (TPM)
- 1,500 requests per day (RPD)

For production Pravah, you'd use Claude or GPT-4 for higher quality reasoning, but Gemini Flash is excellent for learning and development.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `401 Unauthorized` | Check `GEMINI_API_KEY` is set correctly |
| Tests skipped | API key must match pattern `AIzaSy.*` |
| `429 RESOURCE_EXHAUSTED` | Free tier quota exceeded — wait 24h or create new GCP project |
| Slow response | Normal — multi-tool chains take 5-15s |
| "Function not found" | Ensure function name in `defaultFunctions()` matches @Bean method name |

**Note**: Gemini free tier has strict daily limits. If you hit quota limits, either:
1. Wait 24 hours for quota reset
2. Create a new API key from a different Google Cloud project at [aistudio.google.com](https://aistudio.google.com/apikey)

---

## Further Reading

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Google Gemini API](https://ai.google.dev/docs)
- [ReAct Paper](https://arxiv.org/abs/2210.03629)
- ADR-020 in this repo
