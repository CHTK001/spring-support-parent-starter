# Panel 后续交接清单

更新时间: 2026-04-14 21:45

## 项目边界

- 接口项目(后端): `g:\work\spring-support-parent-starter\spring-support-panel-starter`

  - 说明: `panel-starter` 被依赖后即提供 panel 全部后端能力，包括 `controller / service / model / jdbc / ai / document / datasource`。
  - 约束: `monitor` 只负责挂服务器启动，不承载 panel 业务接口。

- 前端项目: `g:\work\vue-support-parent-starter\pages\panel`

  - 说明: panel 页面、工作区、对象树、SQL 编辑器、右侧详情、表编辑、文档页都在这里实现。

- 前端真实联调启动壳: `g:\work\vue-support-parent-starter\test\panel`
  - 说明: 这里是独立单页面测试壳，直接挂载 `@pages/panel` 并代理后端接口，便于真实数据库联调。

## 当前联调环境

- 真实数据库: `172.16.0.40:3306`
- 账号: `root / root@`
- 真实后端联调地址: `http://127.0.0.1:58080`
- 真实前端联调地址: `http://127.0.0.1:8868`

## 继续开发时优先关注的代码位置

- 后端接口入口:

  - `spring-support-panel-starter/src/main/java/com/chua/starter/panel/controller/PanelJdbcController.java`
  - `spring-support-panel-starter/src/main/java/com/chua/starter/panel/service/JdbcPanelService.java`
  - `spring-support-panel-starter/src/main/java/com/chua/starter/panel/service/impl/DefaultJdbcPanelService.java`

- 前端核心页面:
  - `pages/panel/src/index.vue`
  - `pages/panel/src/components/JdbcDetailCard.vue`
  - `pages/panel/src/components/JdbcCatalogTree.vue`
  - `pages/panel/src/api/jdbc.ts`

## 后续任务清单

## 状态说明

- `已完成`: 当前仓库代码已落地
- `部分完成`: 已覆盖部分诉求，但仍有缺口
- `未完成`: 当前仓库中尚未落地
- `未验证`: 代码已调整，但缺少真实环境回归确认

## 最新新增任务(2026-04-14)

- `已完成` 将 panel 当前工作区抽为通用组件 `ScLayout`:
  - 布局统一承载左侧区域 / 中间工作区 / 最右侧 `ScTabs`
  - 左侧区域支持属性控制开启，默认关闭
  - 左侧区域支持拖拽调整宽度
  - 鼠标悬停在拖拽分割条时显示折叠/展开控制，交互参考导航栏折叠
  - 右侧 `ScTabs` 支持继续扩展页签能力
- 当前代码现状:
  - `已完成` 外层工作区已接入 `pages/panel/src/index.vue`
  - `已完成` 中间区与最右侧 `ScTabs` 已接入 `pages/panel/src/components/JdbcDetailCard.vue`
  - `已完成` 公共组件位于 `packages/components/ScLayout`
- 本轮交接时建议优先阅读:
  - `g:\work\vue-support-parent-starter\pages\panel\src\index.vue`
  - `g:\work\vue-support-parent-starter\pages\panel\src\components\JdbcDetailCard.vue`
  - `g:\work\vue-support-parent-starter\packages\components\ScLayout\src\index.vue`

### 一、后端接口任务

- `已完成` `JdbcTableStructure` 的 DDL 以真实库返回为准，MySQL 走 `SHOW CREATE TABLE`，其它数据库走方言回退。
- `已完成` 表注释、字段注释已写回真实数据库，不再只写本地 remark 存储。
- `已完成` 编辑表保存支持直接保存，不经过 SQL 工作区中转，保存后会回刷表结构与已打开表页签。
- `已完成` 表结构接口首次进入编辑表时即可返回字段数据，不依赖手动刷新补齐。
- `部分完成` 打开表数据接口已支持:
  - 筛选
  - 排序
  - 分页/非分页
  - 查询耗时
  - 结果行数
