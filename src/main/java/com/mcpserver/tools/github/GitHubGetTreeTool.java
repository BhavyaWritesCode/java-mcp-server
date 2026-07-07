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

public class GitHubGetTreeTool {

    @SuppressWarnings("deprecation")
    public static McpServerFeatures.SyncToolSpecification build(McpJsonMapper jsonMapper) {

        McpSchema.Tool toolDef = McpSchema.Tool.builder()
                .name("get_repo_tree")
                .description("Fetches the full file and folder tree of a GitHub repository.")
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
                            }
                          },
                          "required": ["owner", "repo"]
                        }
                        """)
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                toolDef,
                (exchange, arguments) -> {
                    String result = getTree(arguments);
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(result)),
                            false
                    );
                }
        );
    }

    private static String getTree(Map<String, Object> args) {
        try {
            String token = System.getenv("GITHUB_TOKEN");
            if (token == null || token.isBlank())
                return "Error: GITHUB_TOKEN environment variable not set.";

            String owner = (String) args.get("owner");
            String repo  = (String) args.get("repo");

            String url = "https://api.github.com/repos/" + owner + "/" + repo
                       + "/git/trees/HEAD?recursive=1";

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
            JsonNode tree = json.path("tree");

            if (!tree.isArray() || tree.isEmpty()) {
                return "No files found in " + owner + "/" + repo;
            }

            StringBuilder out = new StringBuilder();
            out.append("File tree for ").append(owner).append("/").append(repo).append(":\n\n");

            int count = 0;
            for (JsonNode item : tree) {
                String type = item.path("type").asText(); // "blob" or "tree"
                String path = item.path("path").asText();
                out.append("[").append(type).append("] ").append(path).append("\n");
                count++;
            }

            out.append("\nTotal: ").append(count).append(" items");
            return out.toString();

        } catch (Exception e) {
            return "Error fetching repo tree: " + e.getMessage();
        }
    }
}