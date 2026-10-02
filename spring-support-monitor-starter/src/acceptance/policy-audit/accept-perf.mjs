/**
 * 生产级测试 #4 · 性能与容量（**受控探测，不是冲击压测**）
 *
 * <p>为什么不做冲击压测：这台机 16G 上有 11 个 JVM（合计 11.4GB RSS），
 * 我的服务 -Xmx1g，实测在真实负载下 OOM 过 13 次。冲击压测会
 * ① 反复 OOM ② 在途请求全丢 ③ 干扰同机其他人的服务。
 * 因此本项只做**阶梯式并发 + 逐级采样**，找到「服务开始劣化」的拐点就停。</p>
 *
 * <p>硬约束：</p>
 * <ul>
 *   <li>并发上限 24（不追求极限，找拐点足够）</li>
 *   <li>任一级 p95 &gt; 3000ms 或错误率 &gt; 5% 立即停止升压</li>
 *   <li>每一级之间留 8 秒让 GC 与连接池回落</li>
 *   <li>全程监控服务端 RSS / 堆峰值，OOM 立即中止并上报</li>
 * </ul>
 */
import { execFileSync } from "node:child_process";
import { writeFileSync, mkdirSync } from "node:fs";

const API = "http://192.168.50.198:18170/system/api";
const PLINK = "C:\\Program Files\\PuTTY\\plink.exe";
const HOSTKEY = "ssh-ed25519 255 SHA256:cwfyC8w0qteYq8lXud9P9U2aSS8O/bxQOK6Y60BOmsI";
const UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
const OUT = "D:/ch/project/.tmp/perf";
mkdirSync(OUT, { recursive: true });

function remote(cmd) {
  try {
    return execFileSync(PLINK, ["-batch", "-no-antispoof", "-hostkey", HOSTKEY, "-pw", "kali",
      "kali@192.168.50.198", cmd], { encoding: "utf8", timeout: 30000 }).trim();
  } catch (e) { return ""; }
}
function serverStat() {
  const raw = remote("printf 'kali\\n' | sudo -S bash -c 'PID=$(systemctl show system-api.service -p MainPID --value); echo \"$(grep VmRSS /proc/$PID/status | tr -s \" \" | cut -d\" \" -f2) $(grep VmHWM /proc/$PID/status | tr -s \" \" | cut -d\" \" -f2) $(systemctl show system-api.service -p NRestarts --value)\"' 2>/dev/null");
  const p = raw.split(/\s+/);
  return { rssKB: +p[0] || 0, hwmKB: +p[1] || 0, nRestarts: +p[2] || 0 };
}

async function login() {
  const r = await fetch(API + "/v2/user/login", {
    method: "POST", headers: { "Content-Type": "application/json", "User-Agent": UA },
    body: JSON.stringify({ username: "admin", password: "admin123" })
  });
  const j = await r.json();
  return j && j.data ? j.data.accessToken : null;
}

// 读接口为主（不产生写放大），混入一个真实鉴权路径
const TARGETS = [
  { p: "/v2/setting/default", w: 40 },
  { p: "/v2/user/page?pageNo=1&pageSize=10", w: 25 },
  { p: "/v2/role/page?pageNo=1&pageSize=10", w: 15 },
  { p: "/v2/policy/hit/counts", w: 10 },
  { p: "/v2/dict/page?pageNo=1&pageSize=10", w: 10 }
];
const ALL = TARGETS.flatMap(function (t) { return Array(t.w).fill(t.p); });

function pick() { return ALL[Math.floor(Math.random() * ALL.length)]; }

function pct(arr, p) {
  if (!arr.length) return 0;
  const s = [...arr].sort(function (a, b) { return a - b; });
  return Math.round(s[Math.min(s.length - 1, Math.floor(s.length * p))] * 10) / 10;
}

const token = await login();
if (!token) { console.log("登录失败，终止"); process.exit(1); }
console.log("登录成功，开始阶梯加压（上限 24 并发，p95>3000ms 或错误率>5% 立即停止）\n");

