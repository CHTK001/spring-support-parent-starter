# spring-support-project-starter

通用项目模块，提供项目管理、邮件、短信、密钥等基础能力

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-support-project-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 功能概览

| 类/接口 | 说明 |
|---------|------|
| `SysEmailLog` | SysEmailLog |
| `SysEmailTemplate` | SysEmailTemplate |
| `SysProject` | SysProject |
| `SysProjectDefault` | SysProjectDefault |
| `SysSecret` | SysSecret |
| `SysSmsLog` | SysSmsLog |
| `SysSmsTemplate` | SysSmsTemplate |
| `ProjectConfiguration` | 8:42 |
| `ProjectSocketEvents` | ProjectSocketEvents |
| `SysProjectController` | 项目接口 |
| ... | 共 76 个类 |

---

## 配置说明

本模块为零配置模块，引入依赖后即可使用。

---

## 依赖关系

```
spring-support-project-starter
├── spring-support-common-starter
├── spring-support-oauth-client-starter
├── spring-support-server-starter
├── spring-support-mybatis-starter
```