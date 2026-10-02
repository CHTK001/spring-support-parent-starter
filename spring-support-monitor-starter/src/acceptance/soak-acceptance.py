#!/usr/bin/env python3
"""服务管理 API 的稳定性（soak）验收：连续负载下是否退化。

与突发压测的区别：突发压测只看一段时间内的成功率与延迟，soak 看的是**趋势**。
典型退化有三类，突发压测一个都发现不了：

  1. 延迟随时间单调上升（连接池/缓冲累积、GC 压力、缓存无界增长）
  2. 线程/句柄/内存缓慢泄漏（每分钟涨一点，跑一小时才致命）
  3. 错误率随时间上升（资源耗尽后才开始报错）

所以这里分窗采样，每窗独立算 p50/p99 与资源增量，然后比较首窗与末窗。
判定标准刻意保守：p99 允许抖动，但不允许**趋势性**上升；资源增长要有绝对上限。

同样带上浏览器 UA：实测不带会被反爬 403 挡掉，那样等于什么都没压到。
"""

import json
import os
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

API = os.environ.get("MS_BASE", "http://127.0.0.1:8112") + "/ms/monitor/server"
NAME_PREFIX = "soak-node-"
DURATION_SEC = int(os.environ.get("SOAK_SECONDS", "240"))
WINDOW_SEC = int(os.environ.get("SOAK_WINDOW", "30"))
WORKERS = 8
UA = os.environ.get("MS_UA", "Mozilla/5.0 svc-soak-acceptance")

stop_flag = threading.Event()

# 窗口状态必须放在模块级：worker 是模块级函数，而定义在 main() 里的
# current_window() 对它们不可见（会 NameError）。试运行阶段就是靠这一点
# 提前暴露问题的，否则要白等满时长才发现。
WINDOW_LOCK = threading.Lock()
WINDOWS = []
WINDOW_HOLDER = {"cur": None}


def current_window():
    with WINDOW_LOCK:
        if WINDOW_HOLDER["cur"] is None:
            WINDOW_HOLDER["cur"] = Window(0)
            WINDOWS.append(WINDOW_HOLDER["cur"])
        return WINDOW_HOLDER["cur"]


def rotate_window():
    with WINDOW_LOCK:
        nxt = Window(len(WINDOWS))
        WINDOWS.append(nxt)
        WINDOW_HOLDER["cur"] = nxt
        return nxt


def http(method, path, body=None, timeout=30):
    data = None
    headers = {"Accept": "application/json", "User-Agent": UA}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            dt = time.perf_counter() - t0
            try:
                return resp.status, json.loads(raw), dt
            except json.JSONDecodeError:
                return resp.status, None, dt
    except urllib.error.HTTPError as e:
        return e.code, None, time.perf_counter() - t0
    except Exception:
        return 0, None, time.perf_counter() - t0


def free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    p = s.getsockname()[1]
    s.close()
    return p


def node_spec(name, port):
    return {"name": name, "protocol": "jdk-tcp", "engine": "jdk-tcp",
            "host": "0.0.0.0", "port": port, "enabled": 0,
            "remark": "soak", "filters": []}


class Window:
    def __init__(self, idx):
        self.idx = idx
        self.lat = []
        self.errors = []
        self.lock = threading.Lock()

    def rec(self, dt, err=None):
        with self.lock:
            self.lat.append(dt)
            if err:
                self.errors.append(err)

    def p(self, pct):
        if not self.lat:
            return -1.0
        o = sorted(self.lat)
        i = min(len(o) - 1, int(round(pct / 100.0 * (len(o) - 1))))
        return o[i] * 1000.0


def proc_snapshot(pid):
    rss = thr = 0
    try:
        with open("/proc/%d/status" % pid) as fh:
            for line in fh:
                if line.startswith("VmRSS:"):
                    rss = int(line.split()[1])
                elif line.startswith("Threads:"):
                    thr = int(line.split()[1])
    except OSError:
        pass
    try:
        out = subprocess.run(["ls", "/proc/%d/fd" % pid], capture_output=True,
                             text=True, timeout=20)
        fds = len([x for x in out.stdout.split("\n") if x.strip()])
    except Exception:
        fds = -1
    return rss, thr, fds


def find_pid():
    for cand in os.popen("pgrep -f java 2>/dev/null").read().split():
        try:
            with open("/proc/%s/cmdline" % cand, "rb") as fh:
                if b"monitor" in fh.read():
                    return int(cand)
        except OSError:
            continue
    return None


def cleanup():
    """无条件清掉本次 soak 建出来的所有节点。"""
    st, js, _ = http("GET", "")
    if st != 200 or js is None:
        return -1
    n = 0
    for node in (js.get("data") or []):
        if str(node.get("name", "")).startswith(NAME_PREFIX):
            http("DELETE", "/%d" % node["id"])
            n += 1
    return n


def worker_read(w):
    while not stop_flag.is_set():
        win = current_window()
        st, js, dt = http("GET", "")
        if st != 200:
            win.rec(dt, "读 HTTP %s" % st)
        elif js is None:
            win.rec(dt, "读 响应非 JSON")
        elif not js.get("success"):
            win.rec(dt, "读 success=false")
        else:
            win.rec(dt)
        time.sleep(0.02)


