#!/bin/bash
# Agent ?????? (Linux)
# ??: ./deploy.sh <server-url> <host-id> <agent-key>
set -e
cd "$(dirname "$0")"
SERVER_URL="${1:-http://124.221.230.112:8081}"
HOST_ID="${2:-1}"
AGENT_KEY="${3:-unknown}"

cat > agent.yml <<EOF
server:
  url: ${SERVER_URL}
host:
  id: ${HOST_ID}
  key: ${AGENT_KEY}
agent:
  heartbeat: 10s
EOF

# ?? systemd ??
sudo tee /etc/systemd/system/monitor-agent.service > /dev/null <<SVCEOF
[Unit]
Description=Monitor Agent
After=network.target
[Service]
WorkingDirectory=$(pwd)
ExecStart=$(which java) -jar agent.jar --spring.config.additional-location=file:./agent.yml
Restart=always
RestartSec=5
[Install]
WantedBy=multi-user.target
SVCEOF

sudo systemctl daemon-reload
sudo systemctl enable monitor-agent
sudo systemctl restart monitor-agent
echo "Agent deployed and started. Check: sudo systemctl status monitor-agent"
