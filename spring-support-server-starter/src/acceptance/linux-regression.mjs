/**
 * Linux 发布回归验收（经线上平台 8112 驱动 Linux 目标主机）。
 *
 * 覆盖：启动 / 停止（含库 PID 清空）/ 坏包升级自动回滚（含回滚后库 PID 落库）。
 * 期望 13/13 全绿（见 RELEASE-ACCEPTANCE-REPORT.md §9.3）。
 *
 * 环境变量（均有默认值）：RELEASE_BASE, PLATFORM_SSH_HOST/USER/PW/HOSTKEY,
 *   LINUX_PROJECT_PATH（用于定位项目）, LINUX_PORT（默认 18091）
 */
import { execFileSync } from 'node:child_process';

const BASE = process.env.RELEASE_BASE || 'http://192.168.50.198:8112';
const REL = '/ms/server/release';
const P_HOST = process.env.PLATFORM_SSH_HOST || '192.168.50.198';
const P_USER = process.env.PLATFORM_SSH_USER || 'kali';
const P_PW = process.env.PLATFORM_SSH_PW || 'kali';
const HK = process.env.PLATFORM_HOSTKEY || 'ssh-ed25519 255 SHA256:cwfyC8w0qteYq8lXud9P9U2aSS8O/bxQOK6Y60BOmsI';
const ACC = process.env.LINUX_PROJECT_PATH || '/home/kali/ms-accept/acc-port';
const PORT = Number(process.env.LINUX_PORT || 18091);
let TOKEN = '';

const plink = (cmd) => execFileSync('C:\\Program Files\\PuTTY\\plink.exe',
  ['-ssh', '-batch', '-l', P_USER, '-pw', P_PW, '-hostkey', HK, P_HOST, cmd],
  { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 }).trim();

async function login() {
  const r = await fetch(BASE + '/v2/user/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123', codeKey: '', securityCode: '' }) });
  TOKEN = (await r.json())?.data?.accessToken || '';
}
async function api(p, method = 'GET', body) {
  const res = await fetch(BASE + p, { method, headers: { 'Content-Type': 'application/json', 'x-oauth-token': TOKEN },
    body: body === undefined ? undefined : JSON.stringify(body) });
  const t = await res.text();
  try { return JSON.parse(t); } catch { return { raw: t }; }
}
async function poll(taskId, label) {
  const deadline = Date.now() + 420000;
  while (Date.now() < deadline) {
    const d = (await api(`${REL}/task/${taskId}`)).data;
    if (d?.finished) { console.log(`  [${label}] success=${d.success} msg=${String(d.message || '').slice(0, 110)}`); return d; }
    await new Promise((r) => setTimeout(r, 1500));
  }
  console.log(`  [${label}] TIMEOUT`); return null;
}
async function op(p, label) {
  const r = await api(p, 'POST');
  if (!r?.data) { console.log(`  [${label}] no task: ${r?.msg}`); return null; }
  console.log(`  [${label}] taskId=${r.data}`);
  return poll(r.data, label);
}
const results = [];
const rec = (n, p, d) => { results.push({ n, p }); console.log(`  ${p ? 'PASS' : 'FAIL'} ${n}${d ? ' :: ' + d : ''}`); };
const linuxPid = () => { try { return plink(`cat ${ACC}/app.pid 2>/dev/null`).trim(); } catch { return ''; } };
function owners() {
  const out = plink(`ss -ltnp 2>/dev/null | grep ':${PORT} ' || true`);
  const set = new Set();
  for (const line of out.split(/\r?\n/)) { const m = line.match(/pid=(\d+)/); if (m) set.add(m[1]); }
  return [...set];
}
const md5 = (p) => { const m = plink(`md5sum ${p} 2>/dev/null`).match(/[0-9a-f]{32}/); return m ? m[0] : ''; };

(async () => {
  await login();
  const rels = (await api(`${REL}/list`)).data || [];
  let target = null;
  for (const r of rels.filter((x) => x.hostId === 1)) {
    const ps = (await api(`${REL}/${r.id}/projects`)).data || [];
    const hit = ps.find((p) => (p.projectPath || '').includes(ACC.replace('/home/kali/ms-accept/', '')));
    if (hit) { target = { rel: r, proj: hit }; break; }
  }
  if (!target) { console.log(`未找到 projectPath 含 ${ACC} 的项目`); process.exit(1); }
  const PID = target.proj.id;
  console.log(`linux project id=${PID} name=${target.proj.projectName} port=${PORT}`);

  console.log('--- L1 start ---');
  let t = await op(`${REL}/project/${PID}/start`, 'start');
  rec('L1 start success', t?.success === true, String(t?.message || '').slice(0, 110));
  let fp = linuxPid(); let o = owners();
  const pj = (await api(`${REL}/${target.rel.id}/projects`)).data.find((x) => x.id === PID);
  rec('L1 db pid == app.pid', !!fp && String(pj?.pid || '') === fp, `db=${pj?.pid} file=${fp}`);
  rec('L1 port owner == app.pid', o.length === 1 && o[0] === fp, `owners=${o.join(',')} pid=${fp}`);

  console.log('--- L2 stop ---');
  t = await op(`${REL}/project/${PID}/stop`, 'stop');
  rec('L2 stop success', t?.success === true, String(t?.message || '').slice(0, 110));
  o = owners();
  const pj2 = (await api(`${REL}/${target.rel.id}/projects`)).data.find((x) => x.id === PID);
  rec('L2 port free', o.length === 0, `owners=${o.join(',')}`);
  rec('L2 db pid cleared [BUGFIX]', !pj2?.pid, `pid=${pj2?.pid} status=${pj2?.status}`);

  console.log('--- L3 bad-jar upgrade auto rollback ---');
  await op(`${REL}/project/${PID}/start`, 'start2');
  const base = md5(`${ACC}/smoke.jar`);
  const r = await api(`${REL}/project/${PID}/upgrade/run`, 'POST',
    { packagePath: `${ACC}/uploads/port-bad-copy.jar`, autoBackup: true, autoExtract: true, autoRestart: true, autoRollback: true });
  const bad = r?.data ? await poll(r.data, 'upgrade-bad') : null;
  rec('L3 bad upgrade reported FAILED', bad?.success === false, '');
  const logs = bad?.logs || [];
  rec('L3 logs: health failed', logs.some((l) => /health failed/.test(l)), '');
  rec('L3 logs: rolled back', logs.some((l) => /rolled back to previous version/.test(l)), '');
  rec('L3 rollback restored md5', md5(`${ACC}/smoke.jar`) === base && !!base, '');
  o = owners(); fp = linuxPid();
  const pj3 = (await api(`${REL}/${target.rel.id}/projects`)).data.find((x) => x.id === PID);
  rec('L3 rollback: port owner == app.pid', o.length === 1 && o[0] === fp, `owners=${o.join(',')} pid=${fp}`);
  rec('L3 rollback: db pid == app.pid [BUGFIX]', o.length === 1 && String(pj3?.pid || '') === o[0], `db=${pj3?.pid} owner=${o[0]}`);
  rec('L3 rollback: db status running', pj3?.status === 'running', `status=${pj3?.status}`);

  await op(`${REL}/project/${PID}/stop`, 'final-stop');
  const fails = results.filter((x) => !x.p);
  console.log(`\n=== LINUX SUMMARY pass=${results.length - fails.length} fail=${fails.length} ===`);
  fails.forEach((f) => console.log('  FAIL:', f.n));
  process.exit(fails.length ? 2 : 0);
})().catch((e) => { console.error(e); process.exit(1); });