#!/usr/bin/env bash
set -u
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
REPO_ROOT="$(cd "${MODULE_ROOT}/.." && pwd)"

MODULE_NAME="${MODULE_NAME:-spring-support-report-client-starter}"
MAVEN_BIN="${MAVEN_BIN:-mvn}"
MAVEN_EXTRA_ARGS="${MAVEN_EXTRA_ARGS:-}"
MATRIX_MODE="${MATRIX_MODE:-mock}"
JAVA_BIN="${JAVA_BIN:-java}"
JAVAC_BIN="${JAVAC_BIN:-javac}"
JAR_BIN="${JAR_BIN:-jar}"
LOG_DIR="${LOG_DIR:-${MODULE_ROOT}/target/script-matrix}"
WORK_ROOT="${WORK_ROOT:-${MODULE_ROOT}/target/mock-script-matrix}"

mkdir -p "${LOG_DIR}"
cd "${REPO_ROOT}"

pass_count=0
fail_count=0
total_count=0

report_result() {
  local scenario_id="$1"
  local status="$2"
  local detail="$3"
  local log_file="$4"

  echo "${scenario_id}|${status}|${detail}|${log_file}"
  if [ "${status}" = "PASS" ]; then
    pass_count=$((pass_count + 1))
  else
    fail_count=$((fail_count + 1))
  fi
  total_count=$((total_count + 1))
}

run_scenario() {
  local scenario_id="$1"
  local runner_name="$2"
  local log_file="${LOG_DIR}/${scenario_id}.log"

  if "${runner_name}" >"${log_file}" 2>&1; then
    report_result "${scenario_id}" "PASS" "${runner_name}" "${log_file}"
  else
    report_result "${scenario_id}" "FAIL" "${runner_name}" "${log_file}"
  fi
}

run_junit_matrix() {
  local scenarios
  scenarios=$(cat <<'EOF'
NODE_CONTROL_LOCALHOST_PATH|NodeControlHandlerTest#shouldGenerateWatchdogScriptUsingLocalhostPathAndPort
NODE_CONTROL_TEMPLATE_MATRIX|NodeControlHandlerTest#shouldGenerateWindowsAndLinuxWatchdogTemplates
NODE_CONTROL_UNSUPPORTED_TOPIC|NodeControlHandlerTest#shouldRejectUnsupportedControlTopic
NODE_BACKUP_FULL_FLOW|NodeMaintenanceHandlerTest#shouldCreateListDownloadAndDeleteBackupsWithSensitiveValuesFiltered
NODE_UPGRADE_STATUS|NodeMaintenanceHandlerTest#shouldReportUpgradeStatusFromOverridePaths
NODE_UPGRADE_MISSING_PACKAGE|NodeMaintenanceHandlerTest#shouldFailWhenUpgradePackageDoesNotExist
NODE_UPGRADE_PACKAGE_LIST|NodeMaintenanceHandlerTest#shouldListJarZipAndTarGzPackages
NODE_UPGRADE_JAR_ROLLBACK|NodeMaintenanceHandlerTest#shouldExecuteJarUpgradeAndRollback
NODE_UPGRADE_ZIP_ROLLBACK|NodeMaintenanceHandlerTest#shouldExecuteZipUpgradeAndRollbackSeparatedResources
NODE_UPGRADE_TARGZ_ROLLBACK|NodeMaintenanceHandlerTest#shouldExecuteTarGzUpgradeAndRollbackSeparatedResources
NODE_RESTORE_PREVIEW_EXECUTE|NodeMaintenanceHandlerTest#shouldPreviewAndRestoreConfigurationBackup
EOF
)

  while IFS='|' read -r scenario_id test_name; do
    local log_file="${LOG_DIR}/${scenario_id}.log"
    local maven_args=()

    [ -n "${scenario_id}" ] || continue
    if [ -n "${MAVEN_EXTRA_ARGS}" ]; then
      read -r -a maven_args <<< "${MAVEN_EXTRA_ARGS}"
    fi

    if [ "${#maven_args[@]}" -gt 0 ]; then
      if "${MAVEN_BIN}" "${maven_args[@]}" -pl "${MODULE_NAME}" \
          -DskipTests=false \
          -Dmaven.test.skip=false \
          "-Dtest=${test_name}" \
          test >"${log_file}" 2>&1; then
        report_result "${scenario_id}" "PASS" "${test_name}" "${log_file}"
      else
        report_result "${scenario_id}" "FAIL" "${test_name}" "${log_file}"
      fi
    else
      if "${MAVEN_BIN}" -pl "${MODULE_NAME}" \
          -DskipTests=false \
          -Dmaven.test.skip=false \
          "-Dtest=${test_name}" \
          test >"${log_file}" 2>&1; then
        report_result "${scenario_id}" "PASS" "${test_name}" "${log_file}"
      else
        report_result "${scenario_id}" "FAIL" "${test_name}" "${log_file}"
      fi
    fi
  done <<< "${scenarios}"
}

