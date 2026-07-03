package com.mcpserver.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.sql.*;
import java.util.List;

public class SqlTool {

    private static final List<String> BLOCKED_KEYWORDS =
            List.of("DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "TRUNCATE");

    @SuppressWarnings("deprecation")
    public static McpServerFeatures.SyncToolSpecification build(McpJsonMapper jsonMapper) {

        McpSchema.Tool toolDef = McpSchema.Tool.builder()
                .name("execute_sql_query")
                .description("Executes a read-only SQL query against the code review database (review_sessions table) and returns results as text. Destructive queries are blocked before execution.")
                .inputSchema(jsonMapper, """
                        {
                          "type": "object",
                          "properties": {
                            "query": { "type": "string" }
                          },
                          "required": ["query"]
                        }
                        """)
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                toolDef,
                (exchange, arguments) -> {
                    String query = (String) arguments.get("query");
                    String result = executeSqlQuery(query);
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

    private static String executeSqlQuery(String query) {
        if (!isQuerySafe(query)) {
            return "Query blocked: contains a destructive keyword (DROP, DELETE, UPDATE, INSERT, ALTER, or TRUNCATE). Only read-only queries are permitted.";
        }

        String url  = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");

        StringBuilder result = new StringBuilder();

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(query)) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                result.append(meta.getColumnName(i));
                if (i < columnCount) result.append(" | ");
            }
            result.append("\n");

            int rowCount = 0;
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    result.append(rs.getString(i));
                    if (i < columnCount) result.append(" | ");
                }
                result.append("\n");
                rowCount++;
            }

            if (rowCount == 0) result.append("(no rows returned)");

        } catch (SQLException e) {
            return "SQL error: " + e.getMessage();
        }

        return result.toString();
    }
}