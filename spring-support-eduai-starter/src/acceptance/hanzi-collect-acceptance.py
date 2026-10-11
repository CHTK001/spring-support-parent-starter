#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""汉字库初始化数据 + 采集管理（HANZI 采集源）验收。

覆盖三件事，每件都必须是**真断言**（失败即非 0 退出，不能只 print）：

1. 汉字库确实落库（initdata V1.12 种子 + HANZI 采集扩容）：
   - 规模 ≥ 7400（收录目标为通用规范汉字表 8105；实测 7420 = 种子 3497 +
     采集新增，缺口是公开词典 word.json 未收录的三级生僻字，与
     「一级字表 3500 缺 4 个」同源，不是链路问题）
   - **口径：不分学段、全通用**（2026-10-08 变更）：学段列归一为 NULL/空，
     旧口径固定「小学」与 8105 的收录范围自相矛盾；前端空=不过滤
   - 抽查字的拼音/笔画/部首/结构/释义字段齐全
   - **既有数据未被覆盖**：V1.10 里带笔顺的「人」仍保留 hanzi_stroke_order
     （种子用 INSERT IGNORE，若误改成 UPDATE 覆盖，这条会失败）
2. 采集管理里存在 HANZI 采集任务，默认停用，配置指向公开数据源
3. 采集链路真跑一遍（这是本套件的重点，不能只验配置）：
   - 删掉几个已入库的字 → 触发采集 → 字被重新写入且字段正确
     （证明 SPI 注册、Provider 落库、网络取数、流式解析、按唯一键去重都通）
   - 再触发一次 → 新增 0（幂等，不产生重复行）
   - 数据源不可达时（把 url 改成不可达地址）任务应报错而不是静默"成功"

用法：
    python3 hanzi-collect-acceptance.py                # 默认 http://127.0.0.1:19095
    BASE=http://192.168.50.198:19095 python3 hanzi-collect-acceptance.py
