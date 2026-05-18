import { describe, expect, it } from "vitest";
import {
  buildPostgresConfig,
  connectionHostSummary,
  formFromPostgresConfig,
  getPasswordRef,
  maskCredentialRef,
} from "./connectionConfig";

describe("connectionConfig", () => {
  it("masks env credential references", () => {
    expect(maskCredentialRef("env:PRAVAH_DB_PASSWORD")).toBe("env:PR****RD");
    expect(maskCredentialRef("env:AB")).toBe("env:****");
    expect(maskCredentialRef("vault:secret/data/db")).toBe("vault:****");
    expect(maskCredentialRef("${secret.db_password}")).toBe("${secret.****}");
    expect(maskCredentialRef(undefined)).toBe("—");
  });

  it("reads password ref from postgres config", () => {
    const config = {
      host: "localhost",
      credentials: { password: "env:MY_PASSWORD" },
    };
    expect(getPasswordRef(config)).toBe("env:MY_PASSWORD");
  });

  it("builds host-based postgres config", () => {
    const config = buildPostgresConfig({
      host: "db.example.com",
      port: "5433",
      database: "analytics",
      username: "reader",
      passwordRef: "env:DB_PASS",
      jdbcUrl: "",
    });
    expect(config).toEqual({
      host: "db.example.com",
      port: 5433,
      database: "analytics",
      username: "reader",
      credentials: { password: "env:DB_PASS" },
    });
    expect(connectionHostSummary(config)).toBe("db.example.com:5433/analytics");
  });

  it("builds JDBC URL postgres config", () => {
    const config = buildPostgresConfig({
      host: "",
      port: "",
      database: "",
      username: "reader",
      passwordRef: "env:DB_PASS",
      jdbcUrl: "jdbc:postgresql://localhost:5432/mydb",
    });
    expect(config.url).toBe("jdbc:postgresql://localhost:5432/mydb");
    expect(connectionHostSummary(config)).toBe("jdbc:postgresql://localhost:5432/mydb");
  });

  it("parses postgres config into form fields", () => {
    const form = formFromPostgresConfig({
      host: "localhost",
      port: 5432,
      database: "pravah",
      username: "pravah",
      credentials: { password: "env:PRAVAH_DB_PASSWORD" },
    });
    expect(form.host).toBe("localhost");
    expect(form.database).toBe("pravah");
    expect(form.passwordRef).toBe("env:PRAVAH_DB_PASSWORD");
  });

  it("requires password reference when building config", () => {
    expect(() =>
      buildPostgresConfig({
        host: "localhost",
        port: "5432",
        database: "db",
        username: "u",
        passwordRef: "  ",
        jdbcUrl: "",
      }),
    ).toThrow(/password reference/i);
  });
});
