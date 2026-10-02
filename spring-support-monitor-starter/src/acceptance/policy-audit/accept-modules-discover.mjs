/**
 * 其他模块页面 · 新增弹窗字段发现
 *
 * <p>目的：拿到各页面「新增」弹窗的真实字段（标签 / placeholder / 控件类型 / 是否必填），
 * 才能写准确的增删验收。逐页读源码猜字段容易错（策略页签就吃过亏：
 * 限流弹窗不是 ReDialog、接口管控的删除在弹窗里）。</p>
 */
import { createRequire } from "node:module";
import { writeFileSync, mkdirSync } from "node:fs";

const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium } = require("playwright");

const BASE = "http://localhost:8096/system";
const OUT = "D:/ch/project/.tmp/accept-modules";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
mkdirSync(OUT, { recursive: true });

const PAGES = [
  { p: "/manage/user", name: "用户管理" },
  { p: "/manage/role", name: "角色管理" },
  { p: "/manage/menu", name: "菜单管理" },
  { p: "/manage/dept", name: "部门管理" },
  { p: "/manage/post", name: "岗位管理" },
  { p: "/manage/dict", name: "字典管理" },
  { p: "/manage/setting", name: "配置管理" },
  { p: "/manage/message", name: "消息管理" },
  { p: "/manage/announcement", name: "公告管理" },
  { p: "/manage/feedback", name: "反馈管理" }
];

const browser = await chromium.launch({ headless: false, args: ["--window-size=1680,1000"] });
const page = await (await browser.newContext({ userAgent: UA, locale: "zh-CN", viewport: { width: 1600, height: 950 } })).newPage();

await page.goto(`${BASE}/login`, { waitUntil: "domcontentloaded", timeout: 60000 });
await page.locator('input[placeholder="请输入用户名"]').first().waitFor({ state: "visible", timeout: 25000 });
await page.locator('input[placeholder="请输入用户名"]').first().fill("admin");
await page.locator('input[type="password"]').first().fill("admin123");
await page.locator('button:has-text("登录")').first().click();
await page.waitForFunction(() => !document.body.innerText.includes("欢迎登录"), null, { timeout: 60000 });

async function closeDialogs() {
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

const report = {};
for (const pg of PAGES) {
  const rec = { name: pg.name };
  try {
    await closeDialogs();
    await page.goto(`${BASE}${pg.p}`, { waitUntil: "domcontentloaded", timeout: 45000 });
    await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
    await page.waitForTimeout(2200);

    // 找新增按钮
    const btns = page.locator('button:has-text("新增")');
    const labels = [];
    const n = await btns.count();
    for (let i = 0; i < n; i++) if (await btns.nth(i).isVisible()) labels.push((await btns.nth(i).textContent() || "").trim());
    rec.addButtons = [...new Set(labels)];

    // 表格行数（判断是否有数据）
    rec.tableRows = await page.evaluate(() => {
      const vis = el => el.getClientRects().length > 0;
      const t = [...document.querySelectorAll("table")].filter(vis).pop();
      return t ? t.querySelectorAll("tbody tr").length : -1;
    });

    if (labels.length) {
      await btns.first().click();
      const dlg = page.locator('.el-dialog:visible, .el-drawer:visible').last();
      const ok = await dlg.waitFor({ state: "visible", timeout: 12000 }).then(() => true).catch(() => false);
      rec.dialogOpened = ok;
      if (ok) {
        rec.title = ((await dlg.locator('.el-dialog__title, .el-drawer__title, [role="heading"]').first().textContent().catch(() => "")) || "").trim();
        rec.fields = await dlg.evaluate(el => {
          const txt = e => (e.textContent || "").replace(/\s+/g, " ").trim();
          return [...el.querySelectorAll(".el-form-item")].map(fi => {
            const lab = fi.querySelector(".el-form-item__label");
            return {
              label: lab ? txt(lab) : null,
              required: fi.className.includes("is-required"),
              inputs: [...fi.querySelectorAll("input,textarea")].map(x => ({ tag: x.tagName.toLowerCase(), type: x.type || null, ph: x.placeholder || null })),
              controls: [...fi.querySelectorAll(".el-select,.el-segmented,.el-radio-group,.el-switch,.el-tree-select")].map(s => (s.className || "").split(" ")[0])
            };
          });
        });
        rec.buttons = (await dlg.locator("button").allTextContents()).map(s => s.trim()).filter(Boolean);
        await page.screenshot({ path: `${OUT}/dlg-${pg.p.replace(/\//g, "_")}.png` });
      }
    }
  } catch (e) { rec.err = String(e).slice(0, 160); }
  report[pg.p + " " + pg.name] = rec;
  await closeDialogs();
}

writeFileSync(`${OUT}/discover.json`, JSON.stringify(report, null, 2), "utf8");
for (const [k, v] of Object.entries(report)) {
  console.log(`\n########## ${k} ##########`);
  if (v.err) { console.log("  错误: " + v.err); continue; }
  console.log(`  新增按钮: ${JSON.stringify(v.addButtons)}   表格行数: ${v.tableRows}`);
  console.log(`  弹窗: ${v.dialogOpened}  标题: ${v.title || "-"}`);
  for (const f of (v.fields || [])) {
    const ins = (f.inputs || []).map(x => `${x.tag}${x.ph ? ' ph="' + x.ph + '"' : ""}`).join(", ");
    console.log(`    ${f.required ? "*" : " "} ${f.label || "(无标签)"}  ${ins}  ${(f.controls || []).join("/")}`);
  }
  console.log(`  按钮: ${JSON.stringify(v.buttons)}`);
}
await browser.close();
