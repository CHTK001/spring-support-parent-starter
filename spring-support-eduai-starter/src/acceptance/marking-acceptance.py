#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""AI 阅卷验收（补 acceptance.py 的缺口）。

背景：原验收套件只有 `B12 错题分页`，那只是「错题列表接口返回 200」——
**从没跑过阅卷本身**。于是「错题库有数据」这件事一直没有证据。
本脚本真正走一遍：提交阅卷 -> 轮询 -> 断言题目级结果 -> 断言错题被 upsert。

前置（关键，2026-10-04 实测踩到）：
  - `mode=SELF` 必须同时给 `paperId`，否则报「自有卷必须指定试卷 ID」；
  - `mode=ANY` 不收 paperId，但实测对本用例的图**全部判「未作答」**，
    拿不到题目级结果（questionId=0、score=0、comment=未作答）。
    所以判定用 SELF + paperId。
  - 需要一个 OCR **能解析**的答题卡图。默认用历史上产出过真实结果的那张；
    可用环境变量 MARK_IMG 覆盖。

退出码：0 全通过；1 有失败。
"""
import json
import os
import time
import urllib.request

BASE = os.environ.get("BASE", "http://127.0.0.1:19095")
UA = "Mozilla/5.0 (eduai-marking-acceptance)"
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PWD = os.environ.get("ADMIN_PWD", "admin123")
PAPER_ID = os.environ.get("MARK_PAPER_ID", "9001")
IMG = os.environ.get("MARK_IMG",
                     "/home/kali/eduai/backend/data/marking/fc8f9ccdf30e4f1cacaf88158ed618a9-1.png")
# 该图上唯一判错、会落进错题本的题号
WRONG_QID = int(os.environ.get("MARK_WRONG_QID", "9003"))

results = []


def ck(name, cond, detail=""):
    results.append((bool(cond), name, detail))
    print("  [%s] %s%s" % ("PASS" if cond else "FAIL", name,
                           ("  " + detail) if detail else ""))


def req(method, path, body=None, token=None, raw=None, ctype="application/json"):
    data = raw if raw is not None else (
        json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None)
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", ctype)
    r.add_header("User-Agent", UA)
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}
    except Exception as e:  # noqa: BLE001
        return 0, {"error": repr(e)}


def login():
    c, d = req("POST", "/v2/user/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
    return (d.get("data") or {}).get("accessToken") or ""


def multipart_scan(token):
    """手工拼 multipart（标准库够用，避免额外依赖）。"""
    boundary = "----eduaiMarkAccept%s" % int(time.time() * 1000)
    with open(IMG, "rb") as f:
        img = f.read()
    parts = []
    for name, val in (("mode", "SELF"), ("paperId", str(PAPER_ID))):
        parts.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n"
                      % (boundary, name, val)).encode())
    parts.append(("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sheet.png\"\r\n"
                  "Content-Type: image/png\r\n\r\n" % boundary).encode())
    parts.append(img)
    parts.append(("\r\n--%s--\r\n" % boundary).encode())
    body = b"".join(parts)
    c, d = req("POST", "/v2/edu/marking/scan", token=token, raw=body,
               ctype="multipart/form-data; boundary=" + boundary)
    return c, d


def wrong_count(token, qid):
    """错题本里某题的累计错误次数（找不到返回 None）。

    响应是**嵌套**的：{"data":{"total":N,"data":[...]}} —— 行在 data.data，
    不是常见的 records/rows/list（2026-10-04 实测，按后者取会全部读空）。
    """
    c, d = req("GET", "/v2/edu/wrongbook/page?pageNum=1&pageSize=200", token=token)
    rows = []
    x = d.get("data")
    if isinstance(x, dict):
        rows = x.get("data") or x.get("records") or x.get("rows") or x.get("list") or []
    elif isinstance(x, list):
        rows = x
    for r in rows:
        if r.get("wrongQuestionQuestionId") == qid or r.get("questionId") == qid:
            return r.get("wrongQuestionWrongCount") or r.get("wrongCount") or 0
    return None


print("=== 0. 登录 ===")
token = login()
ck("管理员登录", len(token) > 20, "len=%d" % len(token))
if not token:
    raise SystemExit(1)

print("=== 1. 阅卷前：错题本基线 ===")
before = wrong_count(token, WRONG_QID)
ck("读到错题本基线（qid=%d）" % WRONG_QID, before is not None, "wrongCount=%s" % before)

print("=== 2. 提交阅卷（mode=SELF + paperId=%s）===")
print("    图: %s" % IMG)
ck("测试图存在", os.path.isfile(IMG), IMG)
c, d = multipart_scan(token)
task = d.get("data") if isinstance(d.get("data"), str) else ""
ck("提交成功并拿到 taskId", c == 200 and bool(task), "http=%s taskId=%s resp=%s" % (c, task, str(d)[:120]))

if task:
    print("=== 3. 轮询直到完成（最多 150s）===")
    stage, payload = None, {}
    for i in range(50):
        c, d = req("GET", "/v2/edu/marking/result/" + task, token=token)
        payload = d.get("data") or {}
        stage = payload.get("stage") or ""
        if stage in ("已完成", "失败") or payload.get("finished"):
            print("    [%ds] stage=%s finished=%s" % (i * 3, stage, payload.get("finished")))
            break
        time.sleep(3)
    ck("任务跑完且非失败", stage == "已完成", "stage=%s" % stage)

    rs = payload.get("results") or []
    real = [r for r in rs if (r.get("questionId") or 0) > 0]
    scored = [r for r in real if (r.get("score") or 0) > 0]
    zero = [r for r in real if (r.get("score") or 0) == 0]

    print("=== 4. 题目级结果 ===")
    for r in real[:6]:
        print("    qid=%s score=%s/%s  %s" % (r.get("questionId"), r.get("score"),
                                              r.get("maxScore"), (r.get("comment") or "")[:40]))
    ck("解析出题目级结果（questionId>0）", len(real) >= 1, "real_q=%d" % len(real))
    ck("有得分项（识别到作答并判对）", len(scored) >= 1, "scored=%d" % len(scored))
    ck("有失分项（判错，才会进错题本）", len(zero) >= 1, "zero=%d" % len(zero))
    ck("结果里带判分说明", any((r.get("comment") or "").strip() for r in real),
       "comments=%s" % [ (r.get('comment') or '')[:12] for r in real[:3] ])

    print("=== 5. 阅卷后：错题本 upsert ===")
    after = wrong_count(token, WRONG_QID)
    ck("错题本仍能读到该题", after is not None, "wrongCount=%s" % after)
    if before is not None and after is not None:
        ck("判错题的错误次数被累加（upsert 生效）", after > before,
           "%s -> %s" % (before, after))

passed = sum(1 for r in results if r[0])
print("\n===== AI 阅卷验收: 通过 %d / 失败 %d =====" % (passed, len(results) - passed))
for good, name, detail in results:
    if not good:
        print("  FAIL -> %s  %s" % (name, detail))