- `说明` 当前代码已具备分页/非分页、查询耗时、结果行数、服务端排序；筛选仍主要是前端结果集筛选，Navicat 风格服务端筛选仍未完全落地。
- `已完成` 右键危险操作接口已收口为真实执行:
  - 备份表
  - 清空表
  - 截断表
  - 删除表
- `未验证` 真实联调时仍需确认:
  - SQL 执行结果是否真实返回
  - EXPLAIN 是否真实返回
  - 打开表是否真实刷新
  - 注释修改是否真实落库

### 扩展

1. `已完成` 编辑表支持行拖动
2. `已完成` sql 编辑器执行结果已在结果区固定渲染
3. `已完成` 在保存 sql 右侧添加查询耗时
4. `已完成` 点击刷新会完全重置左侧树
5. `部分完成` 已有分页/非分页、冻结列、显示序列、前端结果集筛选、表头排序按钮、服务端排序；Navicat 风格服务端筛选、单元格多类型编辑器仍未全部完成
6. `未完成` 打开表的表头列右键、注解/排序/字段类型/字段注释/数据注释/列宽自适应、日期/JSON 等类型化编辑器仍待实现

### 二、前端页面任务

- `部分完成` 左侧对象树已收敛到搜索、刷新、树结构、右键操作；仍可继续减法。
- `已完成` 左侧树表节点默认可展开，字段默认不展开，仅在展开表时加载字段。
- `已完成` 刷新连接/刷新工作区会完全重置左侧树状态。
- `已完成` 打开表页面已按“编辑数据”模式细化。
- `已完成` 编辑表页已补足:
  - 首次进入自动加载字段
  - 直接保存
  - Ctrl+S 保存
  - 保存后刷新结构
- `已完成` SQL 编辑器和结果区已补:
  - 查询耗时展示
  - 结果区真实渲染
  - 解释 SQL
  - 美化 SQL 不阻塞页面
- `部分完成` 列设置当前已支持:
  - 冻结列
  - 显示序列
- `未完成` 列设置仍待补:
  - 列筛选
  - 列顺序拖拽
  - 列显隐

### 三、用户新增任务(原样保留)

1. `已完成` 编辑表支持行拖动
2. `已完成` sql 编辑器执行结果已生效，结果区有真实渲染
3. `已完成` 在保存 sql 右侧添加查询耗时
4. `已完成` 点击刷新会完全重置左侧树
5. `部分完成` 已有分页/非分页、冻结列、显示序列、前端结果集筛选、表头排序按钮、服务端排序；Navicat 风格服务端筛选、单元格多类型编辑器未全部完成
6. `未完成` 打开表的表头列右键、注解/排序/字段类型/字段注释/数据注释/列宽自适应、日期/JSON 等类型化编辑器仍待实现
7. `已完成` 右键备份表使用 `ElMessageBox`
8. `未完成` 保留设计表删除编辑表
9. `已完成` 右键菜单已抽为通用组件
10. `已完成` 数据库右键已添加刷新数据(重新查询表)，表右键已添加刷新表(重新查询字段)
11. `已完成` panel 工作区已抽为通用组件 `ScLayout`，左侧支持属性开启、拖拽、悬停折叠，右侧支持 `ScTab`

## 建议的开发顺序

1. 先收口后端真实接口:
   - DDL
   - 注释写回
   - 表结构直接保存
   - 打开表结果返回
2. 再收口前端工作区交互:
   - 左树重置
   - 打开表
   - SQL 执行结果
   - 查询耗时
3. 最后补强 Navicat 风格交互:
   - 列设置
   - 右键菜单
   - 单元格类型编辑器
   - 表头右键能力

## 备注

- 继续开发时优先坚持一个原则: `panel` 的接口、控制层、服务能力都放在 `spring-support-panel-starter`，不要再散到 `monitor`。
- 前端如需真实测试，优先走 `agent-browser` 联调，不要只依赖 mock。
