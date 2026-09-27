---
name: pdf
icon: DocumentPdf20Regular
title: PDF 文档处理
description: 读取、提取、合并、拆分、创建 PDF，处理文本、表格、图片、表单与扫描件 OCR，并尽量保留版面结构。
version: 1.0.0
author: system
tags:
  - pdf
  - document
  - ocr
---

# PDF 处理

按任务选择库：文本/表格提取用 `pdfplumber` 或 `PyMuPDF(fitz)`；合并拆分用 `pypdf`；创建用 `reportlab`；扫描件用 OCR。

## 环境

```bash
python -m pip install pdfplumber pymupdf pypdf reportlab
# OCR：系统需安装 tesseract，再装 pytesseract
python -m pip install pytesseract pdf2image
```

## 提取文本与表格

```python
import pdfplumber
with pdfplumber.open(path) as pdf:
    for page in pdf.pages:
        text = page.extract_text()
        tables = page.extract_tables()   # 返回二维列表
```

- 多栏排版用 `page.extract_text(x_tolerance=..., y_tolerance=...)` 或按裁剪框 `page.clip(bbox).extract_text()` 分栏读取。
- PyMuPDF 速度快、适合大文件：`fitz.open(path)`、`page.get_text()`、`page.get_text('dict')` 可拿到带坐标的块。

## 图片

- 提取：`page.get_images()`（PyMuPDF）后用 `doc.extract_image(xref)` 保存。
- 页面转图片：`pix = page.get_pixmap(dpi=200); pix.save('p.png')`。

## 扫描件 OCR

1. `pdf2image.convert_from_path(path, dpi=300)` 转图像。
2. `pytesseract.image_to_string(img, lang='chi_sim+eng')`（中文需中文语言包）。
3. 版面差时先做灰度、二值化、去噪可提高准确率；OCR 结果需人工抽检，不要宣称完全准确。

## 合并 / 拆分 / 表单

```python
from pypdf import PdfReader, PdfWriter
w = PdfWriter()
w.append('a.pdf'); w.append('b.pdf'); w.write('out.pdf')
# 拆分：w.add_page(reader.pages[i])
```

- 表单填写：`pypdf` 的 `update_page_form_field_values`；扁平化（锁定不可再编辑）可在填写后用 PyMuPDF 处理。

## 创建 PDF（reportlab）

- 用 Platypus：`SimpleDocTemplate` + `Paragraph/Spacer/Table/Image`，自动分页。
- 中文必须注册字体（如 `TTFont('STSong', 'SimSun.ttf')`）并在样式中使用，否则中文乱码。

## 原则与交付

- PDF 提取可能丢顺序、合并断行，表格尤甚：关键数字要回原文核对。
- 加密 PDF 先确认是否有权限解密；不要绕过密码保护。
- 交付时给出绝对路径、页数、提取/生成方式与已知误差。
