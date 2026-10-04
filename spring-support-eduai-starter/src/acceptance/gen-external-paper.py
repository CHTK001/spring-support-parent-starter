#!/usr/bin/env python3
"""生成「外来试卷」图片：题库中不存在的题目 + 学生作答，用于 ANY 模式（模型判分）验收。

版式对齐真实答题卡：选择题的作答写在题干行的括号里（与 9002/9003 合成卷一致），
简答题的作答单独一行。避免把作答另起一行——实测那种版式会让 OCR 把「答：B」读成「答0」。
"""
from PIL import Image, ImageDraw, ImageFont

FONT_PATH = "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf"
OUT = "/home/kali/external-paper.png"

W, H = 1240, 1754
BLACK = (0, 0, 0)

# (题干含作答括号, 选项块, 是否简答)
BLOCKS = [
    (
        "1. 下列关于光合作用的说法，正确的是（  B  ）",
        ["A. 光合作用只在夜间进行", "B. 光合作用把光能转化为化学能",
         "C. 光合作用不需要水参与", "D. 光合作用只发生在叶片表面"],
        False,
    ),
    (
        "2. 下列动物中，属于哺乳动物的是（  A  ）",
        ["A. 鲤鱼", "B. 青蛙", "C. 蝴蝶", "D. 鲸鱼"],
        False,
    ),
    (
        "3. 请简述记叙文写作中比喻的作用。（不少于两点）",
        [],
        True,
    ),
]

ANSWER3 = "答：比喻能把事物写得形象生动，也能表达作者的思想感情。"


def main() -> None:
    img = Image.new("RGB", (W, H), (255, 255, 255))
    d = ImageDraw.Draw(img)
    f_title = ImageFont.truetype(FONT_PATH, 44)
    f_q = ImageFont.truetype(FONT_PATH, 34)
    f_opt = ImageFont.truetype(FONT_PATH, 31)
    f_ans = ImageFont.truetype(FONT_PATH, 33)

    y = 90
    d.text((W // 2 - 200, y), "期 中 测 验 试 卷", font=f_title, fill=BLACK)
    y += 92
    d.text((90, y), "姓名：李明    学号：20260099    班级：初二(3)班", font=f_opt, fill=BLACK)
    y += 82
    d.line([(80, y), (W - 80, y)], fill=BLACK, width=3)
    y += 52

    for stem, opts, subjective in BLOCKS:
        d.text((90, y), stem, font=f_q, fill=BLACK)
        y += 64
        for opt in opts:
            d.text((140, y), opt, font=f_opt, fill=BLACK)
            y += 54
        if subjective:
            d.text((120, y), ANSWER3, font=f_ans, fill=BLACK)
            y += 60
        y += 26

    d.text((90, y), "评分：__________", font=f_opt, fill=BLACK)
    img.save(OUT)
    print("GENERATED " + OUT)


if __name__ == "__main__":
    main()