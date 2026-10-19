/**
 * Windows 发布真机端到端验收（经线上平台 8112 驱动目标 Windows 主机）。
 *
 * 覆盖：启动 / 重启 / 备份 / 好包升级（md5 变更）/ 坏包升级自动回滚（含库 PID 落库）/ 停止清 PID。
 * 期望 26/26 全绿（见 RELEASE-ACCEPTANCE-REPORT.md §9）。
 *
 * 依赖（操作机需有 PuTTY plink/pscp 与 OpenSSH ssh）：
 *   - 能 SSH 登录平台主机（默认 kali@192.168.50.198）以执行 set-ssh 与驱逐检查
 *   - 能 SSH 登录 Windows 目标机（默认 Administrator@192.168.50.101）以核对端口属主/PID
 *   - 本地夹具：good-v4.jar（有效且与线上当前版本 md5 不同的包）与 broken-v2.jar（损坏包）
 *
 * 环境变量（均有默认值）：
 *   RELEASE_BASE, PLATFORM_SSH_HOST, PLATFORM_SSH_USER, PLATFORM_SSH_PW, PLATFORM_HOSTKEY,
 *   WIN_SSH_HOST, WIN_SSH_USER, WIN_SSH_KEY, WIN_PROJECT_DIR, WIN_PROJECT_ID, WIN_RELEASE_ID,
 *   WIN_PORT, FIXTURES_DIR, PLINK, SET_SSH_SCRIPT
 */
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import path from 'node:path';

const BASE = process.env.RELEASE_BASE || 'http://192.168.50.198:8112';
const REL = '/ms/server/release';
const P_HOST = process.env.PLATFORM_SSH_HOST || '192.168.50.198';
const P_USER = process.env.PLATFORM_SSH_USER || 'kali';
const P_PW = process.env.PLATFORM_SSH_PW || 'kali';
const HK = process.env.PLATFORM_HOSTKEY || 'ssh-ed25519 255 SHA256:cwfyC8w0qteYq8lXud9P9U2aSS8O/bxQOK6Y60BOmsI';
const W_HOST = process.env.WIN_SSH_HOST || '192.168.50.101';
const W_USER = process.env.WIN_SSH_USER || 'Administrator';
const W_KEY = process.env.WIN_SSH_KEY || 'C:\\Users\\Administrator\\.ssh\\ms_monitor_ed25519';
const P_DIR = process.env.WIN_PROJECT_DIR || 'C:\\ms-acc\\acc-sb';
const PID_PROJ = Number(process.env.WIN_PROJECT_ID || 38);
const PID_REL = Number(process.env.WIN_RELEASE_ID || 5);
const PORT = Number(process.env.WIN_PORT || 18089);
const FIX = process.env.FIXTURES_DIR || path.join(process.cwd(), '.tmp', 'fixtures');
const PLINK = process.env.PLINK || 'C:\\Program Files\\PuTTY\\plink.exe';
const SET_SSH = process.env.SET_SSH_SCRIPT || path.join(process.cwd(), '.tmp', 'set-ssh.sh');
const MAIN = P_DIR + '\\smoke.jar';
const PIDFILE = P_DIR + '\\app.pid';
let TOKEN = '';

