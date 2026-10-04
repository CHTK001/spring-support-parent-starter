#!/usr/bin/env python3
"""eduai AI 全流程验收：组卷 / 其它试卷 AI 阅卷(ANY) / 分析解析 / 出分 / 错题。

针对产物 md5 运行；每项给出可核对的断言与实际值。
"""
import json
import subprocess
import sys
import time
import urllib.request

BASE = "http://127.0.0.1:19095"
PASS, FAIL = [], []


def ck(name, cond, detail=""):
    (PASS if cond else FAIL).append(name)
    print("  [%s] %s%s" % ("PASS" if cond else "FAIL", name, ("  -> " + str(detail)) if detail else ""))


def req(method, path, body=None, token=None, timeout=120):
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    r.add_header("User-Agent", "Mozilla/5.0 acceptance")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"_http": e.code, "_body": e.read().decode("utf-8", "replace")[:300]}


def post_multipart(path, token, file_path, fields, timeout=180):
    boundary = "----acceptboundary1234567890"
    parts = []
    for k, v in fields.items():
        parts.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n" % (boundary, k, v)).encode("utf-8"))
    with open(file_path, "rb") as fh:
        content = fh.read()
    parts.append(("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"%s\"\r\n"
                  "Content-Type: image/png\r\n\r\n" % (boundary, file_path.split("/")[-1])).encode("utf-8"))
    parts.append(content)
    parts.append(("\r\n--%s--\r\n" % boundary).encode("utf-8"))
    payload = b"".join(parts)
    r = urllib.request.Request(BASE + path, data=payload, method="POST")
    r.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    r.add_header("User-Agent", "Mozilla/5.0 acceptance")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"_http": e.code, "_body": e.read().decode("utf-8", "replace")[:300]}


def find_key(obj, key):
    if isinstance(obj, dict):
        if key in obj:
            return obj[key]
        for v in obj.values():
            r = find_key(v, key)
            if r is not None:
                return r
    elif isinstance(obj, list):
        for v in obj:
            r = find_key(v, key)
            if r is not None:
                return r
    return None


