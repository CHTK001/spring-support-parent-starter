/**
 * 权限矩阵 · 补齐决定性两格
 *
 * <p>全量跑的时候这两格被 OOM 打断（ERR）。它们是整张矩阵里唯一
 * 「admin 应当被拒、只有 SUPER_ADMIN 能过」的端点，单独重测。</p>
 *
 * <p>两个端点都是安全的：GET /v2/setting/decrypt 是读，DELETE /v2/cache/all 只清缓存。</p>
 */
const BASE = "http://192.168.50.198:18170/system/api";
const UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

async function call(method, path, token, body) {
  const headers = { "User-Agent": UA };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers["Authorization"] = "Bearer " + token;
  try {
    const res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* ignore */ }
    return { status: res.status, json };
  } catch (e) { return { status: -1, error: String(e).slice(0, 70) }; }
}
async function login(u, p) {
  const r = await call("POST", "/v2/user/login", null, { username: u, password: p });
  return r.json && r.json.data ? r.json.data.accessToken : null;
}
function verdict(r) {
  if (r.status === 403) return "DENY";
  if (r.status === 401) return "UNAUTH";
  if (r.status === -1) return "ERR";
  const msg = (r.json && r.json.msg) || "";
  if (typeof msg === "string" && (msg.indexOf("权限不足") >= 0 || msg.indexOf("无权") >= 0)) return "DENY";
  return "ALLOW";
}

// 先建 SUPER_ADMIN 测试账号
const adminTok = await login("admin", "admin123");
const su = await call("POST", "/v2/user/save", adminTok, {
  sysUserUsername: "zzq_super_01", sysUserNickname: "验收-超管",
  sysUserPassword: "Zzq2026aa", sysUserStatus: 1,
  roleIds: ["1"], updateRole: true
});
console.log("建 SUPER_ADMIN 测试账号: " + (su.json && su.json.success ? "成功" : "失败 " + (su.json && su.json.msg)));

const SUBJECTS = [
  { id: "SUPER_ADMIN", u: "zzq_super_01", p: "Zzq2026aa", expect: "ALLOW" },
  { id: "ADMIN", u: "admin", p: "admin123", expect: "DENY" },
  { id: "OPS", u: "zzq_ops_01", p: "Zzq2026aa", expect: "DENY" },
  { id: "USER", u: "zzq_user_01", p: "Zzq2026aa", expect: "DENY" }
];
const CASES = [
  { verb: "GET", path: "/v2/setting/decrypt", roles: "SUPER_ADMIN" },
  { verb: "DELETE", path: "/v2/cache/all", roles: "AuthConstant.SUPER_ADMIN" }
];

let mismatch = 0;
for (const c of CASES) {
  console.log("\n=== " + c.verb + " " + c.path + "   @Permission(role={" + c.roles + "}) ===");
  for (const s of SUBJECTS) {
    const tok = await login(s.u, s.p);
    if (!tok) { console.log("  " + s.id.padEnd(12) + " 登录失败"); mismatch++; continue; }
    const r = await call(c.verb, c.path, tok);
    const v = verdict(r);
    const ok = v === s.expect;
    if (!ok) mismatch++;
    const msg = r.json ? (r.json.msg || "") : (r.error || "");
    console.log("  " + s.id.padEnd(12) + " 实测=" + v.padEnd(6) + " 期望=" + s.expect.padEnd(6) + (ok ? "✓" : "**不符**") + "  HTTP=" + r.status + "  " + String(msg).slice(0, 60));
    await new Promise(function (r2) { setTimeout(r2, 300); });
  }
}
console.log("\n不符格数: " + mismatch);
