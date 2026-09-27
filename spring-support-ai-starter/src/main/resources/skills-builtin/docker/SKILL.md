---
name: docker
icon: Box20Regular
title: Docker 容器化
description: Docker 容器化实践与最佳实践，覆盖 Dockerfile 编写、多阶段构建、镜像瘦身、.dockerignore、网络与数据卷、Docker Compose 编排。
version: 1.0.0
author: system
tags:
  - docker
  - devops
  - container
---

# Docker 容器化

## 一、Dockerfile 基础

- 使用**官方精简基础镜像**并固定版本（如 `eclipse-temurin:21-jre-alpine`），避免 `latest`。
- 一条指令一层镜像，把变化频率低的层（依赖安装）放前面，变化频繁的源码放后面，提升缓存命中。
- 每个镜像只跑一个主进程；以非 root 用户运行，不把密钥写进镜像。

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --chown=app:app target/app.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

## 二、多阶段构建

- 构建环境与运行环境分离，最终镜像只保留产物，显著减小体积。

```dockerfile
# 构建阶段
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:21-jre-alpine
COPY --from=build /src/target/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

## 三、.dockerignore

- 必须提供 `.dockerignore`，排除无关文件，减小构建上下文、避免缓存失效与泄露：

```
.git
target
node_modules
*.log
.env
README.md
```

## 四、镜像瘦身与优化

- 优先 alpine / distroless / slim 基础镜像；合并 `RUN` 指令并在同层清理包管理器缓存。
- 使用 BuildKit 缓存挂载加速依赖安装；给镜像打语义化标签，不依赖 `latest`。
- 定期扫描漏洞（`docker scout` / Trivy），及时更新基础镜像。

```dockerfile
RUN apk add --no-cache curl \
    && rm -rf /var/cache/apk/*
```

## 五、数据卷与网络

- 容器内数据随容器销毁，持久化数据必须挂载 **volume**；配置可挂载只读文件。
- 容器间通信用自定义 bridge 网络，服务间用容器名/服务名访问，不依赖 IP。
- 只暴露必要端口，端口映射遵循最小开放原则。

```bash
docker volume create pgdata
docker run -d -p 8080:8080 -v pgdata:/var/lib/postgresql/data postgres:16
```

## 六、Docker Compose

- 多服务编排使用 `docker-compose.yml`，声明依赖、环境、卷与网络；环境变量用 `.env` 注入。

```yaml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/app
    depends_on:
      - db
    restart: unless-stopped

  db:
    image: postgres:16
    volumes:
      - pgdata:/var/lib/postgresql/data
    restart: unless-stopped

volumes:
  pgdata:
```

## 七、常用命令

```bash
docker build -t app:1.0 .
docker compose up -d        # 后台启动
docker compose logs -f app  # 跟踪日志
docker compose down         # 停止并移除
docker image prune -a       # 清理无用镜像
```

## 八、检查清单

- [ ] 基础镜像固定版本且精简，使用多阶段构建
- [ ] 提供 .dockerignore，依赖层在前利用缓存
- [ ] 以非 root 运行，密钥通过环境/卷注入
- [ ] 持久化数据使用 volume，服务走自定义网络
- [ ] Compose 编排完整，已做漏洞扫描
