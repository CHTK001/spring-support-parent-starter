/**
 * 生产级测试 #6 · 安全（只读探测，不做破坏性攻击）
 *
 * <p>边界：只做**不会改变服务端状态**的探测 —— 不爆破、不注入写操作、不删改数据。
 * 目的是找出「配置层面」的安全缺陷，而不是打漏洞。</p>
 *
 * <p>探测项：</p>
 * <ol>
 *   <li>安全响应头（CSP / HSTS / X-Content-Type-Options / X-Frame-Options / Referrer-Policy）</li>
 *   <li>CORS：任意 Origin 是否被放行 + 是否允许携带凭证</li>
 *   <li>未登录访问受保护接口（应 401/403）</li>
 *   <li>伪造 token / 空 token / 过期格式 token</li>
 *   <li>敏感信息泄露：响应里是否回显密码哈希、盐、数据库连接串</li>
 *   <li>路径穿越：静态资源与文件接口的 ../ 穿越</li>
 *   <li>错误信息：是否泄露堆栈/SQL/类名</li>
 * </ol>
 */
const API = "http://192.168.50.198:18170/system/api";
const WEB = "http://192.168.50.198:18170";
const UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

async function req(path, opt) {
  opt = opt || {};
  const h = { "User-Agent": UA, ...(opt.headers || {}) };
  if (opt.body !== undefined) h["Content-Type"] = "application/json";
  if (opt.token) h["Authorization"] = "Bearer " + opt.token;
  try {
    const r = await fetch(path.startsWith("http") ? path : API + path, {
      method: opt.method || "GET", headers: h, body: opt.body === undefined ? undefined : JSON.stringify(opt.body),
      redirect: "manual"
    });
    const t = await r.text();
    let j = null; try { j = JSON.parse(t); } catch { /* */ }
    return { status: r.status, headers: Object.fromEntries(r.headers.entries()), text: t, json: j };
  } catch (e) { return { status: -1, err: String(e).slice(0, 100) }; }
}

const out = { at: new Date().toISOString() };

// ---------- 1. 安全响应头 ----------
console.log("=== 1. 安全响应头 ===");
const h1 = await req("/v2/setting/default");
const want = ["content-security-policy", "strict-transport-security", "x-content-type-options", "x-frame-options", "referrer-policy", "permissions-policy", "x-xss-protection"];
out.headers = {};
for (const w of want) {
  const v = h1.headers[w];
  out.headers[w] = v || null;
  console.log("  " + (v ? "✓" : "✗ 缺失") + "  " + w.padEnd(26) + (v ? v.slice(0, 90) : ""));
}
console.log("  --- 实际存在的 header ---");
console.log("  " + Object.keys(h1.headers).join(", "));

// ---------- 2. CORS ----------
console.log("\n=== 2. CORS（任意 Origin）===");
out.cors = [];
for (const origin of ["https://evil.example.com", "null", "http://192.168.50.198:8096"]) {
  const r = await req("/v2/setting/default", { headers: { Origin: origin } });
  const acao = r.headers["access-control-allow-origin"] || null;
  const acac = r.headers["access-control-allow-credentials"] || null;
  const reflect = acao === origin;
  out.cors.push({ origin, acao, acac, reflects: reflect });
  console.log("  Origin=" + origin.padEnd(28) + " ACAO=" + String(acao).padEnd(30) + " ACAC=" + String(acac) + (reflect ? "  **反射该 Origin**" : ""));
}
const pre = await req("/v2/setting/default", { method: "OPTIONS", headers: { Origin: "https://evil.example.com", "Access-Control-Request-Method": "GET" } });
console.log("  预检 OPTIONS: HTTP " + pre.status + "  ACAO=" + (pre.headers["access-control-allow-origin"] || "-") + "  Methods=" + (pre.headers["access-control-access-control-allow-methods"] || pre.headers["access-control-allow-methods"] || "-"));
out.corsPreflight = { status: pre.status, acao: pre.headers["access-control-allow-origin"] || null, allowMethods: pre.headers["access-control-allow-methods"] || null, allowHeaders: pre.headers["access-control-allow-headers"] || null };

