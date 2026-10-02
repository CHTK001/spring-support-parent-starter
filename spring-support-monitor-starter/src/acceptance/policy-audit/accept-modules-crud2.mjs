/**
 * 其余 4 个管理页 · 界面新增/编辑/删除验收（dept / menu / setting / message）
 *
 * <p>这 4 个页面不能套用 accept-modules-crud 的通用套路，各自的「新增」入口形态不同：</p>
 * <ul>
 *   <li>dept   —— toolbar「新增顶级机构」，弹窗首行是「上级机构」select</li>
 *   <li>menu   —— 「新增」是 <b>circle plain 图标按钮</b>（无文字/无 aria-label），
 *                 且 8 个必填项、菜单图标要开图标选择器</li>
 *   <li>setting—— 必须先点分组卡片进「分组详情」，列表区才渲染「新增配置项」</li>
 *   <li>message—— 文案是「发送消息」不是「新增」</li>
 * </ul>
 *
 * <p>工具栏可能被收进「最小化 dock」，此时页面里唯一的「新增」是个不可点的 dock 项
 * （aria-label 竟是「恢复窗口」而 title 是「新增菜单节点」）—— 必须先点「展开工具栏」。</p>
 */
import { createRequire } from "node:module";
import { mkdirSync, writeFileSync } from "node:fs";

const require = createRequire("D:/ch/project/vue-support-parent-starter/package.json");
const { chromium } = require("playwright");

const BASE = "http://localhost:8096/system";
const OUT = "D:/ch/project/.tmp/accept-modules";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
mkdirSync(OUT, { recursive: true });
const ONLY = process.argv[2];
const TAG = "zzq";

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

async function closeAll() {
  for (let i = 0; i < 4; i++) {
    const d = page.locator('.el-dialog:visible, .el-message-box:visible, .el-drawer:visible').last();
    if (!(await d.isVisible().catch(() => false))) return;
    await page.keyboard.press("Escape").catch(() => {});
    await page.waitForTimeout(400);
    if (await d.isVisible().catch(() => false)) {
      const cancel = d.locator('button:has-text("取消")').first();
      if (await cancel.count()) await cancel.click().catch(() => {});
      else await d.locator("button").last().click().catch(() => {});
      await page.waitForTimeout(500);
    }
  }
}

async function expandToolbar() {
  const ex = page.locator('button[aria-label="展开工具栏"]').first();
  if (await ex.count() && await ex.isVisible().catch(() => false)) {
    await ex.click();
    await page.waitForTimeout(1200);
    return true;
  }
  return false;
}

async function fillByLabel(dlg, label, value) {
  const item = dlg.locator(`.el-form-item:has(.el-form-item__label:text-is("${label}"))`).first();
  const inp = item.locator("input, textarea").first();
  try { await inp.fill(value, { timeout: 8000 }); return true; } catch { return false; }
}

/** el-select：点开选第一项 */
async function pickSelect(dlg, label, optText) {
  const item = dlg.locator(`.el-form-item:has(.el-form-item__label:text-is("${label}"))`).first();
  const sel = item.locator(".el-select").first();
  await sel.click();
  await page.waitForTimeout(700);
  const dd = page.locator(".el-select-dropdown:visible").last();
  await dd.waitFor({ state: "visible", timeout: 8000 }).catch(() => {});
  const opt = optText
    ? dd.locator(`.el-select-dropdown__item:has-text("${optText}")`).first()
    : dd.locator(".el-select-dropdown__item").first();
  if (!(await opt.count())) { await page.keyboard.press("Escape"); return false; }
  await opt.click();
  await page.waitForTimeout(500);
  return true;
}

/**
 * 提交弹窗并收集提示。
 *
 * <p>踩过的坑：先 `waitForLoadState(networkidle)` + 固定等 2500ms 再读 `.el-message`，
 * 而 Element Plus 的 message 3s 就自动消失 —— 于是「新增成功」读成空数组，
 * 看起来像提交没发生，实际行已经建好了。改成点击前先挂 MutationObserver 收集。</p>
 */