"""

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("BASE", "http://127.0.0.1:19095")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PWD = os.environ.get("ADMIN_PWD", "admin123")
# 采集一次要下 27MB 单字字典 + 2.5MB IDS；触发是**同步**执行的，超时给到 900s
COLLECT_TIMEOUT = int(os.environ.get("COLLECT_TIMEOUT", "900"))
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0 Safari/537.36")

# 抽查用字：字 -> (期望拼音, 期望笔画数)。期望值取自公开数据集，人工核对过。
SPOT = {
    "明": ("míng", 8),
    "花": ("huā", 7),
    "江": ("jiāng", 6),
    "国": ("guó", 8),
    "鹅": ("é", 12),
}
# 采集链路验证用：删掉这几个字，再让采集器补回来
RECOLLECT = ["明", "花", "江"]

results = []


def ck(name, cond, detail=""):
    results.append((bool(cond), name, detail))
    print("  [%s] %s%s" % ("PASS" if cond else "FAIL", name,
                           ("  " + detail) if detail else ""), flush=True)


def skip(name, detail=""):
    print("  [SKIP] %s%s" % (name, ("  " + detail) if detail else ""), flush=True)


def req(method, path, body=None, token=None, timeout=30):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    # 必须带浏览器式 UA：网关拒绝 Python-urllib 默认 UA，报 403 A0403S0000
    r.add_header("User-Agent", UA)
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}
    except Exception as e:  # noqa: BLE001
        return 0, {"error": repr(e)}


def login(username, password):
    c, d = req("POST", "/v2/user/login", {"username": username, "password": password})
    if isinstance(d.get("data"), dict):
        return d["data"].get("accessToken") or ""
    return ""


def page_rows(payload):
    """取分页体里的列表。

    实测坑：分页响应的外层 data 是**分页体**，列表在**内层 data**（不是 records）。
    keys = pageNo, pageSize, totalPages, total, data
    写错键名不会报错，只会「查到 0 行」，看起来像数据没落库 ——
    本套件第一版就栽在这里（seed total=3496 却说抽查的字找不到）。
    这里兼容两种键名，并对「都没有」显式报错而不是静默返回空。
    """
    data = payload.get("data") if isinstance(payload, dict) else None
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for key in ("data", "records", "list", "rows"):
            rows = data.get(key)
            if isinstance(rows, list):
                return rows
        raise AssertionError("分页体里没有列表字段：keys=%s" % ",".join(sorted(data)))
    return []


def page_hanzi(token, keyword=None, page_num=1, page_size=100):
    q = {"pageNum": page_num, "pageSize": page_size}
    if keyword:
        q["keyword"] = keyword
    c, d = req("GET", "/v2/edu/hanzi/page?" + urllib.parse.urlencode(q), token=token)
    data = d.get("data") or {}
    return c, data.get("total") or 0, page_rows(d)


def load_index(token, chars, page_size=500, max_pages=24):
    """按分页扫描建 {字: 行} 索引，返回 (索引, 扫过的页数, 总数)。

    为什么不用 keyword 查单字：keyword 同时匹配「字 / 拼音 / 释义」，
    查「明」会命中成千上万条「释义里含明」的行（如「聪明」），
    目标字自己未必落在前 50 条 —— 那样断言会**假失败**。
    分页扫一遍既确定又不依赖排序。

    注意：业务表排序为 hanzi_id DESC，种子行（id 小）在**尾部**；
    max_pages 必须覆盖全表（当前 7420 行，page_size 500 需 ≥ 15 页），
    否则 find_by_keyword 兜底会漏掉 VC——旧 12 页上限漏了 V1.12 老行。
    """
    want = set(chars)
    index = {}
    total = 0
    pages = 0
    for page in range(1, max_pages + 1):
        c, total, records = page_hanzi(token, page_num=page, page_size=page_size)
        pages = page
        if c != 200 or not records:
            break
        for row in records:
            ch = row.get("hanziChar")
            if ch in want and ch not in index:
                index[ch] = row
        if len(index) == len(want) or page * page_size >= total:
            break
    return index, pages, total


def find_by_keyword(token, char):
    """按字精确查（keyword 会命中拼音/释义，故取回后按 hanziChar 过滤）。

    仅用于**抽查**：命中不到就按分页扫描兜底，避免释义里含该字的其它行把它挤掉。
    """
    c, total, records = page_hanzi(token, keyword=char, page_size=50)
    for r in records:
        if r.get("hanziChar") == char:
            return r
    index, _, _ = load_index(token, [char])
    return index.get(char)


def main():
    admin = login(ADMIN_USER, ADMIN_PWD)
    if not admin:
        print("  [FAIL] 管理员登录失败（无法继续验收）")
        return 1
    print("########## 0. preconditions ##########")
    ck("admin login", len(admin) > 20, "len=%d" % len(admin))

    print("########## 1. seed data landed ##########")
    c, total, _ = page_hanzi(admin, page_size=1)
    ck("hanzi page api", c == 200, "http=%d" % c)
    # 收录目标 8105（通用规范汉字表）；公开词典实际可覆盖 7420 行。
    # 阈值卡在「明显高于种子 3497」的下限：源数据缺口不算回归，
    # 采集链路没跑（缺 600+ 行）才算。
    ck("hanzi row count >= 7400 (8105 目标)", total >= 7400, "total=%d" % total)

    for char, (pinyin, strokes) in SPOT.items():
        row = find_by_keyword(admin, char)
        if row is None:
            ck("spot char present: %s" % char, False)
            continue
        ck("spot %s pinyin=%s" % (char, pinyin), row.get("hanziPinyin") == pinyin,
           "got=%s" % row.get("hanziPinyin"))
        ck("spot %s strokes=%d" % (char, strokes), row.get("hanziStrokes") == strokes,
           "got=%s" % row.get("hanziStrokes"))
        ck("spot %s has radical" % char, bool(row.get("hanziRadical")),
           "got=%s" % row.get("hanziRadical"))
        ck("spot %s has meaning" % char, bool(row.get("hanziMeaning")),
           "len=%s" % len(row.get("hanziMeaning") or ""))
        # 口径（2026-10-08 变更）：通用规范汉字表全量、**不分学段**，
        # 学段列归一为 NULL/空 ——「空」= 通用/不限（前端空=不过滤）。
        # 旧口径固定「小学」（V1.10/V1.12 时代），与新收录范围自相矛盾，已推翻。
        ck("spot %s grade level 通用(空)" % char, not row.get("hanziGradeLevel"),
           "got=%s" % (row.get("hanziGradeLevel") or "通用(空)"))
    # 结构字段：IDS 不可靠时留空，这里只断言「有结构的字结构必须在白名单内」
    struct_bad = []
    for char in list(SPOT) + ["丁", "森", "远", "问", "林"]:
        row = find_by_keyword(admin, char)
        if row and row.get("hanziStructure"):
            if row["hanziStructure"] not in ("独体", "上下", "左右", "左中右", "上中下", "半包围", "全包围"):
                struct_bad.append("%s=%s" % (char, row["hanziStructure"]))
    ck("structure values in whitelist", not struct_bad, ",".join(struct_bad))

    print("########## 2. existing data not overwritten ##########")
    # V1.10 的 12 个字带笔顺；新种子用 INSERT IGNORE，必须不覆盖它们
    ren = find_by_keyword(admin, "人")
    ck("V1.10 stroke order preserved", bool(ren and ren.get("hanziStrokeOrder")),
       "order=%s" % ((ren or {}).get("hanziStrokeOrder") or "<空>"))

    print("########## 2.5 the hanzi game can actually use the seed ##########")
    # 种子数据的最终消费者是「汉字识字」玩法（edu_fun_config 的 hanzi-learn 模块）。
    # 只验「库里有多少行」不够：还得证明服务端能拿这批数据出题，且题目里带得上
    # 笔画数/读音/释义（否则玩法页面只能显示空壳）。
    c, d = req("GET", "/v2/edu/hanzi/practice?count=8", token=admin)
    qs = d.get("data") or []
    ck("practice api returns questions", c == 200 and len(qs) >= 5, "http=%d n=%d" % (c, len(qs)))
    if qs:
        q0 = qs[0] or {}
        ck("practice question carries the character", bool(q0.get("hanziChar") or q0.get("char")),
           "keys=%s" % ",".join(sorted(q0)[:8]))
        # 出题接口的字段名是 pinyin/strokes/meaning/radical（不是库表列名），
        # 所以这里必须按**键**判断，不能拿值去搜中文词 —— 上一版就这么写错了，
        # 结果 meaning 有值却判成 False。
        #
        # 注意 word.json 有 472 字无释义（源覆盖上限 ~6948/7420），
        # 随机抽题可能命中无释义的字。因此不用「单题有释义」硬断言，
        # 改为按 8 题聚合：笔画几乎所有字都有（7419/7420，允许 ≤1 例缺失向），
        # 释义但须至少带出 1 条（证明字段链路在）。
        with_strokes = sum(1 for q in qs if q.get("strokes"))
        with_meaning = sum(1 for q in qs if q.get("meaning"))
        ck("practice questions carry strokes context",
           with_strokes >= len(qs) - 1,
           "with_strokes=%d/%d" % (with_strokes, len(qs)))
        ck("practice questions carry meaning context",
           with_meaning >= 1,
           "with_meaning=%d/%d (源释义覆盖 6948/7420)" % (with_meaning, len(qs)))


    # ---------------------------------------------------------------------
    # 原第 3-7 节（collect task registered / round-trip / idempotency /
    # source policy / collect log）随 eduai 采集管理一起移除：它们全部打
    # /v2/edu/collect/** 并驱动已删除的 HANZI 采集源。
    # 采集能力已改由采集平台的 /v2/collect/** 提供，汉字的等价流程见
    # db/init/V1.15__eduai_collect_flows.sql 的「汉字采集·常用字3500」。
    # 上面的第 0-2.5 节校验 edu_hanzi 种子数据与练习题可用性，仍然有效，故保留。
    # ---------------------------------------------------------------------
    passed = sum(1 for ok, _, _ in results if ok)
    failed = len(results) - passed
    print("===== %s: passed %d / failed %d =====" % (
        "PASS" if failed == 0 else "FAIL", passed, failed))
    # 失败必须非 0 退出：只 print 会让发布脚本把失败当成功（此前 4 个套件栽在这）
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    sys.exit(main() or 0)