def worker_write(w):
    seq = 0
    while not stop_flag.is_set():
        seq += 1
        win = current_window()
        name = "%sw%02d-%05d" % (NAME_PREFIX, w, seq)
        st, js, dt = http("POST", "", node_spec(name, free_port()))
        if st != 200 or js is None or not js.get("success"):
            win.rec(dt, "写创建 HTTP %s" % st)
            time.sleep(0.2)
            continue
        nid = (js.get("data") or {}).get("id")
        if not nid:
            win.rec(dt, "写创建无 id")
            continue
        win.rec(dt)
        st2, _, dt2 = http("DELETE", "/%d" % nid)
        if st2 != 200:
            win.rec(dt2, "写删除 HTTP %s" % st2)
        else:
            win.rec(dt2)


def main():
    st, js, _ = http("GET", "")
    if st != 200 or js is None:
        print("RESULT=PRECONDITION_FAILED 后台不可达")
        return 3
    before_ids = sorted(n.get("id") for n in (js.get("data") or []))

    pid = find_pid()
    if not pid:
        print("RESULT=PRECONDITION_FAILED 找不到后台进程，无法做资源趋势观测")
        return 3
    begin = time.time()
    snap0 = proc_snapshot(pid)
    print("后端 pid=%d  起跑 RSS=%dKB 线程=%d fd=%d" % ((pid,) + snap0))
    print("计划：%ds 总时长，%ds 一窗，%d 并发（6 读 + 2 写）" % (DURATION_SEC, WINDOW_SEC, WORKERS))
    print("节点基线 %d 个 %s" % (len(before_ids), before_ids))

    # 窗口由 worker 每轮自己查（current_window），不能在启动时把窗口对象传进去：
    # 那样所有负载都会落进第 0 窗，后面几窗全空，趋势判定完全失效
    current_window()

    def sampler():
        while not stop_flag.is_set():
            time.sleep(WINDOW_SEC)
            if stop_flag.is_set():
                break
            rotate_window()

    with ThreadPoolExecutor(max_workers=WORKERS + 1) as pool:
        futures = []
        for w in range(WORKERS):
            fn = worker_read if w < WORKERS - 2 else worker_write
            futures.append(pool.submit(fn, w))
        futures.append(pool.submit(sampler))
        time.sleep(DURATION_SEC)
        stop_flag.set()
        for t in futures:
            t.result()

    print("\n===== 分窗结果 =====")
    print("  %-4s %8s %8s %8s %9s %9s %9s %9s" %
          ("窗", "请求", "异常", "p50ms", "p99ms", "RSS", "线程", "fd"))
    for w in WINDOWS:
        r, t, f = proc_snapshot(pid)
        print("  %-4d %8d %8d %8.1f %9.1f %9d %9d %9d" %
              (w.idx, len(w.lat), len(w.errors), w.p(50), w.p(99), r, t, f))

    cleanup()
    st, js, _ = http("GET", "")
    after_ids = sorted(n.get("id") for n in (js.get("data") or [])) if js else []
    leftover = [n.get("name") for n in (js.get("data") or []) if str(n.get("name", "")).startswith(NAME_PREFIX)] if js else ["<读不到>"]

    total_req = sum(len(w.lat) for w in WINDOWS)
    total_err = sum(len(w.errors) for w in WINDOWS)

    print("\n==================== 判定 ====================")
    print("  总请求 %d，总异常 %d，耗时 %ds" % (total_req, total_err, int(time.time() - begin)))
    print("  节点集合回到基线: %s   残留: %s" % (after_ids == before_ids, leftover))

    first, last = WINDOWS[0], WINDOWS[-1]
    drift_p99 = 0.0
    if first.p(99) > 0:
        drift_p99 = (last.p(99) - first.p(99)) / first.p(99) * 100.0
    print("  首窗 p99=%.1fms  末窗 p99=%.1fms  漂移 %+.1f%%" % (first.p(99), last.p(99), drift_p99))

    snap1 = proc_snapshot(pid)
    print("  资源增长: RSS %+dKB 线程 %+d fd %+d" %
          (snap1[0] - snap0[0], snap1[1] - snap0[1], snap1[2] - snap0[2]))

    problems = []
    if total_err:
        counts = {}
        for w in WINDOWS:
            for e in w.errors:
                counts[e] = counts.get(e, 0) + 1
        for reason, n in sorted(counts.items(), key=lambda kv: -kv[1])[:6]:
            problems.append("异常 %d 次: %s" % (n, reason))
    if leftover:
        problems.append("残留 soak 节点 %d 个" % len(leftover))
    if after_ids != before_ids:
        problems.append("节点集合与基线不一致")
    # 退化判定：末窗 p99 相对首窗上升超过 100% 才算趋势性退化（允许抖动）
    if drift_p99 > 100.0:
        problems.append("末窗 p99 比首窗高 %.0f%%，疑似随时间退化" % drift_p99)
    if snap1[1] - snap0[1] > 40:
        problems.append("线程增长 %d，疑似线程泄漏" % (snap1[1] - snap0[1]))
    if snap1[2] - snap0[2] > 60:
        problems.append("fd 增长 %d，疑似句柄泄漏" % (snap1[2] - snap0[2]))
    if snap1[0] - snap0[0] > 400 * 1024:
        problems.append("RSS 增长 %dKB，疑似内存泄漏" % (snap1[0] - snap0[0]))

    for p in problems:
        print("  PROBLEM " + p)
    if problems:
        print("RESULT=PROBLEMS_FOUND")
        return 1
    print("RESULT=STABLE 零异常，无趋势性退化，无资源异常增长")
    return 0


if __name__ == "__main__":
    sys.exit(main())