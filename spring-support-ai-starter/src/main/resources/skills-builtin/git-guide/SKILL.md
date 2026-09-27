---
name: git-guide
icon: Branch20Regular
title: Git 版本控制
description: Git 常用命令、分支管理、提交规范、冲突解决与最佳实践。
version: 1.0.0
author: system
tags:
  - git
  - version-control
  - collaboration
---

# Git 版本控制

## 常用命令

### 基础操作

```bash
# 初始化/克隆
git init
git clone <url>

# 查看状态
git status
git log --oneline --graph

# 暂存/提交
git add .
git commit -m "feat: 新功能描述"

# 推送/拉取
git push origin main
git pull origin main
```

### 分支管理

```bash
# 创建/切换分支
git checkout -b feature/login
git checkout main

# 合并分支
git merge feature/login

# 删除分支
git branch -d feature/login
```

## 提交信息规范

```
<type>(<scope>): <subject>

type:
  feat: 新功能
  fix: 修复 bug
  docs: 文档
  style: 格式
  refactor: 重构
  test: 测试
  chore: 构建/工具
```

## 冲突解决

```bash
# 查看冲突文件
git status

# 编辑文件解决冲突后
git add .
git commit
```

## 常用技巧

- `git stash` 临时保存
- `git reset --soft HEAD~1` 撤销上次提交
- `git rebase -i HEAD~3` 交互式变基
- `git cherry-pick <commit>` 拣选提交
