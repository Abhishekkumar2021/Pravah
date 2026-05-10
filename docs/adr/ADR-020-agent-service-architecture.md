# ADR-020: Agent Service Architecture (Spring AI + ReAct)

**Status**: Accepted  
**Date**: 2026-05-10

---

## Context

Pravah's agentic layer is a core differentiator. While other ETL platforms provide dashboards and alerts, Pravah provides an Agent that can:

- **Diagnose** why a pipeline failed and apply a fix without human intervention
- **Detect** that a source schema has drifted and suggest the corrective transformation change
- **Build** a new pipeline from a natural-language description
- **Explain** data quality anomalies in plain language with confidence scores
- **Analyze** dead-letter queues and identify the root cause of message failures

These are not simple rule-based alerts. They require reasoning over multiple signals (failure logs, pipeline definition, historical run data, lineage graph, current schema), forming hypotheses, taking actions (calling tools), and verifying results.

**The pattern that enables this is ReAct** (Reasoning + Acting): the LLM reasons about what to do, calls a tool, observes the result, reasons again, and repeats until a conclusion is reached. This requires:
- A capable LLM that supports tool/function calling (Claude claude-sonnet-4-5 via Anthropic API)
- A tool framework that connects LLM tool calls to Pravah's internal APIs
- A memory model that provides context without exceeding the LLM's context window
- Guardrails that prevent the agent from taking destructive actions

---

## Decision

A dedicated **Agent Service** implements the agentic intelligence layer using **Spring AI** with **Claude claude-sonnet-4-5**, following the **ReAct (Reason + Act)** pattern.

**Agent Service responsibilities:**
- Receive agent task requests (from Kafka events or user chat)
- Execute ReAct loops: reason → call tool → observe → reason...
- Maintain three tiers of memory per pipeline/tenant
- Stream responses to the UI via WebSocket
- Write action summaries and RCAs to the audit log

**LLM selection — Claude claude-sonnet-4-5 (Anthropic):**

| Requirement | Claude claude-sonnet-4-5 Capability |
|-------------|-------------------------------------|
| Tool/function calling | Native, multi-turn |
| Long context window | 200K tokens (pipeline history, logs, lineage) |
| Code generation (transform repair) | Excellent |
| Structured output (JSON plans) | Reliable |
| Safety for autonomous action | Constitutional AI training |

**Spring AI integration:**

```java
@Service
public class PipelineAgent {

    private final ChatClient chatClient;
    private final List<FunctionCallback> tools;

    public PipelineAgent(ChatClient.Builder builder, List<AgentTool> agentTools) {
        this.tools = agentTools.stream()
            .map(AgentTool::toFunctionCallback)
            .toList();
        this.chatClient = builder
            .defaultSystem(SYSTEM_PROMPT)
            .defaultFunctions(tools)
            .build();
    }

    public Flux<String> diagnoseFailure(String executionId, String tenantId) {
        AgentContext context = contextLoader.load(executionId, tenantId);

        return chatClient.prompt()
            .user(buildDiagnosisPrompt(context))
            .stream()
            .content();  // streams tokens to WebSocket
    }
}
```

**Tool registry — what the agent can call:**

```java
// Tools are typed Java functions that Spring AI exposes to the LLM
@Tool("get_execution_logs")
public ExecutionLogs getExecutionLogs(
    @ToolParam("execution_id") String executionId,
    @ToolParam("step_id") String stepId,
    @ToolParam("last_n_lines") int lines
) { /* calls Execution Service */ }

@Tool("get_pipeline_definition")
public PipelineDefinition getPipelineDefinition(
    @ToolParam("pipeline_id") String pipelineId,
    @ToolParam("version") String version   // "latest" or specific version
) { /* calls Pipeline Service event store */ }

@Tool("get_column_lineage")
public LineageGraph getColumnLineage(
    @ToolParam("dataset_id") String datasetId
) { /* calls Metadata Service */ }

@Tool("get_recent_runs")
public List<RunSummary> getRecentRuns(
    @ToolParam("pipeline_id") String pipelineId,
    @ToolParam("last_n") int count
) { /* calls Execution Service */ }

@Tool("get_dlq_messages")
public List<DLQMessage> getDLQMessages(
    @ToolParam("topic") String topic,
    @ToolParam("limit") int limit
) { /* calls Connect Service */ }

@Tool("propose_fix")
public FixProposal proposeTransformFix(
    @ToolParam("pipeline_id") String pipelineId,
    @ToolParam("step_id") String stepId,
    @ToolParam("new_sql") String sql
) { /* validates SQL, creates draft pipeline version — does NOT auto-apply */ }

@Tool("trigger_pipeline")
public TriggerResult triggerPipeline(
    @ToolParam("pipeline_id") String pipelineId,
    @ToolParam("parameters") Map<String, String> params
) { /* calls Scheduler Service — requires confirmation if not in auto-heal mode */ }
```

**Guardrails — what the agent CANNOT do:**

- Cannot delete pipelines, connectors, or runners
- Cannot modify production pipeline definitions directly (can create draft versions)
- Cannot trigger pipelines on behalf of users in chat mode (proposes, user confirms)
- Cannot auto-trigger in production without `auto_heal_enabled = true` on the pipeline
- Cannot access other tenants' data (all tool calls are tenant-scoped)
- Cannot make external HTTP calls from tool execution (only internal Pravah APIs)

