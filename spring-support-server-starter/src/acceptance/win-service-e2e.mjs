/**
 * Windows 服务挂载真机端到端验收（经线上平台 8112 驱动目标 Windows 主机）。
 *
 * 场景：目标机「计划任务」服务不可用（Get-Service Schedule != Running），
 * 走免计划任务回退：Startup 启动项 + HKCU Run + WMI 拉起的常驻看门狗（<svc>.watchdog.pid 核验）。
 * 断言：
 *   挂载 —— Startup 启动项、HKCU Run 值、看门狗 pid 文件、看门狗进程、库 serviceName 全部落地；
 *   卸载 —— 上述产物全部清理，且 .ms 目录不残留 <svc>.autostart.ps1 / .watchdog.ps1 / .resident.ps1，
 *           看门狗进程已结束，库 serviceName 清空。
 *
 * 依赖（操作机需有 PuTTY plink 与 OpenSSH ssh）：
 *   - 能 SSH 登录平台主机（默认 kali@192.168.50.198）以执行 set-ssh 与库核对
 *   - 能 SSH 登录 Windows 目标机（默认 Administrator@192.168.50.101）以核对落地产物
 *   - 本地脚本 .tmp/set-ssh.sh（把目标主机置 ssh_enabled=1 / online_enabled=0）
 *
 * 环境变量（均有默认值）：
 *   RELEASE_BASE, PLATFORM_SSH_HOST, PLATFORM_SSH_USER, PLATFORM_SSH_PW, PLATFORM_HOSTKEY,
 *   WIN_SSH_HOST, WIN_SSH_USER, WIN_SSH_KEY, WIN_PROJECT_DIR, WIN_PROJECT_ID, WIN_PROJECT_NAME,
 *   WIN_RELEASE_ID, PLINK, SET_SSH_SCRIPT
 */
import { execFileSync } from 'node:child_process';
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
const ACC = process.env.WIN_PROJECT_DIR || 'C:\\ms-acc\\acc-sb';
const PID_PROJ = Number(process.env.WIN_PROJECT_ID || 38);
const PID_REL = Number(process.env.WIN_RELEASE_ID || 5);
const P_NAME = process.env.WIN_PROJECT_NAME || 'win-springboot';
const PLINK = process.env.PLINK || 'C:\\Program Files\\PuTTY\\plink.exe';
const SET_SSH = process.env.SET_SSH_SCRIPT || path.join(process.cwd(), '.tmp', 'set-ssh.sh');
const SVC = 'ms-' + P_NAME.replace(/[^A-Za-z0-9_.-]+/g, '-').replace(/^-|-$/g, '') + '-' + PID_PROJ;
const MS_DIR = ACC + '\\.ms';

function setSsh() {
  execFileSync(PLINK, ['-ssh', '-batch', '-l', P_USER, '-pw', P_PW, '-hostkey', HK, '-m', SET_SSH, P_HOST],
    { encoding: 'utf8' });
}
function win(cmd) {
  return execFileSync('ssh', ['-i', W_KEY, '-o', 'StrictHostKeyChecking=no', '-o', 'UserKnownHostsFile=NUL',
    '-o', 'LogLevel=ERROR', '-o', 'ConnectTimeout=15', `${W_USER}@${W_HOST}`, cmd],
    { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 }).trim();
}
/** 用 -EncodedCommand 执行 PowerShell，避免多层引号转义与换行问题。 */
function ps(script) {
  const b64 = Buffer.from(script, 'utf16le').toString('base64');
  return win(`powershell -NoProfile -EncodedCommand ${b64}`);
}
/** 解析 PowerShell 输出的 k=v|k=v 形式。 */
function kv(out) {
  const map = {};
  for (const pair of out.split('|')) {
    const i = pair.indexOf('=');
    if (i > 0) map[pair.slice(0, i).trim()] = pair.slice(i + 1).trim();
  }
  return map;
}

