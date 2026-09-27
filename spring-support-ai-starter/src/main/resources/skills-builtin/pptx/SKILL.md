---
name: pptx
icon: SlideLayout20Regular
title: PPT 演示文稿
description: 创建、读取、编辑 PowerPoint（.pptx），处理幻灯片、版式、文本框、图片、图表、动画与备注。
version: 1.0.0
author: system
tags:
  - presentation
  - office
  - slides
---

# PPT 演示文稿处理

## 环境

```bash
python -m pip install python-pptx
```

## 基础操作

```python
from pptx import Presentation
from pptx.util import Inches, Pt

prs = Presentation('template.pptx')

# 读取幻灯片
for slide in prs.slides:
    for shape in slide.shapes:
        if shape.has_text_frame:
            print(shape.text)

# 新建幻灯片
slide_layout = prs.slide_layouts[1]  # 标题和内容
slide = prs.slides.add_slide(slide_layout)
slide.shapes.title.text = '标题'
slide.placeholders[1].text = '内容'

# 保存
prs.save('output.pptx')
```

## 常用技巧

- 使用模板：`Presentation('template.pptx')` 保留母版和版式
- 插入图片：`slide.shapes.add_picture('img.png', Inches(1), Inches(1))`
- 设置字体：`shape.text_frame.paragraphs[0].font.size = Pt(24)`
- 批量修改：遍历 `prs.slides` 批量替换文本
