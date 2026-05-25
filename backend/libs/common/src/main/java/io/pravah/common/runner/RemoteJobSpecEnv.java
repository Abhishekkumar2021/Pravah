package io.pravah.common.runner;

/**
 * Environment variable keys embedded in remote {@link RemoteJobSpecPayload} for runner execution.
 */
public final class RemoteJobSpecEnv {

  public static final String PYTHON_SCRIPT = "PRAVAH_PYTHON_SCRIPT";
  public static final String PYTHON_REQUIREMENTS = "PRAVAH_PYTHON_REQUIREMENTS";

  /** Local filesystem path to requirements.txt on the runner (requirements_file stage config). */
  public static final String PYTHON_REQUIREMENTS_FILE = "PRAVAH_PYTHON_REQUIREMENTS_FILE";

  public static final String SQL_JDBC_URL = "PRAVAH_SQL_JDBC_URL";
  public static final String SQL_USER = "PRAVAH_SQL_USER";
  public static final String SQL_PASSWORD = "PRAVAH_SQL_PASSWORD";
  public static final String SQL_QUERY = "PRAVAH_SQL_QUERY";
  public static final String DUCKDB_SQL = "PRAVAH_DUCKDB_SQL";

  private RemoteJobSpecEnv() {}
}