function plink(cmd) {
  return execFileSync(PLINK, ['-ssh', '-batch', '-l', P_USER, '-pw', P_PW, '-hostkey', HK, P_HOST, cmd],
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
}
function setSsh() {
  execFileSync(PLINK, ['-ssh', '-batch', '-l', P_USER, '-pw', P_PW, '-hostkey', HK, '-m', SET_SSH, P_HOST], { encoding: 'utf8' });
}
function win(cmd) {
  return execFileSync('ssh', ['-i', W_KEY, '-o', 'StrictHostKeyChecking=no', '-o', 'UserKnownHostsFile=NUL',
    '-o', 'ConnectTimeout=15', `${W_USER}@${W_HOST}`, cmd], { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 }).trim();
}
function owners() {
  const out = win('netstat -ano');
  const set = new Set();
  for (const line of out.split(/\r?\n/)) {
    if (!/LISTENING/i.test(line)) continue;
    const m = line.trim().match(/:(\d+)\s+\S+\s+LISTENING\s+(\d+)\s*$/i);
    if (m && Number(m[1]) === PORT) set.add(m[2]);
  }
  return [...set];
}
function winPid() { try { return win(`type "${PIDFILE}"`).trim(); } catch { return ''; } }
function winMd5(p) { const m = win(`certutil -hashfile "${p}" MD5`).match(/[0-9a-fA-F]{32}/); return m ? m[0].toUpperCase() : ''; }
function fileMd5(p) { return createHash('md5').update(readFileSync(p)).digest('hex').toUpperCase(); }
/** 从候选中挑一个与当前主包 md5 不同的有效「好包」，保证升级一定发生版本切换（可复跑）。 */
function pickGoodPackage(currentMainMd5) {
  const cands = ['good-v3.jar', 'good-v4.jar']
    .map((f) => path.join(FIX, f))
    .filter((p) => { try { readFileSync(p); return true; } catch { return false; } });
  if (!cands.length) throw new Error(`缺少有效好包夹具（需 ${FIX} 下 good-v3.jar / good-v4.jar）`);
  return cands.find((p) => fileMd5(p) !== currentMainMd5) || cands[0];
}

async function login() {
  const r = await fetch(BASE + '/v2/user/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123', codeKey: '', securityCode: '' }) });
  TOKEN = (await r.json())?.data?.accessToken || '';
}
async function api(p, method = 'GET', body) {
  const res = await fetch(BASE + p, { method,
    headers: { 'Content-Type': 'application/json', 'x-oauth-token': TOKEN },
    body: body === undefined ? undefined : JSON.stringify(body) });
  const t = await res.text();
  try { return JSON.parse(t); } catch { return { raw: t, status: res.status }; }
}
async function poll(taskId, label, ms = 480000) {
  const deadline = Date.now() + ms;
  while (Date.now() < deadline) {
    const d = (await api(`${REL}/task/${taskId}`)).data;
    if (d?.finished) { console.log(`  [${label}] success=${d.success} msg=${d.message}`); return d; }
    await new Promise((r) => setTimeout(r, 1500));
  }
  console.log(`\n  [${label}] TIMEOUT`); return null;
}
async function op(p, label) {
  setSsh();
  const r = await api(p, 'POST');
  if (!r?.data) { console.log(`  [${label}] no task: ${r?.msg}`); return null; }
  console.log(`  [${label}] taskId=${r.data}`);
  return poll(r.data, label);
}
async function uploadUpgrade(file, name, label) {
  setSsh();
  const form = new FormData();
  form.append('file', new Blob([readFileSync(file)]), name);
  const res = await fetch(`${BASE}${REL}/project/${PID_PROJ}/upload`, { method: 'POST', headers: { 'x-oauth-token': TOKEN }, body: form });
  const j = await res.json();
  console.log(`  [${label}] upload taskId=${j?.data} ${j?.msg || ''}`);
  return j?.data ? poll(j.data, label) : null;
}
async function proj() {
  return ((await api(`${REL}/${PID_REL}/projects`)).data || []).find((x) => x.id === PID_PROJ);
}
const results = [];
const rec = (n, p, d) => { results.push({ n, p }); console.log(`  ${p ? 'PASS' : 'FAIL'} ${n}${d ? ' :: ' + d : ''}`); };