async function submitDlg(dlg) {
  await page.evaluate(() => {
    window.__toasts = [];
    const grab = () => {
      for (const e of document.querySelectorAll(".el-message")) {
        const t = (e.textContent || "").trim();
        if (t && !window.__toasts.includes(t)) window.__toasts.push(t);
      }
    };
    if (window.__toastObs) window.__toastObs.disconnect();
    window.__toastObs = new MutationObserver(grab);
    window.__toastObs.observe(document.body, { childList: true, subtree: true });
  });
  await dlg.locator('button:has-text("确定"), button:has-text("保存")').last().click();
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(2500);
  return page.evaluate(() => { window.__toastObs?.disconnect(); return (window.__toasts || []).slice(0, 3); });
}

async function hasRow(text) {
  return page.evaluate(t => {
    const vis = el => el.getClientRects().length > 0;
    const txt = el => (el.textContent || "").replace(/\s+/g, " ").trim();
    // 树（部门管理 / 菜单管理 的列表是树，不是表 —— 只看表会漏判，踩过一次）
    for (const n of [...document.querySelectorAll(".el-tree-node__content")].filter(vis)) {
      if (txt(n).includes(t)) return { found: true, text: "树节点:" + txt(n).slice(0, 120) };
    }
    // 扫全部可见表：部门管理页面上有 3 个表（部门详情 / 嵌入用户表 / 用户列表），
    // 只看最后一个会漏判
    const tables = [...document.querySelectorAll("table")].filter(vis);
    let total = 0;
    for (const tb of tables) {
      const rows = [...tb.querySelectorAll("tbody tr")];
      total += rows.length;
      for (const tr of rows) if (txt(tr).includes(t)) return { found: true, text: txt(tr).slice(0, 130) };
    }
    return { found: false, rowCount: total, tableCount: tables.length };
  }, text);
}

/** 找到承载目标文本的「行容器」：树节点优先，其次表格行 */
function rowEl(text) {
  const tree = page.locator(`.el-tree-node__content:has-text("${text}")`).first();
  return { tree, table: page.locator(`table tbody tr:has-text("${text}")`).first() };
}

/** 判定前重载页面，避免只看到内存里的旧列表 */
async function reload() {
  const url = page.url();
  await page.goto(url, { waitUntil: "domcontentloaded", timeout: 45000 });
  await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
  await page.waitForTimeout(2500);
  await expandToolbar();
}

/** 读取弹窗内 Element 的表单校验错误 */
async function formErrors(dlg) {
  return dlg.locator(".el-form-item__error").evaluateAll(els => els.map(e => (e.textContent || "").replace(/\s+/g, " ").trim()).filter(Boolean));
}

/** 必填项里哪些是空的（诊断用） */
async function emptyRequired(dlg) {
  return dlg.locator(".el-form-item.is-required").evaluateAll(els => els.map(fi => {
    const lab = fi.querySelector(".el-form-item__label");
    const inp = fi.querySelector("input, textarea");
    const seg = fi.querySelector(".el-segmented, .el-radio-group, .el-select");
    return {
      label: lab ? (lab.textContent || "").replace(/\s+/g, " ").trim() : "(无)",
      value: inp ? inp.value : (seg ? "(选择器)" : "(无输入)")
    };
  }));
}

const results = [];

