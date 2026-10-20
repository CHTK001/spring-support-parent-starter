#!/usr/bin/env bash
# =====================================================================
# guacd (Apache Guacamole 守护进程) 一键安装脚本
#
# 用途: 为 spring-support-server-starter 远控网关部署协议代理服务,
#       支持 SSH / RDP / VNC 三种协议的统一渲染转发(监听 4822 端口)。
#
# 目标环境: Ubuntu 20.04/22.04/24.04 (需 root 或 sudo 权限)
# 部署方式: 优先 Docker; 无 Docker 时回退 apt 直装
# 用法:
#   sudo bash install-guacd.sh              # 默认端口 4822, docker 方式
#   sudo PORT=4823 bash install-guacd.sh    # 自定义端口
#   sudo MODE=apt bash install-guacd.sh     # 强制 apt 方式
#
# 安装后验证:
#   ss -tlnp | grep 4822        # 端口监听检查
#   docker logs -f guacd        # docker 方式查看日志
#   systemctl status guacd      # apt 方式查看状态
#
# @author CH
# @since 4.0.0.42
# =====================================================================
set -euo pipefail

# ---------------------------------------------------------------------
# 可配置参数(环境变量覆盖)
# ---------------------------------------------------------------------
PORT="${PORT:-4822}"                 # guacd 监听端口
MODE="${MODE:-auto}"                 # auto | docker | apt
IMAGE="${IMAGE:-guacamole/guacd:1.5.5}"
CONTAINER_NAME="${CONTAINER_NAME:-guacd}"

log() { echo -e "\033[32m[install-guacd]\033[0m $*"; }
err() { echo -e "\033[31m[install-guacd]\033[0m $*" >&2; }

# ---------------------------------------------------------------------
# 前置检查: 必须 root 运行
# ---------------------------------------------------------------------
if [[ $EUID -ne 0 ]]; then
  err "请使用 root 或 sudo 运行本脚本"
  exit 1
fi

has_docker() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

# ---------------------------------------------------------------------
# 方式一: Docker 部署(推荐)
# ---------------------------------------------------------------------
install_by_docker() {
  log "使用 Docker 部署 guacd (${IMAGE})..."

  # 清理同名旧容器
  if docker ps -a --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
    log "发现旧容器 ${CONTAINER_NAME}, 正在移除..."
    docker rm -f "${CONTAINER_NAME}" >/dev/null
  fi

  docker run -d \
    --name "${CONTAINER_NAME}" \
    --restart unless-stopped \
    -p "127.0.0.1:${PORT}:4822" \
    "${IMAGE}"

  log "容器已启动, 配置 systemd 开机自启兜底..."
  install_docker_systemd_unit
}

# systemd 单元: 即使 docker 服务重启也保证容器拉起(restart 策略已覆盖, 双保险)
install_docker_systemd_unit() {
  local unit="/etc/systemd/system/guacd-container.service"
  cat > "${unit}" <<EOF
[Unit]
Description=guacd container (Apache Guacamole daemon)
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/bin/docker start ${CONTAINER_NAME}
ExecStop=/usr/bin/docker stop ${CONTAINER_NAME}

[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload
  systemctl enable guacd-container.service >/dev/null 2>&1 || true
}

# ---------------------------------------------------------------------
# 方式二: apt 直装(Ubuntu 源自带 guacamole-server)
# ---------------------------------------------------------------------
install_by_apt() {
  log "使用 apt 部署 guacamole-server..."
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq guacamole-server >/dev/null

  # 监听端口注入(guacd 默认读取 /etc/default/guacamole-server 的 GUACD_PORT)
  if [[ "${PORT}" != "4822" ]]; then
    sed -i "s/^#\?GUACD_PORT=.*/GUACD_PORT=${PORT}/" /etc/default/guacamole-server 2>/dev/null \
      || echo "GUACD_PORT=${PORT}" >> /etc/default/guacamole-server
  fi

  systemctl enable --now guacd 2>/dev/null \
    || systemctl enable --now guacamole-server 2>/dev/null \
    || { err "guacd 服务启动失败, 请手动执行: systemctl status guacd"; exit 1; }
}

# ---------------------------------------------------------------------
# 主流程: 按模式分发
# ---------------------------------------------------------------------
case "${MODE}" in
  docker)
    has_docker || { err "MODE=docker 但 Docker 不可用"; exit 1; }
    install_by_docker
    ;;
  apt)
    install_by_apt
    ;;
  auto)
    if has_docker; then
      install_by_docker
    else
      log "未检测到可用 Docker, 回退 apt 方式..."
      install_by_apt
    fi
    ;;
  *)
    err "未知 MODE: ${MODE} (可选 auto/docker/apt)"
    exit 1
    ;;
esac

# ---------------------------------------------------------------------
# 安装后健康检查(最多等待 15 秒)
# ---------------------------------------------------------------------
log "健康检查中..."
for i in $(seq 1 15); do
  if (echo >/dev/tcp/127.0.0.1/"${PORT}") >/dev/null 2>&1; then
    log "guacd 已就绪: 127.0.0.1:${PORT}"
    log "后端配置参考: plugin.server.support.guacd.host=<本机IP> plugin.server.support.guacd.port=${PORT}"
    exit 0
  fi
  sleep 1
done

err "端口 ${PORT} 未就绪, 请排查:"
err "  docker 方式: docker logs ${CONTAINER_NAME}"
err "  apt 方式:    journalctl -u guacd -n 50"
exit 1
