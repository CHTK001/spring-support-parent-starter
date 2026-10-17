package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.SpiIgnore;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.spi.RowNormalizer;
import com.chua.starter.monitor.support.spi.SshConnectionCache;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.monitor.support.support.ShellSupport;
import com.chua.starter.server.support.connection.PowerShellCommand;
import com.chua.starter.server.support.entity.MsServerHost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Windows OpenSSH 采集委托（{@link SshProvider} 内部按平台选用，不单独注册 SPI）。
 *
 * <p>通过 OpenSSH 远程执行 PowerShell：概览一次脚本以 JSON 回传全部基础信息与汇总指标，
 * 避免十余次 PowerShell 启动往返；分区明细仍各用一条 {@code ConvertTo-Csv} 脚本。
 * 文件传输走同端口 SFTP。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@SpiIgnore
public class WindowsSshDelegate implements SystemInfoProvider {

    /**
     * JSON 解析器
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * CSV 引号字符
     */
    private static final String HDR = "\"";

    /**
     * 一 MB 的字节数
     */
    private static final long MB = 1024L * 1024L;

    /**
     * 一 GB 的字节数（双精度，避免整型截断）
     */
    private static final double GB = 1024.0 * 1024.0 * 1024.0;

    /**
     * 采集命令默认超时（毫秒）
     */
    private static final long EXEC_TIMEOUT_MS = 30_000L;

    /**
     * 概览合并采集脚本：一次执行回传全部基础信息与汇总指标（JSON）。
     */
    private static final String COLLECT_SCRIPT = """
            $os=Get-CimInstance Win32_OperatingSystem
            $cs=Get-CimInstance Win32_ComputerSystem
            $procs=Get-CimInstance Win32_Processor -Property Name,NumberOfLogicalProcessors
            $nac=Get-CimInstance Win32_NetworkAdapterConfiguration | Where-Object {$_.IPEnabled}
            $ldisks=Get-CimInstance Win32_LogicalDisk | Where-Object {$_.DriveType -eq 3}
            $allp=Get-CimInstance Win32_Process
            $vc=Get-CimInstance Win32_VideoController
$cpuP=0; try{ $cpuP=[math]::Round((Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor -Filter "Name='_Total'").PercentProcessorTime,1) }catch{ $cpuP=0 }
            $totalMemKb=[long]$os.TotalVisibleMemorySize
            $freeMemKbRaw=[long]$os.FreePhysicalMemory
            $diskTotal=0.0; $diskUsed=0.0
            foreach($d in $ldisks){ $sz=[double]$d.Size; $fr=[double]$d.FreeSpace; $diskTotal+=$sz; $diskUsed+=($sz-$fr) }
            $threadCount=[int](($allp | Measure-Object -Property ThreadCount -Sum).Sum)
            $pkg=''; if(Test-Path 'C:\\ProgramData\\chocolatey\\bin\\choco.exe'){$pkg='choco'} elseif(Get-Command winget -ErrorAction SilentlyContinue){$pkg='winget'}
            $ip=$null; $mac=$null
            if($nac){ $first=$nac | Select-Object -First 1; if($first.IPAddress){ $ip=$first.IPAddress | Where-Object {$_ -match '^\\d+\\.\\d+\\.\\d+\\.\\d+$'} | Select-Object -First 1 }; $mac=$first.MACAddress }
            $uptime=[int]((Get-Date)-$os.LastBootUpTime).TotalSeconds
            $netAdapters=@(Get-CimInstance Win32_PerfFormattedData_Tcpip_NetworkInterface)
            $netRx=0.0; $netTx=0.0; $netBw=0.0
            foreach($ni in $netAdapters){ if($ni.Name -match 'isatap|loopback|teredo|6to4'){continue}; $netRx+=[double]$ni.BytesReceivedPersec; $netTx+=[double]$ni.BytesSentPersec; if([double]$ni.CurrentBandwidth -gt $netBw){$netBw=[double]$ni.CurrentBandwidth} }
            $netRxKb=[math]::Round($netRx/1KB,1)
            $netTxKb=[math]::Round($netTx/1KB,1)
            $netPct=0; if($netBw -gt 0){ $netPct=[math]::Min(100,[math]::Round((($netRx+$netTx)*8)/$netBw*100,1)) }
            $pub=''; try { $pub=[string](Invoke-RestMethod -Uri 'https://ifconfig.me/ip' -TimeoutSec 4 -ErrorAction Stop) } catch { $pub='' }
            $code=[string](Get-ItemProperty -LiteralPath 'HKLM:\\SOFTWARE\\Microsoft\\Cryptography' -ErrorAction SilentlyContinue).MachineGuid
            $o=[ordered]@{
              hostname=$env:COMPUTERNAME
              osVersion=$os.Caption
              arch=$env:PROCESSOR_ARCHITECTURE
              cpuCores=($procs | Measure-Object -Property NumberOfLogicalProcessors -Sum).Sum
              cpuName=($procs | Select-Object -First 1).Name
              memoryMb=[math]::Round($totalMemKb/1024)
              totalMemKb=$totalMemKb
              freeMemKb=$freeMemKbRaw
              cpuPercent=$cpuP
              privateIp=$ip
              mac=$mac
              gpu=($vc.Name -join ', ')
              packageManager=$pkg
              uptimeSec=$uptime
              diskTotalGb=[math]::Round($diskTotal/1GB,2)
              diskUsedGb=[math]::Round($diskUsed/1GB,2)
              processCount=@($allp).Count
              threadCount=$threadCount
              netRxKb=$netRxKb
              netTxKb=$netTxKb
              netPercent=$netPct
              publicIp=$pub
              hostCode=$code
            }
            ConvertTo-Json -InputObject $o -Compress
            """;

