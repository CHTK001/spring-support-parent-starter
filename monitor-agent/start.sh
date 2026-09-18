#!/bin/bash
cd "$(dirname "$0")"
nohup java -jar monitor-agent.jar --spring.config.location=config/agent.yml > agent.log 2>&1 &
echo "Agent started, PID: $!"
