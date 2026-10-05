#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""汉字库初始化数据 + 采集管理（HANZI 采集源）验收。

覆盖三件事，每件都必须是**真断言**（失败即非 0 退出，不能只 print）：

1. 随包初始化数据（initdata V1.12）确实落库：
   - 常用字规模 ≥ 3400（一级字表 3500，源数据缺 4 个生僻字）
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


def load_index(token, chars, page_size=500, max_pages=12):
    """按分页扫描建 {字: 行} 索引，返回 (索引, 扫过的页数, 总数)。

    为什么不用 keyword 查单字：keyword 同时匹配「字 / 拼音 / 释义」，
    查「明」会命中成千上万条「释义里含明」的行（如「聪明」），
    目标字自己未必落在前 50 条 —— 那样断言会**假失败**。
    分页扫一遍既确定又不依赖排序。
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
    ck("seed row count >= 3400", total >= 3400, "total=%d" % total)

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
        ck("spot %s grade level" % char, row.get("hanziGradeLevel") == "小学",
           "got=%s" % row.get("hanziGradeLevel"))
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
        ck("practice question carries stroke/meaning context",
           bool(q0.get("strokes")) and bool(q0.get("meaning")),
           "strokes=%s meaning_len=%s" % (q0.get("strokes"), len(str(q0.get("meaning") or ""))))

    print("########## 3. collect task registered ##########")
    c, d = req("GET", "/v2/edu/collect/source-types", token=admin)
    types = d.get("data") or []
    ck("HANZI in source-types", "HANZI" in types, "types=%s" % ",".join(types))

    c, d = req("GET", "/v2/edu/collect/task/page?pageNum=1&pageSize=50", token=admin)
    records = page_rows(d)
    hanzi_tasks = [t for t in records if t.get("collectTaskSourceType") == "HANZI"]
    ck("HANZI task exists", len(hanzi_tasks) >= 1, "found=%d" % len(hanzi_tasks))
    task = hanzi_tasks[0] if hanzi_tasks else None
    if task is None:
        print("  [FAIL] 没有 HANZI 采集任务，后续采集链路无法验证")
        return 1
    ck("task disabled by default", task.get("collectTaskStatus") == 0,
       "status=%s" % task.get("collectTaskStatus"))
    try:
        cfg = json.loads(task.get("collectTaskSourceConfig") or "{}")
    except Exception:
        cfg = {}
    ck("task config points at public dict", "word.json" in str(cfg.get("url", "")),
       "url=%s" % cfg.get("url"))
    ck("task config has char list", bool(cfg.get("charListUrl")))
    ck("task config has no importProcessor (must use self-persisted path)",
       "importProcessor" not in cfg, "keys=%s" % ",".join(sorted(cfg)))

    def save_task(status, config=None):
        """保存任务（触发前后都要用：触发接口拒绝执行「停用」的任务）。"""
        return req("POST", "/v2/edu/collect/task/save", {
            "collectTaskId": task["collectTaskId"],
            "collectTaskName": task["collectTaskName"],
            "collectTaskSourceType": "HANZI",
            "collectTaskSourceConfig": json.dumps(config if config is not None else cfg,
                                                   ensure_ascii=False),
            "collectTaskSubject": task.get("collectTaskSubject"),
            "collectTaskGradeLevel": task.get("collectTaskGradeLevel"),
            "collectTaskGrade": task.get("collectTaskGrade"),
            "collectTaskDifficulty": task.get("collectTaskDifficulty") or 3,
            "collectTaskStatus": status,
            "collectTaskRemark": task.get("collectTaskRemark"),
        }, token=admin)

    # 触发接口对「停用」任务直接返回 status=skip / 任务未启用（实测），
    # 所以采集链路验证必须先启用；验完恢复停用，别把共享环境的默认态改掉。
    c, d = save_task(1)
    ck("task enabled for the round-trip", c == 200, "http=%d" % c)

    print("########## 4. collect round-trip (delete then recollect) ##########")
    _, before_total, _ = page_hanzi(admin, page_size=1)
    index, pages, scanned_total = load_index(admin, RECOLLECT)
    print("        分页扫描 %d 页/共 %d 条，取到待验证字 %d 个" % (pages, scanned_total, len(index)))
    ids = {}
    for char in RECOLLECT:
        row = index.get(char)
        if row is None:
            ck("recollect target exists: %s" % char, False)
            continue
        ids[char] = row["hanziId"]
    if len(ids) != len(RECOLLECT):
        print("  [FAIL] 待验证的字不全，采集链路无法验证")
        return 1
    for char, hid in ids.items():
        c, d = req("DELETE", "/v2/edu/hanzi/%d" % hid, token=admin)
        ck("delete %s" % char, c == 200 and (d.get("data") in (True, "true")), "http=%d" % c)
    _, after_del_total, _ = page_hanzi(admin, page_size=1)
    ck("row count dropped after delete",
       after_del_total == before_total - len(ids),
       "%d -> %d (删了 %d)" % (before_total, after_del_total, len(ids)))

    t0 = time.time()
    c, d = req("POST", "/v2/edu/collect/task/trigger/%d" % task["collectTaskId"],
               token=admin, timeout=COLLECT_TIMEOUT)
    cost = time.time() - t0
    trigger = d.get("data") or {}
    ck("trigger collect succeeded", c == 200 and trigger.get("status") == "success",
       "http=%d status=%s msg=%s (%.1fs)" % (c, trigger.get("status"), trigger.get("message"), cost))
    print("        采集耗时 %.1fs（下载 27MB 字典 + 2.5MB IDS）" % cost)

    for char in RECOLLECT:
        row = find_by_keyword(admin, char)
        if row is None:
            ck("recollected %s present" % char, False)
            continue
        ck("recollected %s has pinyin" % char, bool(row.get("hanziPinyin")),
           "got=%s" % row.get("hanziPinyin"))
        ck("recollected %s has strokes" % char, bool(row.get("hanziStrokes")),
           "got=%s" % row.get("hanziStrokes"))
        ck("recollected %s has radical" % char, bool(row.get("hanziRadical")),
           "got=%s" % row.get("hanziRadical"))
        ck("recollected %s has meaning" % char, bool(row.get("hanziMeaning")))
    _, after_collect_total, _ = page_hanzi(admin, page_size=1)
    ck("row count restored", after_collect_total == before_total,
       "%d -> %d" % (after_del_total, after_collect_total))

    print("########## 5. idempotency (second run must add 0) ##########")
    c, d = req("POST", "/v2/edu/collect/task/trigger/%d" % task["collectTaskId"],
               token=admin, timeout=COLLECT_TIMEOUT)
    trigger = d.get("data") or {}
    ck("second trigger succeeded", c == 200 and trigger.get("status") == "success",
       "status=%s" % trigger.get("status"))
    _, after_second_total, _ = page_hanzi(admin, page_size=1)
    ck("no duplicates after second run", after_second_total == after_collect_total,
       "%d -> %d" % (after_collect_total, after_second_total))

    print("########## 6. source policy: mirror fallback vs strict pin ##########")
    # 6a 默认（允许镜像兜底）：配置的地址不可达时应自动换镜像并**成功**。
    #     这条断言是镜像容错本身的价值证明 —— 上一版把它写成「必须失败」，
    #     结果与新增的镜像能力自相矛盾（实测 status=success，因为镜像取到了数据）。
    fallback_cfg = dict(cfg)
    fallback_cfg["url"] = "http://127.0.0.1:9/definitely-not-there.json"
    fallback_cfg["useMirrors"] = True
    c, d = save_task(1, fallback_cfg)
    ck("save fallback config accepted", c == 200, "http=%d" % c)
    c, d = req("POST", "/v2/edu/collect/task/trigger/%d" % task["collectTaskId"],
               token=admin, timeout=COLLECT_TIMEOUT)
    trigger = d.get("data") or {}
    ck("broken configured url still succeeds via mirror", trigger.get("status") == "success",
       "status=%s msg=%s" % (trigger.get("status"), (trigger.get("message") or "")[:70]))

    # 6b useMirrors=false（严格 pin 住配置的地址）：不可达必须**报错**。
    #     没有这条就等于没有「只用我配的源」这个能力，出错也被兜底掩盖。
    strict_cfg = dict(cfg)
    # URL must be UNIQUE per run: the provider caches downloads on disk (12h), and a
    # cache entry keyed by an earlier bogus URL would make this probe pass for the
    # wrong reason. Observed exactly that: a "strict" run reported success purely
    # because a previous fallback run had cached the body under that URL.
    strict_cfg["url"] = "http://127.0.0.1:9/definitely-not-there.json?probe=%d" % int(time.time() * 1000)
    strict_cfg["useMirrors"] = False
    c, d = save_task(1, strict_cfg)
    ck("save strict config accepted", c == 200, "http=%d" % c)
    c, d = req("POST", "/v2/edu/collect/task/trigger/%d" % task["collectTaskId"],
               token=admin, timeout=180)
    trigger = d.get("data") or {}
    ck("strict pin + unreachable source reports failure", trigger.get("status") == "fail",
       "status=%s msg=%s" % (trigger.get("status"), (trigger.get("message") or "")[:90]))

    # 还原配置与默认「停用」状态（别把共享环境留成启用+坏地址）
    c, d = save_task(0, cfg)
    ck("restore original config", c == 200, "http=%d" % c)

    print("########## 7. collect log ##########")
    c, d = req("GET", "/v2/edu/collect/log/page?taskId=%d&pageNum=1&pageSize=5"
               % task["collectTaskId"], token=admin)
    logs = page_rows(d)
    ck("collect log written", len(logs) >= 1, "logs=%d" % len(logs))
    if logs:
        last = logs[0]
        print("        最近一次：status=%s saved=%s error=%s" % (
            last.get("collectLogStatus"), last.get("collectLogSavedCount"),
            (last.get("collectLogErrorMsg") or "")[:80]))

    passed = sum(1 for ok, _, _ in results if ok)
    failed = len(results) - passed
    print("===== %s: passed %d / failed %d =====" % (
        "PASS" if failed == 0 else "FAIL", passed, failed))
    # 失败必须非 0 退出：只 print 会让发布脚本把失败当成功（此前 4 个套件栽在这）
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    sys.exit(main() or 0)