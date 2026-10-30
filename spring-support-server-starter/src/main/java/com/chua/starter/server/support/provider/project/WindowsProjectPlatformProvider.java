package com.chua.starter.server.support.provider.project;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.server.support.spi.ProjectPlatformProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Windows 平台发布命令提供者。
 *
 * <p>覆盖 robocopy 备份/回滚、PowerShell 执行包装（PS 4.0 不支持 {@code &&}）、
 * {@code app.pid} 读取、{@code $env:}/{@code $JvmArgs} 变量块、SpringBoot 外部配置写入、
 * PowerShell 单引号转义规则（单引号翻倍）。</p>
 *
 * <p>robocopy 为 Win2012R2 自带（PS 4.0 无 {@code Compress-Archive}），返回码 0-7 均视为成功。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(value = "windows", order = 100)
public class WindowsProjectPlatformProvider implements ProjectPlatformProvider {

    /**
     * 健康检查「就绪等待」上限（秒）：轮询状态脚本直到报告运行中。
     *
     * <p>替代原先固定的 {@code Start-Sleep -Seconds 4}——4 秒对 SpringBoot 类应用过短。</p>
     */
    private static final int READY_WAIT_SECONDS = 20;

    /**
     * 健康检查「稳定窗口」（秒）：就绪后要求连续该时长仍报告运行中，
     * 用于捕捉「启动完成即退出」的崩溃循环。
     */
    private static final int SETTLE_SECONDS = 10;

    @Override
    public String osType() {
        return "WINDOWS";
    }

    @Override
    public String backup(String projectPath, int keep) {
        var ps = new StringBuilder();
        if (projectPath != null && !projectPath.isBlank()) {
            ps.append("Set-Location -LiteralPath '").append(quote(projectPath)).append("'\n");
        }
        ps.append("if (-not (Test-Path 'backups')) { New-Item -ItemType Directory -Path 'backups' | Out-Null }\n");
        // 固定长度时间戳：ToUnixTimeMilliseconds 需 .NET4.6+，Win2012R2(.NET4.5.2) 不支持
        ps.append("$stamp = Get-Date -Format 'yyyyMMddHHmmssfff'\n");
        ps.append("$dest = Join-Path 'backups' ('backup_' + $stamp)\n");
        ps.append("New-Item -ItemType Directory -Force -Path $dest | Out-Null\n");
        // /XF app.pid /XF nginx.pid：排除运行态 PID 文件，避免备份/还原把过期 PID 带回来导致状态误判
        // /R:1 /W:1：同回滚，源文件正被服务进程追加时默认重试会长时间卡住
        ps.append("robocopy '.' $dest /E /R:1 /W:1 /NFL /NDL /NJH /NJS /NP /XD backups /XF app.pid nginx.pid | Out-Null\n");
        ps.append("if ($LASTEXITCODE -gt 7) { throw 'robocopy backup failed' }\n");
        ps.append("Get-ChildItem -Directory -Path 'backups' -Filter 'backup_*' | ")
                .append("Sort-Object Name -Descending | Select-Object -Skip ").append(keep)
                .append(" | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue\n");
        ps.append("Write-Output ('backup created: ' + $dest)");
        return ps.toString();
    }

    @Override
    public String listBackups(String projectPath) {
        var ps = new StringBuilder();
        if (projectPath != null && !projectPath.isBlank()) {
            ps.append("Set-Location -LiteralPath '").append(quote(projectPath)).append("'\n");
        }
        ps.append("if (Test-Path 'backups') {\n");
        ps.append("  Get-ChildItem 'backups' -Filter 'backup_*' | ForEach-Object {\n");
        ps.append("    $kind = 'file'; $size = $_.Length\n");
        ps.append("    if ($_.PSIsContainer) { $kind = 'dir'; $size = '' }\n");
        ps.append("    $epoch = [int][double]::Parse((Get-Date $_.LastWriteTime -UFormat %s))\n");
        ps.append("    $_.Name + '|' + $epoch + '|' + $size + '|' + $kind\n");
        ps.append("  }\n");
        ps.append("}");
        return ps.toString();
    }

