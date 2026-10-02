/**
 * 剩余 4 个管理页 · 弹窗发现（带各自的进入路径）
 *
 * <p>这几个页面的「新增」入口都不是页面级 toolbar 按钮，必须先走一步页面特有操作：</p>
 * <ul>
 *   <li>部门管理：直接点 toolbar 的「新增顶级机构」</li>
 *   <li>菜单管理：先选中一个树节点，工具栏才出现「新增」（代码注释：需选中菜单类型节点）</li>
 *   <li>配置管理：先点分组卡片进「分组详情」，列表区才渲染「新增配置项」</li>
 *   <li>消息管理：toolbar 的「发送消息」</li>
 * </ul>
 */
import { createRequire } from "node:module";
import { mkdirSync, writeFileSync } from "node:fs";

const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium } = require("playwright");

const BASE = "http://localhost:8096/system";
const OUT = "D:/ch/project/.tmp/accept-modules";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch({ headless: false, args: ["--window-size=1680,1000"] });
const page = await (await browser.newContext({ userAgent: UA, locale: "zh-CN", viewport: { width: 1600, height: 950 } })).newPage();
const errs = [];
page.on("pageerror", e => errs.push(String(e).slice(0, 150)));

await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded", timeout: 60000 });
await page.locator('input[placeholder="请输入用户名"]').first().waitFor({ state: "visible", timeout: 25000 });
await page.locator('input[placeholder="请输入用户名"]').first().fill("admin");
await page.locator('input[type="password"]').first().fill("admin123");
await page.locator('button:has-text("登录")').first().click();
await page.waitForFunction(() => !document.body.innerText.includes("欢迎登录"), null, { timeout: 60000 });

/** 工具栏可能被收进「最小化 dock」，此时页面里唯一的「新增」按钮是个不可点的 dock 项 */
async function expandToolbar() {
  const ex = page.locator('button[aria-label="展开工具栏"]').first();
  if (await ex.count() && await ex.isVisible().catch(() => false)) {
    await ex.click();
    await page.waitForTimeout(1200);
    return true;
  }
  return false;
}

async function closeAll() {
  for (let i = 0; i < 3; i++) {
    const d = page.locator('.el-dialog:visible, .el-message-box:visible, .el-drawer:visible').last();
    if (!(await d.isVisible().catch(() => false))) return;
    await page.keyboard.press("Escape").catch(() => {});
    await page.waitForTimeout(400);
    if (await d.isVisible().catch(() => false)) {
      await d.locator('button:has-text("取消")').first().click().catch(() => {});
      await page.waitForTimeout(400);
    }
  }
}

async function dumpDlg(tag) {
  const dlg = page.locator('.el-dialog:visible, .el-drawer:visible').last();
  const ok = await dlg.waitFor({ state: "visible", timeout: 15000 }).then(() => true).catch(() => false);
  if (!ok) { console.log(`  [${tag}] 弹窗未打开`); return false; }
  const title = ((await dlg.locator('.el-dialog__title,.el-drawer__title').first().textContent().catch(() => "")) || "").trim();
  console.log(`  [${tag}] 弹窗标题: ${title}`);
  const fields = await dlg.evaluate(el => {
    const txt = e => (e.textContent || "").replace(/\s+/g, " ").trim();
    return [...el.querySelectorAll(".el-form-item")].map(fi => {
      const lab = fi.querySelector(".el-form-item__label");
      return {
        label: lab ? txt(lab) : null,
        required: fi.className.includes("is-required"),
        inputs: [...fi.querySelectorAll("input,textarea")].map(x => ({ tag: x.tagName.toLowerCase(), ph: x.placeholder || null, disabled: x.disabled })),
        controls: [...fi.querySelectorAll(".el-select,.el-segmented,.el-radio-group,.el-switch,.el-tree-select,.el-cascader")].map(s => (s.className || "").split(" ")[0])
      };
    });
  });
  for (const f of fields) {
    const ins = f.inputs.map(x => `${x.tag}${x.ph ? ' ph="' + x.ph + '"' : ""}${x.disabled ? " [disabled]" : ""}`).join(", ");
    console.log(`    ${f.required ? "*" : " "} ${f.label || "(无标签)"}  ${ins}  ${f.controls.join("/")}`);
  }
  const tabs = await dlg.locator('.el-tabs__item, [role="tab"]').allTextContents();
  if (tabs.length) console.log(`    弹窗内标签页: ${JSON.stringify(tabs.map(s => s.trim()).filter(Boolean))}`);
  console.log(`    按钮: ${JSON.stringify((await dlg.locator("button").allTextContents()).map(s => s.trim()).filter(Boolean))}`);
  await page.screenshot({ path: `${OUT}/dlg2-${tag}.png` });
  return true;
}

