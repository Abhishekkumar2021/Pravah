/** Maps connect-service connector IDs to pipeline connection types (US-12.16). */

import type { ConnectionType } from "@/lib/connectionConfig";
import { SUPPORTED_CONNECTION_TYPES } from "@/lib/connectionConfig";
import type { ConnectorSpec, ConnectorType } from "@/lib/api";

/**
 * Connect-service connector id → pipeline connection `type`.
 * `null` = no pipeline connection yet (use Python/Container stage or future release).
 */
export const CONNECTOR_TO_PIPELINE_CONNECTION_TYPE: Record<string, ConnectionType | null> = {
  postgres: "postgres",
  mysql: null,
  sqlserver: null,
  oracle: null,
  snowflake: null,
  "postgres-cdc": null,
  mongodb: null,
  s3: null,
  "local-file": null,
  "rest-api": null,
  ftp: null,
  sftp: null,
  kafka: null,
  rabbitmq: null,
  stripe: null,
  shopify: null,
  salesforce: null,
  hubspot: null,
  "google-sheets": null,
  airtable: null,
};

export function pipelineConnectionTypeForConnector(connectorId: string): ConnectionType | null {
  return CONNECTOR_TO_PIPELINE_CONNECTION_TYPE[connectorId] ?? null;
}

/** True when this catalog connector can back a workflow SQL stage via /app/connections. */
export function connectorSupportsSqlConnection(connector: ConnectorSpec): boolean {
  if (connector.type !== "DATABASE") return false;
  const mapped = pipelineConnectionTypeForConnector(connector.id);
  return mapped != null && SUPPORTED_CONNECTION_TYPES.includes(mapped);
}

/** Human-readable guidance when a connector cannot become a pipeline connection yet. */
export function connectorWorkflowHint(connector: ConnectorSpec): string {
  if (connectorSupportsSqlConnection(connector)) {
    return "Create a named connection, then reference it in SQL stages as connection: name.";
  }
  switch (connector.type) {
    case "STREAMING":
    case "CDC":
      return "Use a Kafka event trigger plus a Container or Python stage for streaming ingestion.";
    case "SAAS":
    case "PROTOCOL":
    case "FILE":
      return "Use a Python or Container stage with the vendor SDK or HTTP client.";
    default:
      return "Pipeline JDBC connections for this database are not available yet — use Python or Container stages.";
  }
}

export function isDatabaseConnectorType(type: ConnectorType): boolean {
  return type === "DATABASE";
}
