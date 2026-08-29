# 文档生成技能

## 描述
根据代码自动生成 API 文档、README 和使用说明。

## 触发条件
- 用户请求生成文档
- 代码结构发生变化时

## 功能
- API 文档生成
- README 自动生成
- 代码注释提取
- 多语言文档支持

## 使用方式
```bash
# 生成 API 文档
skill doc-gen --type api --source src/

# 生成 README
skill doc-gen --type readme --project "My Project"
```

## 输出格式
- Markdown 格式
- 包含代码示例
- 支持自定义模板
