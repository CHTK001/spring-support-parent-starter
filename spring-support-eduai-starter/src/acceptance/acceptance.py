"""eduai 生产级验收套件（API 层，纯标准库，可在目标主机上直接跑）。

覆盖：
  A 安全矩阵   —— actuator 暴露面 / 未授权 / 越权 / 只读 / 代入限权 / 续期
  B 管理端功能 —— 题库/试卷/知识库/玩法/考试/错题/采集
  C 学生端     —— me / overview / 玩法（学生令牌）
  D 玩法闭环   —— 成语与诗词的题目生成
  E 边界输入   —— SQL 注入 / 超长 / 畸形 JSON（一律不得 500）
  F 并发与延迟 —— 20 并发 × 3 批 + p50/p95

前置：目标机 eduai(19095) 与 oauth(19180/19181) 已就绪；
      库中有验收用学生（默认 2026000001 / 13800000001 / stu123456，可用
      参数覆盖）。脚本自身幂等、只读为主，唯一写入是登录/代入产生的令牌。

**纪律（AGENTS.md §4.6）**：报告必须与产物 md5 绑定。跑之前先记录：
    sha256sum <eduai jar> <oauth jar> ; git -C <repo> rev-parse HEAD
把这三个值与结果一起记录；产物换了就作废、必须重跑。

用法：
    python3 acceptance.py                 # 默认 http://127.0.0.1:19095
    BASE=http://host:19095 python3 acceptance.py
退出码：0 全绿；1 有失败。
"""

import json
import os
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request

BASE = os.environ.get("BASE", "http://127.0.0.1:19095")
UA = "Mozilla/5.0 (eduai-acceptance)"
STUDENT_NO = os.environ.get("STUDENT_NO", "2026000001")
STUDENT_PHONE = os.environ.get("STUDENT_PHONE", "13800000001")
STUDENT_PWD = os.environ.get("STUDENT_PWD", "stu123456")
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PWD = os.environ.get("ADMIN_PWD", "admin123")

passed, failed = 0, 0
fails: list[str] = []


def ck(name: str, cond: bool, detail: str = "") -> None:
    """记一条断言。"""
    global passed, failed
    if cond:
        print(f"  [PASS] {name}")
        passed += 1
    else:
        print(f"  [FAIL] {name}  {detail}")
        failed += 1
        fails.append(f"{name}: {detail}")


def http(method: str, path: str, body=None, token: str = None,
         raw: bytes = None, timeout: float = 20.0) -> tuple[int, str]:
    """发一个请求，返回 (状态码, 响应体)。连接失败返回 (0, 原因)。"""
    data = raw if raw is not None else (
        json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None)
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", UA)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001
        return 0, repr(e)


def jget(text: str, *keys):
    """按路径取 JSON 字段。"""
    try:
        d = json.loads(text)
    except Exception:  # noqa: BLE001
        return None
    for k in keys:
        if not isinstance(d, dict):
            return None
        d = d.get(k)
    return d


def login(username: str, password: str, auth_type: str = None) -> str:
    """登录并返回 accessToken（失败返回空串）。"""
    body = {"username": username, "password": password}
    if auth_type:
        body["authType"] = auth_type
        body["accountType"] = "student"
    _, t = http("POST", "/v2/user/login", body=body)
    return jget(t, "data", "accessToken") or ""


