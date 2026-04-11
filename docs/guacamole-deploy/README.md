# Guacamole 部署说明

当前测试机 `172.16.0.40 / 8.139.4.229` 不适合继续走“Guacamole Web + 数据库”全 Docker 方案:

- `guacamole/guacd` 容器可正常启动
- `guacamole/guacamole` JVM 容器在 `CentOS 7.9 + Docker 20.10.0` 下会报 `Cannot create VM thread`
- `postgres` / `mysql` 官方镜像在当前 Docker 运行时也存在初始化阻塞

结论:

- 不一定要 `MySQL`
- 当前测试环境的推荐落地方式是 `宿主机 Tomcat + Guacamole WAR + JSON Auth + guacd Docker`
- 这样可以绕开数据库依赖，也绕开 JVM 容器问题

## 推荐方案

1. 启动 `guacd` 容器
2. 在宿主机安装 `Tomcat`
3. 部署 `guacamole.war`
4. 安装 `guacamole-auth-json`
5. 在后端配置 `plugin.server.guacamole.auth-mode=json`
6. 在后端配置和网关一致的 `plugin.server.guacamole.json-secret-key`

## 一键脚本

```bash
chmod +x bootstrap-guacamole-host.sh
./bootstrap-guacamole-host.sh
```

默认参数:

- `GUACAMOLE_VERSION=1.6.0`
- `TOMCAT_VERSION=9.0.105`
- `GUACAMOLE_HTTP_PORT=18088`
- `GUACD_PORT=4822`
- `INSTALL_ROOT=/opt/guacamole-host`

执行完成后默认入口:

```text
http://HOST:18088/guacamole/
```

## 后端配置示例

```yaml
plugin:
  server:
    remote-gateway:
      enable: true
      default-provider: guacamole
    guacamole:
      enable: true
      gateway-url: http://172.16.0.40:18088/guacamole
      auth-mode: json
      json-secret-key: 0123456789abcdef0123456789abcdef
      json-expires-seconds: 300
```

说明:

- `json-secret-key` 必须和网关 `guacamole.properties` 中的 `json-secret-key` 一致
- JSON Auth 模式下，后端会直接生成 `?data=` 入口，前端 iframe 不需要再预创建数据库连接
- 如果要继续兼容旧链路，可以保留 `auth-mode=connection`

## 独立验收

仓库里附带了一个本地 PowerShell 脚本，可在不启动业务后端的情况下直接生成 JSON Auth 验证地址:

```powershell
.\generate-json-auth-url.ps1 `
  -Gateway "http://172.16.0.40:18088/guacamole" `
  -Secret "0123456789abcdef0123456789abcdef" `
  -HostName "172.16.0.40" `
  -Port 22 `
  -Protocol "ssh" `
  -Username "root" `
  -Password "******"
```

输出结果可以直接丢给浏览器或 Playwright，用来验证网关链路是否打通。

## 保留文件

- `docker-compose.yml`
- `bootstrap-guacamole.sh`

这两个文件只保留作阻塞环境的历史记录，不再作为当前测试机的主部署方案。

## 参考

- https://guacamole.apache.org/doc/gug/json-auth.html
- https://guacamole.apache.org/doc/gug/installing-guacamole.html
