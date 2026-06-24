package com.mcpserver;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class McpServerApp {

    public static void main(String[] args) {

        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(jsonMapper);

        McpSchema.Tool githubToolDef = McpSchema.Tool.builder()
                .name("analyze_github_repo")
                .description("Fetches metadata about a public GitHub repository - stars, forks, language, and description")
                .inputSchema(jsonMapper, """
                        {
                          "type": "object",
                          "properties": {
                            "owner": { "type": "string" },
                            "repo": { "type": "string" }
                          },
                          "required": ["owner", "repo"]
                        }
                        """)
                .build();

        McpServerFeatures.SyncToolSpecification githubTool = new McpServerFeatures.SyncToolSpecification(
                githubToolDef,
                (exchange, arguments) -> {
                    String owner = (String) arguments.get("owner");
                    String repo = (String) arguments.get("repo");
                    String result = fetchRepoInfo(owner, repo);
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(result)),
                            false
                    );
                }
        );

        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("java-mcp-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(githubTool)
                .build();

        System.err.println("Java MCP Server started and listening on stdio...");
    }

    private static String fetchRepoInfo(String owner, String repo) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + owner + "/" + repo))
                    .header("Accept", "application/vnd.github+json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(response.body());

            return "Repo: " + json.path("full_name").asText()
                    + "\nDescription: " + json.path("description").asText()
                    + "\nStars: " + json.path("stargazers_count").asInt()
                    + "\nForks: " + json.path("forks_count").asInt()
                    + "\nLanguage: " + json.path("language").asText();

        } catch (Exception e) {
            return "Error fetching repo info: " + e.getMessage();
        }
    }
}