(async () => {
  await login();
  await op(`${REL}/project/${PID_PROJ}/stop`, 'pre-stop');

  console.log('--- C1 start ---');
  let t = await op(`${REL}/project/${PID_PROJ}/start`, 'start');
  rec('C1 start task success', t?.success === true, t?.message || '');
  let p = await proj(); let pid1 = winPid();
  rec('C1 db pid == app.pid', !!pid1 && String(p?.pid || '') === pid1, `db=${p?.pid} file=${pid1}`);
  let o = owners();
  rec('C1 port owner == app.pid', o.length === 1 && o[0] === pid1, `owners=${o.join(',')} pid=${pid1}`);
  const md5Base = winMd5(MAIN);

  console.log('--- C2 restart ---');
  t = await op(`${REL}/project/${PID_PROJ}/restart`, 'restart');
  rec('C2 restart task success', t?.success === true, t?.message || '');
  let pid2 = winPid(); p = await proj(); o = owners();
  rec('C2 new pid != old pid', !!pid2 && pid2 !== pid1, `old=${pid1} new=${pid2}`);
  rec('C2 port owner == new pid', o.length === 1 && o[0] === pid2, `owners=${o.join(',')}`);
  rec('C2 db pid updated', String(p?.pid || '') === pid2, `db=${p?.pid}`);

  console.log('--- C3 backup ---');
  t = await op(`${REL}/project/${PID_PROJ}/backup`, 'backup');
  rec('C3 backup task success', t?.success === true, t?.message || '');
  rec('C3 backup listed', ((await api(`${REL}/project/${PID_PROJ}/backups`)).data || []).length > 0, '');

  console.log('--- C4 good upgrade ---');
  const goodPath = pickGoodPackage(winMd5(MAIN));
  t = await uploadUpgrade(goodPath, path.basename(goodPath), 'upgrade-good');
  rec('C4 good upgrade success', t?.success === true, t?.message || '');
  const md5Good = winMd5(MAIN);
  let pid3 = winPid(); p = await proj(); o = owners();
  rec('C4 main replaced (md5 changed)', md5Good !== md5Base && /^[0-9A-F]{32}$/.test(md5Good), `${md5Base} -> ${md5Good}`);
  rec('C4 port owner == app.pid', o.length === 1 && o[0] === pid3, `owners=${o.join(',')} pid=${pid3}`);
  rec('C4 db pid updated', String(p?.pid || '') === pid3, `db=${p?.pid}`);

  console.log('--- C5 bad upgrade -> auto rollback ---');
  t = await uploadUpgrade(path.join(FIX, 'broken-v2.jar'), 'broken-v2.jar', 'upgrade-bad');
  rec('C5 bad upgrade reported FAILED', t?.success === false, `success=${t?.success}`);
  const logs = t?.logs || [];
  rec('C5 logs: health failed', logs.some((l) => /health failed/.test(l)), '');
  rec('C5 logs: rolled back to previous version', logs.some((l) => /rolled back to previous version/.test(l)), '');
  rec('C5 logs: rollback ok', logs.some((l) => /rollback ok/.test(l)), '');
  rec('C5 rollback: main restored', winMd5(MAIN) === md5Good, '');
  let pid4 = winPid(); p = await proj(); o = owners();
  rec('C5 rollback: port owner == app.pid', o.length === 1 && o[0] === pid4, `owners=${o.join(',')} pid=${pid4}`);
  rec('C5 rollback: DB pid == app.pid [BUGFIX]', !!pid4 && String(p?.pid || '') === pid4, `db=${p?.pid} file=${pid4}`);
  rec('C5 rollback: DB status running', p?.status === 'running', `status=${p?.status}`);

  console.log('--- C6 stop ---');
  t = await op(`${REL}/project/${PID_PROJ}/stop`, 'stop');
  rec('C6 stop task success', t?.success === true, t?.message || '');
  o = owners(); p = await proj();
  rec('C6 port free', o.length === 0, `owners=${o.join(',')}`);
  rec('C6 app.pid removed', win(`if exist "${PIDFILE}" (echo yes) else (echo no)`).trim() === 'no', '');
  rec('C6 db pid cleared', !p?.pid, `pid=${p?.pid} status=${p?.status}`);

  console.log('--- C7 temp cleanup ---');
  // 缺陷 11 回归：后台任务落地脚本（ms-bg-*.ps1/.cmd/.out/.err/.done）必须被清掉。
  // 走 cmd（win() 的远端默认 shell），不用 PowerShell 语法。
  // 不能再用 `dir | find /c /v ""`：无匹配时 find 退出码为 1，execFileSync 会直接抛错，
  // 于是「清理得最干净」的情形反而把套件打断。改用 PowerShell 计数，永远返回 0。
  const leftovers = win('powershell -NoProfile -Command "(Get-ChildItem C:\\Windows\\Temp\\ms-bg-* -File -ErrorAction SilentlyContinue | Measure-Object).Count"').trim();
  rec('C7 no ms-bg temp leftovers [BUGFIX]', leftovers === '0', `left=${leftovers}`);

  const fails = results.filter((r) => !r.p);
  console.log(`\n=== WINDOWS E2E SUMMARY pass=${results.length - fails.length} fail=${fails.length} ===`);
  fails.forEach((f) => console.log('  FAIL:', f.n));
  writeFileSync(path.join(process.cwd(), '.tmp', 'win-e2e-result.json'), JSON.stringify({ results }, null, 2));
  process.exit(fails.length ? 2 : 0);
})().catch((e) => { console.error(e); process.exit(1); });