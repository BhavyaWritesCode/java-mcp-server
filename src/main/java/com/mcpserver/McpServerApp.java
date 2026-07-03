package com.mcpserver;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;

import com.mcpserver.tools.GitHubTool;
import com.mcpserver.tools.SqlTool;

public class McpServerApp {

    public static void main(String[] args) {

        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(jsonMapper);
                
        System.err.println("Java MCP Server started and listening on stdio...");

        McpServer.sync(transportProvider)
                .serverInfo("java-mcp-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(
                        GitHubTool.build(jsonMapper),
                        SqlTool.build(jsonMapper)
                )
                .build();

    }
}