create_mock_agent_source() {
  cat > "${WORK_ROOT}/src/MockAgent.java" <<'EOF'
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class MockAgent {
    public static void main(String[] args) throws Exception {
        int port = 18080;
        String node = "node";
        String healthPath = "/actuator/health";

        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--node=")) {
                node = arg.substring("--node=".length());
            } else if (arg.startsWith("--health-path=")) {
                healthPath = normalizePath(arg.substring("--health-path=".length()));
            }
        }

        String version = readVersion();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        String payload = "{\"status\":\"UP\",\"node\":\"" + escape(node) + "\",\"version\":\"" + escape(version) + "\"}";

        server.createContext(healthPath, exchange -> respond(exchange, payload));
        if (!"/health".equals(healthPath)) {
            server.createContext("/health", exchange -> respond(exchange, payload));
        }

        server.start();
        System.out.println("MOCK_AGENT_STARTED|PORT=" + port + "|PATH=" + healthPath + "|VERSION=" + version);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        new CountDownLatch(1).await();
    }

    private static String readVersion() throws IOException {
        try (InputStream inputStream = MockAgent.class.getResourceAsStream("/version.txt")) {
            if (inputStream == null) {
                return "unknown";
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    private static void respond(HttpExchange exchange, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static String normalizePath(String value) {
        if (value == null || value.isEmpty()) {
            return "/health";
        }
        String path = value.startsWith("/") ? value : "/" + value;
        return path.replaceAll("/{2,}", "/");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
EOF
}

create_mock_scripts() {
  cat > "${WORK_ROOT}/bin/start.sh" <<'EOF'
#!/usr/bin/env bash
set -u
set -o pipefail

SCRIPT_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME=""
PORT=""
NODE_NAME="node"
CONTEXT_PATH=""
ACTUATOR_BASE="/actuator"
JAVA_CMD="${JAVA_BIN:-java}"

while getopts ":d:p:n:c:a:j:" opt; do
  case "${opt}" in
    d) APP_HOME="${OPTARG}" ;;
    p) PORT="${OPTARG}" ;;
    n) NODE_NAME="${OPTARG}" ;;
    c) CONTEXT_PATH="${OPTARG}" ;;
    a) ACTUATOR_BASE="${OPTARG}" ;;
    j) JAVA_CMD="${OPTARG}" ;;
    *) echo "usage: start.sh -d <app_home> -p <port> [-n node] [-c context] [-a actuator] [-j java]" >&2; exit 2 ;;
  esac
done

[ -n "${APP_HOME}" ] || { echo "missing app home" >&2; exit 2; }
[ -n "${PORT}" ] || { echo "missing port" >&2; exit 2; }

CURRENT_DIR="${APP_HOME}/current"
RUN_DIR="${APP_HOME}/run"
LOG_DIR="${APP_HOME}/logs"
PID_FILE="${RUN_DIR}/app.pid"
ENV_FILE="${RUN_DIR}/app.env"

mkdir -p "${RUN_DIR}" "${LOG_DIR}"
CONTEXT_PATH="${CONTEXT_PATH#/}"
ACTUATOR_BASE="${ACTUATOR_BASE#/}"
if [ -n "${CONTEXT_PATH}" ]; then
  CONTEXT_PATH="/${CONTEXT_PATH}"
fi
if [ -n "${ACTUATOR_BASE}" ]; then
  ACTUATOR_BASE="/${ACTUATOR_BASE}"
fi
HEALTH_PATH="$(printf '%s%s/health' "${CONTEXT_PATH}" "${ACTUATOR_BASE}" | sed 's#//*#/#g')"
HEALTH_URL="http://127.0.0.1:${PORT}${HEALTH_PATH}"

if [ -f "${PID_FILE}" ] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
  echo "already running" >&2
  exit 1
fi

{
  printf 'PORT=%q\n' "${PORT}"
  printf 'NODE_NAME=%q\n' "${NODE_NAME}"
  printf 'CONTEXT_PATH=%q\n' "${CONTEXT_PATH}"
  printf 'ACTUATOR_BASE=%q\n' "${ACTUATOR_BASE}"
  printf 'JAVA_CMD=%q\n' "${JAVA_CMD}"
  printf 'APP_HOME=%q\n' "${APP_HOME}"
} > "${ENV_FILE}"

