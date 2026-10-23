#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成汉字库初始化数据（initdata 脚本）。

用途
----
「汉字识字」玩法与「基础数据维护」页需要成规模的汉字数据。手工在页面逐字录入
3500 字不现实，而直接从三方接口在线取数会让新装环境「装完没数据可玩」。
本脚本从公开数据集生成一份**随包入库**的初始化数据，做到：

    新装即有 3500 个常用字可玩 + 采集管理里仍可继续续采。

数据来源（全部为公开数据集，文本逐字取自源，未人工转写）
--------------------------------------------------------
1. 字表：现代汉语常用字表（一级 3500 字），逐字取自
   https://github.com/elephantnose/characters （3500常用汉字.txt）
2. 字段主源：chinese-xinhua 的**单字字典** word.json，提供
   word(字) / pinyin(拼音) / strokes(笔画数) / radicals(部首) / explanation(中文释义)
   https://github.com/pwxcoo/chinese-xinhua
3. 部首补缺 + 结构：makemeahanzi（提供 radical 与 IDS 部件分解 ⿰⿱⿴…），
   由 IDS 首字符推导间架结构
   https://github.com/skishore/makemeahanzi

字段口径
--------
| 列 | 来源 | 说明 |
|---|---|---|
| hanzi_char | 字表 | 一个字一条 |
| hanzi_pinyin | word.json.pinyin | 带声调；源数据把 -ng 的 g 写成 U+0261 ɡ，已修正为 g |
| hanzi_strokes | word.json.strokes | 整数 |
| hanzi_radical | radicals → more 里正则 → makemeahanzi.radical | 三级回退 |
| hanzi_structure | makemeahanzi 的 IDS 推导 | **只取可靠映射**；含「？」或首字符为 ⿸/⿻ 时留空（见 IDS_STRUCTURE 注释） |
| hanzi_stroke_order | **无** | 公开数据集只给 SVG 笔顺轨迹，没有笔画名；留空由老师按教材口径补 |
| hanzi_meaning | word.json.explanation 断句整理 | 取本义段（避开古籍例句堆）+「又如」组词，截到 --meaning-chars |
| hanzi_grade_level | 常量 小学 | 与 V1.10 种子、GradeLevels 的中文名口径一致 |
| hanzi_status | 常量 1 | 启用 |

幂等
----
输出用 INSERT IGNORE，靠 edu_hanzi 的唯一索引 uk_edu_hanzi_char 去重，
重复生成/重复执行不会产生重复行，也不会覆盖已有数据（如 V1.10 里带笔顺的 12 个字）。

用法
----
    python3 gen-hanzi-seed.py --out V1.12__initdata_all_eduai.sql

