# 数据库迁移技能

## 描述
管理数据库 schema 变更，支持版本控制和回滚。

## 触发条件
- 用户请求数据库迁移
- Entity 类发生变化时

## 功能
- 自动生成 SQL 迁移脚本
- 版本控制
- 回滚支持
- 数据完整性检查

## 使用方式
```bash
# 生成迁移脚本
skill db-migrate --generate --name "add_user_table"

# 执行迁移
skill db-migrate --run --version 1.0.0

# 回滚迁移
skill db-migrate --rollback --version 1.0.0
```

## 输出格式
- SQL 文件
- 版本号管理
- 执行日志
