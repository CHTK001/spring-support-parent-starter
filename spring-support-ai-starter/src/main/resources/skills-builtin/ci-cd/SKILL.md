---
name: ci-cd
icon: Branch20Regular
title: CI/CD 流水线指南
description: 持续集成与部署实践：分支模型、流水线设计、测试门禁、制品管理、发布策略与回滚。
version: 1.0.0
author: system
category: 研发效能
tags:
  - ci
  - cd
  - devops
---

# CI/CD 流水线指南

## 一、分支模型

- 简单团队用 trunk-based：主干常绿，短命分支（<2 天）+ feature flag 控制未完成功能。
- GitFlow 类模型适合有明确版本发布节奏的产品；分支策略定下来就别频繁摇摆。
- 提交信息规范（conventional commits）让 changelog 与版本号可自动化。

## 二、流水线阶段

- 标准 stage：`lint → build → unit test → integration test → security scan → package → deploy(dev) → deploy(prod)`。
- 失败要快：便宜且常挂的检查（lint、单测）放前面，昂贵的集成/压测放后面。
- 流水线配置即代码（Jenkinsfile/.github/workflows/.gitlab-ci.yml），入库存档可审计。
- 缓存依赖（maven/pnpm/docker layer）把分钟级流水线压到秒级；缓存 key 含锁文件哈希。

## 三、质量门禁

- 单测覆盖率设阈值并只升不降；关键模块（支付、鉴权）单独要求。
- 静态扫描（SonarQube）+ 依赖漏洞扫描（OWASP DC / npm audit）高危阻断。
- PR 必须流水线全绿 + 至少一人评审；禁止「线下说好了直接合」。

## 四、制品管理

- 构建产物（jar/镜像）进制品库（Nexus/Harbor），tag 与 git commit 绑定、可追溯。
- 镜像不可变：同一镜像从 dev 一路推到 prod，只改配置不改镜像。
- 构建要可复现：锁定依赖版本，基础镜像用 digest 固定。

## 五、发布策略

- 灰度/金丝雀：先 5% 流量观察错误率与 RT，再逐步放量；蓝绿用于需要瞬时切换的场景。
- 数据库变更与代码发布解耦：先加列后删列，兼容旧代码的迁移顺序（expand → migrate → contract）。
- 回滚方案先于发布准备：镜像回退 + 配置回退 + 数据兼容；回滚演练和发布演练同样重要。

## 六、检查清单

- [ ] 主干常绿，PR 有门禁与评审
- [ ] 流水线缓存依赖，反馈快
- [ ] 制品可追溯（tag ↔ commit ↔ 部署环境）
- [ ] 生产发布走灰度，回滚路径验证过
- [ ] 安全扫描高危阻断
