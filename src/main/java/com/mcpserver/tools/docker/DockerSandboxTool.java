package com.mcpserver.tools.docker;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.McpJsonMapper;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DockerSandboxTool {

    private static final int TIMEOUT_SEC = 30;

    private record LangConfig(String image, String filename, List<String> runCmd) {}

    private static final Map<String, LangConfig> LANG = Map.of(
        "python",
            new LangConfig("python:3.11-alpine", "code.py",
                List.of("python", "/sandbox/code.py")),
        "javascript",
            new LangConfig("node:18-alpine", "code.js",
                List.of("node", "/sandbox/code.js")),
        "java",
            new LangConfig("eclipse-temurin:17-jdk-alpine", "Main.java",
                List.of("sh", "-c", "javac -d /tmp /sandbox/Main.java && java -cp /tmp Main"))
    );

    @SuppressWarnings("deprecation")
    public static McpServerFeatures.SyncToolSpecification build(McpJsonMapper jsonMapper) {

        McpSchema.Tool toolDef = McpSchema.Tool.builder()
                .name("run_code_sandbox")
                .description("Executes code in an isolated Docker container. Supports python, javascript, and java. No network access inside container.")
                .inputSchema(jsonMapper, """
                        {
                          "type": "object",
                          "properties": {
                            "language": {
                              "type": "string",
                              "enum": ["python", "javascript", "java"],
                              "description": "Programming language to run"
                            },
                            "code": {
                              "type": "string",
                              "description": "Source code to execute"
                            }
                          },
                          "required": ["language", "code"]
                        }
                        """)
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                toolDef,
                (exchange, arguments) -> {
                    String result = runInSandbox(arguments);
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(result)),
                            false
                    );
                }
        );
    }

    private static String runInSandbox(Map<String, Object> args) {
        String language = ((String) args.getOrDefault("language", "python")).toLowerCase().trim();
        String code     = (String) args.get("code");

        if (code == null || code.isBlank())
            return "Error: 'code' is required.";

        LangConfig cfg = LANG.get(language);
        if (cfg == null)
            return "Error: Unsupported language '" + language + "'. Supported: python, javascript, java.";

        File tempDir = null;
        try {
            // Create temp directory for this run
            tempDir = new File(System.getProperty("java.io.tmpdir"),
                               "sandbox-" + UUID.randomUUID());
            tempDir.mkdirs();

            // Write code to file
            Files.writeString(new File(tempDir, cfg.filename()).toPath(), code);

            // Build docker run command
            List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--network=none",
                "--memory=256m",
                "--cpus=0.5",
                "--read-only",
                "--tmpfs=/tmp:size=64m",
                "-v", tempDir.getAbsolutePath() + ":/sandbox:ro",
                cfg.image()
            ));
            cmd.addAll(cfg.runCmd());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true); // merge stderr into stdout
            Process proc = pb.start();

            String output   = new String(proc.getInputStream().readAllBytes());
            boolean finished = proc.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS);

            if (!finished) {
                proc.destroyForcibly();
                return "Error: Code execution timed out after " + TIMEOUT_SEC + " seconds.";
            }

            int exitCode = proc.exitValue();
            return "Exit code: " + exitCode + "\n\n" + output.trim();

        } catch (Exception e) {
            return "Sandbox error: " + e.getMessage();
        } finally {
            // Always clean up temp dir
            if (tempDir != null) deleteDir(tempDir);
        }
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}