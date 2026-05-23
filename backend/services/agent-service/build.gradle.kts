/**
 * services:agent-service
 *
 * Agent Service - AI-powered pipeline assistance.
 *
 * @see docs/architecture/high-level-architecture.md
 * @see docs/adr/ADR-014-llm-integration.md
 */

plugins {
    id("pravah.spring-boot-conventions")
}

description = "Pravah Agent Service - AI-powered assistance"

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:spring-support"))

    // Web + JWT (ADR-009)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // HTTP client for LLM API calls (OpenAI-compatible)
    // Will implement custom client using WebClient

    // Test support
    testImplementation(project(":libs:test-support"))
}
