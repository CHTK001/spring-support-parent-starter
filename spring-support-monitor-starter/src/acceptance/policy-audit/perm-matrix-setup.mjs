/**
 * 权限矩阵 · 阶段 A：建两个测试账号并验证可登录
 *
 * <p>用 admin 的 token 调 POST /v2/user/save 建号，roleIds 指向 sys_role：
 * zzq_ops_01 -> OPS(id=3)，zzq_user_01 -> USER(id=4)。</p>
 *
 * <p>用不存在的 ID / 空体做后续探测，所以这里只建号不改任何既有数据。</p>
 */
const BASE = "http://192.168.50.198:18170/system/api";
const UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

async function req(method, path, token, body) {
  const headers = { "User-Agent": UA, "Content-Type": "application/json" };
  if (token) headers["Authorization"] = "Bearer " + token;
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* 非 JSON 原样保留 */ }
  return { status: res.status, json, text: text.slice(0, 200) };
}

async function login(username, password) {
  const r = await req("POST", "/v2/user/login", null, { username, password });
  const d = r.json && r.json.data;
  return { status: r.status, ok: r.json && r.json.success === true, token: d && d.accessToken, data: d, msg: r.json && r.json.msg };
}

const admin = await login("admin", "admin123");
console.log("admin 登录: " + (admin.ok ? "成功" : "失败 " + admin.msg));
if (!admin.ok) process.exit(1);

const ACCOUNTS = [
  { username: "zzq_ops_01", nickname: "验收-运维", roleId: "3", roleName: "OPS" },
  { username: "zzq_user_01", nickname: "验收-普通用户", roleId: "4", roleName: "USER" }
];

for (const a of ACCOUNTS) {
  const body = {
    sysUserUsername: a.username,
    sysUserNickname: a.nickname,
    sysUserPassword: "Zzq2026aa",
    sysUserStatus: 1,
    roleIds: [a.roleId],
    updateRole: true
  };
  const r = await req("POST", "/v2/user/save", admin.token, body);
  const created = r.json && r.json.success === true;
  console.log("\n建号 " + a.username + " (" + a.roleName + "): HTTP " + r.status + "  " + (created ? "成功" : "失败") + "  " + (r.json ? (r.json.msg || "") : r.text));

  // 建完立刻登录验证
  const li = await login(a.username, "Zzq2026aa");
  console.log("  登录验证: " + (li.ok ? "成功" : "失败") + "  " + (li.ok ? "" : (li.msg || "")));
  if (li.ok && li.data) {
    console.log("  token 长度: " + (li.token ? li.token.length : 0));
    const roles = li.data.roles || li.data.roleList || li.data.roleCodes;
    const perms = li.data.permissions || li.data.permission || li.data.permissionList;
    console.log("  登录返回的字段: " + JSON.stringify(Object.keys(li.data)));
    console.log("  roles: " + JSON.stringify(roles));
    console.log("  permissions: " + JSON.stringify(perms));
  }
}