const STEPS = [1, 2, 4, 8, 12, 16, 20, 24];
const REQ_PER_STEP = 40;
const out = [];
let stopped = null;
const rss0 = serverStat();
console.log("起始服务端: RSS=" + Math.round(rss0.rssKB / 1024) + "MB  峰值=" + Math.round(rss0.hwmKB / 1024) + "MB  NRestarts=" + rss0.nRestarts);
console.log("");

for (const conc of STEPS) {
  const lat = [], codes = {};
  let errors = 0;
  const t0 = Date.now();
  let issued = 0;

  await Promise.all(Array.from({ length: conc }, async function () {
    while (issued < REQ_PER_STEP) {
      issued++;
      const path = pick();
      const s = Date.now();
      try {
        const r = await fetch(API + path, { headers: { "User-Agent": UA, Authorization: "Bearer " + token } });
        lat.push(Date.now() - s);
        const k = String(r.status);
        codes[k] = (codes[k] || 0) + 1;
        if (r.status >= 500) errors++;
        await r.arrayBuffer();
      } catch (e) {
        errors++;
        lat.push(Date.now() - s);
        const k = "NET";
        codes[k] = (codes[k] || 0) + 1;
      }
    }
  }));

  const wall = Date.now() - t0;
  const st = serverStat();
  const qps = (REQ_PER_STEP / (wall / 1000));
  const row = {
    conc, reqs: REQ_PER_STEP, wallMs: wall, qps: Math.round(qps * 10) / 10,
    p50: pct(lat, 0.5), p95: pct(lat, 0.95), max: pct(lat, 1),
    errors, codes, rssMB: Math.round(st.rssKB / 1024), hwmMB: Math.round(st.hwmKB / 1024), nRestarts: st.nRestarts
  };
  out.push(row);
  console.log("  并发=" + String(conc).padEnd(3) + " 请求=" + String(REQ_PER_STEP).padEnd(4) +
    " 吞吐=" + String(row.qps).padStart(7) + " qps" +
    "  p50=" + String(row.p50).padStart(5) + "ms  p95=" + String(row.p95).padStart(6) + "ms  max=" + String(row.max).padStart(6) + "ms" +
    "  错误=" + errors + "  " + JSON.stringify(codes) +
    "  RSS=" + row.rssMB + "MB  NRestarts=" + row.nRestarts);

  // 停止条件
  if (row.p95 > 3000) { stopped = "p95=" + row.p95 + "ms 超过 3000ms 阈值（并发 " + conc + "）"; break; }
  if (errors / REQ_PER_STEP > 0.05) { stopped = "错误率 " + Math.round(errors / REQ_PER_STEP * 100) + "% 超过 5%（并发 " + conc + "）"; break; }
  if (st.nRestarts > rss0.nRestarts) { stopped = "**服务端发生重启**（NRestarts " + rss0.nRestarts + " -> " + st.nRestarts + "），已中止"; break; }

  await new Promise(function (r) { setTimeout(r, 8000); });   // 让 GC 与连接池回落
}

const rssEnd = serverStat();
console.log("\n结束服务端: RSS=" + Math.round(rssEnd.rssKB / 1024) + "MB  峰值=" + Math.round(rssEnd.hwmKB / 1024) + "MB  NRestarts=" + rssEnd.nRestarts);
console.log("停止原因: " + (stopped || "跑满全部阶梯，未触发停止条件"));

// 恢复：确认服务仍健康
const health = await fetch(API + "/v2/setting/default", { headers: { "User-Agent": UA, Authorization: "Bearer " + token } });
console.log("收尾健康检查 /v2/setting/default: HTTP " + health.status);

writeFileSync(OUT + "/perf.json", JSON.stringify({ at: new Date().toISOString(), steps: out, stopped, rssStart: rss0, rssEnd, finalHealth: health.status }, null, 2), "utf8");
console.log("已写 " + OUT + "/perf.json");
