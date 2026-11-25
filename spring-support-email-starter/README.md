# spring-support-email-starter

> Spring Boot 邮箱收发管理模块（当前为规划占位模块）。

## 简介

`spring-support-email-starter`（`com.chua:spring-support-email-starter`）定位为「Spring Boot 邮箱收发管理模块，提供完整的邮箱收发 REST API 接口」（引自模块 pom 描述）。

**当前状态（务必先读）**：本目录现仅存在构建产物 `.flattened-pom.xml`（由 flatten-maven-plugin 生成、已被 git 忽略），**暂无 `pom.xml` 与 `src/` 源码**，且未列入父 pom 的 `<modules>`，当前不参与 reactor 构建。因此本文档只记录可从现有文件验证的事实（模块坐标、规划能力、依赖规划）；端点清单、配置项总表、使用示例等必须待源码落地后再补——按真实性铁律，文档不得预写未经源码验证的能力、配置键或默认值。

## 能力概览（规划，引自模块 pom 描述）

| 能力 | 说明 | 状态 |
|---|---|---|
| 📧 邮件收发接口 | 邮件发送与接收的 REST 接口 | 规划中（暂无源码） |
| 📥 邮件列表查询 | 分页/条件查询邮件列表 | 规划中（暂无源码） |
| 📁 文件夹管理 | 邮箱文件夹的增删改查与排序 | 规划中（暂无源码） |
| 👤 邮箱账户管理 | 邮箱账户的接入与维护 | 规划中（暂无源码） |
| 🔍 邮件搜索 | 按关键词/条件检索邮件 | 规划中（暂无源码） |
| 📎 附件处理 | 附件下载/预览等处理能力 | 规划中（暂无源码） |
| 🔔 WebSocket 实时推送 | 新邮件等事件的实时推送 | 规划中（暂无源码） |

依赖规划（引自 `.flattened-pom.xml` 的 `<dependencies>`）：

| 依赖 | 作用 |
|---|---|
| `org.springframework.boot:spring-boot-starter-web` | 承载邮件 REST 接口 |
| `org.springframework.boot:spring-boot-starter-websocket` | 承载 WebSocket 实时推送 |
| `com.chua:utils-support-email-starter` | 底层邮件发送与解析能力（SMTP/IMAP/POP3，基于 jakarta.mail + angus-mail；实现位于 utils 仓 `utils-support-parent-starter/utils-support-filesystem-parent/utils-support-email-starter`） |
| `com.chua:spring-support-common-starter` | 本仓库通用支撑 |
| `com.chua:spring-support-swagger-starter`（optional） | API 文档 |
| `com.chua:utils-support-common-starter` | 通用工具 |

## 引入方式

模块当前没有可发布的构件（无 `pom.xml`、未列入父 pom 构建），**暂不可被业务工程引入**。待模块实现并随父 pom 发布后，按标准 starter 方式引入（坐标以 `.flattened-pom.xml` 为准）：

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-email-starter</artifactId>
    <version>4.0.0.42</version>
</dependency>
```

## 配置说明

**暂无配置项可文档化。** 模块当前没有任何 `*Properties` 配置类源码；为避免虚构配置键与默认值，本文档不提供配置总表与逐项示例。源码落地后，请按「配置总表 + 逐项示例（含义 / 默认值 / YAML 示例）+ 完整 YAML 示例 + 生产注意事项」结构增强本节，且每个配置键必须能在配置类字段中找到对应。

## 使用示例

**暂无。** 模块尚无 Controller/Service 源码，无法给出真实端点与可运行示例；请勿引用本文档之外的猜测端点。

## 注意事项

- 本模块当前是**空壳**：不要在业务工程中引用 `spring-support-email-starter` 构件，也不要假设它提供任何端点或自动配置。
- 底层邮件能力（SMTP/IMAP/POP3 收发与解析）已存在于 utils 仓的 `utils-support-email-starter`；本模块的规划职责是把该能力包装为 Spring Boot REST/WebSocket 服务，不要在其它模块重复实现同一能力。
- 模块边界：邮件收发 REST 业务归属本模块；按仓库铁律，不得混入渠道管理、账号等其它 starter 的业务，跨模块取数只走契约。
- 源码落地后维护本文档时：新增/修改配置项必须同步核对配置类字段与默认值（含 ≤0 或为空时的回落值）；示例中的口令/密钥一律使用占位符（如 `your-password`、`change-me`），不得写入真实凭据。
