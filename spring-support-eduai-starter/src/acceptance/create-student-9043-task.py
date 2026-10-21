#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Create a paper-9043 marking task for student 2026000001 and wait for result."""
import json
import os
import sys
import time
import urllib.request
import urllib.error

BASE = os.environ.get("BASE", "http://192.168.50.198:19095")
PAPER_ID = "9043"
STUDENT_ID = "2026000001"
IMAGE_DIR = os.environ.get("IMAGE_DIR", "D:/ch/project/.tmp")
IMAGES = [os.path.join(IMAGE_DIR, "9043-page%d.png" % i) for i in range(1, 7)]


def req(method, path, body=None, token=None, raw=None, ctype="application/json"):
    data = raw if raw is not None else (json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None)
    r = urllib.request.Request(BASE + path, data=data, method=method)
    r.add_header("Content-Type", ctype)
    r.add_header("User-Agent", "student-9043-acceptance")
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
    except Exception as e:
        return 0, {"error": repr(e)}


def login(username, password, auth_type=None, account_type=None):
    body = {"username": username, "password": password}
    if auth_type:
        body["authType"] = auth_type
    if account_type:
        body["accountType"] = account_type
    c, d = req("POST", "/v2/user/login", body)
    if not (d.get("data") or {}).get("accessToken"):
        print("RAW_LOGIN_HTTP=%s RESP=%s" % (c, json.dumps(d, ensure_ascii=False)[:300]))
    return d.get("data") or {}


def multipart_scan(token):
    boundary = "----student9043%s" % int(time.time() * 1000)
    parts = []
    for name, val in (("mode", "SELF"), ("paperId", PAPER_ID), ("studentId", STUDENT_ID)):
        parts.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n" % (boundary, name, val)).encode())
    for i, img in enumerate(IMAGES, 1):
        with open(img, "rb") as f:
            data = f.read()
        parts.append(("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"answer-%d.png\"\r\n"
                      "Content-Type: image/png\r\n\r\n" % (boundary, i)).encode())
        parts.append(data)
        parts.append(b"\r\n")
    parts.append(("--%s--\r\n" % boundary).encode())
    body = b"".join(parts)
    return req("POST", "/v2/edu/marking/scan", token=token, raw=body,
               ctype="multipart/form-data; boundary=" + boundary)


def main():
    for img in IMAGES:
        if not os.path.isfile(img):
            print("MISSING", img)
            return 1
    admin = login("admin", "admin123")
    token = admin.get("accessToken") or ""
    if not token:
        print("ADMIN_LOGIN_FAIL", json.dumps(admin, ensure_ascii=False)[:200])
        return 1
    print("admin_token_len=%d" % len(token))

    c, d = multipart_scan(token)
    task_id = d.get("data") if isinstance(d.get("data"), str) else ""
    print("scan_http=%d task_id=%s resp=%s" % (c, task_id, json.dumps(d, ensure_ascii=False)[:160]))
    if not task_id:
        return 1

    deadline = time.time() + 240
    payload = {}
    while time.time() < deadline:
        _, payload = req("GET", "/v2/edu/marking/result/" + task_id, token=token)
        data = payload.get("data") or {}
        stage = data.get("stage") or ""
        if stage in ("已完成", "失败") or data.get("finished"):
            print("done_after=%.0fs stage=%s finished=%s" % (time.time() + 240 - deadline, stage, data.get("finished")))
            break
        time.sleep(3)
    data = payload.get("data") or {}
    results = data.get("results") or []
    print("total=%s status=%s stage=%s results=%d finished=%s" % (
        data.get("total"), data.get("taskStatus"), data.get("stage"), len(results), data.get("finished")))
    if results:
        scored = sum(1 for r in results if (r.get("score") or 0) > 0)
        print("scored=%d total_score=%s" % (scored, data.get("totalScore")))
    with open(os.environ.get("TASK_OUT", "D:/ch/project/.tmp/student-9043-task.txt"), "w") as f:
        f.write(task_id)
    return 0 if data.get("stage") == "已完成" and len(results) > 0 else 2


if __name__ == "__main__":
    sys.exit(main())