    @Override
    public String rollback(String projectPath, String backupName) {
        var ps = new StringBuilder();
        if (projectPath != null && !projectPath.isBlank()) {
            ps.append("Set-Location -LiteralPath '").append(quote(projectPath)).append("'\n");
        }
        if (backupName != null && !backupName.isBlank()) {
            ps.append("$latest = Get-Item -LiteralPath (Join-Path 'backups' '").append(quote(backupName)).append("')\n");
        } else {
            ps.append("$latest = Get-ChildItem -Directory -Path 'backups' -Filter 'backup_*' | ")
                    .append("Sort-Object Name -Descending | Select-Object -First 1\n");
        }
        ps.append("if (-not $latest) { throw 'no backup found' }\n");
        // robocopy 只负责目录结构与新增文件（长路径处理比 .NET 更稳），内容以随后的强制覆盖为准。
        // 因此不因它的返回码中断：服务运行中时 logs/access.log 等正被追加的文件必然拷贝失败，
        // 按默认重试会长时间卡死（实测 90s 不返回），收敛为 /R:1 /W:1 后又会返回失败码 (>7)，
        // 若据此中断就会出现「日志没拷到 → 整个回滚报失败」的假失败。
        // /XD backups 排除备份目录（用 /MIR 会尝试删除正在读取的源目录而自引用失败）
        ps.append("robocopy $latest.FullName '.' /E /XD backups /XF app.pid nginx.pid /R:1 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null\n");
        ps.append("$roboRc = $LASTEXITCODE\n");
        ps.append("$src = $latest.FullName\n");
        ps.append("$skipped = New-Object System.Collections.ArrayList\n");
        ps.append("Get-ChildItem -LiteralPath $src -Recurse -File | ForEach-Object {\n");
        ps.append("  $rel = $_.FullName.Substring($src.Length).Trim([char[]]'\\/')\n");
        ps.append("  if ($rel -eq 'app.pid' -or $rel -like '*\\nginx.pid') { return }\n");
        ps.append("  $dst = Join-Path '.' $rel\n");
        ps.append("  $dir = Split-Path $dst -Parent\n");
        ps.append("  if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }\n");
        ps.append("  try { Copy-Item -LiteralPath $_.FullName -Destination $dst -Force -ErrorAction Stop }\n");
        // 非日志文件被占用必须抛出：宁可回滚报错，也不能报成功后内容没回退（robocopy 的假成功根源）
        ps.append("  catch { if ($rel -like '*\\logs\\*' -or $rel -like '*.log') { [void]$skipped.Add($rel) } else { throw } }\n");
        ps.append("}\n");
        // 回滚不是「覆盖」而是「回到备份时刻」：升级新增的文件备份里没有，只拷回会让它们
        // 原样留下（实测：升级前没有 app.jar，回滚后新 jar 仍在，等于没回滚）。这里按备份
        // 清单反删「当前有、备份没有」的文件；backups 目录自身必须排除，否则历史备份全没。
        // 必须放在 $skipped 汇总之外执行：没有任何被占用的日志文件时也要清理新增文件。
        ps.append("$keep = New-Object 'System.Collections.Generic.HashSet[string]'\n");
        ps.append("Get-ChildItem -LiteralPath $src -Recurse -File | ForEach-Object {\n");
        ps.append("  if ($_.Name -eq 'app.pid' -or $_.Name -eq 'nginx.pid') { return }\n");
        ps.append("  [void]$keep.Add($_.FullName.Substring($src.Length).Trim([char[]]'\\/'))\n");
        ps.append("}\n");
        ps.append("$here = (Get-Location).Path\n");
        ps.append("Get-ChildItem -LiteralPath '.' -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {\n");
        ps.append("  $rel = $_.FullName.Substring($here.Length).Trim([char[]]'\\/')\n");
        ps.append("  if ($rel -eq 'app.pid' -or $_.Name -eq 'nginx.pid') { return }\n");
        ps.append("  if ($rel -like 'backups\\*') { return }\n");
        ps.append("  if (-not $keep.Contains($rel)) { Remove-Item -LiteralPath $_.FullName -Force -ErrorAction SilentlyContinue }\n");
        ps.append("}\n");
        ps.append("if ($skipped.Count -gt 0) {\n");
        ps.append("  Write-Output ('rollback done, skipped locked log file(s): ' + ($skipped -join ', '))\n");
        ps.append("} else {\n");
        ps.append("  Write-Output ('rollback done (robocopy rc=' + $roboRc + ')')\n");
        ps.append("}\n");
        return ps.toString();
    }

