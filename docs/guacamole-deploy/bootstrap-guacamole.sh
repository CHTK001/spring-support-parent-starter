#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INIT_DIR="${BASE_DIR}/initdb"
ENV_FILE="${BASE_DIR}/.env"

mkdir -p "${INIT_DIR}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "缺少 ${ENV_FILE}"
  echo "先复制 .env.example 为 .env 并填入密码。"
  exit 1
fi

if [[ ! -f "${INIT_DIR}/initdb.sql" ]]; then
  echo "生成 Guacamole PostgreSQL 初始化脚本..."
  docker run --rm guacamole/guacamole:1.5.5 /opt/guacamole/bin/initdb.sh --postgresql > "${INIT_DIR}/initdb.sql"
fi

echo "启动 Guacamole Docker Stack..."
docker-compose --env-file "${ENV_FILE}" -f "${BASE_DIR}/docker-compose.yml" up -d

echo
echo "启动完成后可检查:"
echo "  docker-compose --env-file ${ENV_FILE} -f ${BASE_DIR}/docker-compose.yml ps"
echo "  docker logs guacamole --tail 100"
echo "  docker logs guacamole-postgres --tail 100"
echo
echo "默认登录用户名/密码通常为: guacadmin / guacadmin"
echo "首次登录后请立即修改密码。"