// ================= 部门管理 =================
if (!ONLY || ONLY === "dept") {
  const rec = { page: "部门管理", probe: TAG + "-机构" };
  try {
    await closeAll();
    await page.goto(`${BASE}/manage/dept`, { waitUntil: "domcontentloaded", timeout: 45000 });
    await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
    await page.waitForTimeout(2500);
    rec.expanded = await expandToolbar();

    await page.locator('button[aria-label="新增顶级机构"]').first().click();
    let dlg = page.locator(".el-dialog:visible").last();
    await dlg.waitFor({ state: "visible", timeout: 15000 });
    await fillByLabel(dlg, "机构名称", TAG + "-机构");
    await fillByLabel(dlg, "机构编码", TAG + "_dept_01");
    rec.msgCreate = await submitDlg(dlg);
    await closeAll();
    await reload();

    let r = await hasRow(TAG + "-机构");
    rec.created = r.found;
    rec.rowText = r.text;
    await page.screenshot({ path: `${OUT}/dept-created.png` });

    // 编辑（部门列表是树；编辑/删除在**选中节点的详情面板**里，不是行的 hover 按钮）
    if (r.found) {
      const { tree } = rowEl(TAG + "-机构");
      if (await tree.count()) { await tree.first().click(); await page.waitForTimeout(1500); }
      rec.editHost = "选中节点后的详情面板";
      const eb = page.locator('button[aria-label="编辑"]').first();
      if (await eb.count() && await eb.isVisible().catch(() => false)) {
        await eb.click();
        dlg = page.locator(".el-dialog:visible").last();
        const ok = await dlg.waitFor({ state: "visible", timeout: 12000 }).then(() => true).catch(() => false);
        if (ok) {
          await fillByLabel(dlg, "机构名称", TAG + "-机构-改");
          rec.msgEdit = await submitDlg(dlg);
          await closeAll();
          await reload();
          rec.edited = (await hasRow(TAG + "-机构-改")).found;
          rec.editAction = rec.edited ? "已保存" : "保存后搜不到";
          rec.probe = TAG + "-机构-改";
        } else rec.editAction = "编辑弹窗未打开";
        await closeAll();
      } else rec.editAction = "详情面板无可见编辑按钮";
    }

    // 删除
    if (r.found) {
      const { tree } = rowEl(rec.probe);
      if (await tree.count()) { await tree.first().click(); await page.waitForTimeout(1500); }
      const db = page.locator('button[aria-label="删除"]').first();
      if (await db.count() && await db.isVisible().catch(() => false)) {
        await db.click();
        const box = page.locator(".el-message-box:visible");
        const ok = await box.waitFor({ state: "visible", timeout: 8000 }).then(() => true).catch(() => false);
        if (ok) {
          await box.locator('button:has-text("删除"), button:has-text("确定"), button:has-text("确认")').last().click();
          rec.deleteAction = "已确认";
        } else rec.deleteAction = "确认框未出现";
        await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
        await page.waitForTimeout(2500);
        await reload();
        rec.deleted = !(await hasRow(rec.probe)).found;
      } else rec.deleteAction = "详情面板无可见删除按钮";
      await page.screenshot({ path: `${OUT}/dept-deleted.png` });
    }
  } catch (e) { rec.err = String(e).slice(0, 200); }
  results.push(rec);
}