def main() -> int:
    """跑完整套验收。"""
    print("=== 登录取令牌 ===")
    admin = login(ADMIN_USER, ADMIN_PWD)
    ck("管理员登录得到令牌", len(admin) > 20, f"len={len(admin)}")
    student = login(STUDENT_NO, STUDENT_PWD, "STUDENT")
    ck("学生登录（学号）得到令牌", len(student) > 20, f"len={len(student)}")
    student_ph = login(STUDENT_PHONE, STUDENT_PWD, "STUDENT")
    ck("学生登录（家长手机号）得到令牌", len(student_ph) > 20, f"len={len(student_ph)}")

    imp_text = http("POST", "/v2/edu/dimension/students/impersonate",
                    body={"studentId": 1}, token=admin)[1]
    imp = jget(imp_text, "data", "accessToken") or ""
    ck("管理员代入得到临时令牌", len(imp) > 20, f"len={len(imp)}")

    print("\n=== A 安全矩阵 ===")
    c, _ = http("GET", "/actuator/health")
    ck("A1 /actuator/health 无令牌可探活", c == 200, f"http={c}")
    for ep in ("env", "beans", "configprops", "loggers", "threaddump", "mappings"):
        c, _ = http("GET", f"/actuator/{ep}")
        ck(f"A2 /actuator/{ep} 无令牌被拒", c in (401, 403, 404), f"http={c}")
    c, _ = http("POST", "/actuator/loggers", body={"configuredLevel": "DEBUG"})
    ck("A3 POST /actuator/loggers 被拒", c in (401, 403, 404), f"http={c}")
    c, _ = http("GET", "/v2/edu/student/me")
    ck("A4 无令牌访问学生端被拒", c in (401, 403), f"http={c}")
    c, _ = http("GET", "/v2/edu/dimension/students")
    ck("A5 无令牌访问管理端被拒", c in (401, 403), f"http={c}")
    c, _ = http("GET", "/v2/edu/dimension/students", token=student)
    ck("A6 学生令牌访问管理端被拒", c == 403, f"http={c}")
    c, _ = http("GET", "/v2/edu/dimension/students", token=imp)
    ck("A7 代入令牌访问管理端被拒", c == 403, f"http={c}")
    c, _ = http("POST", "/v2/edu/student/study/report", body={}, token=imp)
    ck("A8 代入令牌写学生端被拒（只读）", c == 403, f"http={c}")
    c, _ = http("GET", "/v2/edu/student/overview", token=imp)
    ck("A9 代入令牌读学生端放行", c == 200, f"http={c}")
    c, t = http("POST", "/v2/edu/dimension/students/impersonate",
                body={"studentId": 1}, token=student)
    ck("A10 学生令牌不能代入", c in (400, 403), f"http={c} msg={jget(t, 'msg')}")
    c, t = http("POST", "/v2/user/login", body={"username": ADMIN_USER, "password": "wrong-x"})
    ck("A11 错误口令被拒", jget(t, "ok") is False, f"body={t[:80]}")
    c, _ = http("POST", "/oauth/upgrade", body={}, token=imp)
    ck("A12 代入令牌不得续期", c in (401, 403, 404), f"http={c}")

    print("\n=== B 管理端功能 ===")
    admin_eps = [
        ("B1  题库分页", "GET", "/v2/edu/question/page?pageNum=1&pageSize=5"),
        ("B2  年级维度", "GET", "/v2/edu/question/grade-dimensions"),
        ("B3  试卷分页", "GET", "/v2/edu/paper/page?pageNum=1&pageSize=5"),
        ("B4  试卷模板", "GET", "/v2/edu/paper-template/page?pageNum=1&pageSize=5"),
        ("B5  知识树", "GET", "/v2/edu/knowledge-point/tree"),
        ("B6  知识点列表", "GET", "/v2/edu/knowledge-point/list?pageNum=1&pageSize=5"),
        ("B7  玩法列表", "GET", "/v2/edu/fun/list"),
        ("B8  玩法全量", "GET", "/v2/edu/fun/list-all"),
        ("B9  成语列表", "GET", "/v2/edu/idiom/list?pageNum=1&pageSize=5"),
        ("B10 古诗列表", "GET", "/v2/edu/poem/list?pageNum=1&pageSize=5"),
        ("B11 考试分页", "GET", "/v2/edu/exam/page?pageNum=1&pageSize=5"),
        ("B12 错题分页", "GET", "/v2/edu/wrongbook/page?pageNum=1&pageSize=5"),
        ("B13 采集任务", "GET", "/v2/edu/collect/task/page?pageNum=1&pageSize=5"),
        ("B14 采集元数据", "GET", "/v2/edu/collect/meta"),
        ("B15 学生档案", "GET", "/v2/edu/dimension/students"),
    ]
    for name, m, p in admin_eps:
        c, _ = http(m, p, token=admin)
        ck(name, c == 200, f"http={c}")

    print("\n=== C 学生端 ===")
    # 必须重新登录：本用例前面对同一学生登录了两次（学号 + 手机号），
    # 服务端 online=SINGLE，后一次会踢掉前一次的令牌，用旧令牌必然 403。
    student_fresh = login(STUDENT_NO, STUDENT_PWD, "STUDENT")
    c, t = http("GET", "/v2/edu/student/me", token=student_fresh)
    ck("C1 学生 /me 返回本人", c == 200 and jget(t, "data", "student", "studentId") == 1,
       f"http={c} body={t[:100]}")
    c, _ = http("GET", "/v2/edu/student/overview", token=student_fresh)
    ck("C2 学生 overview", c == 200, f"http={c}")
    c, _ = http("GET", "/v2/edu/fun/list", token=student_fresh)
    ck("C3 学生可读玩法列表", c == 200, f"http={c}")

    print("\n=== D 玩法闭环 ===")
    c, _ = http("GET", "/v2/edu/idiom/game/generate", token=admin)
    ck("D1 成语消消乐生成题目", c == 200, f"http={c}")
    c, _ = http("GET", "/v2/edu/poem/game/generate", token=admin)
    ck("D2 诗词大会生成题目", c == 200, f"http={c}")

    print("\n=== E 边界输入（一律不得 500）===")
    cases = [
        ("E1 SQL 注入用户名", {"username": "' OR '1'='1", "password": "x"}),
        ("E2 1000 字符用户名", {"username": "a" * 1000, "password": "x"}),
        ("E3 5000 字符口令", {"username": ADMIN_USER, "password": "p" * 5000}),
        ("E4 空用户名", {"username": "", "password": ""}),
        ("E5 学号含特殊字符", {"username": "../../etc/passwd", "password": "x",
                          "authType": "STUDENT", "accountType": "student"}),
    ]
    for name, body in cases:
        c, _ = http("POST", "/v2/user/login", body=body)
        ck(name, c != 500, f"http={c}")
    c, _ = http("POST", "/v2/user/login", raw=b"{not-json")
    ck("E6 畸形 JSON 不 500", c != 500, f"http={c}")

    print("\n=== F 并发与延迟 ===")
    lat: list[float] = []
    errs: list[int] = []
    lock = threading.Lock()

    def one() -> None:
        t0 = time.time()
        c, t = http("POST", "/v2/user/login",
                    body={"username": STUDENT_NO, "password": STUDENT_PWD,
                          "authType": "STUDENT", "accountType": "student"}, timeout=30)
        dt = (time.time() - t0) * 1000
        ok = c == 200 and len(jget(t, "data", "accessToken") or "") > 20
        with lock:
            lat.append(dt)
            if not ok:
                errs.append(c)

    for _ in range(3):
        ths = [threading.Thread(target=one) for _ in range(20)]
        for th in ths:
            th.start()
        for th in ths:
            th.join()
    total = len(lat)
    okn = total - len(errs)
    ck(f"F1 20 并发 × 3 批 全部成功（{okn}/{total}）", not errs, f"失败={errs[:5]}")
    if lat:
        p50 = statistics.median(lat)
        p95 = sorted(lat)[int(len(lat) * 0.95) - 1]
        print(f"    延迟 p50={p50:.0f}ms  p95={p95:.0f}ms  max={max(lat):.0f}ms")
        ck("F2 p95 延迟 < 1500ms", p95 < 1500, f"p95={p95:.0f}ms")

    print(f"\n  ===== 生产级验收: 通过 {passed} / 失败 {failed} =====")
    if fails:
        print("  失败明细:")
        for f in fails:
            print("    -", f)
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
