#!/usr/bin/env python3
"""检查终验报告是否已被换包作废（AGENTS.md §4.6）。

背景：本仓验收报告必须与产物 md5 绑定，**换包即失效**。实测同一台共享主机上
eduai jar 会被并发会话反复替换，三份报告因此先后作废。人工比对容易漏，
本脚本把这件事变成可自动执行的检查。

做法：从每份 `ACCEPTANCE-REPORT-*.md` 里抽出它绑定的 jar sha256，
与「线上现役」的 sha256 比对；不一致即判定该报告已失效。

线上指纹怎么来：由调用方通过参数传入（本脚本不联网、不 ssh）：
    --live-eduai <sha256>  --live-oauth <sha256>
通常这样取得：
    ssh <host> 'sha256sum <eduai jar> <oauth jar>'

退出码：0 全部绑定现役产物，或失效报告均已按 §4.6 标注；3 存在**未标注**的失效报告
（须标注或重跑验收）；2 用法错误。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# 报告里对绑定的写法不止一种，实测至少两类：
#   eduai jar     sha256 = 6f13188c...        （显式带 eduai）
#   本地构建 jar sha256 = a2d012b5...        （PROVENANCE 段，指的是 eduai jar）
#   部署后线上 jar sha256 = a2d012b5...
# 所以不能只认第一种 —— 否则会把已失效的报告误判成 VALID（本脚本首版就这样错过了一份）。
_LINE_RE = re.compile(r"sha256\s*=\s*([0-9a-fA-F]{16,64})")
_KIND_RE = re.compile(r"(eduai|oauth)", re.IGNORECASE)
# 未点名 eduai 但语义上就是 eduai jar 的行（PROVENANCE 自证段）
_EDUAI_HINTS = ("本地构建", "部署后线上", "线上 jar", "构建 jar", "jar sha256")


def extract(path: Path) -> dict[str, set[str]]:
    """抽出一份报告里绑定的 eduai/oauth jar sha256 集合（同一类可多处出现）。

    返回集合而非单值：报告可能同时写「构建产物」与「部署后线上」两行，
    两行应相等；若不等而其一命中线上，说明报告自相矛盾，按失效处理更安全。
    """
    text = path.read_text(encoding="utf-8", errors="replace")
    kinds: dict[str, set[str]] = {"eduai": set(), "oauth": set()}
    for line in text.splitlines():
        m = _LINE_RE.search(line)
        if not m:
            continue
        value = m.group(1).lower()
        low = line.lower()
        if "oauth" in low:
            kinds["oauth"].add(value)
        elif "eduai" in low or any(h in line for h in _EDUAI_HINTS):
            kinds["eduai"].add(value)
    return kinds


def compare(bound: str, live: str) -> bool:
    """前缀匹配即可：报告里可能写缩写，线上是全量。"""
    if not bound or not live:
        return False
    return live.startswith(bound) or bound.startswith(live)


def main() -> int:
    ap = argparse.ArgumentParser(description="检查终验报告是否已随换包失效")
    ap.add_argument("--dir", default=None, help="acceptance 目录（默认取本脚本所在目录）")
    ap.add_argument("--live-eduai", default=os_live("EDUAI_JAR_SHA256"),
                    help="线上 eduai jar 的 sha256")
    ap.add_argument("--live-oauth", default=os_live("OAUTH_JAR_SHA256"),
                    help="线上 oauth jar 的 sha256")
    args = ap.parse_args()

    root = Path(args.dir) if args.dir else Path(__file__).resolve().parent
    reports = sorted(root.glob("ACCEPTANCE-REPORT-*.md"))
    if not reports:
        print("  没有找到 ACCEPTANCE-REPORT-*.md")
        return 0

    live = {"eduai": (args.live_eduai or "").lower(),
            "oauth": (args.live_oauth or "").lower()}
    if not live["eduai"] and not live["oauth"]:
        print("  未提供线上指纹（--live-eduai / --live-oauth），无法判定", file=sys.stderr)
        return 2

    stale = 0
    annotated = 0
    print(f"  线上现役：eduai={live['eduai'][:16] or '(未给)'}  oauth={live['oauth'][:16] or '(未给)'}")
    for r in reports:
        bound = extract(r)
        if not bound["eduai"] and not bound["oauth"]:
            print(f"  [SKIP]   {r.name}  未解析到绑定的 sha256")
            continue
        # 已有「已失效」标注的报告，单独标注，避免重复计入
        body = r.read_text(encoding="utf-8", errors="replace")
        already = "已失效" in body[:800]
        parts = []
        ok_all = True
        for kind in ("eduai", "oauth"):
            vals = sorted(bound[kind])
            if not vals:
                continue
            # 同一类必须**全部**命中线上：只命中一个、另一个对不上，说明报告自相矛盾
            ok = all(compare(v, live[kind]) for v in vals)
            shown = "/".join(v[:8] for v in vals)
            parts.append(f"{kind}={shown}{'' if ok else '≠'}")
            ok_all = ok_all and ok
        if ok_all:
            print(f"  [VALID]  {r.name}  {' '.join(parts)}")
        else:
            tag = "已标注失效" if already else "**需标注失效**"
            # 已按 §4.6 标注的历史报告不再计入 stale（否则任何一份历史报告都会让
            # 退出码恒为 3，检查永远红 = 检查失去意义）；未标注的照旧逼人处理。
            if already:
                annotated += 1
            else:
                stale += 1
            print(f"  [STALE]  {r.name}  {' '.join(parts)}  -> {tag}")

    if stale:
        print(f"\n  REPORT_STALE  有 {stale} 份失效报告**未标注**；"
              f"按 §4.6 标注失效或重跑验收")
        return 3
    if annotated:
        print(f"\n  REPORT_STALE_ANNOTATED  {annotated} 份历史报告已失效但均已按 §4.6 标注；"
              f"绑定现役产物的报告 VALID")
        return 0
    print("\n  REPORT_VALID  所有报告都绑定当前线上产物")
    return 0


def os_live(name: str) -> str:
    """从环境变量取默认值。"""
    import os
    return os.environ.get(name, "")


if __name__ == "__main__":
    sys.exit(main())