// ================= 配置管理 =================
if (!ONLY || ONLY === "setting") {
  const rec = { page: "配置管理", probe: TAG + "-配置" };
  // 进指定分组的详情；不传 groupCode 就进第一张卡片
  const enterGroup = async (groupCode) => {
    await closeAll();
    await page.goto(`${BASE}/manage/setting`, { waitUntil: "domcontentloaded", timeout: 45000 });
    await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
    await page.waitForTimeout(3000);
    await expandToolbar();
    // 精确类名：用 [class*="group-card"] 会把 group-card__top / __icon 等子元素一起匹配（176 个）
    const cards = page.locator("div.group-card");
    const waitTable = async () => {
      // 固定等 2500ms 不足以让分组明细表渲染完（实测 debug 用 3000ms 才出行），
      // 改成轮询「可见表格里出现行」为止
      for (let i = 0; i < 24; i++) {
        const n = await page.evaluate(() => {
          const vis = el => el.getClientRects().length > 0;
          return [...document.querySelectorAll("table")].filter(vis).reduce((s, t) => s + t.querySelectorAll("tbody tr").length, 0);
        });
        if (n > 0) return n;
        await page.waitForTimeout(500);
      }
      return 0;
    };
    if (groupCode) {
      const hit = cards.filter({ has: page.locator(`.group-card__code:text-is("${groupCode}")`) }).first();
      if (await hit.count()) { await hit.click(); rec.rowsAfterEnter = await waitTable(); return true; }
      return false;
    }
    await cards.first().click();
    rec.rowsAfterEnter = await waitTable();
    return true;
  };
  const readPrefill = async () => {
    await page.locator('button:has-text("新增配置项")').first().click();
    const d = page.locator(".el-dialog:visible").last();
    await d.waitFor({ state: "visible", timeout: 15000 });
    const g = await d.locator('.el-form-item:has(.el-form-item__label:text-is("分组"))').first()
      .locator("input, textarea").first().inputValue().catch(() => null);
    await closeAll();
    return g;
  };
  try {
    rec.expanded = await expandToolbar();
    await enterGroup(null);
    // 弹窗里的「分组」是预填的，可能与当前卡片不是同一个分组；
    // 若不同，判定时在当前列表里当然找不到（踩过一次：行进了 config 分组却在别的卡片下找）
    const pre = await readPrefill();
    rec.prefilledGroup = pre;
    const inGroup = await enterGroup(pre);
    rec.enteredGroupByCode = inGroup;

    let dlg = page.locator(".el-dialog:visible").last();
    await page.locator('button:has-text("新增配置项")').first().click();
    dlg = page.locator(".el-dialog:visible").last();
    await dlg.waitFor({ state: "visible", timeout: 15000 });
    rec.filledName = await fillByLabel(dlg, "名称", TAG + "-配置");
    await fillByLabel(dlg, "编码", TAG + ".setting.v1");
    rec.pickedType = await pickSelect(dlg, "类型", "字符串");
    rec.msgCreate = await submitDlg(dlg);
    await closeAll();
    await enterGroup(pre);

    // 判据用**编码**而不是名称：settingLabel() 的回退是 `code || name`，
    // 自建配置（编码不在 SETTING_LABELS 字典里）列表只显示编码，名称根本不渲染
    const CODE = TAG + ".setting.v1";
    let r = await hasRow(CODE);
    rec.created = r.found;
    rec.rowText = r.text;
    rec.note = "判据用编码（名称在列表里不显示，见 settingLabel 回退逻辑）";
    await page.screenshot({ path: `${OUT}/setting-created.png` });

    if (r.found) {
      const row = page.locator(`table tbody tr:has-text("${CODE}")`).first();
      const eb = row.locator('button[aria-label="编辑"]').first();
      if (await eb.count()) {
        await eb.click();
        dlg = page.locator(".el-dialog:visible").last();
        const ok = await dlg.waitFor({ state: "visible", timeout: 12000 }).then(() => true).catch(() => false);
        if (ok) {
          const nameItem = dlg.locator('.el-form-item:has(.el-form-item__label:text-is("名称"))').first();
          rec.nameBefore = await nameItem.locator("input, textarea").first().inputValue().catch(() => null);
          await fillByLabel(dlg, "名称", TAG + "-配置-改");
          rec.msgEdit = await submitDlg(dlg);
          await closeAll();
          await enterGroup(pre);
          // 名称在列表里不显示，用「重新打开编辑弹窗回读」证明真的落库了
          const row2 = page.locator(`table tbody tr:has-text("${CODE}")`).first();
          await row2.locator('button[aria-label="编辑"]').first().click();
          dlg = page.locator(".el-dialog:visible").last();
          const ok2 = await dlg.waitFor({ state: "visible", timeout: 12000 }).then(() => true).catch(() => false);
          if (ok2) {
            rec.nameAfter = await dlg.locator('.el-form-item:has(.el-form-item__label:text-is("名称"))').first()
              .locator("input, textarea").first().inputValue().catch(() => null);
            rec.edited = rec.nameAfter === TAG + "-配置-改";
            rec.editAction = rec.edited ? "已保存（编辑弹窗回读确认）" : `回读=${rec.nameAfter}`;
          } else { rec.edited = false; rec.editAction = "回读时编辑弹窗未打开"; }
          await closeAll();
          rec.probe = CODE;
        } else rec.editAction = "编辑弹窗未打开";
        await closeAll();
      } else rec.editAction = "行内无编辑按钮";

      await enterGroup(pre);
      const row2 = page.locator(`table tbody tr:has-text("${rec.probe}")`).first();
      const db = row2.locator('button[aria-label="删除"]').first();
      if (await db.count()) {
        await db.click();
        const box = page.locator(".el-message-box:visible");
        const ok = await box.waitFor({ state: "visible", timeout: 8000 }).then(() => true).catch(() => false);
        if (ok) {
          await box.locator('button:has-text("删除"), button:has-text("确定"), button:has-text("确认")').last().click();
          rec.deleteAction = "已确认";
        } else rec.deleteAction = "确认框未出现";
        await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
        await page.waitForTimeout(2500);
        await enterGroup(pre);
        rec.deleted = !(await hasRow(rec.probe)).found;
      } else rec.deleteAction = "行内无删除按钮";
      await page.screenshot({ path: `${OUT}/setting-deleted.png` });
    }
  } catch (e) { rec.err = String(e).slice(0, 200); }
  results.push(rec);
}

