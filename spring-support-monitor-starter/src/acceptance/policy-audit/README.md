# 生产级验收脚本（2026-10-01/02）

这批脚本是 198 交付的生产级验收工具。原先放在 `D:\ch\project\tools\policy-audit\`，
而 `tools/` 不受任何子仓版本控制 —— 一次 `git clean` 就会全部消失。
按 AGENTS.md §5「带强制校验的传送工具（已入库，勿只留在 .tmp）」的同一条纪律，
复制到这里入库（原位置保留，避免影响并发会话正在使用的路径）。

## 运行前置

- **前端**：`http://localhost:8096/system` 需在跑（`pnpm dev`）
- **后端**：198 上 `http://192.168.50.198:18170`（admin / admin123）
- **跨浏览器**：firefox / webkit 已随 playwright 装好
- **axe-core**：装在 `.tmp/axe`（仓库是 pnpm workspace，`npm install` 装不进去，
  也不写进 package.json 以免污染 lockfile）。`accept-a11y.mjs` 从该路径读 `axe.min.js`

## 脚本清单

### 权限矩阵（#1）
| 脚本 | 作用 |
|---|---|
| `extract-perm-endpoints.mjs` | 从源码抽 156 个 `@Permission` 端点的完整 URL 与声明，作为预期基线 |
| `perm-matrix-setup.mjs` | 建 OPS / USER 测试账号 |
| `perm-matrix-run.mjs` | 逐端点 × 多身份探测，与基线比对 |
| `perm-matrix-superadmin.mjs` | 单独验唯二 SUPER_ADMIN 专属端点（决定性 8 格） |
| `perm-matrix-menu.mjs` | 各角色登录后的侧边栏菜单可见性对比 |

判定口径：**权限网关是拦截器，先于 controller 执行**，所以只要不是 403 /
「权限不足」就算穿过了闸门；后续 400 恰是「已放行但参数无效」的正证据。

### 跨浏览器（#2）· 移动端（#3）
| 脚本 | 作用 |
|---|---|
| `accept-crossbrowser.mjs` | Chromium / Firefox / WebKit 跑核心只读页 + 一个写路径 |
| `accept-mobile.mjs` | iPhone SE / 12、Pixel 5、iPad 四视口，查溢出与控件可达 |

### 性能（#4）
| 脚本 | 作用 |
|---|---|
| `accept-perf.mjs` | 阶梯并发（上限 24），p95>3000ms 或错误率>5% 或服务端重启即停 |

**不是冲击压测**：该机 16G 上有 11 个 JVM（合计 11.6GB），
我的服务 `-Xmx1g`，冲击压测会反复 OOM 并干扰同机其他人的服务。

### 无障碍（#5）
| 脚本 | 作用 |
|---|---|
| `accept-a11y.mjs` | axe-core 扫全部 22 个真实页面，按 impact 统计 |
| `accept-a11y-contrast.mjs` | 实测 computed style 色值算对比度 |

**踩过的坑**：`color-contrast` 必须自己算而不是信 axe。axe 只读
`backgroundColor`，而本仓的 primary 按钮是 `background-color: transparent`
+ `background-image: linear-gradient(...)` 上色 —— 只读前者会把 primary 按钮
算成「白字白底 1:1」，那是采集缺陷不是产品缺陷。

### 安全（#6）
| 脚本 | 作用 |
|---|---|
| `accept-security.mjs` | 安全响应头 / CORS / 未登录访问 / 伪造 token / 敏感信息 / 路径穿越 / 错误信息 |

**只做无副作用探测**：不爆破、不注入写操作、不删改数据。

### 其他模块写路径
| 脚本 | 作用 |
|---|---|
| `accept-modules-crud.mjs` | 角色/岗位/字典/公告/用户 五页增改删（用户删除在「更多」下拉） |
| `accept-modules-crud2.mjs` | 部门/配置/菜单/消息 四页，含各自的特殊入口形态 |
| `accept-modules-discover.mjs` / `-discover2.mjs` / `-deep.mjs` | 弹窗字段与入口形态发现 |
| `p0-menu-trace.mjs` | 登录后全部 XHR 抓包，定位菜单数据来源 |
| `p0-menu-ab.mjs` | admin / USER / OPS 三身份的菜单渲染 A/B |

## 判据失效的坑（都踩过，脚本里已注明）

1. **toast 竞态**：`networkidle + 2500ms` 后读 `.el-message`，而 Element 的
   message 3s 自动消失 → 「新增成功」读成空数组，像没提交
2. **只看最后一个表**：`table.pop()` 在部门管理上取错表（那页有 3 个表）
3. **列表是树不是表**：部门/菜单的列表是 `.el-tree-node__content`，
   只扫 `table` 一律漏判
4. **`[class*="group-card"]`** 匹配 176 个（含 `group-card__top` 等子元素）
5. **类名被 `.slice(0,60)` 截断**：`koi-select-icon-popper` 被误读成
   `koi-select-icon-pop`，CSS 整 token 匹配，选 30s 都没命中
6. **`el-button--danger.is-circle` 有 3 个**：`.first()` 点到别的面板，
   删除「提示已确认」但目标行还在库里
7. **并发跑多个浏览器实例**：Firefox/WebKit 出现「#app 子元素=0、空白、
   零 console error」的假故障，单独复现完全正常 —— 跨浏览器判据必须串行
8. **`elementFromPoint` 取中心点判遮挡**：移动端左右分栏上下堆叠时会落在
   相邻区块上，报假遮挡。改为「四角+中心采样，命中自身才算可达」
9. **恢复复核不带 token**：被复位的接口本身需登录，裸请求恒返回鉴权 403，
   与「策略未恢复」完全是两回事
10. **404/405 算成 ALLOW**：会把「路由不存在」报成「权限泄漏」

## 结论数据（2026-10-02）

| # | 项 | 结果 |
|---|---|---|
| 1 | 权限矩阵 | 非管理员 DENY=151 / ALLOW=0；决定性 8 格全对 |
| 2 | 跨浏览器 | 三引擎 8/8 页渲染长度逐页一致 |
| 3 | 移动端 | 4 视口 × 8 页，溢出 0、不可达 0 |
| 4 | 性能 | 24 并发 385 qps / p95 91ms / 零错误 / RSS 706MB 不变 |
| 5 | 无障碍 | critical 265 → 47（3 类必修清零） |
| 6 | 安全 | 探测 5/5 类通过；CORS 反射与安全响应头已修 |