// ---------- 3. 未登录访问 ----------
console.log("\n=== 3. 未登录访问受保护接口 ===");
out.unauth = [];
for (const p of ["/v2/user/page", "/v2/role/page", "/v2/policy/hit/counts", "/v2/setting/page", "/v2/log/error/page"]) {
  const r = await req(p);
  const msg = r.json ? (r.json.msg || "") : "";
  const leaked = /at\s+[\w.$]+\(|Exception|Caused by|SELECT\s|INSERT\s|jdbc:|org\.com\./i.test(r.text);
  out.unauth.push({ path: p, status: r.status, msg: msg.slice(0, 60), leakedStack: leaked });
  console.log("  " + p.padEnd(26) + " HTTP " + String(r.status).padEnd(4) + (r.status === 401 || r.status === 403 ? "✓ 已拦截" : "**未拦截**") + "  " + msg.slice(0, 46) + (leaked ? "  **泄露堆栈/SQL**" : ""));
}

// ---------- 4. 伪造 token ----------
console.log("\n=== 4. 伪造 / 空 / 畸形 token ===");
out.badToken = [];
const bads = [
  ["空 Bearer", "Bearer "],
  ["无签名", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9."],
  ["alg=none", "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIlNVTUVSX0FETUlOIl19."],
  ["纯垃圾", "not-a-jwt-at-all"],
  ["超长", "A".repeat(4000)]
];
for (const [label, tok] of bads) {
  const r = await req("/v2/user/page", { token: tok });
  const ok = r.status === 200 && r.json && r.json.success === true;
  out.badToken.push({ label, status: r.status, accepted: ok });
  console.log("  " + label.padEnd(12) + " HTTP " + String(r.status).padEnd(4) + (ok ? "**被接受！**" : "✓ 已拒绝"));
}

// ---------- 5. 敏感信息泄露 ----------
console.log("\n=== 5. 敏感信息泄露（登录响应 / 公开接口）===");
const lg = await req("/v2/user/login", { method: "POST", body: { username: "admin", password: "admin123" } });
const body = lg.text || "";
const leaks = [];
for (const [label, re] of [["密码哈希", /"\$?[a-zA-Z]?2[aby]?\$[0-9]{2}\$|\$argon2|passwordHash|sysUserPassword/i], ["盐值", /sysUserSalt|"salt"\s*:/i], ["数据库连接串", /jdbc:mysql:\/\/[^"'\s]+/i], ["服务器绝对路径", /\/opt\/app\/|\/home\/[a-z]+\//i]]) {
  const hit = re.test(body);
  leaks.push({ label, hit });
  console.log("  登录响应含" + label + ": " + (hit ? "**是**" : "否"));
}
out.loginLeaks = leaks;
const pub = await req("/v2/setting/default");
for (const [label, re] of [["数据库连接串", /jdbc:mysql:\/\//i], ["密码类配置值", /password|secret|token/i]]) {
  const hit = re.test(pub.text || "");
  out["public_" + label] = hit;
  console.log("  /v2/setting/default 含" + label + ": " + (hit ? "**是**" : "否"));
}

// ---------- 6. 路径穿越 ----------
console.log("\n=== 6. 路径穿越 ===");
out.traversal = [];
for (const p of [
  "/v2/setting/default?file=../../../../etc/passwd",
  "/../application-dev.yml",
  "/v2/cache/value?key=../../etc/passwd",
  "/system/api/../../../../etc/passwd"
]) {
  const r = await req(p);
  const leaked = /root:x:0:0|jdbc:mysql|spring:\s*$|datasource/i.test(r.text || "");
  out.traversal.push({ path: p, status: r.status, leaked });
  console.log("  " + p.slice(0, 58).padEnd(58) + " HTTP " + String(r.status).padEnd(4) + (leaked ? " **泄露文件内容**" : "未泄露"));
}

// ---------- 7. 错误信息 ----------
console.log("\n=== 7. 错误信息是否泄露内部细节 ===");
const bad = await req("/v2/user/page?pageNo=abc&pageSize=-1");
const detail = [];
if (/at\s+[\w.$]+\(/.test(bad.text || "")) detail.push("堆栈");
if (/SELECT\s|INSERT\s|UPDATE\s/i.test(bad.text || "")) detail.push("SQL");
if (/org\.com\.|com\.chua\.|jakarta\./.test(bad.text || "")) detail.push("类名");
if (/ORA-|SQLException|CommunicationsException/.test(bad.text || "")) detail.push("数据库异常");
out.errorDetail = detail;
console.log("  非法分页参数响应泄露: " + (detail.length ? "**" + detail.join(", ") + "**" : "无"));
console.log("  响应前 200 字: " + (bad.text || "").slice(0, 200).replace(/\s+/g, " "));

console.log("\n已收集，写入 .tmp/security/probe.json");
const fs = await import("node:fs");
fs.mkdirSync("D:/ch/project/.tmp/security", { recursive: true });
fs.writeFileSync("D:/ch/project/.tmp/security/probe.json", JSON.stringify(out, null, 2), "utf8");
