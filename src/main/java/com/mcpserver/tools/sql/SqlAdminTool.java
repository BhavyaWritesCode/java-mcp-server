package com.mcpserver.tools.sql;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.sql.*;
import java.util.List;

public class SqlAdminTool {

    private static final List<String> BLOCKED_KEYWORDS =
            List.of("DROP", "DELETE", "TRUNCATE");

    @SuppressWarnings("deprecation")
    public static McpServerFeatures.SyncToolSpecification build(McpJsonMapper jsonMapper) {

        McpSchema.Tool toolDef = McpSchema.Tool.builder()
                .name("execute_sql_admin")
                .description("Executes admin SQL operations: CREATE DATABASE, CREATE TABLE, INSERT, ALTER, SELECT, SHOW, DESCRIBE. Destructive operations (DROP, DELETE, TRUNCATE) are always blocked.")
                .inputSchema(jsonMapper, """
                        {
                          "type": "object",
                          "properties": {
                            "query": {
                              "type": "string",
                              "description": "SQL statement to execute"
                            },
                            "database": {
                              "type": "string",
                              "description": "Database to connect to. Omit only for CREATE DATABASE operations."
                            }
                          },
                          "required": ["query"]
                        }
                        """)
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                toolDef,
                (exchange, arguments) -> {
                    String query    = (String) arguments.get("query");
                    String database = (String) arguments.get("database");
                    String result   = executeAdminQuery(query, database);
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(result)),
                            false
                    );
                }
        );
    }

    private static boolean isQuerySafe(String sql) {
        String upper = sql.toUpperCase();
        return BLOCKED_KEYWORDS.stream().noneMatch(upper::contains);
    }

    private static String executeAdminQuery(String query, String database) {
        if (query == null || query.isBlank()) {
            return "Error: query cannot be empty.";
        }

        if (!isQuerySafe(query)) {
            return "Query blocked: DROP, DELETE, and TRUNCATE are not permitted in admin tool.";
        }

        String baseUrl = System.getenv("DB_URL");   
        String user    = System.getenv("DB_USER");
        String pass    = System.getenv("DB_PASSWORD");

        // Strip existing db from URL to get base: jdbc:mysql://localhost:3306
        String baseNoDb = baseUrl.substring(0, baseUrl.lastIndexOf('/'));

        // Build connection URL
        String url = (database != null && !database.isBlank())
                ? baseNoDb + "/" + database
                : baseNoDb + "/";  

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt  = conn.createStatement()) {

            String upper = query.trim().toUpperCase();

            boolean isQuery = upper.startsWith("SELECT")
                           || upper.startsWith("SHOW")
                           || upper.startsWith("DESCRIBE");

            if (isQuery) {
                ResultSet rs = stmt.executeQuery(query);
                return formatResultSet(rs);
            } else {
                stmt.execute(query);
                return "Executed successfully:\n" + query.trim().split("\n")[0];
            }

        } catch (SQLException e) {
            return "SQL error: " + e.getMessage();
        }
    }

    private static String formatResultSet(ResultSet rs) throws SQLException {
        StringBuilder out = new StringBuilder();
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        for (int i = 1; i <= colCount; i++) {
            out.append(meta.getColumnName(i));
            if (i < colCount) out.append(" | ");
        }
        out.append("\n");

        int rowCount = 0;
        while (rs.next()) {
            for (int i = 1; i <= colCount; i++) {
                out.append(rs.getString(i));
                if (i < colCount) out.append(" | ");
            }
            out.append("\n");
            rowCount++;
        }

        if (rowCount == 0) out.append("(no rows returned)");
        return out.toString();
    }
}