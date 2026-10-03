#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""学生管理专项验收（补 acceptance.py 的缺口）。

原验收套件 49 项里学生相关只有 B15「列表 GET」，**新增/编辑/删除三项没有覆盖**，
而它们恰恰是「学生管理」页面的主功能（操作列 4 按钮里占了 2 个）。
本脚本覆盖：

  1. 列表 + 筛选（classId / gradeLevel / grade）
  2. 新增学生
  3. **学号自动生成、不允许填写**（请求里硬塞 studentNo 也必须被忽略）
  4. 编辑学生（改姓名/头像/身份证），且**编辑时学号锁定不变**
  5. 头像 / 身份证 回读持久化
  6. 删除学生
  7. 负向：学生令牌不能新增/删除（越权）、删除不存在 id

脚本自建自删测试数据，结束时清理，不污染其它验收脚本依赖的学生集合。
"""
import json
import urllib.request

BASE = "http://127.0.0.1:19095"
UA = "Mozilla/5.0 (eduai-student-manage-acceptance)"
ADMIN_USER, ADMIN_PWD = "admin", "admin123"
STUDENT_NO = "2026000001"
STUDENT_PWD = "stu123456"

results = []
ADMIN_TOKEN = ""


def ck(name, cond, detail=""):
    results.append((bool(cond), name, detail))
    print("  [%s] %s%s" % ("PASS" if cond else "FAIL", name,
                           ("  " + detail) if detail else ""))


def req(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    # 必须带浏览器式 UA：网关拒绝 Python-urllib 默认 UA，报 403 A0403S0000
    # 「用户信息不存在/请重新登录」——文案误导，真因是 UA。见 student-password-acceptance.py。
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
    if isinstance(d.get("data"), dict):
        return d["data"].get("accessToken") or ""
    return ""


def as_list(d):
    x = d.get("data")
    if isinstance(x, list):
        return x
    if isinstance(x, dict):
        for k in ("records", "rows", "list"):
            if isinstance(x.get(k), list):
                return x[k]
    return []


def find(sid):
    for s in as_list(req("GET", "/v2/edu/dimension/students", token=ADMIN_TOKEN)[1]):
        if s.get("studentId") == sid:
            return s
    return None


created_id = None
try:
    print("=== 1. 列表与筛选 ===")
    admin = login(ADMIN_USER, ADMIN_PWD)
    ck("管理员登录", len(admin) > 20, "len=%d" % len(admin))
    ADMIN_TOKEN = admin
    c, d = req("GET", "/v2/edu/dimension/students", token=admin)
    lst = as_list(d)
    ck("学生列表可用", d.get("ok") and len(lst) >= 2, "http=%s count=%d" % (c, len(lst)))
    if lst:
        # 只拼**非空**的筛选参数：把空值也拼进去（classId=）会让筛选查不到任何行，
        # 那是测试自己造出来的假失败，不是接口缺陷。
        target = None
        for s in lst:
            if s.get("studentClassId") and (s.get("studentGradeLevel") or s.get("studentGrade")):
                target = s
                break
        if target:
            q = []
            if target.get("studentClassId"):
                q.append("classId=%s" % target["studentClassId"])
            if target.get("studentGradeLevel"):
                q.append("gradeLevel=%s" % target["studentGradeLevel"])
            if target.get("studentGrade"):
                q.append("grade=%s" % target["studentGrade"])
            c, d = req("GET", "/v2/edu/dimension/students?" + "&".join(q), token=admin)
            sub = as_list(d)
            hit = any(s.get("studentId") == target["studentId"] for s in sub)
            ck("按 classId/gradeLevel/grade 筛选：命中的源学生仍在结果内且不多于全量",
               d.get("ok") and hit and len(sub) <= len(lst),
               "q=%s 筛选后=%d 全量=%d 命中源学生=%s" % ("&".join(q), len(sub), len(lst), hit))
        else:
            print("  （库里学生都没有 classId/gradeLevel，跳过正筛选用例）")
    c, d = req("GET", "/v2/edu/dimension/students?classId=999999&gradeLevel=__x__", token=admin)
    ck("筛选出空集不报错", d.get("ok"), "http=%s count=%d" % (c, len(as_list(d))))

    print("=== 2. 新增学生（学号必须自动生成）===")
    ATTACK_NO = "9998887776"   # 故意塞一个学号，服务端必须忽略
    body = {"studentNo": ATTACK_NO, "studentName": "验收临时学生",
            "studentGradeLevel": "初一", "studentGrade": "七年级",
            "studentEnrollYear": 2026, "studentParentPhone": "13900000002",
            "studentAvatar": "/avatar/accept-temp.png",
            "studentIdCard": "110101202601010011"}
    c, d = req("POST", "/v2/edu/dimension/students/save", body, token=admin)
    sid = d.get("data")
    created_id = sid if isinstance(sid, int) else None
    ck("新增学生成功并返回 studentId", d.get("ok") and created_id is not None,
       "http=%s id=%s resp=%s" % (c, sid, str(d)[:70]))
    rec = find(created_id) if created_id else None
    ck("新增后可查到该学生", rec is not None, "studentId=%s" % created_id)
    gen_no = (rec or {}).get("studentNo")
    ck("**学号由系统生成**：请求里塞的 studentNo 被忽略",
       gen_no and gen_no != ATTACK_NO, "传入=%s 实际=%s" % (ATTACK_NO, gen_no))
    ck("生成的学生学号非空且格式像学号", bool(gen_no) and len(gen_no) >= 8, "studentNo=%s" % gen_no)

    print("=== 3. 编辑学生（学号锁定 + 头像/身份证）===")
    edit = {"studentId": created_id, "studentNo": "1112223333",
            "studentName": "验收临时学生-已改名",
            "studentAvatar": "/avatar/accept-renamed.png",
            "studentIdCard": "110101202602020022"}
    c, d = req("POST", "/v2/edu/dimension/students/save", edit, token=admin)
    ck("编辑学生成功", d.get("ok"), "http=%s resp=%s" % (c, str(d)[:70]))
    rec2 = find(created_id) if created_id else None
    ck("姓名已更新", (rec2 or {}).get("studentName") == "验收临时学生-已改名",
       "name=%s" % (rec2 or {}).get("studentName"))
    ck("**编辑时学号锁定不变**（仍是生成的那个，且不因请求里的值改变）",
       (rec2 or {}).get("studentNo") == gen_no,
       "编辑前=%s 编辑请求里塞=%s 编辑后=%s" % (gen_no, "1112223333", (rec2 or {}).get("studentNo")))
    ck("头像字段已持久化", (rec2 or {}).get("studentAvatar") == "/avatar/accept-renamed.png",
       "avatar=%s" % (rec2 or {}).get("studentAvatar"))
    ck("身份证字段已持久化", (rec2 or {}).get("studentIdCard") == "110101202602020022",
       "idCard=%s" % (rec2 or {}).get("studentIdCard"))

    print("=== 4. 负向：越权与非法输入 ===")
    stu = login(STUDENT_NO, STUDENT_PWD, "STUDENT")
    ck("学生登录（用于越权测试）", len(stu) > 20, "len=%d" % len(stu))
    c, d = req("POST", "/v2/edu/dimension/students/save",
               {"studentName": "越权新增"}, token=stu)
    ck("学生令牌不能新增学生（越权）", not d.get("ok"), "http=%s ok=%s" % (c, d.get("ok")))
    c, d = req("DELETE", "/v2/edu/dimension/students/%s" % created_id, token=stu)
    ck("学生令牌不能删除学生（越权）", not d.get("ok"), "http=%s ok=%s" % (c, d.get("ok")))
    c, d = req("POST", "/v2/edu/dimension/students/save",
               {"studentName": "无令牌新增"}, token=None)
    ck("无令牌不能新增学生", not d.get("ok"), "http=%s ok=%s" % (c, d.get("ok")))
    c, d = req("DELETE", "/v2/edu/dimension/students/99999999", token=admin)
    not500 = c != 500
    ck("删除不存在的 id 不返回 500", not500, "http=%s ok=%s" % (c, d.get("ok")))

    print("=== 5. 删除学生 ===")
    c, d = req("DELETE", "/v2/edu/dimension/students/%s" % created_id, token=admin)
    ck("删除学生成功", d.get("ok"), "http=%s resp=%s" % (c, str(d)[:70]))
    ck("删除后列表中已不存在", find(created_id) is None)
    created_id = None

finally:
    if created_id is not None:
        req("DELETE", "/v2/edu/dimension/students/%s" % created_id, token=ADMIN_TOKEN)
        print("  （已清理测试数据 studentId=%s）" % created_id)

passed = sum(1 for r in results if r[0])
print("\n===== 学生管理专项验收: 通过 %d / 失败 %d =====" % (passed, len(results) - passed))
for good, name, detail in results:
    if not good:
        print("  FAIL -> %s  %s" % (name, detail))
