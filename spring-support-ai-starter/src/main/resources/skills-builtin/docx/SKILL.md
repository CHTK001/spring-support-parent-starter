---
name: docx
icon: Document20Regular
title: Word 文档处理
description: 创建、读取、编辑 Microsoft Word（.docx）文档，处理标题、段落、样式、列表、表格、图片、页眉页脚与批注，并在修改时保留原有格式。
version: 1.0.0
author: system
tags:
  - document
  - office
  - word
---

# Word 文档处理（.docx）

处理 `.docx` 时优先使用 Python 的 `python-docx`；不要把 `.docx` 当纯文本解析（它是 ZIP+XML）。始终使用中文与用户沟通，并在动手前确认文件路径与目标格式。

## 环境

```bash
python -m pip install python-docx
```

如需在旧版 `.doc`（二进制）与 `.docx` 间转换，使用 LibreOffice：`soffice --headless --convert-to docx <file>`。

## 读取

1. `Document(path)` 打开文档。
2. 遍历 `doc.paragraphs`，通过 `paragraph.style.name` 判断标题级别（如 `Heading 1`）。
3. 表格用 `doc.tables`，行/列用 `table.rows` / `table.columns`，单元格用 `cell.text`。
4. 需要完整顺序（段落与表格混排）时按 `doc.element.body` 的 XML 顺序遍历。

## 创建与编辑

- 标题：`doc.add_heading(text, level=1)`；正文：`doc.add_paragraph(text, style=...)`。
- 样式优先复用文档已有样式（`styles`），不要手工拼字号，以保持全篇一致。
- 列表：项目符号用样式 `List Bullet`，编号用 `List Number`。
- 表格：`doc.add_table(rows, cols)`，首行可设为表头；`table.style = 'Light Grid Accent 1'`。
- 图片：`doc.add_picture(path, width=Cm(8))`；内联图片放入单元格用 `cell.paragraphs[0].add_run().add_picture(...)`。
- 页眉页脚：`doc.sections[i].header.paragraphs[0]`。
- 加粗/斜体用 `run.bold = True`、`run.italic = True`，一个 run 表示一段相同格式的文本。

## 修改已有文档的原则

- **保留原格式**：定位到目标段落/表格后就地修改 `run.text` 或新增 run，避免整篇重建。
- 删除段落需操作其底层 XML：`p._element.getparent().remove(p._element)`。
- 保存为新文件或覆盖前先备份；最后用 `python -c "from docx import Document; Document('<out>')"` 回读校验可正常打开。

## 交付

完成后告知输出文件的绝对路径、所做改动，并提示用 Word/WPS 打开核对；涉及字体时提醒中文字体（如宋体、微软雅黑）可能需显式设置 `run.font.name` 与东亚字体 `w:eastAsia`。
