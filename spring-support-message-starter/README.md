# Spring Support Message Starter

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 📖 模块简介

Spring Support Message Starter 是一个统一的消息通知模块，提供消息提供商、消息模板管理以及统一的消息发送能力。

### ✨ 主要特性

- 🏢 **消息提供商** - 数据库表驱动管理各消息服务商配置（短信、邮件、微信、钉钉等）
- 📋 **消息模板** - 数据库表驱动管理各渠道消息模板
- 📤 **统一发送** - 通过 MessagePush SPI 实现统一消息发送
- 📊 **推送日志** - 异步记录消息推送历史，支持分页查询
- 🔄 **自动建表** - 适配 datasource-starter 自动执行 db/init 下的建表脚本

## 🚀 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-message-starter</artifactId>
    <version>4.0.0.42</version>
</dependency>
```

### 渠道实现（可选依赖）

用户根据需要在自己的项目中添加对应的渠道依赖：

- `utils-support-email-starter`    邮件
- `utils-support-alibaba-starter`  阿里云短信
- `utils-support-tencent-starter`  腾讯云短信
- `utils-support-dingding-starter` 钉钉机器人
- `utils-support-feishu-starter`   飞书消息

## 🗄️ 数据表

模块内置 `db/init/V1.0__init_message.sql` 建表脚本，由 datasource-starter 自动执行：

| 表名 | 说明 |
|------|------|
| `sys_message_provider` | 消息提供商表 - 管理各消息服务商配置 |
| `sys_message_template` | 消息模板表 - 管理各渠道消息模板 |
| `sys_message_log` | 消息推送日志表 - 记录推送历史 |

日志表除基础信息外，还包含以下补充字段：

| 字段 | 说明 |
|------|------|
| `sys_message_log_subject` | 消息主题（邮件标题等） |
| `sys_message_log_message_id` | 消息ID（服务商返回，如邮件 Message-ID、短信回执 ID） |
| `sys_message_batch_id` | 批次号（同一次发送请求的多个接收人共享） |
| `sys_message_track_token` / `sys_message_read_status` / `sys_message_read_time` | 邮件阅读追踪 |

## 🔌 接口说明

### 提供商管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/v2/message/provider/page` | GET | 提供商分页查询 |
| `/v2/message/provider/list` | GET | 提供商列表查询 |
| `/v2/message/provider/save` | POST | 新增提供商 |
| `/v2/message/provider/update` | PUT | 更新提供商 |
| `/v2/message/provider/delete` | DELETE | 删除提供商 |

### 模板管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/v2/message/template/page` | GET | 模板分页查询 |
| `/v2/message/template/list` | GET | 模板列表查询 |
| `/v2/message/template/save` | POST | 新增模板 |
| `/v2/message/template/update` | PUT | 更新模板 |
| `/v2/message/template/delete` | DELETE | 删除模板 |

### 消息发送与日志

| 接口 | 方法 | 说明 |
|------|------|------|
| `/v2/message/send` | POST | 按模板发送消息 |
| `/v2/message/send/content` | POST | 按内容发送消息 |
| `/v2/message/channel/list` | GET | 已注册消息渠道列表（SPI） |
| `/v2/message/template/preview` | POST | 模板内容渲染预览 |
| `/v2/message/template/sync` | POST | 手动同步远端模板 |
| `/v2/message/log/page` | GET | 推送日志分页查询 |
| `/v2/message/log/clean` | POST | 清理推送日志（按日期） |
| `/v2/message/track/{token}` | GET | 邮件阅读追踪接口（免鉴权） |

## 📧 邮件阅读追踪

发送 HTML 邮件时，可在内容末尾注入 1x1 透明追踪图（指向追踪接口），
收件人打开邮件即触发该地址，后端将对应日志标记为已读。

```yaml
plugin:
  message:
    track:
      enabled: true                       # 是否开启阅读追踪（默认 false）
      base-url: "http://your-host:8080"   # 追踪地址基础路径（用于拼接追踪图 URL）
```

- 仅在**邮件渠道（email/smtp）+ HTML 内容类型**且开启追踪时生效
- 追踪接口 `/v2/message/track/{token}` 免鉴权，返回 1x1 透明 GIF
- 日志记录 `sys_message_track_token`、`sys_message_read_status`、`sys_message_read_time` 字段，前端日志页可筛选已读/未读

## 🔁 重复消息防抖

提供商可配置**防抖间隔**（`sys_message_provider_interval`，单位分钟，0 表示不限制）：

- 间隔内，**同一接收人 + 相同模板**（按模板发送）或 **同一接收人 + 相同内容**（按内容发送）若已发送成功，则本次跳过不重复发送
- 仅发送**成功**的记录参与防抖，失败记录允许重试
- 跳过时返回 `success=true` + `errorMessage="间隔内已发送，防抖跳过"`，并照常记录日志

## 📝 使用示例

### 按模板发送短信

```json
POST /v2/message/send
{
  "target": ["13800138000", "13900139000"],
  "sysMessageTemplateId": 1,
  "params": { "code": "123456" }
}
```

## 🔗 相关链接

- [返回主文档](../README.md)

## 📄 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。