    @Override
    public String wrap(String projectPath, String script) {
        if (projectPath == null || projectPath.isBlank()) {
            return script;
        }
        var p = quote(projectPath);
        return "if (-not (Test-Path -LiteralPath '" + p + "')) { New-Item -ItemType Directory -Force -Path '"
                + p + "' | Out-Null }\nSet-Location -LiteralPath '" + p + "'\n" + script;
    }

    @Override
    public String ensureDir(String dir) {
        return "New-Item -ItemType Directory -Force -Path '" + quote(dir) + "' | Out-Null";
    }

    @Override
    public String readPid() {
        return "if (Test-Path 'app.pid') { Get-Content 'app.pid' }";
    }

    @Override
    public String readPid(String pidFile) {
        String file = pidFile == null || pidFile.isBlank() ? "app.pid" : pidFile.trim();
        return "if (Test-Path '" + quote(file) + "') { Get-Content '" + quote(file) + "' }";
    }

    @Override
    public String varsBlock(List<String> envNames, List<String> envValues, List<String> jvmArgs, List<String> cmdArgs) {
        var b = new StringBuilder();
        for (var i = 0; i < envNames.size(); i++) {
            var k = envNames.get(i);
            if (k != null && !k.isBlank()) {
                var v = i < envValues.size() && envValues.get(i) != null ? envValues.get(i) : "";
                b.append("$env:").append(k.trim()).append(" = '").append(quote(v)).append("'\n");
            }
        }
        b.append("$JvmArgs = @(").append(csv(jvmArgs)).append(")\n");
        b.append("$AppArgs = @(").append(csv(cmdArgs)).append(")\n");
        return b.toString();
    }

    @Override
    public String jvmArgsLiteral(List<String> jvmArgs) {
        return "$JvmArgs";
    }

    @Override
    public String appArgsLiteral(List<String> cmdArgs) {
        return "$AppArgs";
    }

    @Override
    public String envPattern() {
        return "^\\$env:([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*'(.*)'$";
    }

    @Override
    public String springConfigScript(List<String> keys, List<String> values) {
        var b = new StringBuilder();
        b.append("New-Item -ItemType Directory -Force -Path 'config' | Out-Null\r\n");
        // 逐行写入，避免 PowerShell 数组字面量的尾随逗号导致 "Missing expression after ','"
        b.append("[IO.File]::WriteAllLines((Get-Location).Path + '\\config\\application.properties', @(\r\n");
        List<String> lines = new ArrayList<>();
        for (var i = 0; i < keys.size(); i++) {
            var k = keys.get(i);
            if (k != null && !k.isBlank()) {
                var v = i < values.size() && values.get(i) != null ? values.get(i) : "";
                lines.add("  '" + quote(k) + "=" + quote(v) + "'");
            }
        }
        b.append(String.join(",\r\n", lines)).append("\r\n");
        b.append("))\r\n");
        return b.toString();
    }

