/**
 * 前端权威脚本接口验收：GET /ms/server/release/script/template 返回的脚本
 * 必须与执行期同源（含本轮关键修复、占位符已展开、四段互异）。
 * 期望 13/13 全绿（见 RELEASE-ACCEPTANCE-REPORT.md §9.3）。
 *
 * 环境变量：RELEASE_BASE（默认 http://192.168.50.198:8112）
 *   WIN_HOST_ID（默认 2，WINDOWS）、LINUX_HOST_ID（默认 1，LINUX）
 */
const BASE = process.env.RELEASE_BASE || 'http://192.168.50.198:8112';
const WIN_HOST = Number(process.env.WIN_HOST_ID || 2);
const LIN_HOST = Number(process.env.LINUX_HOST_ID || 1);

const login = await fetch(BASE + '/v2/user/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123', codeKey: '', securityCode: '' }) });
const TOKEN = (await login.json())?.data?.accessToken || '';

async function tpl(hostId, projectType, mainEntry, projectPath, logPath, appName, cmdArgs = []) {
  const q = new URLSearchParams({ hostId: String(hostId), projectType, mainEntry, projectPath, logPath, appName,
    envVars: '[]', jvmArgs: '[]', cmdArgs: JSON.stringify(cmdArgs) });
  const res = await fetch(`${BASE}/ms/server/release/script/template?${q}`, { headers: { 'x-oauth-token': TOKEN } });
  return (await res.json()).data;
}
const results = [];
const rec = (n, p, d) => { results.push({ n, p }); console.log(`  ${p ? 'PASS' : 'FAIL'} ${n}${d ? ' :: ' + d : ''}`); };

const w = await tpl(WIN_HOST, 'springboot', 'smoke.jar', 'C:\\ms-acc\\acc-sb', 'C:\\ms-acc\\acc-sb\\app.log', 'win-springboot', ['--server.port=18089']);
const ws = w?.startScript || '';
rec('WIN start 非空', ws.length > 0, `${ws.length}B`);
rec('WIN 参数拼接（不再字面量 $JvmArgs）', ws.includes("(@($JvmArgs) -join ' ')") && ws.includes("(@($AppArgs) -join ' ')") && !ws.includes("$inner = 'java $JvmArgs"));
rec('WIN 含 Get-Process 兜底', ws.includes('Get-Process -Name'));
rec('WIN 占位符已展开', !/__ENTRY__|__PATH__|__JVMARGS__|__ARGS__/.test(ws));
rec('WIN 端口参数已进 AppArgs', ws.includes("'--server.port=18089'"));
rec('WIN 停止段含孤儿回收 + -match', (w?.stopScript || '').includes('-match') && (w?.stopScript || '').includes('Get-CimInstance'));
rec('WIN 状态脚本以 app.pid 为准', (w?.statusScript || '').includes('app.pid'));
rec('WIN 四段互不相同', new Set([w.startScript, w.stopScript, w.restartScript, w.statusScript]).size === 4);

const l = await tpl(LIN_HOST, 'springboot', 'smoke.jar', '/home/kali/ms-accept/acc-port', '/home/kali/ms-accept/acc-port/app.log', 'linux-portprobe', ['--server.port=18091']);
const ls = l?.startScript || '';
rec('LIN start 非空', ls.length > 0, `${ls.length}B`);
rec('LIN 回显 running pid=（回滚后 PID 可回写）', ls.includes('running pid=$(cat app.pid)'));
rec('LIN 占位符已展开', !/__ENTRY__|__PATH__|__JVMARGS__|__ARGS__/.test(ls));
rec('LIN 停止段自安全模式', (l?.stopScript || '').includes('pgrep -f'));
rec('LIN 四段互不相同', new Set([l.startScript, l.stopScript, l.restartScript, l.statusScript]).size === 4);

const fails = results.filter((x) => !x.p);
console.log(`\n=== TEMPLATE-ENDPOINT SUMMARY pass=${results.length - fails.length} fail=${fails.length} ===`);
process.exit(fails.length ? 2 : 0);