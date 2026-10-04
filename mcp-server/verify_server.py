"""Quick verification script: starts the MCP server and confirms tools/list responds."""
import subprocess
import json
import sys
import time

PROTO_VERSION = "2024-11-05"

init_msg = json.dumps({
    "jsonrpc": "2.0", "id": 1, "method": "initialize",
    "params": {
        "protocolVersion": PROTO_VERSION,
        "capabilities": {},
        "clientInfo": {"name": "kiro-verify", "version": "1.0"},
    },
}) + "\n"

list_msg = json.dumps({
    "jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {},
}) + "\n"

server = subprocess.Popen(
    [sys.executable, "mcp-server/glucotwin_mcp_server.py"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
)

server.stdin.write(init_msg + list_msg)
server.stdin.flush()
time.sleep(3)
server.terminate()

out, err = server.communicate(timeout=5)

print("=== RAW STDOUT ===")
print(out[:4000] if out else "(empty)")

if err:
    print("=== STDERR (first 400 chars) ===")
    print(err[:400])

# Parse each JSON-RPC response line
lines = [l.strip() for l in out.splitlines() if l.strip().startswith("{")]
tool_names = []
for line in lines:
    try:
        msg = json.loads(line)
        if msg.get("id") == 2 and "result" in msg:
            tools = msg["result"].get("tools", [])
            tool_names = [t["name"] for t in tools]
            print("\n=== TOOLS FOUND ===")
            for t in tools:
                print(f"  - {t['name']}: {t['description'][:80]}...")
    except json.JSONDecodeError:
        pass

if tool_names:
    print(f"\n✓ Server started and exposed {len(tool_names)} tools: {tool_names}")
else:
    print("\n⚠ Could not parse tools/list response from stdout (server may use different framing)")
    print("Server process exit code:", server.returncode)