nohup "${JAVA_CMD}" -jar "${CURRENT_DIR}/app.jar" \
  "--port=${PORT}" \
  "--node=${NODE_NAME}" \
  "--health-path=${HEALTH_PATH}" \
  > "${LOG_DIR}/app.log" 2>&1 &

PID=$!
echo "${PID}" > "${PID_FILE}"

for _ in $(seq 1 40); do
  if curl -fsS "${HEALTH_URL}" >/dev/null 2>&1; then
    echo "STARTED|PID=${PID}|URL=${HEALTH_URL}"
    exit 0
  fi
  sleep 0.25
done

echo "START_TIMEOUT|PID=${PID}|URL=${HEALTH_URL}" >&2
exit 1
EOF

  cat > "${WORK_ROOT}/bin/status.sh" <<'EOF'
#!/usr/bin/env bash
set -u
set -o pipefail

APP_HOME=""
while getopts ":d:" opt; do
  case "${opt}" in
    d) APP_HOME="${OPTARG}" ;;
    *) echo "usage: status.sh -d <app_home>" >&2; exit 2 ;;
  esac
done

[ -n "${APP_HOME}" ] || { echo "missing app home" >&2; exit 2; }

PID_FILE="${APP_HOME}/run/app.pid"
ENV_FILE="${APP_HOME}/run/app.env"
if [ -f "${ENV_FILE}" ]; then
  # shellcheck disable=SC1090
  . "${ENV_FILE}"
fi

if [ -f "${PID_FILE}" ] && kill -0 "$(cat "${PID_FILE}")" 2>/dev/null; then
  echo "STATUS=RUNNING|PID=$(cat "${PID_FILE}")|URL=http://127.0.0.1:${PORT}${CONTEXT_PATH}${ACTUATOR_BASE}/health"
else
  echo "STATUS=STOPPED"
fi
EOF

  cat > "${WORK_ROOT}/bin/health.sh" <<'EOF'
#!/usr/bin/env bash
set -u
set -o pipefail

APP_HOME=""
while getopts ":d:" opt; do
  case "${opt}" in
    d) APP_HOME="${OPTARG}" ;;
    *) echo "usage: health.sh -d <app_home>" >&2; exit 2 ;;
  esac
done

[ -n "${APP_HOME}" ] || { echo "missing app home" >&2; exit 2; }

ENV_FILE="${APP_HOME}/run/app.env"
[ -f "${ENV_FILE}" ] || { echo "missing env file" >&2; exit 1; }

# shellcheck disable=SC1090
. "${ENV_FILE}"
curl -fsS "http://127.0.0.1:${PORT}${CONTEXT_PATH}${ACTUATOR_BASE}/health"
EOF

  cat > "${WORK_ROOT}/bin/stop.sh" <<'EOF'
#!/usr/bin/env bash
set -u
set -o pipefail

APP_HOME=""
while getopts ":d:" opt; do
  case "${opt}" in
    d) APP_HOME="${OPTARG}" ;;
    *) echo "usage: stop.sh -d <app_home>" >&2; exit 2 ;;
  esac
done

[ -n "${APP_HOME}" ] || { echo "missing app home" >&2; exit 2; }

PID_FILE="${APP_HOME}/run/app.pid"
if [ ! -f "${PID_FILE}" ]; then
  echo "STOPPED"
  exit 0
fi

PID="$(cat "${PID_FILE}")"
if kill -0 "${PID}" 2>/dev/null; then
  kill "${PID}" 2>/dev/null || true
  for _ in $(seq 1 40); do
    if ! kill -0 "${PID}" 2>/dev/null; then
      break
    fi
    sleep 0.25
  done
fi

rm -f "${PID_FILE}"
echo "STOPPED|PID=${PID}"
EOF

  cat > "${WORK_ROOT}/bin/restart.sh" <<'EOF'
#!/usr/bin/env bash
set -u
set -o pipefail

SCRIPT_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME=""
while getopts ":d:" opt; do
  case "${opt}" in
    d) APP_HOME="${OPTARG}" ;;
    *) echo "usage: restart.sh -d <app_home>" >&2; exit 2 ;;
  esac
done

[ -n "${APP_HOME}" ] || { echo "missing app home" >&2; exit 2; }

ENV_FILE="${APP_HOME}/run/app.env"
[ -f "${ENV_FILE}" ] || { echo "missing env file" >&2; exit 1; }

# shellcheck disable=SC1090
. "${ENV_FILE}"
"${SCRIPT_HOME}/stop.sh" -d "${APP_HOME}" >/dev/null 2>&1 || true
"${SCRIPT_HOME}/start.sh" -d "${APP_HOME}" -p "${PORT}" -n "${NODE_NAME}" -c "${CONTEXT_PATH}" -a "${ACTUATOR_BASE}" -j "${JAVA_CMD}"
EOF

  cat > "${WORK_ROOT}/bin/upgrade.sh" <<'EOF'
