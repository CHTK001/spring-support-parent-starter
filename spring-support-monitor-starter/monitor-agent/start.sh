#!/bin/bash
cd "$(dirname "$0")"
exec java -jar agent.jar --spring.config.additional-location=file:./agent.yml "$@"