    /**
     * SSH 连接缓存，首次使用时从 Spring 容器解析
     */
    private volatile SshConnectionCache resolvedCache;

    /**
     * 懒取 SSH 连接缓存。
     *
     * @return SSH 连接缓存
     */
    private SshConnectionCache sshCache() {
        SshConnectionCache cache = resolvedCache;
        if (cache == null) {
            cache = SpringBeanUtils.getBean(SshConnectionCache.class);
            resolvedCache = cache;
        }
        return cache;
    }

    /**
     * 一次远端执行的结果三元组。
     *
     * @param exitCode 远端进程退出码
     * @param stdout   标准输出（已 trim）
     * @param stderr   标准错误（已 trim）
     */
    private record PsResult(int exitCode, String stdout, String stderr) {
    }

    /**
     * 通过 OpenSSH 执行 PowerShell 脚本并取回标准输出。
     *
     * <p>退出码非 0 必须抛出：只取 stdout 会把「脚本失败、什么都没打印」解析成成功的空列表。</p>
     *
     * @param host 目标主机
     * @param cmd  PowerShell 脚本
     * @return 输出文本（已 trim）；命令本身成功但无输出时为空串
     */
    private String ps(MsServerHost host, String cmd) {
        PsResult r = execute(host, cmd);
        if (r.exitCode() != 0) {
            throw new IllegalStateException("主机 " + host.getId() + " PowerShell 退出码 " + r.exitCode()
                    + (r.stderr().isBlank() ? "" : ": " + ShellSupport.readable(r.stderr())));
        }
        return r.stdout();
    }

    /**
     * 执行 PowerShell 脚本，保留退出码与标准错误。
     *
     * <p>前言与 EncodedCommand 包装统一出自 {@link PowerShellCommand}，本类只负责按 cmd 行长上限分流。</p>
     *
     * @param host 目标主机
     * @param cmd  PowerShell 脚本
     * @return 执行结果三元组
     */
    private PsResult execute(MsServerHost host, String cmd) {
        String encCommand = PowerShellCommand.encoded(cmd);
        // Windows cmd 命令行上限约 8191 字符；超长脚本改走 SFTP 上传文件再读取执行
        if (encCommand.length() <= 8000) {
            return runCommand(host, encCommand);
        }
        return execViaFile(host, cmd == null ? "" : cmd);
    }

    /**
     * 执行一条已组装好的 PowerShell 命令行（记录退出码与错误用于诊断）。
     */
    private PsResult runCommand(MsServerHost host, String command) {
        try {
            com.chua.ssh.support.client.SshClient.ExecResult r = sshCache().execResult(host, command);
            String out = r.stdout() != null ? r.stdout().trim() : "";
            String err = r.stderr() != null ? r.stderr().trim() : "";
            if (r.exitCode() != 0 || log.isDebugEnabled()) {
                log.debug("PowerShell host={} exit={} out_len={} stderr=[{}]", host.getId(), r.exitCode(), out.length(), err);
            }
            return new PsResult(r.exitCode(), out, err);
        } catch (Exception e) {
            log.warn("PowerShell 执行失败 host={}: {}", host.getId(), e.toString());
            throw new IllegalStateException("主机 " + host.getId() + " PowerShell 执行失败: " + e, e);
        }
    }