#!/usr/bin/env bash
set -u
set -o pipefail

APP_HOME=""
PACKAGE_FILE=""
JAR_CMD="${JAR_BIN:-jar}"

while getopts ":d:f:" opt; do
  case "${opt}" in
    d) APP_HOME="${OPTARG}" ;;
    f) PACKAGE_FILE="${OPTARG}" ;;
    *) echo "usage: upgrade.sh -d <app_home> -f <package_file>" >&2; exit 2 ;;
  esac
done

[ -n "${APP_HOME}" ] || { echo "missing app home" >&2; exit 2; }
[ -n "${PACKAGE_FILE}" ] || { echo "missing package file" >&2; exit 2; }
[ -f "${PACKAGE_FILE}" ] || { echo "missing package file" >&2; exit 1; }

CURRENT_DIR="${APP_HOME}/current"
ROLLBACK_ROOT="${APP_HOME}/rollback"
RUN_DIR="${APP_HOME}/run"
ROLLBACK_ID="rollback_$(date +%Y%m%d%H%M%S)"
ROLLBACK_DIR="${ROLLBACK_ROOT}/${ROLLBACK_ID}"
TMP_DIR="${APP_HOME}/tmp/upgrade"
PACKAGE_NAME="$(basename "${PACKAGE_FILE}")"

mkdir -p "${ROLLBACK_DIR}" "${RUN_DIR}"
rm -rf "${TMP_DIR}"
mkdir -p "${TMP_DIR}"
cp -R "${CURRENT_DIR}/." "${ROLLBACK_DIR}/"

case "${PACKAGE_NAME}" in
  *.tar.gz|*.tgz)
    tar -xzf "${PACKAGE_FILE}" -C "${TMP_DIR}"
    PACKAGE_TYPE="tar.gz"
    ;;
  *.zip)
    (cd "${TMP_DIR}" && "${JAR_CMD}" xf "${PACKAGE_FILE}")
    PACKAGE_TYPE="zip"
    ;;
  *.jar)
    cp "${PACKAGE_FILE}" "${CURRENT_DIR}/app.jar"
    PACKAGE_TYPE="jar"
    ;;
  *)
    echo "unsupported package" >&2
    exit 1
    ;;
esac

if [ "${PACKAGE_TYPE}" != "jar" ]; then
  [ -f "${TMP_DIR}/app.jar" ] && cp "${TMP_DIR}/app.jar" "${CURRENT_DIR}/app.jar"
  [ -d "${TMP_DIR}/config" ] && mkdir -p "${CURRENT_DIR}/config" && cp -R "${TMP_DIR}/config/." "${CURRENT_DIR}/config/"
  [ -d "${TMP_DIR}/resources" ] && mkdir -p "${CURRENT_DIR}/resources" && cp -R "${TMP_DIR}/resources/." "${CURRENT_DIR}/resources/"
  [ -d "${TMP_DIR}/bin" ] && mkdir -p "${CURRENT_DIR}/bin" && cp -R "${TMP_DIR}/bin/." "${CURRENT_DIR}/bin/"
fi

echo "${ROLLBACK_ID}" > "${RUN_DIR}/latest.rollback"
echo "UPGRADE_OK|TYPE=${PACKAGE_TYPE}|ROLLBACK_ID=${ROLLBACK_ID}"
EOF

  cat > "${WORK_ROOT}/bin/rollback.sh" <<'EOF'
#!/usr/bin/env bash
set -u
set -o pipefail

APP_HOME=""
ROLLBACK_ID=""

while getopts ":d:r:" opt; do
  case "${opt}" in
    d) APP_HOME="${OPTARG}" ;;
    r) ROLLBACK_ID="${OPTARG}" ;;
    *) echo "usage: rollback.sh -d <app_home> [-r rollback_id]" >&2; exit 2 ;;
  esac
done

[ -n "${APP_HOME}" ] || { echo "missing app home" >&2; exit 2; }

LATEST_FILE="${APP_HOME}/run/latest.rollback"
if [ -z "${ROLLBACK_ID}" ]; then
  [ -f "${LATEST_FILE}" ] || { echo "missing rollback id" >&2; exit 1; }
  ROLLBACK_ID="$(cat "${LATEST_FILE}")"
fi

ROLLBACK_DIR="${APP_HOME}/rollback/${ROLLBACK_ID}"
[ -d "${ROLLBACK_DIR}" ] || { echo "missing rollback directory" >&2; exit 1; }

