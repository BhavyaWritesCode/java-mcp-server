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

public class GitHubReadFileTool {

    @SuppressWarnings("deprecation")
    public static McpServerFeatures.SyncToolSpecification build(McpJsonMapper jsonMapper) {

        McpSchema.Tool toolDef = McpSchema.Tool.builder()
                .name("read_github_file")
                .description("Reads the content of a specific file from a GitHub repository.")
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
                              "description": "File path inside the repo e.g. src/main/java/Main.java"
                            }
                          },
                          "required": ["owner", "repo", "path"]
                        }
                        """)
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                toolDef,
                (exchange, arguments) -> {
                    String result = readFile(arguments);
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(result)),
                            false
                    );
                }
        );
    }

    private static String readFile(Map<String, Object> args) {
        try {
            String token = System.getenv("GITHUB_TOKEN");
            if (token == null || token.isBlank())
                return "Error: GITHUB_TOKEN environment variable not set.";

            String owner = (String) args.get("owner");
            String repo  = (String) args.get("repo");
            String path  = (String) args.get("path");

            String url = "https://api.github.com/repos/" + owner + "/" + repo
                       + "/contents/" + path;

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept",        "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent",    "java-mcp-server")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "GitHub API error " + response.statusCode() + ": " + response.body();
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode json = mapper.readTree(response.body());

            // GitHub returns content as Base64 — must decode
            String encodedContent = json.path("content").asText();
            String cleanEncoded   = encodedContent.replaceAll("\\s", ""); // strip newlines
            byte[] decodedBytes   = Base64.getDecoder().decode(cleanEncoded);
            String fileContent    = new String(decodedBytes);

            int sizeBytes = json.path("size").asInt();

            return "File: " + path + "\n"
                 + "Size: " + sizeBytes + " bytes\n\n"
                 + fileContent;

        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}