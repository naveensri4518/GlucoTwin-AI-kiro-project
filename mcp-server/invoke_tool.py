"""Invoke get_prediction tool and print the result."""
import subprocess, json, sys, time

PROTO = "2024-11-05"
msgs = [
    json.dumps({"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":PROTO,"capabilities":{},"clientInfo":{"name":"t","version":"1"}}}),
    json.dumps({"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_prediction","arguments":{"patient_id":"P001"}}}),
]
server = subprocess.Popen([sys.executable,"mcp-server/glucotwin_mcp_server.py"],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
server.stdin.write("\n".join(msgs)+"\n"); server.stdin.flush()
time.sleep(3); server.terminate()
out,_ = server.communicate(timeout=5)
for line in out.splitlines():
    try:
        msg = json.loads(line)
        if msg.get("id")==2:
            content = msg["result"]["content"][0]["text"]
            print(json.dumps(json.loads(content), indent=2))
    except: pass