rm -rf "${APP_HOME}/current"
mkdir -p "${APP_HOME}/current"
cp -R "${ROLLBACK_DIR}/." "${APP_HOME}/current/"
echo "ROLLBACK_OK|ROLLBACK_ID=${ROLLBACK_ID}"
EOF

  cat > "${WORK_ROOT}/bin/gui.sh" <<'EOF'
#!/usr/bin/env bash
set -u
set -o pipefail

if [ "${1:-}" != "--self-test" ]; then
  echo "usage: gui.sh --self-test" >&2
  exit 2
fi

cat <<'MAP'
CTRL+R=restart
CTRL+S=stop
CTRL+H=health
CTRL+U=upgrade
SELF_TEST=PASS
MAP
EOF

  chmod +x "${WORK_ROOT}/bin/"*.sh
}

setup_mock_harness() {
  rm -rf "${WORK_ROOT}"
  mkdir -p "${WORK_ROOT}/src" "${WORK_ROOT}/classes" "${WORK_ROOT}/resources/v1" \
    "${WORK_ROOT}/resources/v2" "${WORK_ROOT}/artifacts" "${WORK_ROOT}/packages" \
    "${WORK_ROOT}/bin" "${WORK_ROOT}/scenarios"

  export JAVA_BIN JAVAC_BIN JAR_BIN

  create_mock_agent_source
  "${JAVAC_BIN}" -d "${WORK_ROOT}/classes" "${WORK_ROOT}/src/MockAgent.java"
  printf 'v1\n' > "${WORK_ROOT}/resources/v1/version.txt"
  printf 'v2\n' > "${WORK_ROOT}/resources/v2/version.txt"
  "${JAR_BIN}" --create --file "${WORK_ROOT}/artifacts/mock-agent-v1.jar" --main-class MockAgent \
    -C "${WORK_ROOT}/classes" . -C "${WORK_ROOT}/resources/v1" version.txt
  "${JAR_BIN}" --create --file "${WORK_ROOT}/artifacts/mock-agent-v2.jar" --main-class MockAgent \
    -C "${WORK_ROOT}/classes" . -C "${WORK_ROOT}/resources/v2" version.txt

  mkdir -p "${WORK_ROOT}/packages/zip-stage/config" "${WORK_ROOT}/packages/zip-stage/resources" \
    "${WORK_ROOT}/packages/tar-stage/config" "${WORK_ROOT}/packages/tar-stage/bin"
  cp "${WORK_ROOT}/artifacts/mock-agent-v2.jar" "${WORK_ROOT}/packages/zip-stage/app.jar"
  printf 'mode=zip-v2\n' > "${WORK_ROOT}/packages/zip-stage/config/application.properties"
  printf 'zip-banner=v2\n' > "${WORK_ROOT}/packages/zip-stage/resources/banner.txt"
  (cd "${WORK_ROOT}/packages/zip-stage" && "${JAR_BIN}" --create --file "${WORK_ROOT}/packages/mock-agent-v2.zip" -C "${WORK_ROOT}/packages/zip-stage" .)

  cp "${WORK_ROOT}/artifacts/mock-agent-v2.jar" "${WORK_ROOT}/packages/tar-stage/app.jar"
  printf 'mode=tar-v2\n' > "${WORK_ROOT}/packages/tar-stage/config/application.properties"
  printf '#!/usr/bin/env bash\necho tar-package\n' > "${WORK_ROOT}/packages/tar-stage/bin/extra.sh"
  chmod +x "${WORK_ROOT}/packages/tar-stage/bin/extra.sh"
  tar -czf "${WORK_ROOT}/packages/mock-agent-v2.tar.gz" -C "${WORK_ROOT}/packages/tar-stage" .

  create_mock_scripts
}

prepare_node_home() {
  local app_home="$1"

  rm -rf "${app_home}"
  mkdir -p "${app_home}/current/config" "${app_home}/run" "${app_home}/logs"
  cp "${WORK_ROOT}/artifacts/mock-agent-v1.jar" "${app_home}/current/app.jar"
  printf 'mode=baseline\n' > "${app_home}/current/config/application.properties"
}

cleanup_node() {
  local app_home="$1"

  if [ -x "${WORK_ROOT}/bin/stop.sh" ] && [ -d "${app_home}" ]; then
    "${WORK_ROOT}/bin/stop.sh" -d "${app_home}" >/dev/null 2>&1 || true
  fi
}

node_health_url() {
  local app_home="$1"
  local env_file="${app_home}/run/app.env"

  [ -f "${env_file}" ] || return 1
  # shellcheck disable=SC1090
  . "${env_file}"
  printf 'http://127.0.0.1:%s%s%s/health\n' "${PORT}" "${CONTEXT_PATH}" "${ACTUATOR_BASE}"
}

assert_file_contains() {
  local file_path="$1"
  local expected="$2"

  [ -f "${file_path}" ] || return 1
  grep -q "${expected}" "${file_path}"
}

