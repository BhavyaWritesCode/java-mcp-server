package com.mcpserver;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;

import com.mcpserver.tools.github.GitHubTool;
import com.mcpserver.tools.github.GitHubCreateRepoTool;
import com.mcpserver.tools.github.GitHubGetTreeTool;
import com.mcpserver.tools.github.GitHubReadFileTool;
import com.mcpserver.tools.github.GitHubPushFileTool;
import com.mcpserver.tools.sql.SqlTool;
import com.mcpserver.tools.sql.SqlAdminTool;
import com.mcpserver.tools.docker.DockerSandboxTool;


public class McpServerApp {

    public static void main(String[] args) {

        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(jsonMapper);

        ToolCallLogger logger = new ToolCallLogger();

        System.err.println("Java MCP Server started and listening on stdio...");

        McpServer.sync(transportProvider)
                .serverInfo("java-mcp-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(
                        logger.wrap(GitHubTool.build(jsonMapper)),
                        logger.wrap(GitHubCreateRepoTool.build(jsonMapper)),
                        logger.wrap(GitHubGetTreeTool.build(jsonMapper)),
                        logger.wrap(GitHubReadFileTool.build(jsonMapper)),
                        logger.wrap(GitHubPushFileTool.build(jsonMapper)),
                        logger.wrap(SqlTool.build(jsonMapper)),
                        logger.wrap(SqlAdminTool.build(jsonMapper)),
                        logger.wrap(DockerSandboxTool.build(jsonMapper))

                )
                .build();
    }
}