退出码非 0 表示数据源不可用或覆盖率不达标（不会产出半成品文件）。
"""

import argparse
import json
import os
import re
import sys
import unicodedata
import urllib.request
import zipfile
from io import BytesIO

# ---------------------------------------------------------------- 数据源

# 字表：现代汉语常用字表一级字（3500）。主源 + 镜像，任一可用即可。
CHAR_LIST_URLS = [
    "https://raw.githubusercontent.com/elephantnose/characters/master/3500%E5%B8%B8%E7%94%A8%E6%B1%89%E5%AD%97.txt",
    "https://cdn.jsdelivr.net/gh/elephantnose/characters@master/3500%E5%B8%B8%E7%94%A8%E6%B1%89%E5%AD%97.txt",
]

# 单字字典：27MB，**只有 raw.githubusercontent 取得到**（jsdelivr 对 >20MB 直接拒绝，
# 实测返回 "File size exceeded the configured limit of 20 MB."）。
DICT_URLS = [
    "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/word.json",
]

# 部首补缺 + IDS 结构分解（JSONL，一行一个对象）。
IDS_URLS = [
    "https://raw.githubusercontent.com/skishore/makemeahanzi/master/dictionary.txt",
    "https://cdn.jsdelivr.net/gh/skishore/makemeahanzi@master/dictionary.txt",
]

EXPECTED_CHARS = 3500
MIN_DICT_COVERAGE = 0.99
MIN_MEANING_COVERAGE = 0.98

HANZI_RE = re.compile(r"[\u4e00-\u9fff]")

# IDS 首字符 -> 间架结构。只收**可靠**的映射：
#
# 实测（对 3500 常用字统计 IDS 首字符分布）两类必须排除，否则会给老师错数据：
#   ⿸(152) ⿻(123)  这两个是「笔画级」分解，字形上看不出包围关系。
#       例：七 = ⿻一乚、廴 = ⿸？——按表映射会得到「全包围」，而字典口径是独体字。
#   含 ？ 的分解  表示「有部件未知」，不能据此判定结构。
#       例：万 = ⿱一？、世 = ⿻廿？——前者会误判成「上下」。
# 这两类一律留空，由老师在「基础数据维护」页按教材口径补（与笔顺同样的处理）。
IDS_STRUCTURE = {
    "⿰": "左右",
    "⿱": "上下",
    "⿲": "左中右",
    "⿳": "上中下",
    "⿴": "全包围",
    "⿵": "半包围",
    "⿶": "半包围",
    "⿷": "半包围",
    "⿹": "半包围",
    "⿺": "半包围",
}

# 拼音里唯一需要修正的字符：chinese-xinhua 把 -ng 的 g 写成了 U+0261
# LATIN SMALL LETTER VELAR CLICK（ɡ）。实测 3500 字里出现 773 次，
# 直接入库会得到「dīnɡ」这种孩子会读错的读音。其余非 ASCII 字符都是
# 带调元音（ā á ǎ à…），属正常内容，不做替换。
PINYIN_FIX = {"ɡ": "g"}


def is_latin_letter(ch):
    """是否拉丁字母（含带调形式 ü ǖ ǘ…）。

    用 Unicode 名判定而不是硬编码白名单：白名单漏一个带调元音就会把整条读音
    清空（实测第一版就漏掉了 á/à/ā…，导致 14 个字的拼音变空）。
    """
    if "a" <= ch <= "z":
        return True
    try:
        return unicodedata.name(ch).startswith("LATIN ")
    except ValueError:
        return False

# 采集任务种子：让「采集管理」里能一键续采（默认停用，页面设 cron 后启用）。
COLLECT_TASK_NAME = "汉字采集·常用字3500"
COLLECT_TASK_CONFIG = {
    "url": DICT_URLS[0],
    "charListUrl": CHAR_LIST_URLS[0],
    "idsUrl": IDS_URLS[0],
    "limit": 500,
    "gradeLevel": "小学",
    # 与 --meaning-chars 默认值、以及 Provider 内的默认值保持一致：
    # 种子数据与在线采集必须同一口径，否则「续采」出来的数据会和随包种子不一样
    "meaningMaxChars": 90,
}


def log(msg):
    print(msg, flush=True)


def fetch(url, timeout=300):
    req = urllib.request.Request(url, headers={"User-Agent": "eduai-seed-generator/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def fetch_first(urls, timeout=300):
    """按序尝试镜像，全失败则抛异常（不做静默降级）。"""
    last = None
    for url in urls:
        try:
            log("  取数 %s" % url)
            return fetch(url, timeout), url
        except Exception as exc:  # noqa: BLE001
            last = exc
            log("    失败: %s" % exc)
    raise RuntimeError("全部镜像不可用: %s" % last)


def load_char_list():
    raw, used = fetch_first(CHAR_LIST_URLS)
    text = raw.decode("utf-8", errors="replace")
    chars = []
    seen = set()
    for ch in text:
        if HANZI_RE.match(ch) and ch not in seen:
            seen.add(ch)
            chars.append(ch)
    log("  字表 %s -> %d 字" % (used.rsplit("/", 1)[-1], len(chars)))
    if len(chars) != EXPECTED_CHARS:
        raise RuntimeError("字表字数 %d != 期望 %d，源数据可能变了，拒绝产出" % (len(chars), EXPECTED_CHARS))
    return chars


def load_dict():
    raw, used = fetch_first(DICT_URLS, timeout=600)
    log("  字典 %s -> %.1f MB" % (used.rsplit("/", 1)[-1], len(raw) / 1048576.0))
    data = json.loads(raw.decode("utf-8", errors="replace"))
    out = {}
    for item in data:
        if not isinstance(item, dict):
            continue
        word = (item.get("word") or "").strip()
        if len(word) == 1 and HANZI_RE.match(word):
            out[word] = item
    log("  字典单字条目 %d" % len(out))
    return out


def load_ids():
    """makemeahanzi 的 IDS 分解：字 -> (radical, structure)。失败不致命（只影响结构列）。"""
    try:
        raw, used = fetch_first(IDS_URLS, timeout=600)
    except Exception as exc:  # noqa: BLE001
        log("  [警告] IDS 源不可用，hanzi_structure 将留空: %s" % exc)
        return {}
    log("  IDS %s -> %.1f MB" % (used.rsplit("/", 1)[-1], len(raw) / 1048576.0))
    out = {}
    for line in raw.decode("utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            item = json.loads(line)
        except Exception:  # noqa: BLE001
            continue
        ch = (item.get("character") or "").strip()
        if len(ch) != 1 or not HANZI_RE.match(ch):
            continue
        radical = (item.get("radical") or "").strip()
        decomposition = (item.get("decomposition") or "").strip()
        out[ch] = (radical, structure_of(decomposition), decomposition)
    log("  IDS 单字条目 %d" % len(out))
    return out


def clean_text(text):
    """压掉换行/多余空白：字典释义是多段排版，直接入 VARCHAR 会很难读。"""
    if not text:
        return ""
    text = unicodedata.normalize("NFKC", text)
    text = text.replace("\r", "\n")
    # 分隔线、下划线、全角空格等排版噪声
    text = re.sub(r"[─-╿—…·]{3,}", " ", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def normalize_pinyin(text):
    """归一化读音：修 U+0261、只保留拉丁字母、转小写。

    数据源把 -ng 的 g 写成 ɡ（velar click），不修就是「dīnɡ」。
    非拉丁字符（空格、连字符、多余符号）一律丢弃。
    """
    if not text:
        return None
    out = []
    for ch in text.strip().lower():
        ch = PINYIN_FIX.get(ch, ch)
        if is_latin_letter(ch):
            out.append(ch)
    return "".join(out).strip() or None


# 释义里的「模板化后续」标记：出现这些说明已经进入古籍例句/又如段，
# 对识字教学没有价值，在此处断句比按字数硬截更自然。
MEANING_TAIL_MARKERS = ("同本义", "又如", "--《")

# 释义不可用的两种形态（实测 3500 字里 25 条）：
#   1) 空壳：释义就是字本身（如「乎」-> "乎"），等于没有解释；
#   2) 注音条目：含谚文/假名，是别的语种的读音条目，不是中文释义。
MEANING_FOREIGN_RE = re.compile(r"[\uac00-\ud7af\u3040-\u30ff]")
MIN_MEANING_CORE_CHARS = 8


def meaning_is_usable(text):
    """释义是否对识字教学有用。"""
    if not text:
        return False
    if MEANING_FOREIGN_RE.search(text):
        return False
    core = re.sub(r"[^\u4e00-\u9fff]", "", text)
    return len(core) >= MIN_MEANING_CORE_CHARS


def condense_meaning(explanation, head_limit):
    """把字典长释义压成一条**教学可用**的短释义。

    数据源的释义结构是「词头 (造字法。本义…) 同本义 <古籍例句堆> 又如<组词>」。
    直接按总长截断会让「一」的释义变成半页《说文》引文（识字界面上一屏都是
    古籍名句反而看不清字的意思）。所以：
      1) 清排版；
      2) 先在 head_limit 窗口里找「同本义 / 又如 / --《」这些**模板化后续标记**，
         找到就在标记处断句 —— 这是最自然的句界，比按字数硬截好；
         找不到才退到最近的句读标点；
      3) 原文里的「又如…」组词单独摘出来附在后面（识字教学真正用得上的部分）。

    @param explanation 原始释义
    @param head_limit  释义正文保留长度
    @return 短释义；无内容返回 None
    """
    text = clean_text(explanation)
    if not text:
        return None
    if len(text) <= head_limit:
        return text
    window = text[:head_limit]
    cut = -1
    for marker in MEANING_TAIL_MARKERS:
        idx = window.find(marker)
        # 只认「过半处」的标记：靠前的标记往往只是词头的一部分（如「本义」二字）。
        # 取**最早**的一个：后面的标记（如 --《古籍）总在更后面，用它会把
        # 「同本义」之后那一段引文又捞回来（实测「一」的释义因此多出 24 字引文）。
        if idx >= head_limit // 2 and (cut < 0 or idx < cut):
            cut = idx
    if cut < 0:
        cut = max(window.rfind(ch) for ch in "。；;，,、")
    if cut < head_limit // 3:
        cut = head_limit
    head = window[:cut].strip().rstrip("，,、;；")
    m = re.search(r"又如([^。；]{1,60})", text)
    if m:
        words = "；".join(part.strip() for part in re.split(r"[；;]", m.group(1)) if part.strip())
        if words:
            head = (head + "；组词：" + words).strip()
    return head or None


def structure_of(decomposition):
    """由 IDS 部件分解推导间架结构；不可靠时返回空（宁可留空也不给错数据）。

    @param decomposition makemeahanzi 的 decomposition 字段
    @return 结构名或 ""
    """
    if not decomposition:
        return ""
    if "？" in decomposition:
        # 有部件未知，整体结构不可判
        return ""
    if len(decomposition) == 1 and HANZI_RE.match(decomposition):
        # 单一部件 = 独体字
        return "独体"
    return IDS_STRUCTURE.get(decomposition[0], "")


def radical_of(item, ids_entry):
    """部首三级回退：radicals 字段 -> more 字段正则 -> makemeahanzi。"""
    value = (item.get("radicals") or "").strip()
    if value:
        return value[:8]
    more = (item.get("more") or "")
    m = re.search(r"部首\s*(\S+)", more)
    if m:
        return m.group(1)[:8]
    if ids_entry:
        return (ids_entry[0] or "")[:8]
    return ""


def sql_str(value):
    if value is None:
        return "NULL"
    text = str(value).replace("\\", "\\\\").replace("'", "''")
    text = "".join(ch for ch in text if ch == "\t" or ord(ch) >= 32)
    if text == "":
        return "''"
    return "'" + text + "'"


def build_rows(chars, dict_map, ids_map, meaning_chars):
    rows = []
    missing = []
    dropped_meaning = []
    for ch in chars:
        item = dict_map.get(ch)
        if item is None:
            missing.append(ch)
            continue
        ids_entry = ids_map.get(ch)
        decomposition = ids_entry[2] if ids_entry else ""
        pinyin = normalize_pinyin(item.get("pinyin") or "")
        try:
            strokes = int(str(item.get("strokes") or "").strip())
        except ValueError:
            strokes = None
        meaning = condense_meaning(item.get("explanation"), meaning_chars)
        if not meaning_is_usable(meaning):
            dropped_meaning.append(ch)
            meaning = None
        rows.append({
            "hanzi_char": ch,
            "hanzi_pinyin": pinyin,
            "hanzi_strokes": strokes,
            "hanzi_radical": radical_of(item, ids_entry) or None,
            "hanzi_structure": structure_of(decomposition) or None,
            "hanzi_stroke_order": None,
            "hanzi_meaning": meaning,
            "hanzi_grade_level": "小学",
            "hanzi_status": 1,
        })
    return rows, missing, dropped_meaning


COLUMNS = ("hanzi_char", "hanzi_pinyin", "hanzi_strokes", "hanzi_radical",
           "hanzi_structure", "hanzi_stroke_order", "hanzi_meaning",
           "hanzi_grade_level", "hanzi_status")


def render(rows):
    out = []
    batch = 200
    for start in range(0, len(rows), batch):
        chunk = rows[start:start + batch]
        values = ",\n    ".join(
            "(" + ", ".join(sql_str(row.get(c)) for c in COLUMNS) + ")" for row in chunk)
        out.append("INSERT IGNORE INTO edu_hanzi\n    (%s)\nVALUES\n    %s;\n"
                   % (", ".join(COLUMNS), values))
    return "\n".join(out)


def render_task(config_json):
    return (
        "-- 采集任务种子：默认停用（与成语/古诗同口径）。进「采集管理」设置 cron 后启用，\n"
        "-- 或直接点「立即执行」从公开单字字典续采剩余汉字。\n"
        "INSERT INTO edu_collect_task (collect_task_name, collect_task_source_type, collect_task_source_config, collect_task_subject, collect_task_grade_level, collect_task_grade, collect_task_difficulty, collect_task_status, collect_task_remark)\n"
        "SELECT '%s', 'HANZI', '%s', '语文', '小学', NULL, 3, 0, '%s'\n"
        "WHERE NOT EXISTS (SELECT 1 FROM edu_collect_task WHERE collect_task_name = '%s');\n"
        % (COLLECT_TASK_NAME,
           config_json.replace("'", "''"),
           "汉字采集：公开单字字典（拼音/笔画/部首/中文释义）+ makemeahanzi IDS（结构），"
           "按 hanzi_char 去重，单次 500 字。默认停用：请在页面设置 cron 后启用，再点「立即执行」取数。",
           COLLECT_TASK_NAME)
    )


def main():
    parser = argparse.ArgumentParser(description="生成汉字库初始化数据")
    parser.add_argument("--out", default="V1.12__initdata_all_eduai.sql")
    parser.add_argument("--meaning-chars", type=int, default=90,
                        help="释义正文保留长度（hanzi_meaning 已是 TEXT，不写超；该参数控制种子释义简洁度）")
    parser.add_argument("--batch", type=int, default=200)
    args = parser.parse_args()

    log("== 1/4 取字表 ==")
    chars = load_char_list()
    log("== 2/4 取单字字典 ==")
    dict_map = load_dict()
    log("== 3/4 取 IDS（部首补缺 + 结构）==")
    ids_map = load_ids()
    log("== 4/4 组装 ==")
    rows, missing, dropped_meaning = build_rows(chars, dict_map, ids_map, args.meaning_chars)

    coverage = len(rows) / float(len(chars))
    filled = lambda key: sum(1 for r in rows if r.get(key))
    log("  组装 %d 行（覆盖率 %.2f%%），缺字 %d：%s" % (len(rows), coverage * 100, len(missing), "".join(missing)))
    log("  字段填充：拼音 %d/%d  笔画 %d/%d  部首 %d/%d  结构 %d/%d  释义 %d/%d"
        % (filled("hanzi_pinyin"), len(rows), filled("hanzi_strokes"), len(rows),
           filled("hanzi_radical"), len(rows), filled("hanzi_structure"), len(rows),
           filled("hanzi_meaning"), len(rows)))
    if dropped_meaning:
        log("  释义不可用已留空 %d：%s" % (len(dropped_meaning), "".join(dropped_meaning)))

    if coverage < MIN_DICT_COVERAGE:
        raise RuntimeError("字典覆盖率 %.2f%% < 阈值 %.0f%%，拒绝产出" % (coverage * 100, MIN_DICT_COVERAGE * 100))
    no_pinyin = [r["hanzi_char"] for r in rows if not r["hanzi_pinyin"]]
    if no_pinyin:
        raise RuntimeError("存在缺拼音的行（%d）：%s" % (len(no_pinyin), "".join(no_pinyin[:20])))
    # 释义允许少量缺失（源数据里确有注音/空壳条目），但整体覆盖率必须达标，
    # 否则说明数据源结构变了，要人工看过再产出。
    meaning_ratio = filled("hanzi_meaning") / float(len(rows))
    if meaning_ratio < MIN_MEANING_COVERAGE:
        raise RuntimeError("可用释义覆盖率 %.2f%% < 阈值 %.0f%%，拒绝产出"
                           % (meaning_ratio * 100, MIN_MEANING_COVERAGE * 100))

    header = (
        "-- ===========================================\n"
        "-- 汉字库初始化数据（现代汉语常用字表一级 %d 字）\n"
        "--\n"
        "-- 由 tools/gen-hanzi-seed.py 生成，**不要手工编辑**：改数据请改脚本后重跑，\n"
        "-- 否则脚本头里的来源与计数会和内容对不上。\n"
        "--\n"
        "-- 数据来源（公开数据集，文本逐字取自源，未人工转写）：\n"
        "--   字表  现代汉语常用字表一级 %d 字  %s\n"
        "--   字段  chinese-xinhua 单字字典 word.json（拼音/笔画数/部首/中文释义）\n"
        "--   结构  makemeahanzi IDS 部件分解（部首补缺 + 间架结构推导）\n"
        "--\n"
        "-- 口径：\n"
        "--   * 幂等：INSERT IGNORE + uk_edu_hanzi_char，重复执行不产生重复行，\n"
        "--     也不会覆盖已有数据（如 V1.10 里带笔顺的 12 个字）。\n"
        "--   * 拼音已把源数据里的 U+0261（ɡ）修正为 g，否则会得到「dīnɡ」。\n"
        "--   * 释义取本义段（%d 字内，在「同本义/又如/古籍」处断句）+「又如」组词，\n"
        "--     避开大段古籍引文（hanzi_meaning 已是 TEXT，不写超）。\n"
        "--   * 笔顺（hanzi_stroke_order）留空：公开数据集只给 SVG 笔顺轨迹、没有笔画名，\n"
        "--     由老师按教材口径在「基础数据维护」页补。\n"
        "--   * 结构只取可靠映射：含「？」或首字符为 ⿸/⿻ 的分解一律留空\n"
        "--     （那两类会把独体字判成包围结构），详见脚本 IDS_STRUCTURE 注释。\n"
        "--   * 学段固定「小学」（与 V1.10 种子、GradeLevels 的中文名口径一致）。\n"
        "--\n"
        "-- 规模：%d 行；缺字 %d（%s）；释义不可用留空 %d（%s）；\n"
        "--       其余字段填充率见生成器输出。\n"
        "-- 采集侧：本文件末尾同时种一条「%s」采集任务，默认停用。\n"
        "-- ===========================================\n\n"
        % (EXPECTED_CHARS, EXPECTED_CHARS, CHAR_LIST_URLS[0], args.meaning_chars,
           len(rows), len(missing), "".join(missing) or "无",
           len(dropped_meaning), "".join(dropped_meaning) or "无", COLLECT_TASK_NAME)
    )

    config_json = json.dumps(COLLECT_TASK_CONFIG, ensure_ascii=False, sort_keys=True)
    body = render(rows)
    task = render_task(config_json)

    tmp = args.out + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(header)
        fh.write(body)
        fh.write("\n-- ------------------------------------------------------------\n")
        fh.write("-- 采集任务\n")
        fh.write("-- ------------------------------------------------------------\n")
        fh.write(task)
    os.replace(tmp, args.out)
    log("==> 已写出 %s（%.1f KB，%d 行汉字 + 1 条采集任务）"
        % (args.out, os.path.getsize(args.out) / 1024.0, len(rows)))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # noqa: BLE001
        print("生成失败: %s" % exc, file=sys.stderr)
        sys.exit(1)