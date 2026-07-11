package com.mcpserver;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.time.Instant;

public class ToolCallLogger {

    private final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("deprecation")
    public McpServerFeatures.SyncToolSpecification wrap(
            McpServerFeatures.SyncToolSpecification spec) {

        return new McpServerFeatures.SyncToolSpecification(
                spec.tool(),
                (exchange, arguments) -> {
                    long start = System.currentTimeMillis();
                    String toolName  = spec.tool().name();
                    String inputJson = toJson(arguments);

                    McpSchema.CallToolResult result;
                    String status   = "SUCCESS";
                    String errorMsg = null;

                    try {
                        result = spec.call().apply(exchange, arguments);
                        if (Boolean.TRUE.equals(result.isError())) {
                            status = "ERROR";
                        }
                    } catch (Exception ex) {
                        status   = "ERROR";
                        errorMsg = ex.getMessage();
                        result   = new McpSchema.CallToolResult(
                                java.util.List.of(new McpSchema.TextContent("Tool error: " + ex.getMessage())),
                                true
                        );
                    }

                    long duration = System.currentTimeMillis() - start;
                    persistLog(toolName, inputJson, toJson(result), status, errorMsg, duration);

                    return result;
                }
        );
    }

    private void persistLog(String toolName, String inputJson, String outputJson,
                             String status, String errorMsg, long durationMs) {
        String url  = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");

        String sql = """
                INSERT INTO tool_call_logs
                    (tool_name, input_json, output_json, status, error_msg, duration_ms, called_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, toolName);
            ps.setString(2, inputJson);
            ps.setString(3, outputJson);
            ps.setString(4, status);
            ps.setString(5, errorMsg);
            ps.setLong(6, durationMs);
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
            ps.executeUpdate();

        } catch (Exception e) {
            // Logging must never crash the server
            System.err.println("[ToolCallLogger] DB write failed: " + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}