assert_not_exists() {
  local file_path="$1"
  [ ! -e "${file_path}" ]
}

assert_health_contains() {
  local app_home="$1"
  local expected="$2"
  local output

  output="$("${WORK_ROOT}/bin/health.sh" -d "${app_home}")" || return 1
  printf '%s' "${output}" | grep -q "${expected}"
}

expect_command_failure() {
  local expected="$1"
  shift
  local output

  if output="$("$@" 2>&1)"; then
    printf '%s\n' "${output}"
    return 1
  fi

  printf '%s\n' "${output}"
  printf '%s' "${output}" | grep -q "${expected}"
}

scenario_cluster_localhost_path_start() (
  local scenario_root="${WORK_ROOT}/scenarios/cluster_start"
  local node_a="${scenario_root}/node-a"
  local node_b="${scenario_root}/node-b"

  trap 'cleanup_node "${node_a}"; cleanup_node "${node_b}"' EXIT

  prepare_node_home "${node_a}"
  prepare_node_home "${node_b}"
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19180 -n node-a -c /cluster-a -a /actuator-a -j "${JAVA_BIN}"
  "${WORK_ROOT}/bin/start.sh" -d "${node_b}" -p 19181 -n node-b -c /cluster-b -a /actuator-b -j "${JAVA_BIN}"
  assert_health_contains "${node_a}" '"node":"node-a"'
  assert_health_contains "${node_b}" '"node":"node-b"'
)

scenario_cluster_multi_status() (
  local scenario_root="${WORK_ROOT}/scenarios/cluster_status"
  local node_a="${scenario_root}/node-a"
  local node_b="${scenario_root}/node-b"

  trap 'cleanup_node "${node_a}"; cleanup_node "${node_b}"' EXIT

  prepare_node_home "${node_a}"
  prepare_node_home "${node_b}"
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19182 -n status-a -c /status-a -a /manage -j "${JAVA_BIN}"
  "${WORK_ROOT}/bin/start.sh" -d "${node_b}" -p 19183 -n status-b -c /status-b -a /manage -j "${JAVA_BIN}"
  "${WORK_ROOT}/bin/status.sh" -d "${node_a}" | grep -q 'STATUS=RUNNING'
  "${WORK_ROOT}/bin/status.sh" -d "${node_b}" | grep -q 'STATUS=RUNNING'
)

scenario_cluster_restart() (
  local scenario_root="${WORK_ROOT}/scenarios/cluster_restart"
  local node_a="${scenario_root}/node-a"
  local before_pid
  local after_pid

  trap 'cleanup_node "${node_a}"' EXIT

  prepare_node_home "${node_a}"
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19184 -n restart-a -c /restart-a -a /manage -j "${JAVA_BIN}"
  before_pid="$(cat "${node_a}/run/app.pid")"
  "${WORK_ROOT}/bin/restart.sh" -d "${node_a}"
  after_pid="$(cat "${node_a}/run/app.pid")"
  [ "${before_pid}" != "${after_pid}" ]
  assert_health_contains "${node_a}" '"version":"v1"'
)

scenario_cluster_stop() (
  local scenario_root="${WORK_ROOT}/scenarios/cluster_stop"
  local node_a="${scenario_root}/node-a"

  trap 'cleanup_node "${node_a}"' EXIT

  prepare_node_home "${node_a}"
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19185 -n stop-a -c /stop-a -a /manage -j "${JAVA_BIN}"
  "${WORK_ROOT}/bin/stop.sh" -d "${node_a}" | grep -q 'STOPPED'
  "${WORK_ROOT}/bin/status.sh" -d "${node_a}" | grep -q 'STATUS=STOPPED'
)

scenario_cluster_health() (
  local scenario_root="${WORK_ROOT}/scenarios/cluster_health"
  local node_a="${scenario_root}/node-a"

  trap 'cleanup_node "${node_a}"' EXIT

  prepare_node_home "${node_a}"
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19186 -n health-a -c /health-a -a /ops -j "${JAVA_BIN}"
  [ "$(node_health_url "${node_a}")" = "http://127.0.0.1:19186/health-a/ops/health" ]
  assert_health_contains "${node_a}" '"status":"UP"'
)

scenario_start_duplicate_fail() (
  local scenario_root="${WORK_ROOT}/scenarios/start_duplicate_fail"
  local node_a="${scenario_root}/node-a"

  trap 'cleanup_node "${node_a}"' EXIT

  prepare_node_home "${node_a}"
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19191 -n dup-a -c /dup-a -a /manage -j "${JAVA_BIN}"
  expect_command_failure 'already running' \
    "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19191 -n dup-a -c /dup-a -a /manage -j "${JAVA_BIN}"
)

