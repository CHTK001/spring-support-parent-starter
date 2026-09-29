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
        ps.append("robocopy '.' $dest /E /NFL /NDL /NJH /NJS /NP /XD backups /XF app.pid nginx.pid | Out-Null\n");
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
        // /E 覆盖恢复；/XD backups 排除备份目录（用 /MIR 会尝试删除正在读取的源目录而自引用失败）
        ps.append("robocopy $latest.FullName '.' /E /XD backups /XF app.pid nginx.pid /NFL /NDL /NJH /NJS /NP | Out-Null\n");
        ps.append("if ($LASTEXITCODE -gt 7) { throw 'robocopy rollback failed' }\n");
        ps.append("Write-Output 'rollback done'");
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
    public String readPid() {
        return "if (Test-Path 'app.pid') { Get-Content 'app.pid' }";
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
        var restartCmd = restart && restartScript != null && !restartScript.isBlank()
                ? wrap(projectPath, restartScript)
                : (wrap(projectPath, stopScript) + "\n" + wrap(projectPath, startScript));
        var statusCmd = wrap(projectPath, statusScript);
        var rollbackCmd = wrap(projectPath, rollback(projectPath, null));
        var startCmd = wrap(projectPath, startScript);
        var ps = new StringBuilder();
        ps.append(restartCmd).append('\n');
        ps.append("Start-Sleep -Seconds 4\n");
        ps.append("$out = $(").append(statusCmd).append(") -join ' '\n");
        ps.append("if (\"$out\" -match 'running') { 'health ok' }\n");
        ps.append("else {\n")
                .append("  'health failed, rolling back...'\n  ")
                .append(rollbackCmd).append('\n')
                // 回滚文件后重启旧版本，再做二次健康检查
                .append("  ").append(startCmd.replace("\n", "\n  ")).append('\n')
                .append("  Start-Sleep -Seconds 4\n")
                .append("  $out2 = $(").append(statusCmd).append(") -join ' '\n")
                .append("  if (\"$out2\" -match 'running') { 'rollback ok' } else { 'rollback failed'; exit 1 }\n")
                .append("}");
        return ps.toString();
    }

    @Override
    public String tailLog(String logPath, int lines) {
        return "if (Test-Path '" + logPath + "') { Get-Content -Path '" + logPath + "' -Tail " + lines + " }";
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