// ================= 消息管理 =================
if (!ONLY || ONLY === "message") {
  const rec = { page: "消息管理", probe: TAG + "-消息" };
  try {
    await closeAll();
    await page.goto(`${BASE}/manage/message`, { waitUntil: "domcontentloaded", timeout: 45000 });
    await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
    await page.waitForTimeout(2500);
    rec.expanded = await expandToolbar();

    await page.locator('button:has-text("发送消息")').first().click();
    let dlg = page.locator(".el-dialog:visible").last();
    await dlg.waitFor({ state: "visible", timeout: 15000 });
    const filled = await fillByLabel(dlg, "消息标题", TAG + "-消息");
    rec.filledTitle = filled;
    rec.dialogButtons = (await dlg.locator("button").allTextContents()).map(s => s.trim()).filter(Boolean);
    if (filled) rec.msgCreate = await submitDlg(dlg);
    await closeAll();

    let r = await hasRow(TAG + "-消息");
    rec.created = r.found;
    rec.rowText = r.text;
    await page.screenshot({ path: `${OUT}/message-created.png` });

    if (r.found) {
      const row = page.locator(`table tbody tr:has-text("${TAG}-消息")`).first();
      const rowBtns = await row.locator("button").evaluateAll(els => els.map(e => (e.getAttribute("aria-label") || e.textContent || "").replace(/\s+/g, " ").trim()).filter(Boolean));
      rec.rowButtons = rowBtns;
      const db = row.locator('button[aria-label*="删除"]').first();
      if (await db.count()) {
        await db.click();
        const box = page.locator(".el-message-box:visible");
        const ok = await box.waitFor({ state: "visible", timeout: 8000 }).then(() => true).catch(() => false);
        if (ok) {
          await box.locator('button:has-text("删除"), button:has-text("确定"), button:has-text("确认")').last().click();
          rec.deleteAction = "已确认";
        } else rec.deleteAction = "确认框未出现";
        await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
        await page.waitForTimeout(2500);
        rec.deleted = !(await hasRow(rec.probe)).found;
      } else rec.deleteAction = "行内无删除按钮（操作列按钮=" + JSON.stringify(rowBtns) + "）";
      await page.screenshot({ path: `${OUT}/message-deleted.png` });
    }
  } catch (e) { rec.err = String(e).slice(0, 200); }
  results.push(rec);
}