    @Override
    public String extract(String projectPath, String packagePath) {
        var n = packagePath == null ? "" : packagePath.toLowerCase();
        var pkg = quote(packagePath == null ? "" : packagePath);
        var dst = quote(projectPath == null ? "" : projectPath);
        if (n.endsWith(".zip")) {
            // 逐项解压并覆盖同名文件：ExtractToDirectory 遇已存在文件会抛异常，无法用于重复升级
            return "Add-Type -AssemblyName System.IO.Compression.FileSystem\n"
                    + "$zip = [IO.Compression.ZipFile]::OpenRead('" + pkg + "')\n"
                    + "try {\n"
                    + "  foreach ($e in $zip.Entries) {\n"
                    + "    $dest = Join-Path '" + dst + "' $e.FullName\n"
                    + "    if ([string]::IsNullOrEmpty($e.Name)) { New-Item -ItemType Directory -Force -Path $dest | Out-Null }\n"
                    + "    else { $dir = Split-Path $dest -Parent; if ($dir) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }; [IO.Compression.ZipFileExtensions]::ExtractToFile($e, $dest, $true) }\n"
                    + "  }\n"
                    + "} finally { $zip.Dispose() }";
        }
        // tar / tar.gz / tgz / gz：Windows 10 1803+ 自带 tar.exe；旧系统无内置工具，给出明确提示
        var tarFlag = n.endsWith(".tar") ? "-xf" : "-xzf";
        return "if (-not (Get-Command tar.exe -ErrorAction SilentlyContinue)) { throw 'Windows 未内置 tar，无法解压 "
                + packagePath + "，请改用 zip 包' }\n"
                + "tar.exe " + tarFlag + " '" + pkg + "' -C '" + dst + "'";
    }

    @Override
    public String place(String projectPath, String packagePath, String mainEntry) {
        var target = mainEntry == null || mainEntry.isBlank() ? baseName(packagePath) : mainEntry;
        return "Copy-Item -LiteralPath '" + quote(packagePath) + "' -Destination '"
                + quote(projectPath) + "\\" + quote(target) + "' -Force";
    }

    @Override
    public String guardedRestart(String projectPath, String restartScript, String stopScript,
                                 String startScript, String statusScript, boolean restart) {
        return guardedRestart(projectPath, restartScript, stopScript, startScript, statusScript, restart, 0);
    }

