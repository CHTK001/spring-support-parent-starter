---
name: k8s-ops
icon: Box20Regular
title: Kubernetes 运维指南
description: Kubernetes 集群运维与排障：Pod/Deployment、探针、资源与 HPA、ConfigMap/Secret、滚动发布与常见故障。
version: 1.0.0
author: system
category: 运维部署
tags:
  - k8s
  - kubernetes
  - ops
---

# Kubernetes 运维指南

## 一、核心对象

- Pod 是调度最小单元；业务用 Deployment（无状态）/ StatefulSet（有状态）管理，别裸跑 Pod。
- Service 提供稳定访问入口：ClusterIP（内部）、NodePort/LoadBalancer（外部）、Headless（有状态直连）。
- Ingress 管 L7 路由与 TLS；命名空间做环境/团队隔离，配额与网络策略按 ns 收口。

## 二、探针与生命周期

- liveness 探针失败会重启容器：探活逻辑必须轻量、不依赖外部慢资源，否则故障时雪上加霜。
- readiness 决定是否接流量：启动慢的应用调大 `initialDelaySeconds` 或用 startupProbe。
- preStop + `terminationGracePeriodSeconds` 做优雅退出：先摘流量再停进程。

```yaml
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  periodSeconds: 10
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
```

## 三、资源与弹性

- 必须设置 `requests`/`limits`：requests 影响调度，limits 防 CRC/OOMKilled；没配额的 Pod 会被驱逐策略优先牺牲。
- OOMKilled（exit 137）= 超内存限制，调大 limits 或修内存泄漏。
- HPA 按 CPU/内存/自定义指标扩缩容，注意指标采集延迟；min/max 副本兜底。
- 节点污点与亲和性控制调度分布，避免单节点热点。

## 四、配置与密钥

- ConfigMap 挂非敏感配置，Secret 存密钥（etcd 加密 + RBAC 限权）。
- env 注入 vs 文件挂载：大配置用挂载，且 `subPath` 避免覆盖目录。
- 配置变更不自动生效到已运行容器：滚动重启（`kubectl rollout restart`）加载新配置。

## 五、发布与回滚

- 滚动发布参数：`maxSurge`/`maxUnavailable` 平衡速度与容量；发布中保留旧 ReplicaSet 便于回滚。
- `kubectl rollout status` 观察进度，卡住即排查事件；`kubectl rollout undo` 一键回退。
- 镜像 tag 别用 latest；digest 固定 + 镜像仓库凭据走 imagePullSecrets。

## 六、排障三板斧

- `kubectl get pods -o wide` + `kubectl describe pod <p>`（看 Events：调度失败、探针失败、镜像拉取失败）。
- `kubectl logs <p> --previous` 看崩溃前日志；`kubectl exec -it <p> -- sh` 进容器现场（需镜像带 shell，或用 ephemeral 容器）。
- `kubectl top pods` 看资源水位；CrashLoopBackOff 先看 describe 事件再谈重启策略。

## 七、检查清单

- [ ] 业务由 Deployment/StatefulSet 托管，副本 ≥ 2
- [ ] liveness/readiness 探针配置合理，启动窗口足够
- [ ] requests/limits 全覆盖，OOM 有告警
- [ ] 配置与密钥分离，密钥有 RBAC 约束
- [ ] 发布可灰度、可回滚，排障会看 Events
