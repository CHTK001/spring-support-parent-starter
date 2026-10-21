# 阅卷详情结构化视图修复验收（2026-10-21）

## 验收范围

- 页面：`pages/eduai/src/components/marking/SheetStructureView.vue`
- 任务：`fe637245bed14f428cebafe1ff1b884d`（试卷 9043，6 页，43 题）
- 前端：`http://127.0.0.1:19093`
- 后端：`http://192.168.50.198:19095/actuator/health` = 200

## 问题

1. 上下页切换功能无效。
2. 右侧解析数据不显示。
3. 左侧与右侧对不上，第一页右侧显示全部题目。

## 根因

- 模板调用了 `changePage(...)`，组件脚本中未定义该方法。
- 模板使用了 `currentQuestions`，组件脚本中未定义该计算属性。
- 原有兜底逻辑在锚点绑定失败时返回全部题目，导致每一页右栏都显示 43 题。

## 修复

- 新增 `changePage(delta)`，上一页/下一页统一走页码切换。
- 新增 `currentQuestions`，按当前页 `questionAnchors` 的题号过滤题目。
- 重做阅卷详情 UI：
  - 顶部工具栏：阅卷详情、版面解析/原始试卷、卷面识别学生、上/下一页页码切换。
  - 左侧：当前页答卷扫描图，支持版面区域与题号锚点。
  - 右侧：当前页解析题目，含 AI 分、老师分、作答、AI 评语、老师评语、知识点、入题库。
  - 保留“保存老师分”汇总动作。

## 浏览器验收

真实 Chromium/Playwright，登录后进入 `/edu/marking/detail?taskId=fe637245bed14f428cebafe1ff1b884d`。

完整验收覆盖：后端健康、学生 API 登录、结构接口完整性、浏览器登录、详情页渲染、模式/翻页控件、第 1 页锚点、右侧解析内容、6 页逐页切换、图片换页、43 题全局唯一、上一页返回、页面错误与 HTTP 异常。

- 第 1 页：5 题
- 第 2 页：5 题
- 第 3 页：6 题
- 第 4 页：10 题
- 第 5 页：12 题
- 第 6 页：5 题
- 6 页题号不重复，共 43 题
- 上一页从第 6 页返回第 5 页，右侧显示 12 题
- 无页面 JS 错误、无异常 HTTP

结果：**20 PASS / 0 FAIL**

验收脚本：

```text
spring-support-eduai-starter/src/acceptance/verify-sheet-ui-full-acceptance.cjs
spring-support-eduai-starter/src/acceptance/verify-page-questions.cjs
```

截图：

```text
spring-support-eduai-starter/src/acceptance/verify-sheet-ui-full-20261021.png
spring-support-eduai-starter/src/acceptance/verify-sheet-ui-20261020.png
```

## 类型检查

- `SheetStructureView.vue`：无新增类型错误。
- eduai 包：无新增类型错误。
- 全仓库 `vue-tsc` 仍有 `pages/ai`、`pages/datasource` 等目录的存量错误，不属于本次阅卷详情改动范围。

## 结论

阅卷详情页的页码切换、左右联动、右侧当前页解析数据、统一 UI 重做均达到验收口径，可交付。