import { describe, expect, it } from "vitest";
import {
  connectorSupportsSqlConnection,
  connectorWorkflowHint,
  pipelineConnectionTypeForConnector,
} from "@/lib/connectorCatalog";
import type { ConnectorSpec } from "@/lib/api";

function spec(partial: Partial<ConnectorSpec> & Pick<ConnectorSpec, "id" | "type">): ConnectorSpec {
  return {
    name: partial.name ?? partial.id,
    description: partial.description ?? "",
    icon: partial.icon ?? "database",
    version: "1.0.0",
    category: "test",
    mode: "SOURCE",
    configFields: [],
    capabilities: {},
    tags: [],
    ...partial,
  };
}

describe("connectorCatalog", () => {
  it("maps postgres connector to pipeline postgres type", () => {
    expect(pipelineConnectionTypeForConnector("postgres")).toBe("postgres");
    expect(
      connectorSupportsSqlConnection(
        spec({ id: "postgres", type: "DATABASE", name: "PostgreSQL" }),
      ),
    ).toBe(true);
  });

  it("does not treat SaaS connectors as SQL-ready", () => {
    const stripe = spec({ id: "stripe", type: "SAAS", name: "Stripe" });
    expect(connectorSupportsSqlConnection(stripe)).toBe(false);
    expect(connectorWorkflowHint(stripe).toLowerCase()).toContain("python");
  });
});
