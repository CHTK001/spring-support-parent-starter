#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
YOLO 训练一次性 HTTP 服务（与 Java YoloTrainProvider 配套）。

协议：
  POST /start   body: {"data": str, "model": str, "epochs": int,
                        "imgsz": int, "device": str, "project": str, "name": str}
                启动 ultralytics YOLO 训练线程，立即返回 {"task": "1"}。
  GET  /status  -> {"status": starting|running|done|failed,
                    "current_epoch": int, "total_epochs": int, "best_map": float,
                    "message": str, "best_model": str}
  GET  /health  -> {"ok": true}

约定：
  - data 为含 data.yaml 的数据集目录（YOLO 官方布局：images/ + labels/ + data.yaml）。
  - 训练完成时把 best.pt 拷贝到 project/ 目录（Java 以 best.pt 存在为完成信号）。
  - 服务在启动后把 "PORT=<port>" 打印到 stdout 首行，供 Java 协商端口后关闭子进程句柄。
  - 训练失败会记录 message 并以 status=failed 返回，同时退出码非 0。

依赖：pip install ultralytics
"""
import json
import os
import shutil
import socket
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:
    from ultralytics import YOLO
except Exception as e:  # pragma: no cover
    YOLO = None
    _IMPORT_ERR = str(e)


class State:
    status = "starting"
    current_epoch = 0
    total_epochs = 0
    best_map = 0.0
    message = ""
    best_model = ""


# 模块级全局训练状态（Handler 与训练线程共享）
state = State()


def _free_port():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


def _run_train(args):
    global state
    try:
        if YOLO is None:
            raise RuntimeError("ultralytics 未安装: " + (_IMPORT_ERR or "import failed"))
        model = YOLO(args.get("model", "yolo11n.pt"))
        project = args.get("project", "runs")
        name = args.get("name", "train")
        total = int(args.get("epochs", 10))
        state.total_epochs = total
        model.train(
            data=args["data"],
            epochs=total,
            imgsz=int(args.get("imgsz", 640)),
            device=args.get("device", "cpu"),
            project=project,
            name=name,
            verbose=False,
        )
        best = getattr(model, "best", None)
        if best and os.path.exists(str(best)):
            state.best_model = str(best)
            state.message = "best model at " + str(best)
        else:
            state.best_model = os.path.join(project, name, "weights", "best.pt")
            state.message = "trained model at " + state.best_model
        state.status = "done"
    except Exception as e:  # noqa: BLE001
        state.status = "failed"
        state.message = str(e)


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path.rstrip("/") == "/start":
            try:
                length = int(self.headers.get("Content-Length", 0))
                args = json.loads(self.rfile.read(length).decode("utf-8"))
            except Exception as e:  # noqa: BLE001
                self._send(400, {"ok": False, "message": str(e)})
                return
            t = threading.Thread(target=_run_train, args=(args,), daemon=True)
            t.start()
            self._send(200, {"ok": True, "task": "1"})
        else:
            self._send(404, {"ok": False})

    def do_GET(self):
        path = self.path.split("?")[0].rstrip("/")
        if path == "/health":
            self._send(200, {"ok": True})
        elif path == "/status":
            self._send(200, {
                "status": state.status,
                "current_epoch": state.current_epoch,
                "total_epochs": state.total_epochs,
                "best_map": state.best_map,
                "message": state.message,
                "best_model": state.best_model,
            })
        else:
            self._send(404, {"ok": False})

    def log_message(self, *a):  # 静默
        pass


def main():
    port = _free_port()
    print("PORT=%d" % port, flush=True)
    srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    # 由 Java 侧在训练结束后 destroy 子进程
    srv.serve_forever()


if __name__ == "__main__":
    main()
