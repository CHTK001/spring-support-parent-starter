# 安全问题记录：文件存储节点 file-storage order<50 绕过自动挂载的 OAuth 鉴权

- **状态**：🔴 未修复（待单独排期修复部署）
- **等级**：高（私有桶未授权读/写/删）
- **发现日期**：2026-10-09（CORS/FTP CRLF 修复上线的全量探针验收过程中发现）
- **发现环境**：生产 `192.168.50.198:8112`，monitor jar `4.0.0.42`
  （MD5 `0c3a99889f3e25a058630f68e41321a5`）
- **性质**：既有缺陷，**非**本次 CORS/FTP 提交引入——该批提交只动了
  `Cors#applyTo` 装配与 401 响应的 CORS 回显，未触及过滤器链排序。

## 一、现象

生产节点 `fs-demo`（file-storage 描述符 `order=1`，端口 19080）：

```bash
# 不带任何令牌，直接访问私有桶 → 200，可列目录/读文件
curl -i http://192.168.50.198:19080/localFileStorage/

# 同一个桶挂到独立文件服务 18082（无 file-storage 过滤器、走平台统一鉴权）→ 401（正确）
curl -i http://192.168.50.198:18082/localFileStorage/
```

`localFileStorage` 在 `plugin.oauth.file-storage.private-buckets` 私有桶清单内，
按语义无令牌访问必须 401。

## 二、根因

自动挂载链路（`ServerNodeRegistry`）：

1. `attachOAuthFilter`（ServerNodeRegistry.java L606-635）：节点含 `file-storage`
   且未显式声明 `oauth` 时，反射装载 `OAuthServerFilter`（`plugin.oauth.enable=true`
   且配置了私有桶时 order 固定 **50**，见 oauth-client-starter `OAuthServerFilter#getOrder`）。
2. `ServerFilterFactory.createFilters`（L140-144）：描述符声明的 `order` 经
   `DelegatingServerFilter` **覆盖**底层过滤器自身 order；L153 按 `getOrder()`
   升序合并成链。

⇒ 当 file-storage 描述符 `order < 50` 时，文件网关先执行、直接应答并终止请求，
`OAuthServerFilter` 永远执行不到——鉴权被静默跳过。

## 三、复现与对照（2026-10-09 实测，均有记录留痕）

同一私有桶 `localFileStorage`，唯一变量是 file-storage 描述符的 `order`：

| 场景 | 无令牌 GET | 结论 |
|---|---|---|
| 生产 `fs-demo`，`order=1` | **200** | 🔴 绕过 |
| 临时节点，`order=0` | **200** | 🔴 绕过（探针对照实验） |
| 临时节点，`order` 省略（回退底层默认 100） | 401 | ✅ 正确 |
| 临时节点，`order=100` | 401 | ✅ 正确 |
| 独立文件服务 18082（无 file-storage 过滤器） | 401 | ✅ 正确 |

## 四、影响面

- **触发条件**：节点含 `file-storage` 过滤器且其 `order < 50`，且未显式声明
  `oauth` 过滤器。此时私有桶的列目录/下载/上传/删除全部无鉴权暴露。
- **正常路径不受影响**：UI（`ScServerFormDrawer`）新建过滤器 order 从 100 起
  （`nextOrder=100`，重排 `100+i*10`），因此 UI 建的节点 order≥100 不触发。
- **可触发路径**：直接调管理 API 建/改节点指定 `order<50`；或旧配置/手工导入数据。
- **当前生产**：全量节点扫描（2026-10-09）——仅 `fs-demo`（order=1）一例命中，
  其余 2 个节点无 file-storage 过滤器。

## 五、修复思路（供后续排期）

在 `attachOAuthFilter` 中消除「自动挂载鉴权的 order 固定值」与「描述符可任意覆盖
order」之间的竞态，两种方向：

1. **装配期钳制**：检测到 file-storage `order <= 自动挂载 oauth order` 时，把
   file-storage 的有效 order 抬到 oauth 之后（用 `DelegatingServerFilter` 覆写，
   或建链时钳制描述符 order）；或
2. **动态挂载 order**：自动挂载的 oauth order 取 `min(fileStorageOrder) - 1`，
   保证鉴权永远排在文件网关前面。

**修复部署后的行为变化（需在验收中显式核对）**：`fs-demo` 无令牌访问将由 200
变 401——届时 e2e/探针里引用 `fs-demo` 的用例需同步改断言。

**附带核查项**：`bindFileStorageConfigListener`（ServerNodeRegistry.java L647-675）
以 `filter.getClass().getName()` 精确匹配 `FILE_STORAGE_FILTER_CLASS`，但链上
元素是 `DelegatingServerFilter` 包装（createFilters L140），类名匹配疑似恒不成立
→ 「私有桶热更新联动刷新 oauth」疑似从未生效。修复时一并验证该路径。

## 六、验收口径变更留痕

探针脚本 `probe-live.mjs` 曾用 `order: 0` 建临时文件存储节点——该值恰好命中本缺陷，
导致「无令牌应 401」的敏感性对照失真。已将探针建节点参数改为 **省略 order**
（回退底层默认 100，与 UI 行为一致）。本缺陷修复后，建议探针补回一条
`order<50` 的负例，作为回归防线。

## 七、参考

- `ServerNodeRegistry#attachOAuthFilter`（自动挂载）与 `#bindFileStorageConfigListener`
  —— spring-support-monitor-starter
- `ServerFilterFactory#createFilters` L140-153（order 覆盖与链排序）
- `DelegatingServerFilter#getOrder`（描述符 order 优先于底层实现）
- oauth-client-starter `OAuthServerFilter#getOrder() = 50`
- 验收留痕：2026-10-09 全量探针 `PROBE_RESULT PASS` + e2e
  `service-types-live.spec.ts` 3/3（该批结果不含 fs-demo 用例，fs-demo 越权
  以本文档为准单独跟踪）
