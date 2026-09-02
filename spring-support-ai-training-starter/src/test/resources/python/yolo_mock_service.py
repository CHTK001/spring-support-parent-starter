#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""YOLO 训练服务的协议 mock（C 级冒烟用）。

与 classpath 内 python/yolo_train_service.py 保持相同线协议：
  - 启动后 stdout 打印首行 PORT=<port>（端口协商）
  - POST /start  -> 200，后台线程模拟训练
  - GET  /status -> {status, current_epoch, total_epochs, best_map, message, best_model}

仅依赖 Python 标准库，无需 ultralytics / 数据集 / 联网。
模拟 3 个 epoch 后置 done，用于验证 Java 侧 YoloTrainProvider 的
进程拉起 -> 端口协商 -> HTTP 下发 -> 轮询 -> 结果回收 全链路。
"""
import http.server
import json
import sys
import threading
import time

# 模块级全局状态（线程间共享）
state = {
    "status": "starting",
    "current_epoch": 0,
    "total_epochs": 3,
    "best_map": 0.0,
    "message": "",
    "best_model": "",
}

EPOCH_SECONDS = 1.2
TOTAL_EPOCHS = 3


def _run_train():
    """模拟训练线程：逐 epoch 推进 best_map，完成后置 done。"""
    state["status"] = "running"
    for i in range(1, TOTAL_EPOCHS + 1):
        time.sleep(EPOCH_SECONDS)
        state["current_epoch"] = i
        state["best_map"] = round(0.10 + i * 0.05, 4)
    state["status"] = "done"
    state["message"] = "mock train complete"
    state["best_model"] = "C:/mock/best.pt"


class Handler(http.server.BaseHTTPRequestHandler):
    """一次性训练服务：/start 起训练线程，/status 返回快照。"""

    def log_message(self, fmt, *args):  # 静默访问日志
        pass

    def do_POST(self):
        if self.path == "/start":
            length = int(self.headers.get("Content-Length", 0))
            self.rfile.read(length)
            threading.Thread(target=_run_train, daemon=True).start()
            body = b'{"task":1}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()

    def do_GET(self):
        if self.path == "/status":
            body = json.dumps(state).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()


def main():
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    # 端口协商协议：stdout 首行 PORT=<port>，Java waitForReady 按此前缀识别
    print("PORT=%d" % srv.server_address[1], flush=True)
    srv.serve_forever()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(0)
