/**
 * 权限矩阵 · 阶段 B：全量 156 端点 × 3 个身份
 *
 * <p>判定口径：权限网关是拦截器（PermissionControlInterceptor），**先于** controller 方法执行。
 * 所以只要不是 403 / 「权限不足」，就说明请求**穿过了权限闸门**；随后因为参数不合法
 * 返回 400/业务错误，恰恰是「已放行」的正证据。不能用「200 才算放行」——
 * 那样会把「已放行但参数无效」误判成拒绝。</p>
 *
 * <p>安全约束：所有变更类请求都用**不存在的 ID（999999999）或空体**，
 * 因此「放行」也不会产生真实副作用。少数即使空体也有真实副作用的端点
 * （全局限流下发、文件服务器启停、调度触发、邮件发送）对 admin 跳过执行，
 * 只用非管理员身份探测（被网关拦住 = 无副作用）。</p>
 *
 * <p>预期来自 PermissionRules.evaluate：
 *   SUPER_ADMIN -> 无条件放行
 *   ADMIN       -> 放行，除非 role() 恰好声明单个 SUPER_ADMIN
 *   其他角色     -> required(role() ∪ value() ∪ permission()) 与 (roles ∪ permissions) 有交集才放行
 *   本矩阵的 OPS / USER 既不在任何 required 里、也没有权限串 -> 预期全部拒绝
 */
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";

const OUT = "D:/ch/project/.tmp/perm-matrix";
mkdirSync(OUT, { recursive: true });
const endpoints = JSON.parse(readFileSync(OUT + "/endpoints.json", "utf8"));

const BASE = "http://192.168.50.198:18170/system/api";
const UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
const ABSENT_ID = "999999999";

/** 即使空体也会产生真实副作用的端点：只允许非管理员探测 */
const SIDE_EFFECT = [
  "/v2/policy/ratelimit/global/apply",   // 下发全局限流 —— 已知会把自己锁死
  "/v2/server/start", "/v2/server/stop",  // 文件服务器启停
  "/v2/cache/value",
  "/v2/policy/hotreload/reload"
];
function isSideEffect(p) {
  return SIDE_EFFECT.some(s => p === s || p.startsWith(s + "/"))
    || /\/server\/(start|stop|restart)$/.test(p)
    || /^\/v2\/email\//.test(p)
    || /^\/v2\/schedule\/(trigger|run|exec)/.test(p);
}

const SUBJECTS = [
  { id: "admin", user: "admin", pwd: "admin123", expect: "ALLOW" },
  { id: "ops", user: "zzq_ops_01", pwd: "Zzq2026aa", expect: "DENY" },
  { id: "user", user: "zzq_user_01", pwd: "Zzq2026aa", expect: "DENY" }
];

async function call(method, path, token, body) {
  const headers = { "User-Agent": UA };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers["Authorization"] = "Bearer " + token;
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 20000);
  try {
    const res = await fetch(BASE + path, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body),
      signal: ctrl.signal
    });
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* 非 JSON */ }
    return { status: res.status, json };
  } catch (e) {
    return { status: -1, error: String(e).slice(0, 80) };
  } finally {
    clearTimeout(timer);
  }
}

async function login(u, p) {
  const r = await call("POST", "/v2/user/login", null, { username: u, password: p });
  return r.json && r.json.data ? r.json.data.accessToken : null;
}

/** 拼查询参数：id 类用不存在的值，其余给无害值 */
function buildQuery(e) {
  if (!e.requestParams.length) return "";
  const parts = [];
  for (const p of e.requestParams) {
    let v = "1";
    if (/id$/i.test(p)) v = ABSENT_ID;
    else if (/^(page|pageNo|pageNum|current|index)$/i.test(p)) v = "1";
    else if (/^(size|pageSize|limit|rows)$/i.test(p)) v = "10";
    else if (/^(type|status|group|key|code|sort|order|orderBy)$/i.test(p)) v = "1";
    parts.push(encodeURIComponent(p) + "=" + encodeURIComponent(v));
  }
  return "?" + parts.join("&");
}

