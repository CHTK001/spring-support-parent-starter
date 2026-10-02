/**
 * 权限矩阵 · 端点清单抽取（只读）
 *
 * <p>把 @Permission 标注的端点连同「类级 @RequestMapping + 方法级 Mapping」拼成完整 URL，
 * 作为权限矩阵的预期基线。矩阵跑完后再用实测 HTTP 状态与这份基线比对。</p>
 *
 * <p>为什么必须从源码抽而不是从前端路由猜：前端的 v-admin 指令只是「隐藏按钮」，
 * 真正的闸门在服务端拦截器（PermissionControlInterceptor -> PermissionRules.evaluate），
 * 只有注解里的 role()/value() 才是真实约束。</p>
 *
 * <p>注意：本文件里刻意不用「模板字符串 + 全角标点」的写法。
 * 曾经因为收尾反引号被写成双引号（U+0022）导致整个文件解析失败，
 * 而 read 工具会把 U+0022 显示成反引号，肉眼与 read 都看不出来，只有码点能证伪。</p>
 */
import { readFileSync, writeFileSync, readdirSync, statSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const ROOT = "D:/ch/project/spring-support-api-starter";
const OUT = "D:/ch/project/.tmp/perm-matrix";
mkdirSync(OUT, { recursive: true });

function walk(dir, acc) {
  acc = acc || [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    const st = statSync(p);
    if (st.isDirectory()) {
      if (name !== "target") walk(p, acc);
    } else if (name.endsWith(".java")) {
      acc.push(p);
    }
  }
  return acc;
}

const files = walk(ROOT);
const endpoints = [];

for (const f of files) {
  const src = readFileSync(f, "utf8");
  if (src.indexOf("@Permission") < 0) continue;
  const lines = src.split(/\r?\n/);

  // 类级 @RequestMapping
  let classBase = "";
  for (let i = 0; i < Math.min(lines.length, 120); i++) {
    const m = lines[i].match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"/);
    if (m) { classBase = m[1]; break; }
  }
  const clsName = (src.match(/public\s+class\s+(\w+)/) || [])[1] || "";

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (!/^\s*@Permission\b/.test(line)) continue;

    // 上溯 12 行找方法级 Mapping
    let verb = "", path = "";
    for (let k = i; k >= 0 && k > i - 12; k--) {
      const mm = lines[k].match(/@(Get|Post|Put|Delete|Patch|Request)Mapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"/);
      if (mm) { verb = mm[1] === "Request" ? "ANY" : mm[1].toUpperCase(); path = mm[2]; break; }
      const rm = lines[k].match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"[^)]*method\s*=\s*RequestMethod\.(\w+)/);
      if (rm) { verb = rm[2].toUpperCase(); path = rm[1]; break; }
      const bare = lines[k].match(/@(Get|Post|Put|Delete|Patch)Mapping\s*$/);
      if (bare) { verb = bare[1].toUpperCase(); path = ""; break; }
    }
    if (!verb) continue;

    // 方法签名与 @RequestParam 名（拼安全探测 URL 用）
    let sig = "";
    for (let k = i; k < Math.min(lines.length, i + 14); k++) {
      if (/public\s+[\w<>,\[\]\s.?]+\s+\w+\s*\(/.test(lines[k])) { sig = lines[k].trim(); break; }
    }
    const params = [];
    const reParam = /@RequestParam\s*(?:\([^)]*\))?\s*(\w+)/g;
    let pm;
    while ((pm = reParam.exec(sig)) !== null) params.push(pm[1]);

    const perm = line.trim();
    const roles = ((perm.match(/role\s*=\s*\{([^}]*)\}/) || [])[1] || "").replace(/\s+/g, "");
    // 简写形式 @Permission("sys:login:delete") —— 只匹配 value= 会漏掉，
    // 曾因此误报「12 个端点无任何权限约束」，实际全部有 value（踩过一次）
    const valueLong = (perm.match(/value\s*=\s*"([^"]*)"/) || [])[1] || "";
    const valueShort = (perm.match(/@Permission\s*\(\s*"([^"]*)"/) || [])[1] || "";
    const value = valueLong || valueShort;
    const permStr = (perm.match(/permission\s*=\s*"([^"]*)"/) || [])[1] || "";
    const hasBody = /@RequestBody/.test(sig) || (i + 6 > lines.length ? false : lines.slice(i, i + 8).join(" ").indexOf("@RequestBody") >= 0);

    // 方法映射可能不带前导斜杠（@GetMapping("page")），直接拼会得到 /v2/tenantpage
    let full = classBase.replace(/\/+$/, "");
    if (path) full = full + (path.startsWith("/") ? path : "/" + path);
    if (!full) full = path;

    endpoints.push({
      ctrl: clsName,
      file: f.replace(/^D:\/ch\/project\//, "").replace(/\\/g, "/"),
      verb: verb,
      path: full.replace(/\/{2,}/g, "/"),
      roles: roles,
      value: value,
      permission: permStr,
      requestParams: params,
      hasBody: hasBody,
      sig: sig.slice(0, 130),
      line: i + 1
    });
  }
}

writeFileSync(OUT + "/endpoints.json", JSON.stringify(endpoints, null, 2), "utf8");

console.log("端点总数: " + endpoints.length);

console.log("\n=== 动词分布 ===");
const byVerb = {};
for (const e of endpoints) byVerb[e.verb] = (byVerb[e.verb] || 0) + 1;
for (const k of Object.keys(byVerb).sort()) console.log("  " + k + ": " + byVerb[k]);

console.log("\n=== 路径前缀分布 ===");
const byPrefix = {};
for (const e of endpoints) {
  const seg = e.path.split("/").filter(Boolean).slice(0, 2).join("/");
  byPrefix[seg] = (byPrefix[seg] || 0) + 1;
}
const pfx = Object.keys(byPrefix).sort(function (a, b) { return byPrefix[b] - byPrefix[a]; }).slice(0, 30);
for (const k of pfx) console.log("  /" + k + ": " + byPrefix[k]);

const needParam = endpoints.filter(function (e) { return e.requestParams.length; });
console.log("\n需要 RequestParam 的端点: " + needParam.length);
console.log("带 RequestBody 的端点: " + endpoints.filter(function (e) { return e.hasBody; }).length);

// required 为空 = 注解在、但不构成任何约束（PermissionRules 里 required.isEmpty() 直接放行）
const open = endpoints.filter(function (e) { return !e.roles && !e.value && !e.permission; });
console.log("\n@Permission 无任何约束（必然放行）: " + open.length + " 个");
for (const e of open) console.log("  " + e.verb.padEnd(7) + " " + e.path.padEnd(40) + " " + e.ctrl);

// 只有 role、没 value 的端点数
const roleOnly = endpoints.filter(function (e) { return e.roles && !e.value && !e.permission; });
console.log("\n只有 role 限定、无权限串的端点: " + roleOnly.length + " 个");

console.log("\n=== 策略模块 SysPolicyController ===");
for (const e of endpoints.filter(function (x) { return x.ctrl === "SysPolicyController"; })) {
  console.log("  " + e.verb.padEnd(6) + " " + e.path.padEnd(44) + " roles=" + (e.roles || "-").padEnd(32) + " value=" + (e.value || "-"));
}