**Memory architecture (three tiers):**

```
Short-term (Conversation context)
  Storage: Redis (24h TTL per session)
  Content: Last 20 turns of the current conversation
  Purpose: Maintain context within a session

Long-term (Pattern memory)
  Storage: pgvector / Elasticsearch (persistent)
  Content: Embeddings of past failure patterns, successful fixes
  Purpose: "This failure pattern looks like the Snowflake timeout issue from last month"
  Retrieval: RAG — embed current context, find similar past events

Episodic (Per-pipeline event memory)
  Storage: Pipeline event store (ADR-017) + recent Kafka events
  Content: Last 30 days of pipeline events, runs, schema changes
  Purpose: "This pipeline was modified 3 times this week and started failing after v3"
  Retrieval: Direct time-range query against event store
```

**ReAct loop example — auto-heal on JobFailed:**

```
Event: JobFailed (execution_id=abc, step_id=transform, error=SchemaError)

Agent receives event via Kafka subscription

ReAct loop:
  Thought: "The job failed with SchemaError. I need to understand what schema issue occurred."
  Action: get_execution_logs(execution_id=abc, step_id=transform, last_n_lines=50)
  Observation: "Column 'loyalty_tier' not found in source table"

  Thought: "The source table is missing a column that the transform expects. Let me check
            when this column was expected."
  Action: get_pipeline_definition(pipeline_id=orders-pipeline, version=latest)
  Observation: "SELECT customer_id, loyalty_tier, revenue FROM orders"

  Thought: "The transform queries 'loyalty_tier' but it's not in the source.
            Let me check the lineage to see when this column last existed."
  Action: get_column_lineage(dataset_id=postgres://prod/public.orders)
  Observation: "Column 'loyalty_tier' was present until 2026-05-08. Schema drift detected."

  Thought: "The source schema changed. The transform needs to be updated.
            loyalty_tier was removed. I should propose a fix that handles its absence."
  Action: propose_fix(pipeline_id=orders-pipeline, step_id=transform,
            new_sql="SELECT customer_id, COALESCE(loyalty_tier, 'UNKNOWN') as loyalty_tier,
                     revenue FROM orders")
  Observation: { "draft_version_id": "v4", "validation": "PASSED" }

  Thought: "I have a validated fix. I'll apply it and retry since auto_heal_enabled=true."
  Action: trigger_pipeline(pipeline_id=orders-pipeline, parameters={version: v4})

  Final output: "Schema drift detected — 'loyalty_tier' was removed from the source table
                on 2026-05-08. Applied fix: handling missing column with COALESCE. Pipeline
                retried with draft version v4. RCA posted to #data-alerts."
```

**Agent modes:**

| Mode | Trigger | Auto-action? |
|------|---------|-------------|
| Auto-heal | `JobFailed` Kafka event, `auto_heal_enabled=true` | Yes — applies fix, retries |
| Chat | User message via WebSocket | No — proposes, user confirms |
| Scheduled review | Cron, weekly pipeline health report | No — generates report |
| Schema drift responder | `schema.drift.detected` Kafka event | Notifies only — proposes fix |

---

## Consequences

### Positive

- **Autonomous incident response reduces MTTR**: auto-heal on known failure patterns (schema drift, transient timeouts, bad credentials) resolves incidents without paging a human at 3am.
- **NL pipeline builder lowers the barrier to entry**: non-engineers can describe a pipeline in plain language and get a working draft in seconds. This expands Pravah's addressable user base.
- **Agent reasoning is auditable**: every ReAct step (Thought + Action + Observation) is logged. The full reasoning chain for any auto-heal decision is inspectable in the audit log.
- **Spring AI abstracts LLM switching**: if Claude is unavailable or a better model becomes available, switching requires changing one configuration value. Tool definitions remain the same.

### Negative

- **LLM API cost**: every auto-heal invocation uses Claude's API. High-failure-rate pipelines could generate significant LLM API costs. Per-tenant token budget limits are required.
- **LLM non-determinism**: the same failure may produce different diagnoses across runs. This is acceptable for a "best effort" auto-heal but not for compliance-critical decisions.
- **Hallucination risk in fix proposals**: the LLM may propose a SQL fix that looks plausible but is semantically wrong. Validation (SQL parsing, dry-run) catches syntax errors but not semantic errors.
- **Context window limits**: a pipeline with 10,000 log lines and 50 historical runs exceeds even Claude's 200K context. The episodic memory tier must intelligently select the most relevant context.

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Agent applies incorrect auto-fix to production | Dry-run validation before apply; max 1 auto-heal attempt per execution; human required for second attempt |
| LLM API key leaked | Key stored in Vault; short TTL; rate-limited by Vault policy |
| Agent escapes tool sandbox (prompt injection) | Tool implementations validate inputs; no shell execution; no external HTTP calls; all calls go through typed Java methods |
| Token budget exhausted on runaway pipeline | Per-tenant monthly token quota enforced by Agent Service; billing alert at 80% |
