package com.mcpserver.tools.github;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class GitHubCreateRepoTool {

    @SuppressWarnings("deprecation")
    public static McpServerFeatures.SyncToolSpecification build(McpJsonMapper jsonMapper) {

        McpSchema.Tool toolDef = McpSchema.Tool.builder()
                .name("create_github_repo")
                .description("Creates a new GitHub repository under the authenticated user's account.")
                .inputSchema(jsonMapper, """
                        {
                          "type": "object",
                          "properties": {
                            "name": {
                              "type": "string",
                              "description": "Repository name"
                            },
                            "description": {
                              "type": "string",
                              "description": "Short description of the repository"
                            },
                            "private": {
                              "type": "boolean",
                              "description": "Whether the repo is private. Defaults to false."
                            },
                            "auto_init": {
                              "type": "boolean",
                              "description": "Initialize with a README. Defaults to true."
                            }
                          },
                          "required": ["name"]
                        }
                        """)
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                toolDef,
                (exchange, arguments) -> {
                    String result = createRepo(arguments);
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(result)),
                            false
                    );
                }
        );
    }

    private static String createRepo(Map<String, Object> args) {
        try {
            String token = System.getenv("GITHUB_TOKEN");
            if (token == null || token.isBlank())
                return "Error: GITHUB_TOKEN environment variable not set.";

            String name        = (String) args.get("name");
            String description = (String) args.getOrDefault("description", "");
            boolean isPrivate  = (boolean) args.getOrDefault("private", false);
            boolean autoInit   = (boolean) args.getOrDefault("auto_init", true);

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode body = mapper.createObjectNode();
            body.put("name",        name);
            body.put("description", description);
            body.put("private",     isPrivate);
            body.put("auto_init",   autoInit);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/user/repos"))
                    .header("Accept",        "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent",    "java-mcp-server")
                    .header("Content-Type",  "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                return "GitHub API error " + response.statusCode() + ": " + response.body();
            }

            JsonNode json = mapper.readTree(response.body());
            return "Repo created successfully!"
                + "\nURL: "       + json.path("html_url").asText()
                + "\nFull name: " + json.path("full_name").asText()
                + "\nPrivate: "   + json.path("private").asBoolean();

        } catch (Exception e) {
            return "Error creating repo: " + e.getMessage();
        }
    }
}