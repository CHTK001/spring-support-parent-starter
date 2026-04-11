#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "请使用 root 执行该脚本"
  exit 1
fi

GUACAMOLE_VERSION="${GUACAMOLE_VERSION:-1.6.0}"
TOMCAT_VERSION="${TOMCAT_VERSION:-9.0.105}"
INSTALL_ROOT="${INSTALL_ROOT:-/opt/guacamole-host}"
DOWNLOAD_DIR="${INSTALL_ROOT}/downloads"
GUACAMOLE_HOME_DIR="${INSTALL_ROOT}/guacamole-home"
TOMCAT_HOME="${INSTALL_ROOT}/apache-tomcat-${TOMCAT_VERSION}"
HTTP_PORT="${GUACAMOLE_HTTP_PORT:-18088}"
GUACD_PORT="${GUACD_PORT:-4822}"
GUACD_CONTAINER_NAME="${GUACD_CONTAINER_NAME:-guacd}"
JSON_SECRET_KEY="${GUACAMOLE_JSON_SECRET_KEY:-}"
GUACAMOLE_BASE_URL="${GUACAMOLE_BASE_URL:-https://archive.apache.org/dist/guacamole/${GUACAMOLE_VERSION}/binary}"
TOMCAT_BASE_URL="${TOMCAT_BASE_URL:-https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin}"

mkdir -p "${DOWNLOAD_DIR}" "${GUACAMOLE_HOME_DIR}/extensions"

if [[ -z "${JSON_SECRET_KEY}" ]]; then
  if command -v openssl >/dev/null 2>&1; then
    JSON_SECRET_KEY="$(openssl rand -hex 16)"
  else
    JSON_SECRET_KEY="$(head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  fi
fi

download() {
  local url="$1"
  local target="$2"
  if [[ ! -f "${target}" ]]; then
    curl -fsSL "${url}" -o "${target}"
  fi
}

download \
  "${GUACAMOLE_BASE_URL}/guacamole-${GUACAMOLE_VERSION}.war" \
  "${DOWNLOAD_DIR}/guacamole-${GUACAMOLE_VERSION}.war"
download \
  "${GUACAMOLE_BASE_URL}/guacamole-auth-json-${GUACAMOLE_VERSION}.tar.gz" \
  "${DOWNLOAD_DIR}/guacamole-auth-json-${GUACAMOLE_VERSION}.tar.gz"
download \
  "${TOMCAT_BASE_URL}/apache-tomcat-${TOMCAT_VERSION}.tar.gz" \
  "${DOWNLOAD_DIR}/apache-tomcat-${TOMCAT_VERSION}.tar.gz"

rm -rf "${GUACAMOLE_HOME_DIR}/extensions/guacamole-auth-json"
mkdir -p "${GUACAMOLE_HOME_DIR}/extensions/guacamole-auth-json"
tar -xzf "${DOWNLOAD_DIR}/guacamole-auth-json-${GUACAMOLE_VERSION}.tar.gz" \
  -C "${GUACAMOLE_HOME_DIR}/extensions/guacamole-auth-json" --strip-components=1
cp -f \
  "${GUACAMOLE_HOME_DIR}/extensions/guacamole-auth-json/guacamole-auth-json-${GUACAMOLE_VERSION}.jar" \
  "${GUACAMOLE_HOME_DIR}/extensions/"

if [[ ! -d "${TOMCAT_HOME}" ]]; then
  tar -xzf "${DOWNLOAD_DIR}/apache-tomcat-${TOMCAT_VERSION}.tar.gz" -C "${INSTALL_ROOT}"
fi

cp -f "${DOWNLOAD_DIR}/guacamole-${GUACAMOLE_VERSION}.war" "${TOMCAT_HOME}/webapps/guacamole.war"
rm -rf "${TOMCAT_HOME}/webapps/guacamole"

cat > "${GUACAMOLE_HOME_DIR}/guacamole.properties" <<EOF
guacd-hostname: 127.0.0.1
guacd-port: ${GUACD_PORT}
extension-priority: json
json-secret-key: ${JSON_SECRET_KEY}
EOF

cat > "${TOMCAT_HOME}/bin/setenv.sh" <<EOF
#!/usr/bin/env bash
export GUACAMOLE_HOME="${GUACAMOLE_HOME_DIR}"
export CATALINA_OPTS="-Dguacamole.home=${GUACAMOLE_HOME_DIR} -Xms128m -Xmx512m -XX:+UseG1GC"
EOF
chmod +x "${TOMCAT_HOME}/bin/setenv.sh"

sed -i "0,/Connector port=\"8080\"/s//Connector port=\"${HTTP_PORT}\"/" "${TOMCAT_HOME}/conf/server.xml"
sed -i "0,/Server port=\"8005\"/s//Server port=\"-1\"/" "${TOMCAT_HOME}/conf/server.xml"

docker rm -f "${GUACD_CONTAINER_NAME}" >/dev/null 2>&1 || true
docker run -d \
  --restart unless-stopped \
  --name "${GUACD_CONTAINER_NAME}" \
  -p "127.0.0.1:${GUACD_PORT}:4822" \
  "guacamole/guacd:${GUACAMOLE_VERSION}" >/dev/null

cat > /etc/systemd/system/guacamole-host.service <<EOF
[Unit]
Description=Guacamole Host Tomcat
After=network.target docker.service
Requires=docker.service

[Service]
Type=forking
Environment=GUACAMOLE_HOME=${GUACAMOLE_HOME_DIR}
ExecStart=${TOMCAT_HOME}/bin/startup.sh
ExecStop=${TOMCAT_HOME}/bin/shutdown.sh
Restart=on-failure
RestartSec=5
User=root

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now guacamole-host.service

for _ in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:${HTTP_PORT}/guacamole/" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "Guacamole Host 部署完成"
echo "入口: http://$(hostname -I | awk '{print $1}'):${HTTP_PORT}/guacamole/"
echo "JSON Auth 密钥: ${JSON_SECRET_KEY}"
echo "请把相同密钥同步到后端 plugin.server.guacamole.json-secret-key"