// ================= 菜单管理 =================
if (!ONLY || ONLY === "menu") {
  const rec = { page: "菜单管理", probe: TAG + "-菜单" };
  try {
    await closeAll();
    await page.goto(`${BASE}/manage/menu`, { waitUntil: "domcontentloaded", timeout: 45000 });
    await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
    await page.waitForTimeout(2500);
    rec.expanded = await expandToolbar();
    // 新增是 circle plain 图标按钮（无文字/无 aria-label），只能用结构选
    const circles = page.locator("button.el-button.is-circle");
    rec.circleCount = await circles.count();
    rec.circleAria = await circles.evaluateAll(els => els.map(e => ({ title: e.getAttribute("title"), aria: e.getAttribute("aria-label"), text: (e.textContent || "").trim() })));

    await circles.first().click();
    let dlg = page.locator(".el-dialog:visible").last();
    await dlg.waitFor({ state: "visible", timeout: 15000 });
    // 菜单图标是必填。图标选择器是 **popover**（koi-select-icon-pop，152 个图标 / 3 个来源标签），
    // 不是弹窗 —— 按 .el-dialog 找会找不到（踩过一次）
    const iconItem = dlg.locator('.el-form-item:has(.el-form-item__label:text-is("菜单图标"))').first();
    // 要点外层 div（.koi-select-icon-input），点里面的 input 不会弹出选择器
    await iconItem.locator(".koi-select-icon-input").first().click();
    // 类名是 koi-select-icon-popper（两个 p）。之前按 .slice(0,60) 截断看到
    // 「koi-select-icon-pop」是假象，CSS 类选择器是整 token 匹配，所以一直没命中
    const pop = page.locator(".koi-select-icon-popper:visible").last();
    const popOk = await pop.waitFor({ state: "visible", timeout: 10000 }).then(() => true).catch(() => false);
    rec.iconPickerOpened = popOk;
    rec.poppers = await page.evaluate(() => {
      const v = el => el.getClientRects().length > 0;
      return [...document.querySelectorAll(".el-popper, .el-popover, [class*='pop']")]
        .filter(v).map(e => (e.className || "").toString().slice(0, 70));
    });
    if (popOk) {
      // 图标格是 .koi-select-icon-button（80 个）；按 li/[class*=item] 会只命中 5 个标签页
      const cells = pop.locator(".koi-select-icon-button");
      rec.iconCellCount = await cells.count();
      await cells.first().click();
      await page.waitForTimeout(1000);
      rec.iconValueAfter = await iconItem.locator("input").first().inputValue().catch(() => null);
      rec.iconFilled = !!rec.iconValueAfter;
    }
    // 不能按 Esc 收 popover —— 会把整个新增弹窗一起关掉（实测 dialogStillOpen=false）。
    // 点弹窗标题处把焦点挪开即可。
    await dlg.locator(".el-dialog__title").first().click({ position: { x: 5, y: 5 } }).catch(() => {});
    await page.waitForTimeout(600);
    dlg = page.locator(".el-dialog:visible").last();
    rec.dialogStillOpen = await dlg.isVisible().catch(() => false);
    await fillByLabel(dlg, "菜单名称", TAG + "-菜单");
    await fillByLabel(dlg, "路由名称", TAG + "Menu");
    // 路径正则是 /^\/[a-zA-Z0-9:]+(\/[a-zA-Z0-9:]+)*$/ —— 不允许连字符/下划线，
    // 但报错文案只说「必须斜杠开头」，文案与规则对不上（已记为缺陷）
    await fillByLabel(dlg, "路由Path", "/zzqmenu");
    // handleAdd() 不带 parent 时 menuType=2（菜单），且 linkMode 默认 component
    // → 页面路径是必填（formRules.component）
    rec.filledComponent = await fillByLabel(dlg, "页面路径", "system/zzqmenu/index");
    rec.msgCreate = await submitDlg(dlg);
    if (!rec.msgCreate.length) {
      rec.formErrors = await formErrors(dlg);
      rec.requiredState = await emptyRequired(dlg);
    }
    await closeAll();
    const found = async t => page.evaluate(x => {
      const v = el => el.getClientRects().length > 0;
      return [...document.querySelectorAll(".el-tree-node__content")].filter(v).some(n => (n.textContent || "").includes(x));
    }, t);
    rec.created = await found(TAG + "-菜单");
    rec.rowText = rec.created ? TAG + "-菜单 (树节点)" : null;
    await page.screenshot({ path: `${OUT}/menu-created.png` });

    if (rec.created) {
      await page.locator(`.el-tree-node__content:has-text("${TAG}-菜单")`).first().click();
      await page.waitForTimeout(1500);
      rec.detailButtons = await page.evaluate(() => {
        const v = el => el.getClientRects().length > 0;
        return [...document.querySelectorAll("button")].filter(v).map(x => (x.textContent || "").replace(/\s+/g, " ").trim() || `[${x.getAttribute("aria-label")}]`).filter(Boolean);
      });
    }

    // 编辑（菜单的增/删/改都在右键菜单里）
    if (rec.created) {
      await page.locator(`.el-tree-node__content:has-text("${TAG}-菜单")`).first().click({ button: "right" });
      await page.waitForTimeout(900);
      const editItem = page.locator('[role="menuitem"]:has-text("编辑")').last();
      if (await editItem.count() && await editItem.isVisible().catch(() => false)) {
        rec.editAction = "已打开";
        await editItem.click();
        dlg = page.locator(".el-dialog:visible").last();
        const ok = await dlg.waitFor({ state: "visible", timeout: 12000 }).then(() => true).catch(() => false);
        if (ok) {
          rec.nameBefore = await dlg.locator('.el-form-item:has(.el-form-item__label:text-is("菜单名称"))').first()
            .locator("input, textarea").first().inputValue().catch(() => null);
          await fillByLabel(dlg, "菜单名称", TAG + "-菜单-改");
          rec.msgEdit = await submitDlg(dlg);
          if (!rec.msgEdit.length) {
            rec.formErrors = await formErrors(dlg);
            rec.requiredState = await emptyRequired(dlg);
          }
          await closeAll();
          await page.goto(`${BASE}/manage/menu`, { waitUntil: "domcontentloaded", timeout: 45000 });
          await page.waitForLoadState("networkidle", { timeout: 25000 }).catch(() => {});
          await page.waitForTimeout(2500);
          await expandToolbar();
          rec.edited = await found(TAG + "-菜单-改");
          rec.editAction = rec.edited ? "已保存" : "保存后树里搜不到新值";
          rec.probe = TAG + "-菜单-改";
        } else rec.editAction = "编辑弹窗未打开";
        await closeAll();
      } else rec.editAction = "右键菜单无「编辑」";
    }

    // 删除走**右键菜单**（@node-contextmenu → 菜单里有文字「删除」）。
    if (rec.created) {
      // 工具栏也有 circle plain 的 danger 图标按钮，但页面上有 3 个，
      // 按 el-button--danger 取 first() 会点到别的面板（实测点了之后目标行还在库里）
      rec.dangerCircles = await page.locator("button.el-button--danger.is-circle").count();
      let clicked = false;
      await page.locator(`.el-tree-node__content:has-text("${TAG}-菜单")`).first().click({ button: "right" });
      await page.waitForTimeout(900);
      // 上下文菜单是 teleport 出去的 .menu-context-menu__item[role=menuitem]
      const ctxItem = page.locator('[role="menuitem"]:has-text("删除")').last();
      if (await ctxItem.count() && await ctxItem.isVisible().catch(() => false)) {
        rec.ctxDeleteVisible = true;
        await ctxItem.click();
        clicked = true;
      } else {
        rec.ctxDeleteVisible = false;
        rec.ctxItems = await page.evaluate(() => {
          const v = el => el.getClientRects().length > 0;
          return [...document.querySelectorAll("[role='menuitem'], .menu-context-menu__item")].filter(v).map(x => (x.textContent || "").replace(/\s+/g, " ").trim());
        });
      }
      if (clicked) {
        const box = page.locator(".el-message-box:visible");
        const ok = await box.waitFor({ state: "visible", timeout: 8000 }).then(() => true).catch(() => false);
        if (ok) {
          await box.locator('button:has-text("删除"), button:has-text("确定"), button:has-text("确认")').last().click();
          rec.deleteAction = "已确认";
        } else rec.deleteAction = "确认框未出现";
        await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
        await page.waitForTimeout(2500);
        // handleDelete 内部会 loadList()，树是异步重渲染的 —— 轮询等它消失，
        // 固定等一次会读成「还在」（踩过一次）
        for (let i = 0; i < 20; i++) {
          if (!(await found(rec.probe))) { rec.deleted = true; break; }
          await page.waitForTimeout(500);
        }
        if (rec.deleted == null) rec.deleted = false;
      } else rec.deleteAction = "右键菜单无「删除」";
      await page.screenshot({ path: `${OUT}/menu-deleted.png` });
    }
  } catch (e) { rec.err = String(e).slice(0, 200); }
  results.push(rec);
}