    @Override
    public String guardedRestart(String projectPath, String restartScript, String stopScript,
                                 String startScript, String statusScript, boolean restart, int port) {
        var restartCmd = restart && restartScript != null && !restartScript.isBlank()
                ? wrap(projectPath, restartScript)
                : (wrap(projectPath, stopScript) + "\n" + wrap(projectPath, startScript));
        var statusCmd = wrap(projectPath, statusScript);
        var rollbackCmd = wrap(projectPath, rollback(projectPath, null));
        var startCmd = wrap(projectPath, startScript);
        var ps = new StringBuilder();
        ps.append(restartCmd).append('\n');
        // 与 Posix 同构的三段式健康检查（详见 PosixProjectPlatformProvider 的注释）：
        // 就绪等待 → 端口探活 → 稳定窗口，替代原先固定 Start-Sleep 4 后单次判定。
        ps.append("$up = $false\n");
        ps.append("for ($i = 0; $i -lt ").append(READY_WAIT_SECONDS).append("; $i += 2) {\n");
        ps.append("  $o = $(").append(statusCmd).append(") -join ' '\n");
        ps.append("  if ($o -match 'running') { $up = $true; break }\n");
        ps.append("  Start-Sleep -Seconds 2\n");
        ps.append("}\n");
        if (port > 0) {
            // 与 Posix 同源：必须确认监听者是 app.pid 记录的那个进程，
            // 否则无关进程/残留孤儿监听同端口会被误判成「新版本健康」。
            // 刻意定义成命名函数（与 Posix 的 listening_port 对称）：便于目标机自检脚本
            // 脱离整段健康检查单独调用它做真机验证。
            ps.append("function listening_port([int]$want) {\n");
            ps.append("  $owner = (Get-Content 'app.pid' -ErrorAction SilentlyContinue | Select-Object -First 1)\n");
            ps.append("  if (-not $owner) { return $false }\n");
            ps.append("  foreach ($ln in (netstat -ano | Select-String -Pattern (':' + $want + '\\s') | Select-String -Pattern 'LISTENING')) {\n");
            ps.append("    $f = ($ln.Line -split '\\s+') | Where-Object { $_ -ne '' }\n");
            ps.append("    if ($f[-1].Trim() -eq \"$owner\".Trim()) { return $true }\n");
            ps.append("  }\n");
            ps.append("  return $false\n");
            ps.append("}\n");
            // 调用点：就绪后轮询探活，最多 READY_WAIT_SECONDS 秒
            ps.append("if ($up) {\n");
            ps.append("  $lis = $false\n");
            ps.append("  for ($i = 0; $i -lt ").append(READY_WAIT_SECONDS).append("; $i += 2) {\n");
            ps.append("    if (listening_port ").append(port).append(") { $lis = $true; break }\n");
            ps.append("    Start-Sleep -Seconds 2\n");
            ps.append("  }\n");
            ps.append("  if (-not $lis) { 'health failed: 端口 ").append(port).append(" 未由本项目进程监听'; $up = $false }\n");
            ps.append("}\n");
        }
        ps.append("if ($up) {\n");
        ps.append("  for ($i = 0; $i -lt ").append(SETTLE_SECONDS).append("; $i += 2) {\n");
        ps.append("    Start-Sleep -Seconds 2\n");
        ps.append("    $o = $(").append(statusCmd).append(") -join ' '\n");
        ps.append("    if ($o -notmatch 'running') { 'health failed: 启动后退出'; $up = $false; break }\n");
        ps.append("  }\n");
        ps.append("}\n");
        ps.append("if ($up) { 'health ok' }\n");
        ps.append("else {\n")
                .append("  'health failed, rolling back...'\n  ");
        // 与 Posix 同源：先停掉刚起的失败实例再回滚，否则新进程会与仍存活的旧进程抢端口/文件
        if (stopScript != null && !stopScript.isBlank()) {
            ps.append(wrap(projectPath, stopScript).replace("\n", "\n  ")).append('\n');
        }
        ps.append(rollbackCmd).append('\n')
                // 回滚文件后重启旧版本，再做二次健康检查
                .append("  ").append(startCmd.replace("\n", "\n  ")).append('\n')
                .append("  Start-Sleep -Seconds ").append(SETTLE_SECONDS + 4).append('\n')
                .append("  $out2 = $(").append(statusCmd).append(") -join ' '\n")
                .append("  if (\"$out2\" -match 'running') { 'rollback ok' } else { 'rollback failed'; exit 1 }\n")
                // 与 Posix 侧同口径：回滚成功也以非 0 退出，避免升级任务被判成功、日志打「升级成功」。
                .append("  'upgrade failed, rolled back to previous version'\n")
                .append("  exit 1\n")
                .append("}");
        return ps.toString();
    }

    @Override
    public String tailLog(String logPath, int lines) {
        return "if (Test-Path '" + logPath + "' -PathType Container) { "
                + "$f = Get-ChildItem -Path '" + logPath + "' -File | Sort-Object LastWriteTime -Descending "
                + "| Select-Object -First 1; if (-not $f) { Write-Output '日志目录为空: " + logPath + "' } "
                + "else { Get-Content -Path $f.FullName -Tail " + lines + " } } "
                + "elseif (Test-Path '" + logPath + "') { Get-Content -Path '" + logPath + "' -Tail " + lines + " } "
                + "else { Write-Output '日志文件不存在: " + logPath + "' }";
    }

    @Override
    public String quote(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    /**
     * 取路径最后一段（投放时主入口为空则用包名）。
     *
     * @param path 路径
     * @return 文件名
     */
    private String baseName(String path) {
        var n = path == null ? "" : path.replace('\\', '/');
        var idx = n.lastIndexOf('/');
        return idx >= 0 ? n.substring(idx + 1) : n;
    }

    /**
     * 拼接 PowerShell 数组元素（单引号包裹、逗号分隔）。
     *
     * @param items 参数列表
     * @return 数组元素串
     */
    private String csv(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (var it : items) {
            if (it != null && !it.isBlank()) {
                parts.add("'" + quote(it.trim()) + "'");
            }
        }
        return String.join(",", parts);
    }
}