scenario_start_missing_port_fail() (
  local scenario_root="${WORK_ROOT}/scenarios/start_missing_port_fail"
  local node_a="${scenario_root}/node-a"

  mkdir -p "${node_a}"
  expect_command_failure 'missing port' \
    "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -n miss-port -j "${JAVA_BIN}"
)

scenario_status_missing_home_fail() (
  expect_command_failure 'missing app home' "${WORK_ROOT}/bin/status.sh"
)

scenario_stop_missing_home_fail() (
  expect_command_failure 'missing app home' "${WORK_ROOT}/bin/stop.sh"
)

scenario_health_missing_env_fail() (
  local scenario_root="${WORK_ROOT}/scenarios/health_missing_env_fail"
  local node_a="${scenario_root}/node-a"

  prepare_node_home "${node_a}"
  expect_command_failure 'missing env file' "${WORK_ROOT}/bin/health.sh" -d "${node_a}"
)

scenario_restart_missing_env_fail() (
  local scenario_root="${WORK_ROOT}/scenarios/restart_missing_env_fail"
  local node_a="${scenario_root}/node-a"

  prepare_node_home "${node_a}"
  expect_command_failure 'missing env file' "${WORK_ROOT}/bin/restart.sh" -d "${node_a}"
)

scenario_upgrade_jar_rollback() (
  local scenario_root="${WORK_ROOT}/scenarios/upgrade_jar"
  local node_a="${scenario_root}/node-a"

  trap 'cleanup_node "${node_a}"' EXIT

  prepare_node_home "${node_a}"
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19187 -n jar-a -c /jar-a -a /manage -j "${JAVA_BIN}"
  assert_health_contains "${node_a}" '"version":"v1"'
  "${WORK_ROOT}/bin/stop.sh" -d "${node_a}" >/dev/null
  "${WORK_ROOT}/bin/upgrade.sh" -d "${node_a}" -f "${WORK_ROOT}/artifacts/mock-agent-v2.jar" | grep -q 'TYPE=jar'
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19187 -n jar-a -c /jar-a -a /manage -j "${JAVA_BIN}"
  assert_health_contains "${node_a}" '"version":"v2"'
  "${WORK_ROOT}/bin/stop.sh" -d "${node_a}" >/dev/null
  "${WORK_ROOT}/bin/rollback.sh" -d "${node_a}" | grep -q 'ROLLBACK_OK'
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19187 -n jar-a -c /jar-a -a /manage -j "${JAVA_BIN}"
  assert_health_contains "${node_a}" '"version":"v1"'
)

scenario_upgrade_zip_rollback() (
  local scenario_root="${WORK_ROOT}/scenarios/upgrade_zip"
  local node_a="${scenario_root}/node-a"

  trap 'cleanup_node "${node_a}"' EXIT

  prepare_node_home "${node_a}"
  "${WORK_ROOT}/bin/upgrade.sh" -d "${node_a}" -f "${WORK_ROOT}/packages/mock-agent-v2.zip" | grep -q 'TYPE=zip'
  assert_file_contains "${node_a}/current/config/application.properties" 'mode=zip-v2'
  assert_file_contains "${node_a}/current/resources/banner.txt" 'zip-banner=v2'
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19188 -n zip-a -c /zip-a -a /manage -j "${JAVA_BIN}"
  assert_health_contains "${node_a}" '"version":"v2"'
  "${WORK_ROOT}/bin/stop.sh" -d "${node_a}" >/dev/null
  "${WORK_ROOT}/bin/rollback.sh" -d "${node_a}" >/dev/null
  assert_file_contains "${node_a}/current/config/application.properties" 'mode=baseline'
  assert_not_exists "${node_a}/current/resources/banner.txt"
)

scenario_upgrade_targz_rollback() (
  local scenario_root="${WORK_ROOT}/scenarios/upgrade_targz"
  local node_a="${scenario_root}/node-a"

  trap 'cleanup_node "${node_a}"' EXIT

  prepare_node_home "${node_a}"
  "${WORK_ROOT}/bin/upgrade.sh" -d "${node_a}" -f "${WORK_ROOT}/packages/mock-agent-v2.tar.gz" | grep -q 'TYPE=tar.gz'
  assert_file_contains "${node_a}/current/config/application.properties" 'mode=tar-v2'
  [ -x "${node_a}/current/bin/extra.sh" ]
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19189 -n tar-a -c /tar-a -a /manage -j "${JAVA_BIN}"
  assert_health_contains "${node_a}" '"version":"v2"'
  "${WORK_ROOT}/bin/stop.sh" -d "${node_a}" >/dev/null
  "${WORK_ROOT}/bin/rollback.sh" -d "${node_a}" >/dev/null
  assert_file_contains "${node_a}/current/config/application.properties" 'mode=baseline'
  assert_not_exists "${node_a}/current/bin/extra.sh"
)