function classify(r) {
  if (r.status === -1) return "ERR";
  if (r.status === 401) return "UNAUTH";
  if (r.status === 403) return "DENY";
  const msg = (r.json && (r.json.msg || r.json.message)) || "";
  if (typeof msg === "string" && (msg.indexOf("权限不足") >= 0 || msg.indexOf("无权") >= 0 || msg.indexOf("禁止访问") >= 0)) return "DENY";
  // 404/405 = 路由不存在或方法不匹配，**权限网关根本没被触发**。
  // 早先把它算成 ALLOW，会把 13 条不存在的路由报成「权限泄漏」，是假红。
  if (r.status === 404 || r.status === 405) return "NOTFOUND";
  return "ALLOW";
}

/** 端点是否声明为 SUPER_ADMIN 专属（role() 恰好单个 SUPER_ADMIN） */
function superAdminOnly(e) {
  if (!e.roles) return false;
  const arr = e.roles.split(",").map(s => s.replace(/.*\./, "").trim()).filter(Boolean);
  return arr.length === 1 && arr[0].toUpperCase() === "SUPER_ADMIN";
}

function expectedFor(subjectId, e) {
  if (subjectId === "admin") return superAdminOnly(e) ? "DENY" : "ALLOW";
  return "DENY";
}

const results = {};
for (const s of SUBJECTS) {
  const token = await login(s.user, s.pwd);
  console.log("\n########## " + s.id + " (" + s.user + ") 登录=" + (token ? "成功" : "失败") + " ##########");
  if (!token) { results[s.id] = { error: "登录失败" }; continue; }
  const rows = [];
  let denied = 0, allowed = 0, skipped = 0, errs = 0, notfound = 0;
  for (const e of endpoints) {
    const method = e.verb === "ANY" ? "GET" : e.verb;
    const path = e.path + buildQuery(e);
    const body = e.hasBody ? {} : undefined;
    const side = isSideEffect(e.path);
    // admin 跳过有真实副作用的端点（此前已由 admin 全量验过，不重复触发）
    if (side && s.id === "admin") {
      rows.push(Object.assign({}, e, { actual: "SKIP", status: 0, note: "有真实副作用，admin 跳过", expected: expectedFor(s.id, e) }));
      skipped++;
      continue;
    }
    const r = await call(method, path, token, body);
    const actual = classify(r);
    if (actual === "DENY") denied++;
    else if (actual === "ALLOW") allowed++;
    else if (actual === "NOTFOUND") notfound++;
    else errs++;
    const msg = r.json ? (r.json.msg || "") : (r.error || "");
    rows.push(Object.assign({}, e, {
      actual: actual, status: r.status, msg: String(msg).slice(0, 90),
      expected: expectedFor(s.id, e), side: side
    }));
    // 节流：连续 156×3 个请求会把 -Xmx1g 打爆（实测 13 次 OOM，见交付说明），
    // 留出 GC 空间；顺带让矩阵不成为压测
    await new Promise(function (res) { setTimeout(res, 120); });
  }
  results[s.id] = { subject: s.user, rows: rows };
  console.log("  DENY=" + denied + "  ALLOW=" + allowed + "  NOTFOUND=" + notfound + "  SKIP=" + skipped + "  ERR/UNAUTH=" + errs);

  // 与预期不符的（NOTFOUND 表示路由不存在，权限网关未被触发，不参与判定）
  const bad = rows.filter(function (r) {
    return r.actual !== "SKIP" && r.actual !== "ERR" && r.actual !== "UNAUTH" && r.actual !== "NOTFOUND" && r.actual !== r.expected;
  });
  console.log("  与预期不符: " + bad.length);
  for (const b of bad.slice(0, 25)) {
    console.log("    [" + b.actual + " 期望 " + b.expected + "] " + b.verb + " " + b.path + "  HTTP=" + b.status + "  " + b.msg);
  }
}

writeFileSync(OUT + "/matrix.json", JSON.stringify(results, null, 2), "utf8");
console.log("\n已写 " + OUT + "/matrix.json");
