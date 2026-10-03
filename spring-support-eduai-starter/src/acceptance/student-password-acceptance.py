#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验三项业务能力（验收套件未覆盖改密，故单独验）：
  1) 学生能登录
  2) 管理员能修改学生密码
  3) 学生能自助改密
另含越权/错旧密码负向，并在结束时把口令改回原值，避免破坏其它验收脚本。
"""
import json
import urllib.request

BASE = "http://127.0.0.1:19095"
UA = "Mozilla/5.0 (eduai-password-acceptance)"
STUDENT_NO = "2026000001"
ORIG_PWD = "stu123456"
ADMIN_USER, ADMIN_PWD = "admin", "admin123"
ADMIN_SET = "AdminSet7654321"   # 管理员重置成这个
SELF_SET = "SelfSet7654321"     # 学生自助改密成这个

results = []


def ck(name, cond, detail=""):
    results.append((bool(cond), name, detail))
    print("  [%s] %s%s" % ("PASS" if cond else "FAIL", name,
                           ("  " + detail) if detail else ""))


def req(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    # 必须带浏览器式 UA：网关会拒绝 Python-urllib 的默认 UA（实测 403 A0403S0000
    # 「用户信息不存在/请重新登录」——那个文案极具误导性，真因是 UA 被拒）。
    # 与 acceptance.py 保持一致，否则两套脚本结论不可比。
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


def login(username, password, auth_type="STUDENT"):
    # 与 acceptance.py 一致：仅在 auth_type 非空时才带 authType/accountType，
    # 避免给管理员登录塞 authType: null。
    body = {"username": username, "password": password}
    if auth_type:
        body["authType"] = auth_type
        body["accountType"] = "student"
    c, d = req("POST", "/v2/user/login", body)
    tok = ""
    if isinstance(d.get("data"), dict):
        tok = d["data"].get("accessToken") or ""
    return c, d.get("ok"), tok


def ok_and_token(d):
    return d.get("ok") and (d.get("data") or {}).get("accessToken")


print("=== 1. 学生登录 ===")
c, ok, tok = login(STUDENT_NO, ORIG_PWD)
ck("学生用学号登录（原始口令）", ok and len(tok) > 20, "http=%s ok=%s len=%d" % (c, ok, len(tok)))
c2, ok2, tok_ph = login("13800000001", ORIG_PWD)
ck("学生用家长手机号登录", ok2 and len(tok_ph) > 20, "http=%s ok=%s len=%d" % (c2, ok2, len(tok_ph)))

print("=== 2. 管理员登录 + 取学生列表 ===")
c, aok, admin = login(ADMIN_USER, ADMIN_PWD, None)
ck("管理员登录", aok and len(admin) > 20, "http=%s ok=%s len=%d" % (c, aok, len(admin)))
c, d = req("GET", "/v2/edu/dimension/students", token=admin)
students = []
if isinstance(d.get("data"), dict):
    students = d["data"].get("records") or d["data"].get("rows") or d["data"].get("list") or []
elif isinstance(d.get("data"), list):
    students = d["data"]
me = None
other = None
for s in students:
    no = s.get("studentNo") or s.get("studentNumber") or s.get("no")
    if no == STUDENT_NO:
        me = s
    elif other is None:
        other = s
ck("管理员能查到学生列表", len(students) >= 1, "count=%d" % len(students))
sid = (me or {}).get("studentId") or (me or {}).get("id")
ck("找到目标学生 studentId", sid is not None, "studentId=%s" % sid)

print("=== 3. 管理员重置学生密码 ===")
c, d = req("POST", "/v2/edu/dimension/students/reset-password",
           {"studentId": sid, "newPassword": ADMIN_SET}, token=admin)
ck("管理员重置成功（接口返回 ok）", d.get("ok"), "http=%s resp=%s" % (c, str(d)[:80]))
c, ok, tok = login(STUDENT_NO, ADMIN_SET)
ck("学生用管理员设的新口令能登录", ok and len(tok) > 20, "ok=%s len=%d" % (ok, len(tok)))

print("=== 4. 学生自助改密 ===")
c, d = req("POST", "/v2/edu/student/change-password",
           {"oldPassword": ADMIN_SET, "newPassword": SELF_SET}, token=tok)
ck("学生自助改密（带正确旧口令）", d.get("ok"), "http=%s resp=%s" % (c, str(d)[:80]))
c, ok, tok2 = login(STUDENT_NO, SELF_SET)
ck("学生用自助改密后的新口令能登录", ok and len(tok2) > 20, "ok=%s len=%d" % (ok, len(tok2)))
c, ok_old, _ = login(STUDENT_NO, ADMIN_SET)
ck("旧口令（自助改密前）已失效", not ok_old, "ok=%s" % ok_old)

print("=== 5. 负向用例 ===")
c, d = req("POST", "/v2/edu/student/change-password",
           {"oldPassword": "WrongOldPwd", "newPassword": "ShouldNotWork1"}, token=tok2)
ck("自助改密：旧口令错误则拒绝", not d.get("ok"), "http=%s ok=%s" % (c, d.get("ok")))
c, ok_still, _ = login(STUDENT_NO, SELF_SET)
ck("负向用例后口令未被改掉", ok_still, "ok=%s" % ok_still)
if other:
    oid = other.get("studentId") or other.get("id")
    c, d = req("POST", "/v2/edu/dimension/students/reset-password",
               {"studentId": oid, "newPassword": "Hacked123456"}, token=tok2)
    ck("学生令牌不能重置他人密码（越权重置）", not d.get("ok"), "http=%s ok=%s" % (c, d.get("ok")))
else:
    ck("学生令牌不能重置他人密码（越权重置）", False, "库里只有 1 个学生，跳过（需先造第 2 个）")

print("=== 6. 恢复原口令（避免破坏其它脚本）===")
c, d = req("POST", "/v2/edu/dimension/students/reset-password",
           {"studentId": sid, "newPassword": ORIG_PWD}, token=admin)
ck("管理员重置回原口令成功", d.get("ok"), "http=%s" % c)
c, ok, tok_r = login(STUDENT_NO, ORIG_PWD)
ck("原口令恢复后学生可登录", ok and len(tok_r) > 20, "ok=%s len=%d" % (ok, len(tok_r)))

passed = sum(1 for r in results if r[0])
print("\n===== 学生改密三项验收: 通过 %d / 失败 %d =====" % (passed, len(results) - passed))
for good, name, detail in results:
    if not good:
        print("  FAIL -> " + name + "  " + detail)