def main():
    jar_md5 = subprocess.run(["md5sum", "/home/kali/eduai/backend/eduai-backend.jar"],
                             capture_output=True, text=True).stdout.split()[0]
    print("== 产物 md5 = %s ==" % jar_md5)

    login = req("POST", "/v2/user/login", {"username": "admin", "password": "admin123"})
    token = find_key(login, "accessToken")
    print("== 登录 token 长度 = %d ==" % len(token or ""))
    if not token:
        print("登录失败，终止")
        return 2

    # ---------- 1. 组卷 ----------
    print("\n===== 1. 组卷（AI 生成试卷）=====")
    gen = req("POST", "/v2/edu/paper/generate", {
        "name": "验收卷-AI组卷-20261004",
        "subject": "语文",
        "gradeLevel": "初中",
        "grade": "初二",
        "questionCount": 3,
        "scorePerQuestion": 5,
        "totalScore": 15,
        "duration": 45,
        "typeConfigs": [
            {"type": "SINGLE_CHOICE", "count": 2, "scorePerQuestion": 5},
            {"type": "FILL", "count": 1, "scorePerQuestion": 5},
        ],
        "difficultyDistribution": [1, 2],
    }, token=token, timeout=300)
    paper_id = find_key(gen, "data")
    ck("组卷-接口返回试卷ID", isinstance(paper_id, int) and paper_id > 0, paper_id)
    if not isinstance(paper_id, int):
        print("     返回体:", json.dumps(gen, ensure_ascii=False)[:300])

    questions = []
    if isinstance(paper_id, int):
        detail = req("GET", "/v2/edu/paper/%d" % paper_id, token=token)
        questions = find_key(detail, "questions") or []
        ck("组卷-试卷题目数=3", len(questions) == 3, "实际 %d" % len(questions))
        stems = [str(q.get("stem") or "") for q in questions]
        ck("组卷-题干非空", all(len(s.strip()) > 0 for s in stems), stems)
        answers = [str(q.get("answer") or "") for q in questions]
        ck("组卷-标准答案非空", all(len(a.strip()) > 0 for a in answers), answers)
        ck("组卷-题目带编号", all(str(q.get("questionNo") or "").strip() for q in questions),
           [q.get("questionNo") for q in questions])

    # ---------- 2. 分析解析 ----------
    print("\n===== 2. 分析解析（题目解析字段）=====")
    if questions:
        analyses = [str(q.get("analysis") or "") for q in questions]
        non_empty = sum(1 for a in analyses if len(a.strip()) > 0)
        ck("组卷-题目解析非空", non_empty == len(questions),
           "%d/%d 条非空; 样例=%s" % (non_empty, len(questions), analyses[0][:40] if analyses else "-"))

    # ---------- 3. 其它试卷 AI 阅卷（ANY）----------
    print("\n===== 3. 其它试卷 AI 阅卷（ANY 模式，题库中不存在的卷）=====")
    gen_img = subprocess.run(["python3", "/home/kali/gen-external-paper.py"], capture_output=True, text=True)
    ck("ANY-外来试卷图生成", gen_img.returncode == 0 and "GENERATED" in gen_img.stdout,
       (gen_img.stdout or gen_img.stderr).strip()[:120])

    task_id = None
    if gen_img.returncode == 0:
        scan = post_multipart("/v2/edu/marking/scan?mode=ANY", token,
                              "/home/kali/external-paper.png",
                              {"studentId": "20260099", "maxScore": "5"}, timeout=300)
        task_id = find_key(scan, "data")
        ck("ANY-提交返回任务ID", isinstance(task_id, str) and len(task_id) > 8, task_id)

    result = {}
    if task_id:
        for _ in range(120):
            time.sleep(3)
            result = req("GET", "/v2/edu/marking/result/%s" % task_id, token=token)
            if find_key(result, "finished"):
                break
        rows = find_key(result, "results") or []
        ck("ANY-识别出 3 道题", len(rows) == 3, "实际 %d" % len(rows))
        scores = [(r.get("score"), r.get("maxScore")) for r in rows]
        print("     逐题得分:", scores)
        print("     逐题评语:", [(r.get("comment") or "")[:36] for r in rows])
        if len(rows) == 3:
            s = [r.get("score") for r in rows]
            ck("ANY-第1题(正确作答)得满分", s[0] == 5, "实际 %s" % s[0])
            ck("ANY-第2题(要点不全)为部分分", s[1] is not None and 0 < s[1] < 5, "实际 %s" % s[1])
            ck("ANY-第3题(答非所问)判 0 分", s[2] == 0, "实际 %s" % s[2])
            ck("ANY-判分有区分度(非全同)", len(set(s)) > 1, "得分序列 %s" % s)
            ck("ANY-每题都有评语/解析", all((r.get("comment") or "").strip() for r in rows))

    # ---------- 4. 错题 ----------
    print("\n===== 4. 错题本 =====")
    wb = req("GET", "/v2/edu/wrongbook/page?pageNum=1&pageSize=20", token=token)
    rows = find_key(wb, "data") or []
    if isinstance(rows, dict):
        rows = rows.get("data") or []
    total = None
    _d = find_key(wb, "data")
    if isinstance(_d, dict):
        total = _d.get("total")
    print("     错题总数=%s 本页=%d" % (total, len(rows)))
    if isinstance(rows, list) and rows:
        sample = rows[0]
        print("     样例:", json.dumps(sample, ensure_ascii=False)[:220])
        ck("错题-记录含题目ID", any(r.get("wrongQuestionQuestionId") for r in rows))
        ck("错题-记录含最近错题时间", any(r.get("wrongQuestionLastWrongTime") for r in rows))
    ck("错题-接口可用(返回分页结构)", total is not None and total > 0, "total=%s" % total)

    print("\n===== 汇总：PASS %d / FAIL %d =====" % (len(PASS), len(FAIL)))
    if FAIL:
        print("失败项: " + "; ".join(FAIL))
    return 0 if not FAIL else 1


if __name__ == "__main__":
    sys.exit(main())