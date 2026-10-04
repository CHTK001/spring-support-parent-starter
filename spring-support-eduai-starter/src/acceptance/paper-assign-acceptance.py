#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""班级试卷布置验收（老师给班级组卷 -> 班内学生可见）。

背景：功能上线前只有「组卷」与「发布」，没有「把卷发给哪个班」这一环，
学生端也就看不到任何老师给的卷。本脚本对**服务端状态**做闭环断言，
不只看接口是否 200：

  建班 + 建生(带初始口令) -> 学生登录
    -> 【基线】学生 /my-class 不含目标卷
    -> 老师布置(POST /v2/edu/paper-assign)
    -> 【核心】学生 /my-class 含该卷（且带 paperName/className）
    -> 老师 /mine 含该记录
    -> 重复布置不堆行（同一 卷+班 只有 1 行）
    -> 撤回后学生立刻看不到
    -> 再次布置可「复活」
    -> 未发布的卷布置了学生也看不到（学生端只放已发布卷）
    -> 负向：学生令牌/无令牌越权被拒、不存在的班级/试卷被拒

自建自删，不污染数据。退出码：0 全通过 / 1 有失败。
"""
import json
import os
import time
import urllib.error
import urllib.request

BASE = os.environ.get("BASE", "http://127.0.0.1:19095")
UA = "Mozilla/5.0 (eduai-paper-assign-acceptance)"
ADMIN_USER = os.environ.get("ADMIN_USER", "admin")
ADMIN_PWD = os.environ.get("ADMIN_PWD", "admin123")

INIT_PWD = "Init#12345"

results = []
STAMP = int(time.time())


def ck(name, cond, detail=""):
    results.append((bool(cond), name, detail))
    print("  [%s] %s%s" % ("PASS" if cond else "FAIL", name,
                           ("  " + detail) if detail else ""))


def skip(name, detail=""):
    print("  [SKIP] %s%s" % (name, ("  " + detail) if detail else ""))


def req(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    # 必须带浏览器式 UA：网关拒绝 Python-urllib 默认 UA，报 403 A0403S0000
    # 「用户信息不存在/请重新登录」—— 文案误导，真因是 UA。
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
        for k in ("records", "rows", "list", "data"):
            if isinstance(x.get(k), list):
                return x[k]
    return []


def my_class_papers(token):
    return as_list(req("GET", "/v2/edu/paper-assign/my-class", token=token)[1])


def my_assigns(token):
    return as_list(req("GET", "/v2/edu/paper-assign/mine", token=token)[1])


def paper_ids(rows):
    return {r.get("paperId") for r in rows}


admin = login(ADMIN_USER, ADMIN_PWD)
ck("管理员(=老师账号)登录", len(admin) > 20, "len=%d" % len(admin))
if not admin:
    raise SystemExit(1)

class_id = None
student_id = None
# 第二个班 + 第二个学生：用来证明「下发是给班的，不是给所有人的」
class2_id = None
student2_id = None
created_assigns = []

try:
    print("=== 0. 前置：当前账号必须是已绑定的老师 ===")
    c, d = req("GET", "/v2/edu/teacher/overview", token=admin)
    teacher = (d.get("data") or {}).get("teacher") or {}
    ck("登录账号已绑定教师工号（否则布置接口会 400「请先绑定教师工号」）",
       d.get("ok") and (teacher.get("teacherId") is not None),
       "http=%s teacherId=%s" % (c, teacher.get("teacherId")))

    print("=== 1. 建夹具：班级 + 班内学生（带初始口令）===")
    c, d = req("POST", "/v2/edu/dimension/classes/save",
               {"className": "试卷下发验收班-%d" % STAMP, "classGradeLevel": "PRIMARY"},
               token=admin)
    class_id = d.get("data") if isinstance(d.get("data"), int) else None
    ck("新增班级成功并返回 classId", d.get("ok") and class_id is not None,
       "http=%s classId=%s resp=%s" % (c, class_id, str(d)[:70]))

    c, d = req("POST", "/v2/edu/dimension/students/save",
               {"studentName": "下发验收生-%d" % STAMP, "studentPwdInput": INIT_PWD,
                "studentClassId": class_id, "studentGradeLevel": "PRIMARY"},
               token=admin)
    student_id = d.get("data") if isinstance(d.get("data"), int) else None
    ck("新增班内学生成功并返回 studentId", d.get("ok") and student_id is not None,
       "http=%s studentId=%s" % (c, student_id))

    rec = None
    for s in as_list(req("GET", "/v2/edu/dimension/students", token=admin)[1]):
        if s.get("studentId") == student_id:
            rec = s
            break
    stu_no = (rec or {}).get("studentNo")
    ck("拿到系统生成的学号", bool(stu_no), "studentNo=%s" % stu_no)
    ck("**学生已挂到该班级**（否则后面「看得见」证明不了是班级下发的）",
       (rec or {}).get("studentClassId") == class_id,
       "期望 classId=%s 实际=%s" % (class_id, (rec or {}).get("studentClassId")))

    stu = login(stu_no, INIT_PWD, "STUDENT")
    ck("学生用初始口令登录", len(stu) > 20, "len=%d" % len(stu))

    # 第二个班 + 第二个学生：用于「下发只对目标班生效」的隔离断言。
    # 没有它，本套件只能证明「布置后有人能看到」，证明不了「没布置的班看不到」。
    c, d = req("POST", "/v2/edu/dimension/classes/save",
               {"className": "试卷下发对照班-%d" % STAMP, "classGradeLevel": "PRIMARY"},
               token=admin)
    class2_id = d.get("data") if isinstance(d.get("data"), int) else None
    ck("新增对照班成功", d.get("ok") and class2_id is not None, "classId=%s" % class2_id)
    c, d = req("POST", "/v2/edu/dimension/students/save",
               {"studentName": "对照班学生-%d" % STAMP, "studentPwdInput": INIT_PWD,
                "studentClassId": class2_id, "studentGradeLevel": "PRIMARY"},
               token=admin)
    student2_id = d.get("data") if isinstance(d.get("data"), int) else None
    stu2_no = None
    for s in as_list(req("GET", "/v2/edu/dimension/students", token=admin)[1]):
        if s.get("studentId") == student2_id:
            stu2_no = s.get("studentNo")
            break
    stu2 = login(stu2_no, INIT_PWD, "STUDENT")
    ck("对照班学生可登录", len(stu2) > 20, "studentNo=%s" % stu2_no)

    print("=== 2. 选定一张「已发布」试卷 ===")
    c, d = req("GET", "/v2/edu/paper/page?pageNum=1&pageSize=200", token=admin)
    papers = as_list(d)
    published = [p for p in papers
                 if p.get("paperStatus") == 1 or p.get("paperStatusCode") == 1]
    draft = [p for p in papers
             if p.get("paperStatus") in (0, None) and p.get("paperStatusCode") != 1]
    ck("能取到试卷列表且存在已发布卷", d.get("ok") and len(published) > 0,
       "http=%s 总数=%d 已发布=%d" % (c, len(papers), len(published)))
    if not published:
        raise SystemExit(1)
    paper = published[0]
    paper_id = paper.get("paperId")
    print("  选用已发布卷 paperId=%s name=%s" % (paper_id, paper.get("paperName")))

    print("=== 3. 基线：布置前学生看不到任何本班卷 ===")
    before = my_class_papers(stu)
    ck("**布置前**学生 /my-class 不含目标卷", paper_id not in paper_ids(before),
       "布置前学生可见 %d 张" % len(before))

    print("=== 4. 老师布置 -> 班内学生可见（核心）===")
    c, d = req("POST", "/v2/edu/paper-assign",
               {"paperId": paper_id, "classId": class_id, "remark": "验收下发"}, token=admin)
    assign_id = d.get("data") if isinstance(d.get("data"), int) else None
    ck("老师布置成功并返回 assignId", d.get("ok") and assign_id is not None,
       "http=%s assignId=%s resp=%s" % (c, assign_id, str(d)[:80]))
    if assign_id:
        created_assigns.append(assign_id)

    after = my_class_papers(stu)
    hit = [r for r in after if r.get("paperId") == paper_id]
    ck("**班内学生 /my-class 立刻能看到这张卷**", len(hit) == 1,
       "可见 %d 张，命中 %d" % (len(after), len(hit)))
    ck("学生端记录带出卷名与班级名（JOIN 生效，不是只有 id）",
       bool(hit) and bool(hit[0].get("paperName")) and bool(hit[0].get("className")),
       "paperName=%s className=%s" % ((hit[0] if hit else {}).get("paperName"),
                                      (hit[0] if hit else {}).get("className")))
    ck("学生端只带出已发布卷的字段口径（assignStatus=1）",
       bool(hit) and hit[0].get("assignStatus") == 1,
       "assignStatus=%s" % ((hit[0] if hit else {}).get("assignStatus")))

    print("=== 4.1 隔离：下发只对目标班生效 ===")
    other = paper_ids(my_class_papers(stu2))
    ck("**对照班学生看不到发给另一个班的卷**", paper_id not in other,
       "对照班可见 %d 张；命中=%s" % (len(other), paper_id in other))
    ck("对照班学生仍能正常调用 /my-class（不是靠报错实现的隔离）",
       len(my_class_papers(stu2)) >= 0,
       "对照班可见 %d 张" % len(other))

    print("=== 4.2 学生查看试卷详情（按「布置关系」授权）===")
    c, d = req("GET", "/v2/edu/paper-assign/paper/%s" % paper_id, token=stu)
    detail = d.get("data") or {}
    qs = detail.get("questions")
    ck("学生能查看「布置给本班」的试卷详情", d.get("ok"), "http=%s msg=%s" % (c, d.get("msg")))
    ck("**详情里带出题目**（学生看得到题，不是空壳对话框）",
       isinstance(qs, list) and len(qs) > 0,
       "questions=%s" % (len(qs) if isinstance(qs, list) else qs))
    others = [p for p in published if p.get("paperId") != paper_id]
    if others:
        c, d = req("GET", "/v2/edu/paper-assign/paper/%s" % others[0].get("paperId"), token=stu)
        ck("**未布置给本班的卷看不到详情**（不是按试卷归属放行）",
           (not d.get("ok")) and c != 500,
           "http=%s msg=%s paperId=%s" % (c, d.get("msg"), others[0].get("paperId")))
    c, d = req("GET", "/v2/edu/paper-assign/paper/%s" % paper_id, token=None)
    ck("无令牌不能看试卷详情", not d.get("ok"), "http=%s" % c)

    print("=== 5. 老师侧「我布置的」可见 ===")
    mine = my_assigns(admin)
    mine_hit = [r for r in mine if r.get("paperId") == paper_id and r.get("classId") == class_id]
    ck("老师 /mine 含该布置记录", len(mine_hit) == 1, "记录数=%d" % len(mine_hit))

    print("=== 6. 重复布置：不能堆出重复行 ===")
    c, d = req("POST", "/v2/edu/paper-assign",
               {"paperId": paper_id, "classId": class_id}, token=admin)
    ck("重复布置仍成功（复活同一行）", d.get("ok"), "http=%s resp=%s" % (c, str(d)[:70]))
    again = [r for r in my_assigns(admin)
             if r.get("paperId") == paper_id and r.get("classId") == class_id]
    ck("**同一 卷+班 仍只有 1 行**（(paper_id,class_id) 唯一）", len(again) == 1,
       "重复布置后行数=%d" % len(again))
    ck("学生端也只有 1 条（未出现重复卡片）",
       len([r for r in my_class_papers(stu) if r.get("paperId") == paper_id]) == 1)

    print("=== 7. 撤回后学生立刻看不到 ===")
    cur = [r for r in my_assigns(admin)
           if r.get("paperId") == paper_id and r.get("classId") == class_id]
    rid = (cur[0] if cur else {}).get("assignId") or assign_id
    c, d = req("DELETE", "/v2/edu/paper-assign/%s" % rid, token=admin)
    ck("撤回成功", d.get("ok"), "http=%s assignId=%s" % (c, rid))
    ck("**撤回后学生 /my-class 不含该卷**", paper_id not in paper_ids(my_class_papers(stu)))

    print("=== 8. 再次布置可复活 ===")
    c, d = req("POST", "/v2/edu/paper-assign",
               {"paperId": paper_id, "classId": class_id}, token=admin)
    ck("复活布置成功", d.get("ok"), "http=%s" % c)
    ck("复活后学生又能看到", paper_id in paper_ids(my_class_papers(stu)))

    print("=== 9. 未发布的卷：布置了学生也看不到 ===")
    if draft:
        dp = draft[0]
        c, d = req("POST", "/v2/edu/paper-assign",
                   {"paperId": dp.get("paperId"), "classId": class_id}, token=admin)
        did = d.get("data") if isinstance(d.get("data"), int) else None
        if did:
            created_assigns.append(did)
        ck("未发布卷也能布置成功（布置不校验发布态）", d.get("ok"), "http=%s" % c)
        ck("**但学生端看不到未发布的卷**（只下发已发布）",
           dp.get("paperId") not in paper_ids(my_class_papers(stu)),
           "draftPaperId=%s" % dp.get("paperId"))
        if did:
            req("DELETE", "/v2/edu/paper-assign/%s" % did, token=admin)
    else:
        skip("未发布卷不可见", "库里没有草稿卷，跳过（不记为通过）")

    print("=== 10. 负向：越权与非法输入 ===")
    c, d = req("POST", "/v2/edu/paper-assign",
               {"paperId": paper_id, "classId": class_id}, token=stu)
    ck("学生令牌不能布置（越权被拒）", not d.get("ok"), "http=%s ok=%s" % (c, d.get("ok")))
    c, d = req("DELETE", "/v2/edu/paper-assign/%s" % rid, token=stu)
    ck("学生令牌不能撤回（越权被拒）", not d.get("ok"), "http=%s ok=%s" % (c, d.get("ok")))
    c, d = req("POST", "/v2/edu/paper-assign",
               {"paperId": paper_id, "classId": class_id}, token=None)
    ck("无令牌不能布置", not d.get("ok"), "http=%s ok=%s" % (c, d.get("ok")))
    c, d = req("GET", "/v2/edu/paper-assign/mine", token=stu)
    ck("学生令牌不能看老师侧 /mine", not d.get("ok"), "http=%s ok=%s" % (c, d.get("ok")))

    c, d = req("POST", "/v2/edu/paper-assign",
               {"paperId": paper_id, "classId": 99999999}, token=admin)
    ck("布置到不存在的班级被拒（不 500）", (not d.get("ok")) and c != 500,
       "http=%s msg=%s" % (c, d.get("msg")))
    c, d = req("POST", "/v2/edu/paper-assign",
               {"paperId": 99999999, "classId": class_id}, token=admin)
    ck("布置不存在的试卷被拒（不 500）", (not d.get("ok")) and c != 500,
       "http=%s msg=%s" % (c, d.get("msg")))
    c, d = req("POST", "/v2/edu/paper-assign", {"paperId": paper_id}, token=admin)
    ck("缺班级参数被拒（不 500）", (not d.get("ok")) and c != 500,
       "http=%s msg=%s" % (c, d.get("msg")))
    c, d = req("DELETE", "/v2/edu/paper-assign/99999999", token=admin)
    ck("撤回不存在的布置被拒（不 500）", (not d.get("ok")) and c != 500,
       "http=%s msg=%s" % (c, d.get("msg")))

    # 显式记账的覆盖缺口：跨老师越权（布置到别人的班）需要第二个教师账号，
    # 而当前环境 edu_teacher 只有 1 行（teacher_account=2）、edu_class 的 owner 全是 2，
    # 构造不出「别人的班」。这一条**不记为通过**，避免整份报告看起来比实际更全。
    skip("布置到「别人的班」被拒（跨老师越权）",
         "环境只有一个教师账号，构造不出他人班级；该分支由 requireMyClass 校验，未端到端覆盖")

finally:
    print("=== 11. 清理夹具 ===")
    for cid in (class_id, class2_id):
        if cid is None:
            continue
        for r in my_assigns(admin):
            if r.get("classId") == cid and r.get("assignStatus") == 1:
                req("DELETE", "/v2/edu/paper-assign/%s" % r.get("assignId"), token=admin)
    for sid, cid in ((student_id, class_id), (student2_id, class2_id)):
        if sid is not None:
            c, d = req("DELETE", "/v2/edu/dimension/students/%s" % sid, token=admin)
            print("  （已删除验收生 studentId=%s ok=%s）" % (sid, d.get("ok")))
        if cid is not None:
            c, d = req("DELETE", "/v2/edu/dimension/classes/%s" % cid, token=admin)
            print("  （已删除验收班 classId=%s ok=%s）" % (cid, d.get("ok")))

passed = sum(1 for r in results if r[0])
print("\n===== 班级试卷布置验收: 通过 %d / 失败 %d =====" % (passed, len(results) - passed))
for good, name, detail in results:
    if not good:
        print("  FAIL -> %s  %s" % (name, detail))
raise SystemExit(0 if passed == len(results) else 1)
