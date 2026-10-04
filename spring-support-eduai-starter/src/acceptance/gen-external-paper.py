#!/usr/bin/env python3
"""生成「外来试卷」图片：题库中不存在的题目 + 学生作答，用于 ANY 模式（模型判分）验收。

设计要点（都来自实测）：
- 作答一律用中文（CJK）。实测这套 OCR 配置下，孤立拉丁字母（如「（B）」「答：B」）
  很容易被整段丢掉，导致作答被判「未作答」；这不是判分链路的问题，而是识别侧对孤立
  拉丁字母的局限。语文类外来卷本就以中文作答，用它验收更贴近真实且可复现。
- 三档作答：第 1 题正确、第 2 题要点不全（部分分）、第 3 题答非所问（0 分），
  用于验证 ANY 判分有区分度而不是「一律给满/给零」。
"""
from PIL import Image, ImageDraw, ImageFont

FONT_PATH = "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf"
OUT = "/home/kali/external-paper.png"

W, H = 1240, 1754
BLACK = (0, 0, 0)

BLOCKS = [
    (
        "1. 请写出「床前明月光」的下一句。",
        "答：疑是地上霜",
    ),
    (
        "2. 请简述记叙文写作中比喻的作用。（不少于两点）",
        "答：比喻能表达感情。",
    ),
    (
        "3. 请说出一位唐代诗人的名字。",
        "答：我喜欢打篮球。",
    ),
]


def main() -> None:
    img = Image.new("RGB", (W, H), (255, 255, 255))
    d = ImageDraw.Draw(img)
    f_title = ImageFont.truetype(FONT_PATH, 46)
    f_q = ImageFont.truetype(FONT_PATH, 36)
    f_ans = ImageFont.truetype(FONT_PATH, 36)

    y = 110
    d.text((W // 2 - 210, y), "外 来 验 收 卷", font=f_title, fill=BLACK)
    y += 100
    d.text((90, y), "姓名：李明    学号：20260099", font=f_ans, fill=BLACK)
    y += 90
    d.line([(80, y), (W - 80, y)], fill=BLACK, width=3)
    y += 70

    for stem, ans in BLOCKS:
        d.text((90, y), stem, font=f_q, fill=BLACK)
        y += 74
        d.text((120, y), ans, font=f_ans, fill=BLACK)
        y += 110

    img.save(OUT)
    print("GENERATED " + OUT)


if __name__ == "__main__":
    main()