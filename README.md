# java-mcp-server
 
A Java-based MCP (Model Context Protocol) server that connects Claude Desktop to GitHub, MySQL, and isolated Docker code execution environments.
 
Built with the MCP SDK 0.17.1 and stdio transport.
 
---
 
## Tools
 
| Tool | Description |
|---|---|
| `analyze_github_repo` | Fetch metadata (stars, forks, language) of any public GitHub repo |
| `create_github_repo` | Create a new GitHub repository under your account |
| `get_repo_tree` | List all files and folders in a GitHub repository |
| `read_github_file` | Read the content of any file inside a GitHub repository |
| `push_github_file` | Create or update a file in a GitHub repository |
| `execute_sql_query` | Run read-only SELECT queries against your MySQL database |
| `execute_sql_admin` | Run admin operations — CREATE DATABASE, CREATE TABLE, INSERT, ALTER |
| `run_code_sandbox` | Execute Python, JavaScript, or Java code in an isolated Docker container |
 
All tool calls are automatically logged to a `tool_call_logs` table in your MySQL database.
 
---
 
## Prerequisites
 
- [Docker](https://www.docker.com/products/docker-desktop) installed and running
- A MySQL database (local or remote)
- A GitHub Personal Access Token with `repo` scope
---
 
## Quick Start
 
### 1. Pull the image
 
```bash
docker pull bhavya06s/java-mcp-server
```
 
### 2. Pull sandbox images (required for code execution)
 
```bash
docker pull python:3.11-alpine
docker pull node:18-alpine
docker pull eclipse-temurin:17-jdk-alpine
```
 
### 3. Set up MySQL
 
Run this once in your MySQL database:
 
```sql
CREATE TABLE IF NOT EXISTS tool_call_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_name   VARCHAR(100)  NOT NULL,
    input_json  TEXT,
    output_json TEXT,
    status      ENUM('SUCCESS', 'ERROR') NOT NULL,
    error_msg   TEXT,
    duration_ms BIGINT,
    called_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tool (tool_name),
    INDEX idx_ts   (called_at)
);
```
 
### 4. Configure Claude Desktop
 
Add the following to your `claude_desktop_config.json`:
 
**MacOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`  
**Windows:** `C:\Users\<yourname>\AppData\Roaming\Claude\claude_desktop_config.json`
 
```json
{
  "mcpServers": {
    "java-mcp-server": {
      "command": "docker",
      "args": [
        "run", "--rm", "-i",
        "-e", "DB_URL",
        "-e", "DB_USER",
        "-e", "DB_PASSWORD",
        "-e", "GITHUB_TOKEN",
        "-v", "/var/run/docker.sock:/var/run/docker.sock",
        "bhavya06s/java-mcp-server"
      ],
      "env": {
        "DB_URL": "jdbc:mysql://host.docker.internal:3306/yourdb",
        "DB_USER": "yourusername",
        "DB_PASSWORD": "yourpassword",
        "GITHUB_TOKEN": "your_github_token"
      }
    }
  }
}
```
 
> **Note:** Use `host.docker.internal` instead of `localhost` in `DB_URL` when connecting to a local MySQL instance from inside a Docker container.
 
### 5. Restart Claude Desktop
 
Fully quit and reopen Claude Desktop. The tools will be available immediately.
 
---
 
## Usage Examples
 
**GitHub**
```
Analyze the GitHub repo torvalds/linux
Create a repo called my-new-project with a README
Get the file tree of BhavyaWritesCode/java-mcp-server
Read the file src/main/java/com/mcpserver/McpServerApp.java from BhavyaWritesCode/java-mcp-server
Push a file called notes.md to my-new-project with content "Hello World"
```
 
**SQL**
```
Run this SQL: SELECT * FROM review_sessions LIMIT 5
Create a database called myapp with a users table containing id, name, and email columns
Insert a test row into the users table
```
 
**Code Sandbox**
```
Use the run_code_sandbox tool to run this Python code: print(sum(range(1, 101)))
Run this JavaScript: console.log([1,2,3].map(x => x * 2))
Run this Java: public class Main { public static void main(String[] args) { System.out.println("Hello!"); } }
```
 
---
 
## Tool Call Logging
 
Every tool call is automatically logged to the `tool_call_logs` table:
 
```sql
SELECT tool_name, status, duration_ms, called_at
FROM tool_call_logs
ORDER BY called_at DESC
LIMIT 10;
```
 
---
 
## Security
 
The Docker code sandbox runs with the following restrictions:
- `--network=none` — no internet access from inside the container
- `--memory=256m` — memory capped at 256MB
- `--cpus=0.5` — CPU capped at 0.5 cores
- `--read-only` — container filesystem is read-only
- 30 second hard timeout — runaway code is killed automatically
Destructive SQL operations (`DROP`, `DELETE`, `TRUNCATE`) are always blocked.
 
---
 
## Building from Source
 
```bash
git clone https://github.com/BhavyaWritesCode/java-mcp-server.git
cd java-mcp-server
mvn clean package
```
 
Run locally:
```bash
java -jar target/java-mcp-server-1.0-SNAPSHOT.jar
```
 
---
 
## Tech Stack
 
- Java 23
- MCP SDK 0.17.1
- MySQL (via JDBC)
- Docker (sandbox execution)
- GitHub REST API
- Jackson (JSON)
- Maven Shade Plugin (fat JAR)
---