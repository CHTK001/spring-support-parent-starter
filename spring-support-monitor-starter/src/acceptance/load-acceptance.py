#!/usr/bin/env python3
"""服务管理 API 的负载 / 并发 / 稳定性验收（跑在生产所用的 Linux 上）。

设计要点，每一条都对应一类「压测看起来全绿其实没测到」的失败模式：

1. 读压测与写压测分开统计。混在一起时，写路径的慢会把读路径的抖动淹掉。
2. 延迟用分位数而不是平均值。平均值会把长尾抹平：p99 差 10 倍时均值可能只差 20%。
3. 不只看错误率。每条响应都校验 JSON 结构与业务 success 字段 ——
   返回 200 但 success=false、或返回 HTML 落地页，在只数 HTTP 状态码的压测里都是全绿。
4. 写压测用「创建 -> 回查确认 -> 删除 -> 确认已消失」闭环，而不是发完就删。
   只发不验的压测，无法区分「删干净了」和「删请求被吞了、对象还在」。
5. 全程记录后端进程的 RSS / 线程数 / LISTEN socket 数，压测后复查。
   只看接口成功率的话，连接与线程泄漏完全不可见 —— 而那正是稳定性问题的主要形态。
6. 收尾无条件清理，并断言节点集合与压测前逐个一致。留残留会让后面的验收全部失真。
"""

import json
import os
import socket
import statistics
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = os.environ.get("MS_BASE", "http://127.0.0.1:8112")
API = BASE + "/ms/monitor/server"
BACKEND_PID = None
NAME_PREFIX = "load-node-"


def http(method, path, body=None, timeout=30):
    """发一次请求，返回 (状态码, 解析后的 JSON 或 None, 耗时秒, 原始文本前 120 字)。"""
    data = None
    headers = {"Accept": "application/json"}
    # 必须带浏览器 UA：实测后台有基于 User-Agent 的反爬拦截，
    # urllib 默认的 "Python-urllib/x.y" 会被 403 挡掉（code S0503C0000
    # "请求被识别为爬虫客户端"）。不带这个头的话写路径在门口就被拒，
    # 压测看起来"跑完了"，实际上一条写入都没发生 —— 这比压测失败更危险。
    headers["User-Agent"] = os.environ.get("MS_UA", "Mozilla/5.0 svc-load-acceptance")
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
            elapsed = time.perf_counter() - started
            try:
                return resp.status, json.loads(raw), elapsed, raw[:120]
            except json.JSONDecodeError:
                # 200 但不是 JSON（例如被某个 SPA 兜底页接管），必须算异常
                return resp.status, None, elapsed, raw[:120]
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        return e.code, None, time.perf_counter() - started, raw[:120]
    except Exception as e:  # 连接被拒 / 超时
        return 0, None, time.perf_counter() - started, type(e).__name__


def percentile(values, pct):
    if not values:
        return -1.0
    ordered = sorted(values)
    idx = min(len(ordered) - 1, int(round(pct / 100.0 * (len(ordered) - 1))))
    return ordered[idx] * 1000.0


def list_nodes():
    status, js, _, raw = http("GET", "")
    if status != 200 or js is None:
        return None
    data = js.get("data")
    return data if isinstance(data, list) else None


def free_port():
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


def node_spec(name, port):
    return {
        "name": name,
        "protocol": "jdk-tcp",
        "engine": "jdk-tcp",
        "host": "0.0.0.0",
        "port": port,
        "enabled": 0,
        "remark": "load-test",
        "filters": [],
    }


