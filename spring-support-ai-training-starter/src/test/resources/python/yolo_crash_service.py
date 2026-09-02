#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""YOLO 训练服务「进程崩溃」场景 mock（C 级冒烟/快速失败回归用）。

线协议与 yolo_mock_service.py 一致（stdout 首行 PORT= 协商 → POST /start），
区别：收到 /start 后先回 200，随即退出进程，模拟真实训练服务在训练中崩溃。
用于验证 Java 侧在子进程死亡时立即判定失败，而非空转轮询至超时。
仅依赖 Python 标准库，无需 ultralytics。
"""
import http.server
import os
import threading


class Handler(http.server.BaseHTTPRequestHandler):
    """一次性崩溃服务：/start 回 200 后 0.4s 退出进程。"""

    def log_message(self, fmt, *args):  # 静默访问日志
        pass

    def do_POST(self):
        if self.path == "/start":
            length = int(self.headers.get("Content-Length", 0))
            if length:
                self.rfile.read(length)
            body = b'{"task":1}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            # 0.4s 后崩溃（先让 200 响应送达，再模拟训练中途进程死亡）
            threading.Timer(0.4, os._exit, args=(1,)).start()
        else:
            self.send_response(404)
            self.end_headers()

    def do_GET(self):
        self.send_response(404)
        self.end_headers()


def main():
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    print("PORT=%d" % srv.server_address[1], flush=True)
    srv.serve_forever()


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