const report = {};

// ---- 部门管理 ----
console.log("\n########## 部门管理 ##########");
await page.goto(`${BASE}/manage/dept`, { waitUntil: "domcontentloaded", timeout: 45000 });
await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
await page.waitForTimeout(2500);
console.log("  树节点: " + await page.locator(".el-tree-node").count());
console.log("  表格行: " + await page.evaluate(() => { const v = e => e.getClientRects().length > 0; const t = [...document.querySelectorAll("table")].filter(v)[0]; return t ? t.querySelectorAll("tbody tr").length : -1; }));
console.log("  首行: " + await page.evaluate(() => { const v = e => e.getClientRects().length > 0; const t = [...document.querySelectorAll("table")].filter(v)[0]; const tr = t?.querySelector("tbody tr"); return tr ? (tr.textContent || "").replace(/\s+/g, " ").trim().slice(0, 120) : null; }));
await page.locator('button[aria-label="新增顶级机构"]').first().click();
await dumpDlg("dept");
report.dept = { ok: true };
await closeAll();

// ---- 菜单管理 ----
console.log("\n########## 菜单管理 ##########");
await page.goto(`${BASE}/manage/menu`, { waitUntil: "domcontentloaded", timeout: 45000 });
await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
await page.waitForTimeout(2500);
const node = page.locator(".el-tree-node__content").first();
await node.click();
await page.waitForTimeout(1200);
console.log("  已展开工具栏: " + await expandToolbar());
console.log("  选中节点后按钮: " + JSON.stringify([...new Set(await page.evaluate(() => { const v = e => e.getClientRects().length > 0; return [...document.querySelectorAll("button")].filter(v).map(x => (x.textContent || "").replace(/\s+/g, " ").trim() || `[${x.getAttribute("aria-label")}]`).filter(Boolean); }))]));
// 排除最小化 dock 里的失效项
const mAdd = page.locator('button[aria-label*="新增"]:not(.koi-shell-minimized-dock)').first();
if (await mAdd.count() && await mAdd.isVisible().catch(() => false)) { await mAdd.click(); await dumpDlg("menu"); } else console.log("  选中后仍无可见新增按钮");
await closeAll();

// ---- 配置管理 ----
console.log("\n########## 配置管理 ##########");
await page.goto(`${BASE}/manage/setting`, { waitUntil: "domcontentloaded", timeout: 45000 });
await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
await page.waitForTimeout(3000);
console.log("  分组卡片: " + await page.locator('.group-card, [class*="group-card"]').count());
const grp = page.locator('.group-card, [class*="group-card"]').first();
await grp.click().catch(() => {});
await page.waitForTimeout(2500);
console.log("  进详情后按钮: " + JSON.stringify([...new Set(await page.evaluate(() => { const v = e => e.getClientRects().length > 0; return [...document.querySelectorAll("button")].filter(v).map(x => (x.textContent || "").replace(/\s+/g, " ").trim() || `[${x.getAttribute("aria-label")}]`).filter(Boolean); }))]));
const sAdd = page.locator('button:has-text("新增配置项")').first();
if (await sAdd.count() && await sAdd.isVisible().catch(() => false)) { await sAdd.click(); await dumpDlg("setting"); } else console.log("  进详情后无「新增配置项」");
await closeAll();

// ---- 消息管理 ----
console.log("\n########## 消息管理 ##########");
await page.goto(`${BASE}/manage/message`, { waitUntil: "domcontentloaded", timeout: 45000 });
await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
await page.waitForTimeout(2500);
await page.locator('button:has-text("发送消息")').first().click();
await dumpDlg("message");
await closeAll();

writeFileSync(`${OUT}/discover2.json`, JSON.stringify({ report, errs }, null, 2), "utf8");
console.log("\npageErrors: " + errs.length);
await browser.close();
