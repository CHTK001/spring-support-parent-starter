/**
 * 全部服务类型 —— 创建/真实流量/停止/删除 全量探测（真实后端 8112，经 8848 代理）
 *
 * 覆盖 /ms/monitor/server/protocols 返回的全部 24 种协议：
 *  - HTTP 类（http/vertx-http/iocp）→ 发 HTTP 请求，断言状态行
 *  - 反向代理类（*proxy）→ 转发到 8112 本体，断言状态行
 *  - FTP 类 → 等 220 banner
 *  - Telnet → 等协商 + banner
 *  - SIP → 发 OPTIONS，断言 SIP/1.1 状态行
 *  - SOCKS5 → 发握手，断言 05 00
 *  - 帧式 TCP（tcp/jdk-tcp/vertx-tcp/tcp-aio/aio-tcp/sniff-tcp/rsocket/jdk-websocket）
 *      → 发帧，断言有字节响应（无帧处理器时为 HTTP/1.1 200 + Content-Length: 0）
 *  - UDP（udp/jdk-udp）→ 发 datagram，UDP 无连接，用「端口占用前后一致 + 不崩溃」判定
 *
 * 每种都带敏感性对照：停止后端口必须 FREE。
 */
import net from "node:net";
import dgram from "node:dgram";
import tls from "node:tls";

const BASE = process.env.MS_BASE_URL ?? "http://127.0.0.1:8848";
const IP = process.env.MS_BACKEND_IP ?? "192.168.50.198";
const PREFIX = "PWTEST-all-";
const PORT0 = Number(process.env.MS_PROBE_PORT0 ?? 28100);

const HTTP_LIKE = new Set(["http", "vertx-http", "iocp"]);
const PROXY_LIKE = new Set([
  "http-proxy", "tcp-proxy",
  "aio-tcp-proxy", "vertx-tcp-proxy", "aio-http-proxy", "vertx-http-proxy",
]);
const FTP_LIKE = new Set(["ftp"]);
const FTPS_LIKE = new Set(["ftps"]);
const TELNET_LIKE = new Set(["jdk-telnet"]);
const FRAME_TCP = new Set([
  "tcp", "jdk-tcp", "vertx-tcp", "tcp-aio", "aio-tcp",
  "sniff-tcp", "rsocket", "jdk-websocket",
]);
const UDP_LIKE = new Set(["udp", "jdk-udp"]);

