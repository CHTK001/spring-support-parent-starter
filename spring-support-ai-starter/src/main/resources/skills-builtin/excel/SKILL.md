---
name: excel
icon: DocumentTable20Regular
title: Excel 电子表格
description: 创建、读取、分析与编辑 Excel（.xlsx/.xls）及 CSV，处理工作表、单元格、公式、条件格式、图表、数据透视与批量数据，并保留既有格式。
version: 1.0.0
author: system
tags:
  - spreadsheet
  - office
  - data
---

# Excel / CSV 处理

- `.xlsx` 读写与格式用 `openpyxl`；数据分析、批量计算用 `pandas`；老式 `.xls` 用 `xlrd`。
- `.csv` 用 `pandas.read_csv` 或标准库 `csv`，注意编码（优先 `utf-8-sig`，Excel 打开中文不乱码）。

## 环境

```bash
python -m pip install openpyxl pandas
```

## 读取与分析（pandas）

```python
import pandas as pd
df = pd.read_excel(path, sheet_name=0)   # 或 read_csv(path)
df.head(); df.info(); df.describe()
df.groupby('列').agg({'金额': 'sum'})
```

- 合并用 `merge`，分组聚合用 `groupby`，缺失值用 `isna()/fillna()`。
- 写出：`df.to_excel(path, index=False)`、`df.to_csv(path, index=False, encoding='utf-8-sig')`。

## 格式与公式（openpyxl）

- 打开：`load_workbook(path)`；`wb.sheetnames`、`wb['Sheet1']`。
- 单元格：`ws['A1'].value`；`ws.cell(row, column).value`；按行 `ws.iter_rows(values_only=True)`。
- 公式直接写字符串：`ws['D2'] = '=SUM(A2:C2)'`。
  - **注意**：openpyxl 不计算公式结果；需要缓存结果时用 LibreOffice 重算：`soffice --headless --convert-to xlsx <file>`，或在读取时用 `data_only=True`（仅在文件曾被 Excel/LO 打开计算后有效）。
- 样式：`Font(bold=True, color='FFFFFF')`、`PatternFill('solid', fgColor='4472C4')`、`Alignment(horizontal='center')`、`Border(...)`。
- 数字格式：`cell.number_format = '#,##0.00'`；日期 `'yyyy-mm-dd'`；百分比 `'0.0%'`。
- 列宽：`ws.column_dimensions['A'].width = 20`；冻结首行：`ws.freeze_panes = 'A2'`。
- 条件格式：`from openpyxl.formatting.rule import ColorScaleRule, CellIsRule`。
- 图表：`BarChart/LineChart/PieChart`，`ws.add_chart(chart, 'E2')`。

## 大数据量

- 行数很多时分块：`pd.read_csv(path, chunksize=100000)` 逐块聚合，避免一次性占满内存。
- openpyxl 只读模式：`load_workbook(path, read_only=True)`；写出用 `write_only=True`。

## 修改原则与交付

- 编辑已有表格时，先用 `load_workbook`（不带 data_only）保留公式与样式，只改目标区域，避免 `pandas` 整表重写导致格式丢失。
- 保存后回读校验行列数与关键单元格；交付时给出绝对路径、各工作表名称与主要改动。