    /**
     * 超长脚本：以无 BOM 的 UTF-8 写入临时文件，经 SFTP 上传，再用一条短 EncodedCommand
     * 显式以 UTF-8 读入并 Invoke-Expression，规避 cmd 长度上限与 -File 的编码/版本差异。
     */
    private PsResult execViaFile(MsServerHost host, String body) {
        String uuid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String remote = "C:/Windows/Temp/ms-run-" + uuid + ".ps1";
        String fileArg = "C:\\Windows\\Temp\\ms-run-" + uuid + ".ps1";
        java.nio.file.Path local = null;
        try {
            local = Files.createTempFile("ms-run-", ".ps1");
            Files.write(local, body.getBytes(StandardCharsets.UTF_8));
            com.chua.ssh.support.client.SftpClient sftp = sshCache().getSftp(host);
            sftp.upload().local(local.toString()).remote(remote).exec();
            String runner = "$__c = Get-Content -Raw -LiteralPath '" + fileArg + "' -Encoding UTF8\r\n"
                    + "try { Invoke-Expression $__c } catch {\r\n"
                    + "  '" + ShellSupport.FAIL_MARK + ":脚本执行异常'\r\n"
                    + "  $_.Exception.Message\r\n"
                    + "  $_.InvocationInfo.PositionMessage\r\n"
                    + "}\r\n";
            return runCommand(host, PowerShellCommand.encoded(runner));
        } catch (Exception e) {
            log.warn("PowerShell 文件方式执行失败 host={}: {}", host.getId(), e.toString());
            throw new IllegalStateException("主机 " + host.getId() + " PowerShell 脚本下发失败: " + e, e);
        } finally {
            try {
                sshCache().getSftp(host).rm().path(remote).exec();
            } catch (Exception ignored) {
            }
            if (local != null) {
                try {
                    Files.deleteIfExists(local);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private List<String> csvSplit(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQ = !inQ;
                continue;
            }
            if (c == ',' && !inQ) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    @Override
    public boolean supports(MsServerHost host) {
        return "WINDOWS".equalsIgnoreCase(host == null ? null : host.getServerHostOsType());
    }

    @Override
    public HostInfoVO hostInfo(MsServerHost host) {
        HostInfoVO info = new HostInfoVO();
        info.setOs("WINDOWS");
        info.setOsName("Windows");
        String json = ps(host, COLLECT_SCRIPT);
        if (json == null || json.isBlank()) {
            return info;
        }
        try {
            JsonNode n = MAPPER.readTree(json);
            info.setHostname(text(n, "hostname"));
            info.setOsVersion(text(n, "osVersion"));
            info.setArch(text(n, "arch"));
            info.setCpuCores(intOrNull(n, "cpuCores"));
            info.setCpuName(text(n, "cpuName"));
            info.setMemoryMb(longOrNull(n, "memoryMb"));
            info.setPrivateIp(text(n, "privateIp"));
            info.setMac(text(n, "mac"));
            info.setGpu(text(n, "gpu"));
            info.setCpuPercent(doubleOrNull(n, "cpuPercent"));
            info.setPackageManager(text(n, "packageManager"));
            info.setUptimeSec(longOrNull(n, "uptimeSec"));
            info.setDiskTotalGb(doubleOrNull(n, "diskTotalGb"));
            info.setDiskUsedGb(doubleOrNull(n, "diskUsedGb"));
            info.setProcessCount(intOrNull(n, "processCount"));
            info.setThreadCount(intOrNull(n, "threadCount"));
            info.setNetworkRxKb(doubleOrNull(n, "netRxKb"));
            info.setNetworkTxKb(doubleOrNull(n, "netTxKb"));
            info.setNetworkPercent(doubleOrNull(n, "netPercent"));
            String pub = text(n, "publicIp");
            if (pub != null && !pub.isBlank()) {
                info.setPublicIp(pub.trim());
            }
            String code = text(n, "hostCode");
            if (code != null && !code.isBlank()) {
                info.setHostCode(code.trim());
            }

            Long totalKb = longOrNull(n, "totalMemKb");
            Long freeKb = longOrNull(n, "freeMemKb");
            if (totalKb != null && freeKb != null && totalKb >= freeKb) {
                long totalMb = totalKb / 1024;
                long usedMb = (totalKb - freeKb) / 1024;
                info.setMemoryMb(totalMb);
                info.setMemoryTotalMb(totalMb);
                info.setMemoryUsedMb(usedMb);
                info.setMemoryUsed(usedMb);
                info.setMemoryUsage(Math.round((totalKb - freeKb) * 1000.0 / totalKb) / 10.0);
            }
        } catch (Exception e) {
            log.warn("解析 Windows 采集 JSON 失败 host={}: {}", host.getId(), e.getMessage());
        }
        return info;
    }

    @Override
    public List<ProcessRowVO> processes(MsServerHost host, int page, int sz) {
        String out = ps(host, WindowsProfileScripts.PROCESSES);
        Map<String, String> owners = processOwners(host);
        List<ProcessRowVO> list = new ArrayList<>();
        List<Double> cpuTimes = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "ProcessId") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 10) {
                continue;
            }
            String pid = p.get(0).trim();
            long memory;
            try {
                memory = Long.parseLong(p.get(2).trim()) / MB;
            } catch (NumberFormatException e) {
                memory = 0;
            }
            list.add(new ProcessRowVO(
                    toLong(pid),
                    toLong(p.get(1)),
                    p.get(5).trim(),
                    owners.getOrDefault(pid, ""),
                    0,
                    memory,
                    toLong(p.get(7)),
                    p.get(9).trim(),
                    p.get(6).trim(),
                    p.get(8).trim()));
            cpuTimes.add(time(p.get(3)) + time(p.get(4)));
        }
        double sum = cpuTimes.stream().mapToDouble(Double::doubleValue).sum();
        if (sum > 0) {
            for (int i = 0; i < list.size(); i++) {
                list.set(i, list.get(i).withCpu(Math.round(cpuTimes.get(i) / sum * 1000) / 10.0));
            }
        }
        list.sort((a, b) -> Double.compare(b.cpu(), a.cpu()));
        return paginate(list, page, sz);
    }

    /**
     * 采集进程属主映射（pid → 域\账号），与 WinRM 通道共用一条脚本。
     *
     * @param host 目标主机
     * @return pid 到属主的映射，永不为 null
     */
    private Map<String, String> processOwners(MsServerHost host) {
        Map<String, String> owners = new HashMap<>();
        String out = ps(host, WindowsProfileScripts.PROCESS_OWNERS);
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "Id") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() >= 2) {
                owners.put(p.get(0).trim(), p.get(1).trim());
            }
        }
        return owners;
    }

    @Override
    public List<ServiceRowVO> services(MsServerHost host, int page, int sz) {
        String out = ps(host, "Get-CimInstance -ClassName Win32_Service | Select Name,DisplayName,State,"
                + "StartMode,ProcessId | ConvertTo-Csv -NoTypeInformation");
        List<ServiceRowVO> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "Name") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 5) {
                continue;
            }
            ServiceRowVO vo = new ServiceRowVO(
                    p.get(0).trim(),
                    p.get(1).trim(),
                    p.get(2).trim().toUpperCase(Locale.ROOT),
                    p.get(3).trim(),
                    toLong(p.get(4)));
            list.add(vo);
        }
        return paginate(list, page, sz);
    }

    @Override
    public List<DiskRowVO> disks(MsServerHost host) {
        String out = ps(host, "Get-CimInstance Win32_LogicalDisk | Where-Object {$_.DriveType -eq 3} "
                + "| Select DeviceID,Size,FreeSpace,VolumeName | ConvertTo-Csv -NoTypeInformation");
        List<DiskRowVO> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "DeviceID") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 4) {
                continue;
            }
            long size = toLong(p.get(1));
            long free = toLong(p.get(2));
            String volume = p.get(3).trim();
            DiskRowVO vo = new DiskRowVO(
                    volume.isEmpty() ? p.get(0).trim() + "\\" : volume + " (" + p.get(0).trim() + ")",
                    p.get(0).trim(),
                    "NTFS",
                    round1(size / GB),
                    round1((size - free) / GB),
                    round1(free / GB),
                    size > 0 ? round1((size - free) * 100.0 / size) : 0);
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<NetworkInterfaceRowVO> networkInterfaces(MsServerHost host) {
        String out = ps(host, WindowsProfileScripts.NETWORK);
        List<NetworkInterfaceRowVO> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "Description") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 7) {
                continue;
            }
            NetworkInterfaceRowVO vo = new NetworkInterfaceRowVO();
            vo.setName(p.get(0).trim());
            vo.setDisplayName(p.get(0).trim());
            vo.setStatus("true".equalsIgnoreCase(p.get(6).trim()) ? "UP" : "DOWN");
            vo.setMac(p.get(1).trim());
            vo.setIpv4(p.get(2).trim());
            vo.setIpv6(p.get(3).trim());
            vo.setMtu(toLong(p.get(4)));
            vo.setSpeed(RowNormalizer.speedText(toLong(p.get(5))));
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<NetProcessRowVO> netProcesses(MsServerHost host) {
        java.util.Map<String, String> names = new java.util.HashMap<>();
        String procOut = ps(host, "Get-Process | Select-Object Id,ProcessName | ConvertTo-Csv -NoTypeInformation");
        for (String line : procOut.split("\\r?\\n")) {
            if (line.isBlank() || line.startsWith(HDR + "Id")) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() >= 2) {
                names.put(p.get(0).trim(), p.get(1).trim());
            }
        }
        String out = ps(host, WindowsProfileScripts.NETSTAT);
        return WindowsNetstatRows.parse(out, names);
    }

    @Override
    public List<FileEntryVO> files(MsServerHost host, String path) {
        String q = "'" + path.replace("'", "''") + "'";
        String out = ps(host, "if (-not (Test-Path -LiteralPath " + q + " -PathType Container)) { '"
                + ShellSupport.NO_DIR_MARK + "' } else { Get-ChildItem -Force -LiteralPath " + q
                + " | Select Name,Length,LastWriteTime,Mode | ConvertTo-Csv -NoTypeInformation }");
        if (out != null && out.trim().equals(ShellSupport.NO_DIR_MARK)) {
            throw new IllegalStateException("目录不存在或不是目录: " + path);
        }
        List<FileEntryVO> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "Name") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 4) {
                continue;
            }
            list.add(new FileEntryVO(
                    p.get(0).trim(),
                    p.get(3).contains("d"),
                    toLong(p.get(1)),
                    p.get(2).trim(),
                    "",
                    "",
                    ""));
        }
        list.sort((a, b) -> {
            if (a.directory() != b.directory()) {
                return a.directory() ? -1 : 1;
            }
            return a.name().compareToIgnoreCase(b.name());
        });
        return list;
    }

    @Override
    public boolean uploadFile(MsServerHost host, String remotePath, InputStream data, long size) {
        File tmp = null;
        try {
            tmp = File.createTempFile("ms-win-upload-", ".bin");
            Files.copy(data, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            sshCache().getSftp(host).upload().local(tmp.getAbsolutePath()).remote(remotePath).exec();
            return true;
        } catch (Exception e) {
            log.error("SFTP upload failed host={} path={}", host.getId(), remotePath, e);
            // 只回 false 会让用户看到「上传失败: 路径」，真实原因留在日志无人读
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp.toPath());
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响主流程
                }
            }
        }
    }

    @Override
    public InputStream downloadFile(MsServerHost host, String remotePath) {
        File tmp = null;
        try {
            tmp = File.createTempFile("ms-win-download-", ".bin");
            sshCache().getSftp(host).download().remote(remotePath).local(tmp.getAbsolutePath()).exec();
            byte[] bytes = Files.readAllBytes(tmp.toPath());
            return new ByteArrayInputStream(bytes);
        } catch (Exception e) {
            log.error("SFTP download failed host={} path={}", host.getId(), remotePath, e);
            return null;
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp.toPath());
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响主流程
                }
            }
        }
    }

    @Override
    public String exec(MsServerHost host, String cmd) {
        PsResult r = execute(host, cmd);
        if (r.exitCode() == 0) {
            return append(r.stdout(), r.stderr());
        }
        // PowerShell 解析期错误不进 try/catch，探针行必须由通道自己补上，否则空输出会被当成成功
        return append(append(r.stdout(), ShellSupport.FAIL_MARK + ":远端退出码 " + r.exitCode()), r.stderr());
    }

    /**
     * 追加一段文本，空段跳过。
     *
     * @param base  已有文本
     * @param extra 追加文本，可为空
     * @return 拼接结果
     */
    private static String append(String base, String extra) {
        return extra.isEmpty() ? base : (base.isEmpty() ? extra : base + "\n" + extra);
    }

    @Override
    public List<EnvVarVO> env(MsServerHost host) {
        String out = ps(host, "Get-ChildItem env: | Select Name,Value | ConvertTo-Csv -NoTypeInformation");
        List<EnvVarVO> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "Name") || line.isBlank()) {
                continue;
            }
            String[] p = line.replace(HDR, "").split(",", 2);
            if (p.length < 2) {
                continue;
            }
            list.add(EnvVarVO.of(p[0], p[1]));
        }
        return list;
    }

    @Override
    public boolean mkdir(MsServerHost host, String path) {
        // -Force 自动补齐父目录且目录已存在不报错
        String q = psQuote(path);
        String out = ps(host, "New-Item -ItemType Directory -Force -Path " + q + " | Out-Null; if(Test-Path -LiteralPath " + q + "){'OK'}");
        return out != null && out.contains("OK");
    }

    @Override
    public boolean delete(MsServerHost host, String path) {
        // Remove-Item -Recurse 同时处理文件与非空目录，删完回检
        String q = psQuote(path);
        String out = ps(host, "Remove-Item -Recurse -Force -LiteralPath " + q + " -ErrorAction SilentlyContinue; if(-not (Test-Path -LiteralPath " + q + ")){'OK'}");
        return out != null && out.contains("OK");
    }

    @Override
    public boolean rename(MsServerHost host, String from, String to) {
        // Move-Item -Force 覆盖同名目标；目标父目录需已存在
        String qf = psQuote(from);
        String qt = psQuote(to);
        String out = ps(host, "Move-Item -Force -LiteralPath " + qf + " -Destination " + qt + "; if(Test-Path -LiteralPath " + qt + "){'OK'}");
        return out != null && out.contains("OK");
    }

    /**
     * 单引号包裹 PowerShell 路径参数，转义路径中的单引号。
     *
     * @param value 原始路径
     * @return 单引号安全串
     */
    private static String psQuote(String value) {
        String v = value == null ? "" : value;
        return "'" + v.replace("'", "''") + "'";
    }

    /**
     * 内存分页。
     *
     * @param all  全量行
     * @param page 页码，从 1 开始
     * @param sz   每页条数
     * @param <T>  行类型
     * @return 当前页；越界返回空列表
     */
    private static <T> List<T> paginate(List<T> all, int page, int sz) {
        int from = (page - 1) * sz;
        return from >= all.size() ? Collections.emptyList() : all.subList(from, Math.min(from + sz, all.size()));
    }

    /**
     * 宽松长整转换，非法时返回 0。
     *
     * @param text 数字字符串，可为 null
     * @return 解析结果
     */
    private static long toLong(String text) {
        Long value = toLongString(text);
        return value == null ? 0L : value;
    }

    /**
     * 宽松长整转换，保留"取不到"与"0"的区别。
     *
     * @param text 数字字符串，可为 null
     * @return 解析结果；非法时返回 null
     */
    private static Long toLongString(String text) {
        if (text == null) {
            return null;
        }
        try {
            return (long) Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取 CPU 时间列，缺失或为 0 时返回 0。
     *
     * @param text CSV 单元格
     * @return 微秒数
     */
    private static double time(String text) {
        if (text == null || text.isBlank()) {
            return 0d;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    /**
     * 保留一位小数。
     *
     * @param value 原值
     * @return 一位小数的值
     */
    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    // ---- JSON 字段取值辅助 ----

    /**
     * 取 JSON 文本字段。
     *
     * @param n   JSON 节点
     * @param key 字段名
     * @return 文本；缺失或 null 返回 null
     */
    private static String text(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asText();
    }

    /**
     * 取 JSON 整数字段。
     *
     * @param n   JSON 节点
     * @param key 字段名
     * @return 整数；缺失返回 null
     */
    private static Integer intOrNull(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asInt();
    }

    /**
     * 取 JSON 长整字段。
     *
     * @param n   JSON 节点
     * @param key 字段名
     * @return 长整；缺失返回 null
     */
    private static Long longOrNull(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asLong();
    }

    /**
     * 取 JSON 浮点字段。
     *
     * @param n   JSON 节点
     * @param key 字段名
     * @return 浮点；缺失返回 null
     */
    private static Double doubleOrNull(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return v == null || v.isNull() ? null : v.asDouble();
    }
}
