#!/bin/bash
# Agent ?????? (macOS)
# ??: ./deploy-mac.sh <server-url> <host-id> <agent-key>
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

# ?? launchd ??
PLIST=~/Library/LaunchAgents/com.chua.monitor-agent.plist
cat > $PLIST <<PLISTEOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>com.chua.monitor-agent</string>
  <key>ProgramArguments</key>
  <array>
    <string>/usr/bin/java</string>
    <string>-jar</string>
    <string>$(pwd)/agent.jar</string>
    <string>--spring.config.additional-location=file:$(pwd)/agent.yml</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
</dict>
</plist>
PLISTEOF

launchctl load $PLIST
echo "Agent deployed. Check: launchctl list | grep monitor-agent"
