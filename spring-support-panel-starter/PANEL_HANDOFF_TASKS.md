# Panel 后续交接清单

更新时间: 2026-04-13

## 项目边界

- 接口项目(后端): `g:\work\spring-support-parent-starter\spring-support-panel-starter`

  - 说明: `panel-starter` 被依赖后即提供 panel 全部后端能力，包括 `controller / service / model / jdbc / ai / document / datasource`。
  - 约束: `monitor` 只负责挂服务器启动，不承载 panel 业务接口。

- 前端项目: `g:\work\vue-support-parent-starter\pages\panel`

  - 说明: panel 页面、工作区、对象树、SQL 编辑器、右侧详情、表编辑、文档页都在这里实现。

- 前端真实联调启动壳: `g:\work\vue-support-parent-starter\scripts\panel-playground`
  - 说明: 这里用于本地 Vite 启动和代理后端接口，便于真实数据库联调。

## 当前联调环境

- 真实数据库: `172.16.0.40:3306`
- 账号: `root / root@`
- 真实后端联调地址: `http://127.0.0.1:58080`
- 真实前端联调地址: `http://127.0.0.1:5702`

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

### 一、后端接口任务

- `JdbcTableStructure` 的 DDL 需要完全以真实库返回为准，MySQL 优先走 `SHOW CREATE TABLE`，其它数据库再做方言回退，不能再由前端拼伪 DDL。
- 表注释、字段注释要写回真实数据库，不允许只写本地 remark 存储。
- 编辑表保存需要支持“直接保存”，不经过 SQL 工作区中转，保存后要回刷表结构与打开中的表页签。
- 表结构接口需要保证字段首次进入编辑表时即返回完整字段数据，不能靠手动刷新补齐。
- 打开表数据接口需要继续补 Navicat 风格能力:
  - 筛选
  - 排序
  - 分页/非分页
  - 查询耗时
  - 结果行数
- 右键危险操作接口继续收口为真实执行:
  - 备份表
  - 清空表
  - 截断表
  - 删除表
- 真实联调时需要继续验证:
  - SQL 执行结果是否真实返回
  - EXPLAIN 是否真实返回
  - 打开表是否真实刷新
  - 注释修改是否真实落库

### 扩展

1.编辑表支持行拖动
2.sql 编辑器执行怎么没有效果，在结果里没有信数据 3.在保存 sql 右侧添加查询耗时 4.点击刷新应该完全充值左侧树 5.添加类似 navcat 的筛选&排序，单元格编辑器(文本，十六进制，图像，网页)。并且表头添加排序按钮 6.打开表的表头列支持右键支持注解，表，排序，显示字段类型，显示字段注释，显示数据注释，调整列宽自适应内容等功能。支持日期格式数据编辑数据是弹出日期框的并不是输入等要根据类型判断编辑组件，json 格式可以在单元格编辑器点击字段自动获取到 sql 需要的字段，可以创建一个自定义的组件完成上述功能

### 二、前端页面任务

- 左侧对象树继续按 Navicat 客户端模式收敛，只保留搜索、刷新、树结构本身。
- 左侧树表节点默认应显示可展开按钮，字段默认不展开，只在点击展开当前表时加载字段。
- 刷新连接/刷新工作区时，需要完全重置左侧树状态，避免旧展开态、旧节点、旧缓存残留。
- 打开表页面继续按 Navicat 的“编辑数据”模式细化，不要退回详情页模式。
- 编辑表页继续补足:
  - 首次进入自动加载字段
  - 直接保存
  - Ctrl+S 保存
  - 保存后刷新结构
- SQL 编辑器和结果区继续补:
  - 查询耗时展示
  - 结果区真实渲染
  - 解释 SQL
  - 美化 SQL 不阻塞页面
- 列设置需要继续完善:
  - 冻结列
  - 显示序列
  - 列筛选
  - 列顺序拖拽
  - 列显隐

### 三、用户新增任务(原样保留)

1. 编辑表支持行拖动
2. sql 编辑器执行怎么没有效果，在结果里没有信数据
3. 在保存 sql 右侧添加查询耗时
4. 点击刷新应该完全充值左侧树
5. 添加类似 navcat 的筛选&排序，单元格编辑器(文本，十六进制，图像，网页)。并且表头添加排序按钮
6. 打开表的表头列支持右键支持注解，表，排序，显示字段类型，显示字段注释，显示数据注释，调整列宽自适应内容等功能。支持日期格式数据编辑数据是弹出日期框的并不是输入等要根据类型判断编辑组件，json 格式可以在单元格编辑器点击字段自动获取到 sql 需要的字段，可以创建一个自定义的组件完成上述功能
7.右键备份表使用ElMessageBox而不是弹出框
8.保留设计表删除编辑表
9.这个右键是否已经是通用组件了
10.数据库右键添加刷新数据(重新查询表)，表右键添加刷新表(重新查询字段)
11.将现在panel的工具间就是左边区域，中间区，最右侧ScTabs作为一个通用组件ScLayout, 左侧现在支持属性开启(默认关闭)，支持拖拽，鼠标放到拖拽部分显示折叠(导航栏的折叠)，支持放大添加右侧的ScTab, 能理解我要什么么
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