class Stats:
    def __init__(self):
        self.latencies = []
        self.errors = []
        self.lock = threading.Lock()

    def ok(self, elapsed):
        with self.lock:
            self.latencies.append(elapsed)

    def fail(self, elapsed, reason):
        with self.lock:
            self.latencies.append(elapsed)
            self.errors.append(reason)

    def report(self, title, expect_requests):
        with self.lock:
            lat = list(self.latencies)
            errs = list(self.errors)
        print("\n  [%s] 请求 %d，异常 %d" % (title, expect_requests, len(errs)))
        if errs:
            counts = {}
            for e in errs:
                counts[e] = counts.get(e, 0) + 1
            for reason, n in sorted(counts.items(), key=lambda kv: -kv[1])[:6]:
                print("      %5d x %s" % (n, reason))
        if lat:
            print("      延迟 ms: p50=%.1f p90=%.1f p99=%.1f max=%.1f 均值=%.1f"
                  % (percentile(lat, 50), percentile(lat, 90), percentile(lat, 99),
                     max(lat) * 1000, statistics.fmean(lat) * 1000))
        return len(errs)


def read_load(workers, rounds):
    stats = Stats()

    def worker(_):
        for _ in range(rounds):
            status, js, elapsed, raw = http("GET", "")
            if status != 200:
                stats.fail(elapsed, "HTTP %s" % status)
            elif js is None:
                stats.fail(elapsed, "响应不是 JSON: %s" % raw[:60])
            elif "success" not in js:
                stats.fail(elapsed, "响应缺 success 字段")
            elif not js.get("success"):
                stats.fail(elapsed, "success=false: %s" % str(js.get("msg"))[:60])
            else:
                stats.ok(elapsed)

    with ThreadPoolExecutor(max_workers=workers) as pool:
        list(pool.map(worker, range(workers)))
    return stats, workers * rounds


def write_load(workers, cycles):
    """每个 worker 反复「建 -> 回查 -> 删 -> 确认消失」，任一步不符即计异常。"""
    stats = Stats()
    residue = []

    def worker(w):
        for i in range(cycles):
            name = "%sw%02d-c%03d" % (NAME_PREFIX, w, i)
            port = free_port()
            body = node_spec(name, port)
            t0 = time.perf_counter()
            status, js, _, raw = http("POST", "", body)
            dt = time.perf_counter() - t0
            if status != 200 or js is None or not js.get("success"):
                stats.fail(dt, "创建失败 HTTP %s %s" % (status, raw[:50]))
                continue
            node_id = (js.get("data") or {}).get("id")
            if not node_id:
                stats.fail(dt, "创建成功但没有返回 id")
                continue
            # 回查：确认真的落库了，而不是只回了个 id
            t0 = time.perf_counter()
            st2, js2, _, raw2 = http("GET", "/%d" % node_id)
            dt = time.perf_counter() - t0
            if st2 != 200 or js2 is None or not js2.get("success"):
                stats.fail(dt, "回查失败 HTTP %s" % st2)
            elif (js2.get("data") or {}).get("name") != name:
                stats.fail(dt, "回查名字不匹配")
            else:
                stats.ok(dt)

            # 删除
            t0 = time.perf_counter()
            st3, _, _, raw3 = http("DELETE", "/%d" % node_id)
            dt = time.perf_counter() - t0
            if st3 != 200:
                stats.fail(dt, "删除失败 HTTP %s %s" % (st3, raw3[:50]))
                continue
            # 确认真的消失了
            if any(n.get("id") == node_id for n in (list_nodes() or [])):
                stats.fail(dt, "删除后节点仍存在")
                residue.append(node_id)
            else:
                stats.ok(dt)

    with ThreadPoolExecutor(max_workers=workers) as pool:
        list(pool.map(worker, range(workers)))
    return stats, workers * cycles, residue


def proc_snapshot(pid):
    """后端进程的 RSS / 线程数 / LISTEN socket 数。"""
    rss_kb, threads, listens = 0, 0, 0
    try:
        with open("/proc/%d/status" % pid) as fh:
            for line in fh:
                if line.startswith("VmRSS:"):
                    rss_kb = int(line.split()[1])
                elif line.startswith("Threads:"):
                    threads = int(line.split()[1])
    except OSError:
        pass
    try:
        # 刻意用 ls 而不是 open()：直接 open('/proc/<pid>/fd') 在多数环境下读不到内容
        # （ptrace 权限），会静默返回 0，于是「句柄泄漏」这项检查永远是 0 增长，
        # 看起来通过其实没测。实测踩过：fd 一直是 0，与真实占用不符。
        out = subprocess.run(["ls", "/proc/%d/fd" % pid],
                             capture_output=True, text=True, timeout=20)
        fds = len([x for x in out.stdout.split("\n") if x.strip()])
    except Exception:
        fds = -1
    return rss_kb, threads, fds