writeFileSync(`${OUT}/crud2.json`, JSON.stringify({ results, errs }, null, 2), "utf8");
console.log("=== 其余管理页 · 界面增/改/删 ===\n");
for (const r of results) {
  console.log(`【${r.page}】 probe=${r.probe}`);
  if (r.err) console.log("  错误: " + r.err);
  for (const k of ["expanded", "groupName", "prefilledGroup", "enteredGroupByCode", "rowsAfterEnter", "filledName", "filledComponent", "pickedType", "filledTitle", "iconPickerOpened", "iconCellCount", "iconValueAfter", "iconFilled", "dialogStillOpen", "circleCount", "editHost"])
    if (r[k] != null) console.log(`  ${k}: ${r[k]}`);
  if (r.nameBefore != null) console.log(`  名称回读: ${r.nameBefore} -> ${r.nameAfter ?? r.nameAfterEdit}`);
  if (r.note) console.log("  备注: " + r.note);
  if (r.circleAria) console.log("  circle 按钮可访问名: " + JSON.stringify(r.circleAria.slice(0, 3)));
  if (r.dialogButtons) console.log("  弹窗按钮: " + JSON.stringify(r.dialogButtons));
  if (r.formErrors?.length) console.log("  表单校验错误: " + JSON.stringify(r.formErrors));
  if (r.requiredState) console.log("  必填项状态: " + JSON.stringify(r.requiredState));
  if (r.rowButtons) console.log("  行内按钮: " + JSON.stringify(r.rowButtons));
  if (r.msgCreate?.length) console.log("  新增提示: " + JSON.stringify(r.msgCreate));
  if (r.created != null) console.log(`  界面新增 → 找到该行: ${r.created ? "通过" : "**失败**"}   ${r.rowText ? "行=" + r.rowText : ""}`);
  if (r.editAction) console.log(`  编辑动作: ${r.editAction}` + (r.msgEdit?.length ? `  提示=${JSON.stringify(r.msgEdit)}` : ""));
  if (r.edited != null) console.log(`  界面编辑 → 找到新值: ${r.edited ? "通过" : "**失败**"}`);
  if (r.detailButtons) console.log("  详情面板按钮: " + JSON.stringify([...new Set(r.detailButtons)]));
  if (r.dangerCircles != null) console.log("  danger circle 按钮数: " + r.dangerCircles);
  if (r.ctxDeleteVisible != null) console.log("  右键菜单有「删除」: " + r.ctxDeleteVisible + (r.ctxItems ? "  菜单项=" + JSON.stringify(r.ctxItems) : ""));
  if (r.deleteAction) console.log(`  删除动作: ${r.deleteAction}`);
  if (r.deleted != null) console.log(`  界面删除 → 已无: ${r.deleted ? "通过" : "**失败**"}`);
  console.log("");
}
console.log("pageErrors: " + errs.length);
for (const e of errs.slice(0, 3)) console.log("  " + e);
await browser.close();
