/**
 * 后台启动 monitor 后端（detached）
 * 用 node spawn 直连 java，绕开 cmd.exe 的 8191 命令行限制（classpath 32KB）
 */
import { spawn } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const WORKDIR = "D:\\ch\\project\\spring-support-parent-starter\\spring-support-monitor-starter";
const cp = readFileSync(join(WORKDIR, "classpath.txt"), "utf8").trim();

// 清理残留 java（只杀占用 8081/29181 的）
import { execSync } from "node:child_process";
try {
  const out = execSync('netstat -ano | findstr "LISTENING" | findstr ":8081 :29181"', { encoding: "utf8" });
  const pids = [...new Set(out.split(/\r?\n/).map(l => l.trim().split(/\s+/).pop()).filter(p => p && p !== "0"))];
  for (const pid of pids) {
    try { execSync(`taskkill /F /PID ${pid}`); console.log(`killed stale pid ${pid}`); } catch {}
  }
} catch { /* no stale */ }

const child = spawn("java", [
  "-cp", `target\\classes;${cp}`,
  "com.chua.starter.monitor.support.MonitorApplication",
  "--server.port=8081",
], {
  cwd: WORKDIR,
  detached: true,
  stdio: ["ignore", "ignore", "ignore"],
});

child.unref();
writeFileSync(join(WORKDIR, "backend.pid"), String(child.pid));
console.log(`backend spawned pid=${child.pid} (detached)`);