let TOKEN = '';
async function api(p, method = 'GET', body) {
  const res = await fetch(BASE + p, {
    method,
    headers: { 'Content-Type': 'application/json', 'x-oauth-token': TOKEN },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const t = await res.text();
  try { return JSON.parse(t); } catch { return { raw: t }; }
}
async function poll(id, label, ms = 240000) {
  const dl = Date.now() + ms;
  while (Date.now() < dl) {
    const d = (await api(`${REL}/task/${id}`)).data;
    if (d?.finished) {
      console.log(`  [${label}] success=${d.success} :: ${String(d.message || '').slice(0, 160)}`);
      return d;
    }
    await new Promise((r) => setTimeout(r, 1200));
  }
  console.log(`  [${label}] TIMEOUT`);
  return null;
}

const results = [];
const rec = (n, p, d) => {
  results.push({ n, p: !!p });
  console.log(`  ${p ? 'PASS' : 'FAIL'} ${n}${d ? ' :: ' + d : ''}`);
};
/** --dry：只做登录 + 目标机探测，不触发挂载/卸载（用于验证本脚本自身的 SSH/PS 通路）。 */
const DRY = process.argv.includes('--dry');

const startupCmd = `[Environment]::GetFolderPath('Startup')`;
const runKey = 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run';
const pidFile = `${MS_DIR}\\${SVC}.watchdog.pid`;

/** 采集挂载/卸载后目标机上的全部落地产物。 */
function probeTarget() {
  const script = `
$startup = Join-Path ([Environment]::GetFolderPath('Startup')) '${SVC}.cmd'
$run = (Get-ItemProperty -Path '${runKey}' -Name '${SVC}' -ErrorAction SilentlyContinue).'${SVC}'
if ($null -eq $run) { $run = '' }
$pidf = '${pidFile}'
$wp = ''
if (Test-Path $pidf) { $wp = (Get-Content $pidf -ErrorAction SilentlyContinue | Select-Object -First 1) }
$alive = $false
if ($wp -match '^\\d+$') { $alive = [bool](Get-Process -Id ([int]$wp) -ErrorAction SilentlyContinue) }
"startup=$([bool](Test-Path $startup))|run=$run|pid=$wp|alive=$alive|autostart=$([bool](Test-Path '${MS_DIR}\\${SVC}.autostart.ps1'))|watchdog=$([bool](Test-Path '${MS_DIR}\\${SVC}.watchdog.ps1'))|resident=$([bool](Test-Path '${MS_DIR}\\${SVC}.resident.ps1'))"
`;
  return kv(ps(script));
}

setSsh();
const login = await api('/v2/user/login', 'POST',
  { username: 'admin', password: 'admin123', codeKey: '', securityCode: '' });
TOKEN = login?.data?.accessToken || '';
if (!TOKEN) {
  console.log('FATAL 登录失败，无法继续');
  process.exit(3);
}
const projectSvc = () => api(`${REL}/${PID_REL}/projects`).then((r) => (r?.data || []).find((x) => x.id === PID_PROJ)?.serviceName);

if (DRY) {
  console.log('--- dry run: 目标机探测 ---');
  console.log(JSON.stringify(probeTarget(), null, 2));
  console.log('db serviceName =', String(await projectSvc()));
  process.exit(0);
}

console.log(`--- S4 before: 环境（服务名 ${SVC}） ---`);
rec('S0 Task Scheduler 不可用（回退场景成立）', /Stopped/i.test(ps('(Get-Service Schedule).Status')),
  ps('(Get-Service Schedule).Status'));

console.log('--- S1 install ---');
let t = await api(`${REL}/project/${PID_PROJ}/service/install`, 'POST');
rec('S1 install 任务已提交', !!t?.data, t?.msg || '');
let d = await poll(t?.data, 'install');
rec('S1 install 任务成功', d?.success === true, String(d?.message || '').slice(0, 160));
rec('S1 走回退路径（无 schtasks 报错）',
  !/network address is invalid/i.test(String(d?.message || '')), '');
let s = probeTarget();
rec('S1 Startup 启动项文件已创建', s.startup === 'True', `startup=${s.startup}`);
rec('S1 HKCU Run 值已写入', s.run.length > 0 && s.run.includes('.cmd'), `run=${s.run}`);
rec('S1 看门狗 pid 文件已落地', /^\d+$/.test(s.pid), `pid=${s.pid}`);
rec('S1 看门狗进程存活', s.alive === 'True', `alive=${s.alive}`);
rec('S1 库中 serviceName 已回写', (await projectSvc()) === SVC, String(await projectSvc()));

console.log('--- S2 uninstall ---');
t = await api(`${REL}/project/${PID_PROJ}/service/uninstall`, 'POST');
rec('S2 uninstall 任务已提交', !!t?.data, t?.msg || '');
d = await poll(t?.data, 'uninstall');
rec('S2 uninstall 任务成功', d?.success === true, String(d?.message || '').slice(0, 160));
s = probeTarget();
rec('S2 Startup 启动项已删除', s.startup === 'False', `startup=${s.startup}`);
rec('S2 HKCU Run 值已删除', s.run.length === 0, `run=${s.run}`);
rec('S2 看门狗 pid 文件已删除', s.pid.length === 0, `pid=${s.pid}`);
rec('S2 看门狗进程已结束', s.alive === 'False', `alive=${s.alive}`);
rec('S2 无残留 .autostart.ps1', s.autostart === 'False', `autostart=${s.autostart}`);
rec('S2 无残留 .watchdog.ps1', s.watchdog === 'False', `watchdog=${s.watchdog}`);
rec('S2 无残留 .resident.ps1', s.resident === 'False', `resident=${s.resident}`);
rec('S2 库中 serviceName 已清空', (await projectSvc()) == null, String(await projectSvc()));

const fails = results.filter((x) => !x.p);
console.log(`\n=== WINDOWS SERVICE-MOUNT E2E SUMMARY pass=${results.length - fails.length} fail=${fails.length} ===`);
fails.forEach((f) => console.log('  FAIL:', f.n));
process.exit(fails.length ? 2 : 0);