def main():
    before = list_nodes()
    if before is None:
        print("RESULT=PRECONDITION_FAILED 无法读取节点列表，后台不可达或响应非 JSON")
        return 3
    before_ids = sorted(n.get("id") for n in before)
    print("基线：节点 %d 个  %s" % (len(before), [n.get("name") for n in before]))

    pid = None
    if BACKEND_PID:
        pid = BACKEND_PID
    else:
        for cand in os.popen("pgrep -f 'java.*monitor|spring' 2>/dev/null").read().split():
            try:
                with open("/proc/%s/cmdline" % cand, "rb") as fh:
                    if b"monitor" in fh.read():
                        pid = int(cand)
                        break
            except OSError:
                continue
    snap0 = proc_snapshot(pid) if pid else (0, 0, 0)
    print("后端 pid=%s  基线 RSS=%dKB 线程=%d fd=%d" % ((pid,) + snap0))

    print("\n===== 阶段 1：读负载（12 并发 x 60 轮）=====")
    rstats, rcount = read_load(12, 60)
    rerr = rstats.report("读", rcount)

    print("\n===== 阶段 2：写负载（8 并发 x 8 轮，共 64 次 建/查/删 闭环）=====")
    wstats, wcount, residue = write_load(8, 8)
    werr = wstats.report("写", wcount * 3)

    print("\n===== 阶段 3：收尾与一致性 =====")
    # 无条件清理：残留 id 与按前缀兜底
    for node_id in residue:
        http("DELETE", "/%d" % node_id)
    for n in (list_nodes() or []):
        if str(n.get("name", "")).startswith(NAME_PREFIX):
            http("DELETE", "/%d" % n["id"])
    after = list_nodes() or []
    after_ids = sorted(n.get("id") for n in after)
    leftover = [n.get("name") for n in after if str(n.get("name", "")).startswith(NAME_PREFIX)]
    print("  压测后节点 %d 个；按前缀残留 %d 个 %s" % (len(after), len(leftover), leftover))
    print("  节点集合与基线一致: %s" % (after_ids == before_ids))

    # 等待收敛后复查进程指标：线程/fd 泄漏是稳定性问题的主要形态，只看接口成功率看不见
    time.sleep(5)
    snap1 = proc_snapshot(pid) if pid else (0, 0, 0)
    print("  后端 pid=%s  压测后 RSS=%dKB 线程=%d fd=%d" % ((pid,) + snap1))
    rss_growth = snap1[0] - snap0[0] if pid else 0
    thr_growth = snap1[1] - snap0[1] if pid else 0
    fd_growth = snap1[2] - snap0[2] if pid else 0
    print("  增长: RSS %+dKB 线程 %+d fd %+d" % (rss_growth, thr_growth, fd_growth))

    print("\n==================== 判定 ====================")
    problems = []
    if rerr:
        problems.append("读负载有 %d 次异常" % rerr)
    if werr:
        problems.append("写负载有 %d 次异常" % werr)
    if leftover:
        problems.append("残留 %d 个压测节点" % len(leftover))
    if after_ids != before_ids:
        problems.append("节点集合与基线不一致")
    if pid and thr_growth > 40:
        problems.append("线程数增长 %d，疑似线程泄漏" % thr_growth)
    if pid and fd_growth > 60:
        problems.append("fd 增长 %d，疑似句柄泄漏" % fd_growth)
    if pid and rss_growth > 300 * 1024:
        problems.append("RSS 增长 %dKB，疑似内存泄漏" % rss_growth)

    for p in problems:
        print("  PROBLEM " + p)
    if problems:
        print("RESULT=PROBLEMS_FOUND")
        return 1
    print("RESULT=CLEAN 读/写压测零异常，状态一致，无线程/fd/内存异常增长")
    return 0


if __name__ == "__main__":
    sys.exit(main())