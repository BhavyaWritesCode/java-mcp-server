package com.mcpserver.tools.github;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class GitHubPushFileTool {

    @SuppressWarnings("deprecation")
    public static McpServerFeatures.SyncToolSpecification build(McpJsonMapper jsonMapper) {

        McpSchema.Tool toolDef = McpSchema.Tool.builder()
                .name("push_github_file")
                .description("Creates or updates a file in a GitHub repository with a commit message.")
                .inputSchema(jsonMapper, """
                        {
                          "type": "object",
                          "properties": {
                            "owner": {
                              "type": "string",
                              "description": "GitHub username or org"
                            },
                            "repo": {
                              "type": "string",
                              "description": "Repository name"
                            },
                            "path": {
                              "type": "string",
                              "description": "File path inside the repo e.g. README.md"
                            },
                            "content": {
                              "type": "string",
                              "description": "Plain text content to write into the file"
                            },
                            "message": {
                              "type": "string",
                              "description": "Commit message"
                            },
                            "branch": {
                              "type": "string",
                              "description": "Branch to push to. Defaults to main."
                            }
                          },
                          "required": ["owner", "repo", "path", "content", "message"]
                        }
                        """)
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                toolDef,
                (exchange, arguments) -> {
                    String result = pushFile(arguments);
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(result)),
                            false
                    );
                }
        );
    }

    private static String pushFile(Map<String, Object> args) {
        try {
            String token = System.getenv("GITHUB_TOKEN");
            if (token == null || token.isBlank())
                return "Error: GITHUB_TOKEN environment variable not set.";

            String owner   = (String) args.get("owner");
            String repo    = (String) args.get("repo");
            String path    = (String) args.get("path");
            String content = (String) args.get("content");
            String message = (String) args.get("message");
            String branch  = (String) args.getOrDefault("branch", "main");

            String url = "https://api.github.com/repos/" + owner + "/" + repo
                       + "/contents/" + path;

            HttpClient client = HttpClient.newHttpClient();

            // Step 1 — check if file exists, get SHA if it does
            String existingSha = getExistingSha(client, url, token);

            // Step 2 — Base64 encode the content
            String encodedContent = Base64.getEncoder()
                    .encodeToString(content.getBytes());

            // Step 3 — build request body
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode body = mapper.createObjectNode();
            body.put("message", message);
            body.put("content", encodedContent);
            body.put("branch",  branch);

            // Only include SHA if updating existing file
            if (existingSha != null) {
                body.put("sha", existingSha);
            }

            // Step 4 — PUT request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept",        "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent",    "java-mcp-server")
                    .header("Content-Type",  "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 200 = updated, 201 = created
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                return "GitHub API error " + response.statusCode() + ": " + response.body();
            }

            JsonNode json       = mapper.readTree(response.body());
            String commitUrl    = json.path("commit").path("html_url").asText();
            String action       = (existingSha != null) ? "updated" : "created";

            return "File " + action + " successfully!"
                 + "\nPath: "   + path
                 + "\nBranch: " + branch
                 + "\nCommit: " + commitUrl;

        } catch (Exception e) {
            return "Error pushing file: " + e.getMessage();
        }
    }

    // Returns SHA if file exists, null if it doesn't
    private static String getExistingSha(HttpClient client, String url, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept",        "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent",    "java-mcp-server")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = mapper.readTree(response.body());
                return json.path("sha").asText();
            }

            // 404 = file doesn't exist yet — that's fine
            return null;

        } catch (Exception e) {
            return null;
        }
    }
}