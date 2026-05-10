# Phase 7 — AI/Agent Architecture

This phase covers the theory and engineering behind Pravah's agentic intelligence layer. It bridges the gap between "integrating an LLM API" and "building a production-grade AI agent that operates autonomously on real infrastructure."

The chapters move from LLM fundamentals through agentic patterns, Spring AI integration, RAG, prompt engineering, memory architecture, and finally agent evaluation — the full engineering discipline required to build the Agent Service (ADR-020).

---

## Chapters

| # | Chapter | Key Topics |
|---|---------|-----------|
| 7.1 | [LLM Fundamentals for Engineers](7.1-llm-fundamentals-for-engineers.md) | Transformers, tokenization, context windows, sampling, temperature |
| 7.2 | [Agentic Patterns — ReAct, Plan-and-Execute, Tool Use](7.2-agentic-patterns.md) | ReAct loop, tool calling, multi-step reasoning, agent frameworks |
| 7.3 | [Spring AI Integration](7.3-spring-ai-integration.md) | ChatClient, tool callbacks, streaming, model abstraction |
| 7.4 | [Retrieval-Augmented Generation (RAG)](7.4-retrieval-augmented-generation.md) | Embeddings, vector search, chunking, hybrid retrieval |
| 7.5 | [Prompt Engineering & Guardrails](7.5-prompt-engineering-guardrails.md) | System prompts, few-shot, chain-of-thought, output validation |
| 7.6 | [Agent Memory Architecture](7.6-agent-memory-architecture.md) | Short-term, long-term, episodic; pgvector, Redis, event store |
| 7.7 | [Evaluating & Testing AI Agents](7.7-evaluating-testing-ai-agents.md) | Metrics, evals framework, LLM-as-judge, regression testing |