scenario_upgrade_missing_package_fail() (
  local scenario_root="${WORK_ROOT}/scenarios/upgrade_missing_package_fail"
  local node_a="${scenario_root}/node-a"

  prepare_node_home "${node_a}"
  expect_command_failure 'missing package file' \
    "${WORK_ROOT}/bin/upgrade.sh" -d "${node_a}" -f "${scenario_root}/missing.jar"
)

scenario_rollback_missing_id_fail() (
  local scenario_root="${WORK_ROOT}/scenarios/rollback_missing_id_fail"
  local node_a="${scenario_root}/node-a"

  prepare_node_home "${node_a}"
  expect_command_failure 'missing rollback id' "${WORK_ROOT}/bin/rollback.sh" -d "${node_a}"
)

scenario_start_custom_java() (
  local scenario_root="${WORK_ROOT}/scenarios/custom_java"
  local node_a="${scenario_root}/node-a"
  local pid

  trap 'cleanup_node "${node_a}"' EXIT

  prepare_node_home "${node_a}"
  "${WORK_ROOT}/bin/start.sh" -d "${node_a}" -p 19190 -n java-a -c /java-a -a /manage -j "${JAVA_BIN}"
  pid="$(cat "${node_a}/run/app.pid")"
  ps -p "${pid}" -o args= | grep -q "${JAVA_BIN}"
)

scenario_gui_self_test() (
  local output

  output="$("${WORK_ROOT}/bin/gui.sh" --self-test)"
  printf '%s' "${output}" | grep -q 'CTRL+R=restart'
  printf '%s' "${output}" | grep -q 'CTRL+S=stop'
  printf '%s' "${output}" | grep -q 'CTRL+H=health'
  printf '%s' "${output}" | grep -q 'SELF_TEST=PASS'
)

scenario_gui_invalid_arg_fail() (
  expect_command_failure 'usage: gui.sh --self-test' "${WORK_ROOT}/bin/gui.sh" --bad-arg
)

run_mock_matrix() {
  local scenarios
  scenarios=$(cat <<'EOF'
LOCALHOST_MULTI_NODE_START|scenario_cluster_localhost_path_start
LOCALHOST_MULTI_NODE_STATUS|scenario_cluster_multi_status
LOCALHOST_MULTI_NODE_RESTART|scenario_cluster_restart
LOCALHOST_MULTI_NODE_STOP|scenario_cluster_stop
LOCALHOST_MULTI_NODE_HEALTH|scenario_cluster_health
START_DUPLICATE_FAIL|scenario_start_duplicate_fail
START_MISSING_PORT_FAIL|scenario_start_missing_port_fail
STATUS_MISSING_HOME_FAIL|scenario_status_missing_home_fail
STOP_MISSING_HOME_FAIL|scenario_stop_missing_home_fail
HEALTH_MISSING_ENV_FAIL|scenario_health_missing_env_fail
RESTART_MISSING_ENV_FAIL|scenario_restart_missing_env_fail
UPGRADE_JAR_ROLLBACK|scenario_upgrade_jar_rollback
UPGRADE_ZIP_RESOURCE_ROLLBACK|scenario_upgrade_zip_rollback
UPGRADE_TARGZ_RESOURCE_ROLLBACK|scenario_upgrade_targz_rollback
UPGRADE_MISSING_PACKAGE_FAIL|scenario_upgrade_missing_package_fail
ROLLBACK_MISSING_ID_FAIL|scenario_rollback_missing_id_fail
START_CUSTOM_JAVA_PATH|scenario_start_custom_java
GUI_SELF_TEST|scenario_gui_self_test
GUI_INVALID_ARG_FAIL|scenario_gui_invalid_arg_fail
EOF
)

  setup_mock_harness

  while IFS='|' read -r scenario_id runner_name; do
    [ -n "${scenario_id}" ] || continue
    run_scenario "${scenario_id}" "${runner_name}"
  done <<< "${scenarios}"
}

case "${MATRIX_MODE}" in
  mock)
    run_mock_matrix
    ;;
  junit)
    run_junit_matrix
    ;;
  *)
    echo "Unsupported MATRIX_MODE: ${MATRIX_MODE}" >&2
    exit 2
    ;;
esac

echo "SUMMARY|MODE=${MATRIX_MODE}|TOTAL=${total_count}|PASS=${pass_count}|FAIL=${fail_count}|LOG_DIR=${LOG_DIR}"

if [ "${fail_count}" -gt 0 ]; then
  exit 1
fi
