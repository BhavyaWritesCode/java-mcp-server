package com.mcpserver.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class GitHubTool {

    @SuppressWarnings("deprecation")
    public static McpServerFeatures.SyncToolSpecification build(McpJsonMapper jsonMapper) {

        McpSchema.Tool toolDef = McpSchema.Tool.builder()
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

        return new McpServerFeatures.SyncToolSpecification(
                toolDef,
                (exchange, arguments) -> {
                    String owner = (String) arguments.get("owner");
                    String repo  = (String) arguments.get("repo");
                    String result = fetchRepoInfo(owner, repo);
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(result)),
                            false
                    );
                }
        );
    }

    private static String fetchRepoInfo(String owner, String repo) {
        try {
            String url = "https://api.github.com/repos/" + owner + "/" + repo;

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "java-mcp-server")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "GitHub API returned status " + response.statusCode() + ": " + response.body();
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(response.body());

            return "Repo: "        + json.path("full_name").asText()
                + "\nDescription: " + json.path("description").asText()
                + "\nStars: "       + json.path("stargazers_count").asInt()
                + "\nForks: "       + json.path("forks_count").asInt()
                + "\nLanguage: "    + json.path("language").asText();

        } catch (Exception e) {
            return "Error fetching repo info: " + e.getMessage();
        }
    }
}