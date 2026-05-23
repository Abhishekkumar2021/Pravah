package io.pravah.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Agent Service Application Entry Point.
 *
 * <p>Provides AI-powered assistance for pipeline creation and debugging. Uses LLM via
 * OpenAI-compatible API (e.g., Google Gemini).
 *
 * @see <a href="../../../docs/adr/ADR-014-llm-integration.md">ADR-014: LLM Integration</a>
 */
@SpringBootApplication
@org.springframework.context.annotation.ComponentScan(
    basePackages = {"io.pravah.agent", "io.pravah.spring.security"})
public class AgentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentServiceApplication.class, args);
  }
}
