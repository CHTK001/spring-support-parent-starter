#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""学生「首次登录引导改密」验收。

背景：管理员给学生的初始口令往往是全班同一个/很简单，不该长期使用。
本脚本走完整闭环并断言每一步的**服务端状态**（不只看接口 200）：

  建生(带初始口令) -> 标记=需改密 -> 学生登录 -> /me mustChangePassword=True
  -> 自助改密 -> 标记=已改 -> 用新口令重登 -> /me mustChangePassword=False
  -> 管理员重置口令 -> 标记再次=需改密

自建自删，不污染数据。退出码：0 全通过 / 1 有失败。
"""
import json
import os
import time
import urllib.request

BASE = os.environ.get("BASE", "http://127.0.0.1:19095")
UA = "Mozilla/5.0 (eduai-firstlogin-acceptance)"
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PWD = os.environ.get("ADMIN_PWD", "admin123")

INIT_PWD = "Init#12345"
NEW_PWD = "MyOwn#98765"
RESET_PWD = "Reset#24680"

results = []


def ck(name, cond, detail=""):
    results.append((bool(cond), name, detail))
    print("  [%s] %s%s" % ("PASS" if cond else "FAIL", name,
                           ("  " + detail) if detail else ""))


def req(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    # 网关拒绝非浏览器 UA（Python-urllib 默认 UA 会 403 且文案误导）
    r.add_header("User-Agent", UA)
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode())
        except Exception:
            return e.code, {}
    except Exception as e:  # noqa: BLE001
        return 0, {"error": repr(e)}


def login(username, password, auth_type=None):
    body = {"username": username, "password": password}
    if auth_type:
        body["authType"] = auth_type
        body["accountType"] = "student"
    c, d = req("POST", "/v2/user/login", body)
    return (d.get("data") or {}).get("accessToken") or ""


def me(token):
    c, d = req("GET", "/v2/edu/student/me", token=token)
    return d.get("data") or {}


admin = login(ADMIN_USER, ADMIN_PWD)
ck("管理员登录", len(admin) > 20, "len=%d" % len(admin))
if not admin:
    raise SystemExit(1)

sid = None
try:
    print("=== 1. 建生（带初始口令）===")
    c, d = req("POST", "/v2/edu/dimension/students/save",
               {"studentName": "引导改密验收生", "studentPwdInput": INIT_PWD}, token=admin)
    sid = d.get("data") if isinstance(d.get("data"), int) else None
    ck("新增学生成功", d.get("ok") and sid is not None, "http=%s id=%s" % (c, sid))

    rec = {}
    for s in (req("GET", "/v2/edu/dimension/students", token=admin)[1].get("data") or []):
        if s.get("studentId") == sid:
            rec = s
            break
    sno = rec.get("studentNo")
    ck("拿到系统生成的学号", bool(sno), "studentNo=%s" % sno)

    print("=== 2. 学生用初始口令登录 ===")
    stu = login(sno, INIT_PWD, "STUDENT")
    ck("初始口令可登录", len(stu) > 20, "len=%d" % len(stu))
    info = me(stu)
    ck("**/me 提示需引导改密**（mustChangePassword=true）",
       info.get("mustChangePassword") is True, "mustChangePassword=%s" % info.get("mustChangePassword"))

    print("=== 3. 学生自助改密 ===")
    c, d = req("POST", "/v2/edu/student/change-password",
               {"oldPassword": INIT_PWD, "newPassword": NEW_PWD}, token=stu)
    ck("自助改密成功", d.get("ok"), "http=%s resp=%s" % (c, str(d)[:80]))

    print("=== 4. 用新口令重登，引导应消失 ===")
    stu2 = login(sno, NEW_PWD, "STUDENT")
    ck("新口令可登录", len(stu2) > 20, "len=%d" % len(stu2))
    info2 = me(stu2)
    ck("**改密后 /me 不再提示**（mustChangePassword=false）",
       info2.get("mustChangePassword") is False, "mustChangePassword=%s" % info2.get("mustChangePassword"))
    ck("旧口令已失效", len(login(sno, INIT_PWD, "STUDENT")) == 0)

    print("=== 5. 管理员重置口令 -> 应重新提示改密 ===")
    c, d = req("POST", "/v2/edu/dimension/students/reset-password",
               {"studentId": sid, "newPassword": RESET_PWD}, token=admin)
    ck("管理员重置成功", d.get("ok"), "http=%s" % c)
    stu3 = login(sno, RESET_PWD, "STUDENT")
    ck("重置后的口令可登录", len(stu3) > 20, "len=%d" % len(stu3))
    info3 = me(stu3)
    ck("**重置后再次提示改密**（mustChangePassword=true）",
       info3.get("mustChangePassword") is True, "mustChangePassword=%s" % info3.get("mustChangePassword"))
finally:
    if sid is not None:
        c, d = req("DELETE", "/v2/edu/dimension/students/%s" % sid, token=admin)
        print("  （已清理验收生 studentId=%s ok=%s）" % (sid, d.get("ok")))

passed = sum(1 for r in results if r[0])
print("\n===== 首次登录引导改密验收: 通过 %d / 失败 %d =====" % (passed, len(results) - passed))
for good, name, detail in results:
    if not good:
        print("  FAIL -> %s  %s" % (name, detail))