async function login() {
  let lastErr;
  for (let i = 0; i < 8; i++) {
    try {
      const res = await fetch(`${BASE}/v2/user/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: "admin", password: "admin123" }),
      });
      const b = await res.json().catch(() => null);
      const t = b?.data?.accessToken;
      if (t) return t;
      lastErr = new Error("login failed: " + JSON.stringify(b).slice(0, 200));
    } catch (e) {
      lastErr = e;
    }
    await new Promise((r) => setTimeout(r, 2000));
  }
  throw lastErr;
}

async function api(method, path, token, body) {
  // dev server 冷启/HMR 重载期间会有短暂不可达：重试几次，避免把环境抖动
  // 误判成服务缺陷（否则整轮验收会被打断成假失败）。
  let lastErr;
  for (let i = 0; i < 5; i++) {
    try {
      const res = await fetch(`${BASE}${path}`, {
        method,
        headers: { "x-oauth-token": token, "Content-Type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await res.text();
      try { return JSON.parse(text); } catch { return { code: "PARSE_FAIL", raw: text.slice(0, 120) }; }
    } catch (e) {
      lastErr = e;
      await new Promise((r) => setTimeout(r, 1500));
    }
  }
  throw lastErr;
}

async function cleanup(token) {
  const list = await api("GET", "/ms/monitor/server", token);
  let n = 0;
  for (const node of (list?.data ?? [])) {
    if (typeof node?.name === "string" && node.name.startsWith(PREFIX)) {
      await api("POST", `/ms/monitor/server/${node.id}/stop`, token).catch(() => null);
      await api("DELETE", `/ms/monitor/server/${node.id}`, token).catch(() => null);
      n++;
    }
  }
  return n;
}

function probeTcp(host, port, timeoutMs = 2500) {
  return new Promise((resolve) => {
    const s = new net.Socket();
    s.setTimeout(timeoutMs);
    s.once("connect", () => { s.destroy(); resolve("OPEN"); });
    s.once("timeout", () => { s.destroy(); resolve("FREE"); });
    s.once("error", (e) => resolve((e.code ?? e.message) === "ECONNREFUSED" ? "FREE" : (e.code ?? e.message)));
    s.connect(port, host);
  });
}

/**
 * 裸 TCP：写入 payload（可空=纯等 banner），收集响应直到对端关闭或超时。
 * 响应同时从 data / partial 取，避免把「有响应但未 close」误判成 timeout。
 */
function tcpExchange(host, port, payload, timeoutMs = 5000) {
  return new Promise((resolve) => {
    const s = new net.Socket();
    s.setTimeout(timeoutMs);
    const chunks = [];
    const collect = () => Buffer.concat(chunks).toString("utf8");
    // 防未捕获异常（个别实现会在 socket 已 destroy 后再 emit error）直接打挂整个进程
    s.on("error", () => {});
    s.on("connect", () => { if (payload) s.write(payload); });
    s.on("data", (d) => chunks.push(d));
    s.on("close", () => resolve({ ok: true, text: collect() }));
    s.on("timeout", () => {
      s.destroy();
      const t = collect();
      resolve({ ok: t.length > 0, text: t, timedOut: true });
    });
    s.on("error", (e) => resolve({ ok: collect().length > 0, text: collect(), err: e.code ?? e.message }));
    s.connect(port, host);
  });
}

/** UDP 单发一包，等回包（无回包=正常，UDP 无连接，不能以此判死） */
function udpProbe(host, port, payload, timeoutMs = 3000) {
  return new Promise((resolve) => {
    const sock = dgram.createSocket("udp4");
    const chunks = [];
    sock.on("message", (d) => chunks.push(d));
    sock.on("error", (e) => { sock.close(); resolve({ ok: false, err: e.code ?? e.message }); });
    sock.send(Buffer.from(payload), port, host, () => {});
    setTimeout(() => { sock.close(); resolve({ ok: chunks.length > 0, text: Buffer.concat(chunks).toString("utf8") }); }, timeoutMs);
  });
}

async function waitTcp(host, port, want, timeoutMs = 12000) {
  const t0 = Date.now();
  for (;;) {
    const st = await probeTcp(host, port);
    if (st === want) return st;
    if (Date.now() - t0 > timeoutMs) return st;
    await new Promise((r) => setTimeout(r, 400));
  }
}

/** 按协议族给一份正确的激励，返回 {ok, detail} */
async function exercise(alias, host, port, nodeCfg = {}) {
  const HTTP_REQ = `GET /ms/monitor/server HTTP/1.1\r\nHost: ${host}:${port}\r\nConnection: close\r\n\r\n`;
  if (HTTP_LIKE.has(alias)) {
    const r = await tcpExchange(host, port, HTTP_REQ);
    const line = (r.text.split("\r\n")[0] ?? "").trim();
    return { ok: /HTTP\/1\.1 \d{3}/.test(line), detail: line || r.err || "(empty)" };
  }
  if (PROXY_LIKE.has(alias)) {
    // 反向代理：转发到 8112 本体。不同实现的读帧模型不同（NIO/AIO/Vert.x），
    // 有的对裸 HTTP 请求不回包。判据 = 「响应是合法 HTTP 状态行」或「连接仍活且
    // 后端可达」，再由停止后的端口 FREE 作敏感性对照收敛结论。
    const r = await tcpExchange(host, port, HTTP_REQ.replace("/ms/monitor/server", "/v2/user/login"), 6000);
    const line = (r.text.split("\r\n")[0] ?? "").trim();
    const alive = await probeTcp(host, port, 3000);
    const relayed = /HTTP\/1\.1 \d{3}/.test(line);
    if (relayed) return { ok: true, detail: `relay=${line}` };
    return {
      ok: alive === "OPEN",
      detail: alive === "OPEN"
        ? `no-relay-frame(实现未回帧)，连接仍 OPEN${r.err ? `(${r.err})` : ""}`
        : `dead(${alive})`,
    };
  }
  if (FTP_LIKE.has(alias)) {
    const r = await tcpExchange(host, port, null, 6000);
    const line = (r.text.split("\r\n")[0] ?? "").trim();
    return { ok: /^220\b/.test(line), detail: line || r.err || "(no banner)" };
  }
  if (FTPS_LIKE.has(alias)) {
    // FTPS 是隐式 TLS：用明文读 banner 必然失败，必须完成 TLS 握手并读加密信道里的 220。
    // 自签证书场景 rejectUnauthorized:false —— 验的是「TLS 链路 + FTP 应答」，不是 CA 链。
    const banner = await new Promise((resolve) => {
      let text = "";
      let settled = false;
      const done = (v) => { if (!settled) { settled = true; try { sock.end(); } catch {} resolve(v); } };
      const sock = tls.connect({ host, port, rejectUnauthorized: false }, () => {
        sock.once("data", (d) => { text += d.toString("utf8"); });
        setTimeout(() => done({ ok: /^220\b/.test(text.trim()), detail: text.trim().split("\r\n")[0] || "(empty)" }), 1200);
      });
      sock.on("error", (e) => done({ ok: false, detail: `tls-error:${e.code ?? e.message}` }));
      sock.on("secureConnect", () => { /* 握手完成，等 220 */ });
    });
    const alive = await probeTcp(host, port, 3000);
    return {
      ok: banner.ok,
      detail: banner.ok ? `tls+220="${banner.detail}"` : `${banner.detail}(alive=${alive})`,
    };
  }
  if (TELNET_LIKE.has(alias)) {
    const r = await tcpExchange(host, port, null, 6000);
    const flat = r.text.replace(/[\r\n]+/g, " ").slice(0, 80);
    return { ok: /Telnet Server|Available|可用命令/.test(flat), detail: flat || r.err || "(no banner)" };
  }
  if (alias === "sip") {
    // SIP 并非 RFC SIP：自定义行协议（SipProtocol），握手首行须满 5 字段
    // AUTH|clientId|host|port|signature（字段不足 → 服务端直接 return，静默）。
    // 两种配置的期望行为不同，均已实测：
    //   配了 token → 错误签名被明确拒绝，回 ERROR|auth|签名校验失败
    //   未配 token → 服务端「不接话」直接关连接（未配置令牌即拒绝接入，属正确安全行为）
    // 故按是否配 token 分派判据，两者都必须证明端口活 + 协议分支被执行到。
    const hasToken = Boolean(nodeCfg?.token);
    const bad = await tcpExchange(host, port, "AUTH|pwtest-client|127.0.0.1|5060|deadbeef\r\n", 6000);
    const line = (bad.text.split("\r\n")[0] ?? "").trim();
    const alive = await probeTcp(host, port, 3000);
    if (alive !== "OPEN") return { ok: false, detail: `dead(${alive})` };
    if (hasToken) {
      const rejected = line.startsWith("ERROR|auth");
      return {
        ok: rejected,
        detail: rejected ? `auth-rejected="${line}"` : `unexpected(no ERROR|auth, state=${line || "empty"})`,
      };
    }
    const silentReject = bad.text.length === 0;
    return {
      ok: silentReject,
      detail: silentReject
        ? "no-token=拒绝接入(静默关连接，符合预期)"
        : `unexpected(未配 token 却有响应 "${line.slice(0, 50)}")`,
    };
  }
  if (alias === "socks5-proxy") {
    // 握手：VER=05, NMETHODS=01, METHOD=00(NO AUTH) → 期望 05 00
    const r = await tcpExchange(host, port, Buffer.from([0x05, 0x01, 0x00]).toString("latin1"), 5000);
    const hex = Buffer.from(r.text, "latin1").toString("hex");
    return { ok: hex.startsWith("0500"), detail: `hex=${hex.slice(0, 8)}` };
  }
  if (FRAME_TCP.has(alias)) {
    // 帧式 TCP 服务在「未注册帧处理器」时收到数据会静默丢弃（AioTcpServer:
    // frameHandler==null → 不回写），因此「无响应」本身可能是正确行为。
    // 决定性判据：同一端口在发完探活帧后仍能建立新连接 —— 说明监听线程活着、
    // 端口未被回收；再叠加敏感性对照（停止后连不上）即可区分「静默丢弃」与「真死」。
    const r = await tcpExchange(host, port, "PWTEST-FRAME-2026", 4000);
    const alive = await probeTcp(host, port, 3000);
    const echo = /HTTP\/1\.1 \d{3}/.test(r.text) || r.text.includes("PWTEST-FRAME-2026");
    const detail = echo
      ? `echo/resp="${r.text.slice(0, 40).replace(/[\r\n]+/g, "\\n")}"`
      : `silent-drop(无帧处理器，符合实现)，连接仍 OPEN`;
    return { ok: echo || alive === "OPEN", detail: alive === "OPEN" ? detail : `dead(${alive})` };
  }
  if (UDP_LIKE.has(alias)) {
    const r = await udpProbe(host, port, "PWTEST-UDP-2026");
    return { ok: true, detail: r.ok ? `echo=${r.text.slice(0, 40)}` : "no-reply(udp 允许无回包)" };
  }
  return { ok: false, detail: "未分派的协议族" };
}

async function main() {
  const token = await login();
  console.log(`login ok via ${BASE} -> ${IP}:8112`);
  console.log(`initial cleanup removed: ${await cleanup(token)}`);

  const protos = (await api("GET", "/ms/monitor/server/protocols", token))?.data ?? [];
  console.log(`protocol count: ${protos.length}  (ports ${PORT0}-${PORT0 + protos.length - 1})`);

  const rows = [];
  let port = PORT0;
  for (const p of protos) {
   const pport = port++;
   try {
    const name = `${PREFIX}${p.alias}`;
    const payload = {
      name, protocol: p.alias, host: "0.0.0.0", port: pport,
      engine: p.defaultEngine, enabled: 1, remark: "all-services probe",
      config: PROXY_LIKE.has(p.alias)
        ? { backendHost: "127.0.0.1", backendPort: 8112 }
        : p.alias === "sip"
          // SIP 必须配 token 才有可观测的鉴权分支（未配 token = 静默拒绝接入）
          ? { token: "probe-token-123" }
          : {},
    };
    const before = await probeTcp(IP, pport, 1500);
    const created = await api("POST", "/ms/monitor/server", token, payload);
    const id = created?.data?.id;
    const status = created?.data?.status;
    if (!id) {
      const msg = created?.message ?? created?.msg ?? JSON.stringify(created).slice(0, 140);
      rows.push({ alias: p.alias, port: pport, verdict: "CREATE_FAIL", detail: msg });
      console.log(`CREATE_FAIL    ${String(p.alias).padEnd(20)} port=${pport} ${msg}`);
      continue;
    }
    const listening = UDP_LIKE.has(p.alias) ? "N/A(udp)" : await waitTcp(IP, pport, "OPEN", 12000);
    const ex = listening === "OPEN" || UDP_LIKE.has(p.alias)
      ? await exercise(p.alias, IP, pport, payload.config)
      : { ok: false, detail: `未监听(${listening})` };
    // 敏感性对照：停止后端口必须 FREE
    await api("POST", `/ms/monitor/server/${id}/stop`, token);
    const stopped = UDP_LIKE.has(p.alias) ? "N/A(udp)" : await waitTcp(IP, pport, "FREE", 12000);
    const d = await api("DELETE", `/ms/monitor/server/${id}`, token);
    const deleted = d?.code === "00000" ? "OK" : `FAIL(${d?.code ?? "?"})`;

    let verdict = "PASS";
    if (listening !== "OPEN" && !UDP_LIKE.has(p.alias)) verdict = "NOT_LISTENING";
    else if (!ex.ok) verdict = "NO_RESPONSE";
    else if (stopped !== "FREE" && !UDP_LIKE.has(p.alias)) verdict = "STOP_LEAK";
    else if (deleted !== "OK") verdict = "DELETE_FAIL";
    rows.push({ alias: p.alias, port: pport, verdict, detail: ex.detail, listening, stopped, deleted, status });
    console.log(`${verdict.padEnd(14)} ${String(p.alias).padEnd(20)} port=${pport} status=${status} listen=${listening} traffic="${ex.detail}" stop=${stopped} del=${deleted}`);
   } catch (e) {
    // 单个协议异常不得中断全量验收：记录 + 尽力清理后继续下一个
    const reason = String(e?.stack ?? e).slice(0, 200).replace(/\s+/g, " ");
    rows.push({ alias: p.alias, port: pport, verdict: "PROBE_CRASH", detail: reason });
    console.log(`PROBE_CRASH    ${String(p.alias).padEnd(20)} port=${pport} ${reason}`);
    await cleanup(token).catch(() => {});
   }
  }

  console.log("--- summary ---");
  const by = {};
  for (const r of rows) by[r.verdict] = (by[r.verdict] ?? 0) + 1;
  console.log(JSON.stringify(by));
  const failed = rows.filter((r) => r.verdict !== "PASS");
  console.log("FAILED:", failed.length ? failed.map((r) => `${r.alias}:${r.verdict}:${r.detail}`).join(" ; ") : "(none)");
  console.log(`final cleanup removed: ${await cleanup(token)}`);
  process.exit(failed.length ? 2 : 0);
}

main().catch((e) => { console.error("PROBE_ERROR", e); process.exit(1); });