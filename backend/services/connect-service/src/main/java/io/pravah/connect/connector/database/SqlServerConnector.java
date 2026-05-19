package io.pravah.connect.connector.database;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Microsoft SQL Server database connector.
 */
@Component
public class SqlServerConnector extends AbstractJdbcConnector {

    @Override
    protected String getConnectorId() {
        return "sqlserver";
    }

    @Override
    protected String getDriverClassName() {
        return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }

    @Override
    protected String getDefaultPort() {
        return "1433";
    }

    @Override
    protected String getIconName() {
        return "sqlserver";
    }

    @Override
    protected String getDisplayName() {
        return "SQL Server";
    }

    @Override
    protected String getDescription() {
        return "Microsoft SQL Server - enterprise relational database with comprehensive analytics and BI features.";
    }

    @Override
    protected List<String> getTags() {
        return List.of("database", "sql", "relational", "sqlserver", "mssql", "microsoft", "enterprise");
    }

    @Override
    protected boolean supportsCdc() {
        return true; // Via CDC tables
    }

    @Override
    protected String buildJdbcUrl(Map<String, Object> config) {
        String host = getString(config, "host");
        int port = getInt(config, "port", 1433);
        String database = getString(config, "database");
        boolean ssl = getBoolean(config, "ssl", false);

        StringBuilder url = new StringBuilder();
        url.append("jdbc:sqlserver://").append(host).append(":").append(port);
        url.append(";databaseName=").append(database);
        url.append(";encrypt=").append(ssl);
        url.append(";trustServerCertificate=true");

        return url.toString();
    }
}
