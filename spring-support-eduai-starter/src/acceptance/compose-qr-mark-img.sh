#!/usr/bin/env bash
python3 - <<'PY'
from PIL import Image

base_path = "/home/kali/eduai/backend/data/marking/fc8f9ccdf30e4f1cacaf88158ed618a9-1.png"
qr_path = "/tmp/paper-qr-9001.png"
out_path = "/tmp/combined-9001.png"

base = Image.open(base_path).convert("RGB")
qr = Image.open(qr_path).convert("RGB")

qsize = 128
qr_small = qr.resize((qsize, qsize), Image.LANCZOS)

# 底部加 140px 白色留白放二维码，避免遮挡答题内容
pad = 140
W, H = base.width, base.height + pad
canvas = Image.new("RGB", (W, H), (255, 255, 255))
canvas.paste(base, (0, 0))
# 二维码放右下角，留 16px 边距
qx = W - qsize - 16
qy = base.height + (pad - qsize) // 2
canvas.paste(qr_small, (qx, qy))

canvas.save(out_path)
print("COMBINED", out_path, canvas.size)
PY