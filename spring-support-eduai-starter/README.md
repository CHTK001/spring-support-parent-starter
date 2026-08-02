# Spring Support EduAI Starter

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 模块简介

Spring Support EduAI Starter 是一个教育AI辅助教学模块，提供AI驱动的教学工具集。

### 主要特性

- **题库管理** - 题目录入、批量导入、多维度分类筛选（类型/学科/年段/年级/年份/难度/知识点）
- **AI组卷** - 根据知识点、难度分布、题型数量智能生成试卷
- **AI阅卷** - 主观题自动批改，客观题自动判分，支持异步任务
- **知识库** - 文档向量化存储，语义检索，知识点图谱
- **错题库** - 错题自动记录，按知识点/题型聚合，个性化复习推荐

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.chua</groupId>
    <artifactId>spring-support-eduai-starter</artifactId>
    <version>4.0.0.42</version>
</dependency>
```

## 数据表

模块内置 `db/init/V1.0__init_eduai.sql` 建表脚本，由 datasource-starter 自动执行：

| 表名 | 说明 |
|------|------|
| `edu_question` | 题目库（类型/学科/年段/年级/年份/难度/知识点/题干/选项/答案/解析） |
| `edu_knowledge_point` | 知识点（树形结构，含向量字段） |
| `edu_knowledge_doc` | 知识库文档（文件名/类型/向量存储ID） |
| `edu_paper` | 试卷（名称/总分/时长/状态） |
| `edu_paper_question` | 试卷-题目关联（顺序/分值） |
| `edu_exam` | 考试（关联试卷/班级/时间） |
| `edu_answer_sheet` | 答题卡（学生ID/考试ID/答案/提交时间） |
| `edu_marking_result` | 阅卷结果（题目ID/得分/评语/批改JSON） |
| `edu_wrong_question` | 错题记录（学生ID/题目ID/错误次数/掌握度/最近错误时间） |

## 接口说明

### 题库管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/v2/edu/question/page` | GET | 分页查询题目 |
| `/v2/edu/question/save` | POST | 新增/导入题目 |
| `/v2/edu/question/{id}` | GET | 获取题目详情 |
| `/v2/edu/question/{id}` | DELETE | 删除题目 |
| `/v2/edu/question/import` | POST | 批量导入（Excel/JSON） |

### AI组卷

| 接口 | 方法 | 说明 |
|------|------|------|
| `/v2/edu/paper/generate` | POST | 智能生成试卷 |
| `/v2/edu/paper/{id}` | GET | 获取试卷详情 |
| `/v2/edu/paper/{id}/adjust` | PUT | 替换某题 |

### AI阅卷

| 接口 | 方法 | 说明 |
|------|------|------|
| `/v2/edu/marking/submit` | POST | 提交答题卡，返回任务ID |
| `/v2/edu/marking/result/{taskId}` | GET | 轮询阅卷结果 |
| `/v2/edu/marking/confirm` | PUT | 教师确认/修正评分 |

### 知识库

| 接口 | 方法 | 说明 |
|------|------|------|
| `/v2/edu/knowledge/upload` | POST | 上传文档（PDF/Word） |
| `/v2/edu/knowledge/search` | GET | 语义检索 |
| `/v2/edu/knowledge/graph` | GET | 知识点图谱 |

### 错题库

| 接口 | 方法 | 说明 |
|------|------|------|
| `/v2/edu/wrongbook/list` | GET | 按学生/学科/知识点查询 |
| `/v2/edu/wrongbook/review` | POST | 生成复习卷 |
| `/v2/edu/wrongbook/mastery` | PUT | 更新掌握度 |

## 配置示例

```yaml
plugin:
  eduai:
    marking:
      async: true                # 是否异步阅卷
      timeout: 300               # 阅卷超时（秒）
    paper:
      max-total-score: 100       # 试卷最高总分
      default-question-count: 20 # 默认题目数量
    knowledge:
      vector-dim: 768            # 向量维度
```

## 相关链接

- [返回主文档](../README.md)

## 许可证

本项目采用 [Apache License 2.0](../LICENSE) 许可证。