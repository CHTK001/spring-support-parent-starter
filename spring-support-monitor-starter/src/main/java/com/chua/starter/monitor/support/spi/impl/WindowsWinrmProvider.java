// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.monitor.support.enums.ShellDialect;
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
import com.chua.winrm.support.client.WinRmExecClient;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Windows WinRM 系统信息 Provider。
 *
 * <p>通过 WinRM 远程执行 PowerShell 采集，输出以 {@code ConvertTo-Csv} 承载。所有报文在本类内部
 * 解析为 SPI 的强类型视图，命令文本与 CSV 列不会外溢到调用方。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "winrm", order = 100)
public class WindowsWinrmProvider implements SystemInfoProvider {

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
     * 内联下发上限：远端 cmd 命令行约 8191 字符，留余量（与 WindowsSshDelegate 同口径）
     */
    private static final int INLINE_LIMIT = 8000;

    /**
     * 脚本 Base64 分片长度：包装成 EncodedCommand 后按 UTF-16LE + Base64 放大约 2.7 倍
     */
    private static final int B64_CHUNK = 2400;

    /**
     * 上传分片的 Base64 载荷长度：整条命令行 = {@code PowerShellCommand} 前言约 127 字符
     * + 脚本 UTF-16LE 再 Base64（×2.67），要落在 {@link #INLINE_LIMIT} 内，脚本本体只能约 2900 字符；
     * 扣掉 {@code [IO.File]::AppendAllText('暂存路径', '…')} 的 70 字符壳，取 2700。
     * 折算下来每片仅约 2KB 文件字节 —— WinRM 只有命令行通道时的物理上限。
     */
    private static final int UPLOAD_B64_CHARS = 2700;

    /**
     * 下载分片字节数：回传走 WinRM 响应信封（默认 512KB 上限），Base64 后约 1.33 倍，取 96KB
     */
    private static final int DOWNLOAD_CHUNK_BYTES = 96 * 1024;

    /**
     * 上传体积闸门：2MB 已需上千条命令往返，再大只会长时间占住请求线程
     */
    private static final long UPLOAD_LIMIT = 2L * MB;

    /**
     * 下载体积闸门：单片 96KB，同体积下往返次数约为上传的四十分之一
     */
    private static final long DOWNLOAD_LIMIT = 8L * MB;

    /**
     * 文件不存在的探测标记：读不到字节不能当成空文件返回
     */
    private static final String MISSING_MARK = "__MS_FILE_MISSING__";

    /**
     * 主机绑定身份的凭据解析器，首次使用时从 Spring 容器解析
     */
    private volatile SshConnectionCache resolvedCache;

    /**
     * WinRM 一次执行的结果三元组。
     *
     * @param exitCode 远端进程退出码
     * @param stdout   标准输出（已 trim）
     * @param stderr   标准错误（已 trim）
     */
    private record PsResult(int exitCode, String stdout, String stderr) {
    }

    /**
     * 通过 WinRM 执行 PowerShell 并取回标准输出。
     *
     * @param host 目标主机
     * @param cmd  PowerShell 脚本
     * @return 输出文本（已 trim）；失败返回空串
     */
    private String ps(MsServerHost host, String cmd) {
        return run(host, cmd).stdout();
    }

    /**
     * 通过 WinRM 执行 PowerShell，保留退出码与标准错误。
     *
     * <p>脚本必须经 {@code -EncodedCommand}（UTF-16LE Base64）下发：WinRM 的 shell 是 cmd.exe，
     * 内联进双引号参数的脚本会在第一个换行处被截断，多行命令只执行了前半段却返回空输出、
     * 退出码仍为 0，调用方看到的是「执行成功且无输出」。{@code chcp 65001} 保证回传是 UTF-8。</p>
     *
     * <p>WinRM 同样受远端 cmd 的 8191 字符行上限约束（超限报 {@code The command line is too long.}），
     * 故长脚本改走 {@link #execViaFile} —— 与 SSH 通道的文件分支同一套语义。</p>
     *
     * @param host 目标主机
     * @param cmd  PowerShell 脚本
     * @return 执行结果三元组，永不为 null
     */
    private PsResult run(MsServerHost host, String cmd) {
        String psCmd = PowerShellCommand.encoded(cmd);
        if (psCmd.length() <= INLINE_LIMIT) {
            return session(host, psCmd);
        }
        return execViaFile(host, cmd == null ? "" : cmd);
    }

    /**
     * 一次 WinRM 往返执行已组装好的命令行（自建自收连接）。
     *
     * <p>大报文偶发 {@code 500 无法读取服务器响应}：同一连接上不会自愈，只能换一条重来。</p>
     *
     * @param host    目标主机
     * @param command 完整命令行
     * @return 执行结果三元组
     */
    private PsResult session(MsServerHost host, String command) {
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            WinRmExecClient client = client(host);
            try {
                return execute(client, command);
            } catch (IllegalStateException e) {
                last = e;
                log.warn("WinRM 第 {} 次执行失败 host={}: {}", attempt, host.getId(), e.getMessage());
            } finally {
                client.closeQuietly();
            }
        }
        throw last;
    }

    /**
     * 在同一条 WinRM 连接上按序执行多段 PowerShell 脚本。
     *
     * <p>分片传输的每条命令都重新握手会把单次往返从几十毫秒放大到亚秒级，整份文件的传输时间
     * 随之变成不可接受；单条命令遇到传输异常则换一条连接重来，不影响已成功的前序分片。</p>
     *
     * @param host    目标主机
     * @param scripts PowerShell 脚本列表，按序编码下发
     * @return 每条脚本的执行结果，顺序与入参一致
     */
    private List<PsResult> pipeline(MsServerHost host, List<String> scripts) {
        List<PsResult> done = new ArrayList<>(scripts.size());
        final WinRmExecClient[] client = {client(host)};
        try {
            for (String script : scripts) {
                String command = PowerShellCommand.encoded(script);
                for (int attempt = 1; ; attempt++) {
                    try {
                        done.add(execute(client[0], command));
                        break;
                    } catch (IllegalStateException e) {
                        if (attempt == 2) {
                            throw e;
                        }
                        log.warn("WinRM 分片执行失败，换连接重试: {}", e.getMessage());
                        client[0].closeQuietly();
                        client[0] = client(host);
                    }
                }
            }
            return done;
        } finally {
            client[0].closeQuietly();
        }
    }

    /**
     * 在既有 WinRM 连接上执行一条命令行。
     *
     * @param client  已连接的客户端
     * @param command 完整命令行
     * @return 执行结果三元组
     */
    private PsResult execute(WinRmExecClient client, String command) {
        try {
            com.chua.winrm.support.client.WinRmExecClient.ExecResult r = client.executeCommand(command, 30);
            return new PsResult(r.exitCode(), trim(r.stdout()), trim(r.stderr()));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("WinRM 命令执行失败: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }

    /**
     * 建立一条已握手的 WinRM 连接。
     *
     * @param host 目标主机
     * @return 已连接的客户端，调用方负责关闭
     */
    private WinRmExecClient client(MsServerHost host) {
        SshConnectionCache.LoginCredential cred = credential(host);
        WinRmExecClient client = WinRmExecClient.builder()
                .host(host.getServerHostAddress())
                .port(host.getServerHostPort() != null ? host.getServerHostPort() : 5985)
                .username(cred.username())
                .password(cred.password())
                .connectTimeout(10000).sessionTimeout(30000)
                .build();
        try {
            client.connect();
            return client;
        } catch (Exception e) {
            client.closeQuietly();
            throw new IllegalStateException("主机 " + host.getId() + " WinRM 连接失败: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }

    /**
     * 超长脚本：Base64 分片写入远端临时文件，再用一条短 EncodedCommand 解码并执行。
     *
     * <p>分片而非一次写完：WinRM 没有 SFTP 可绕道，单条命令也受同一个行长上限约束。
     * 所有分片与执行命令共用一条连接，逐片重连会把一次长脚本放大成几十秒的握手开销。</p>
     *
     * @param host 目标主机
     * @param body PowerShell 脚本原文
     * @return 执行结果三元组
     */
    private PsResult execViaFile(MsServerHost host, String body) {
        String staging = tempPath("ms-run");
        try {
            String b64 = java.util.Base64.getEncoder()
                    .encodeToString(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            List<String> scripts = new ArrayList<>();
            scripts.add("[IO.File]::WriteAllText('" + staging + "', '')");
            for (int i = 0; i < b64.length(); i += B64_CHUNK) {
                scripts.add("[IO.File]::AppendAllText('" + staging + "', '"
                        + b64.substring(i, Math.min(i + B64_CHUNK, b64.length())) + "')");
            }
            scripts.add("$__c = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("
                    + "[IO.File]::ReadAllText('" + staging + "')))\r\n"
                    + "[IO.File]::Delete('" + staging + "')\r\n"
                    + "try { Invoke-Expression $__c } catch {\r\n"
                    + "  '" + ShellSupport.FAIL_MARK + ":脚本执行异常'\r\n"
                    + "  $_.Exception.Message\r\n"
                    + "  $_.InvocationInfo.PositionMessage\r\n"
                    + "}\r\n");
            List<PsResult> results = pipeline(host, scripts);
            for (int i = 0; i < results.size() - 1; i++) {
                PsResult piece = results.get(i);
                if (piece.exitCode() != 0) {
                    throw new IllegalStateException("分片下发脚本失败: " + describe(piece));
                }
            }
            return results.get(results.size() - 1);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("WinRM 文件方式执行失败 host={}: {}", host.getId(), e.toString());
            throw new IllegalStateException("主机 " + host.getId() + " WinRM 脚本下发失败: " + e, e);
        } finally {
            try {
                session(host, PowerShellCommand.encoded(
                        "Remove-Item -LiteralPath '" + staging + "' -Force -ErrorAction SilentlyContinue"));
            } catch (Exception ignored) {
                // 暂存文件清理失败不影响主流程
            }
        }
    }

    /**
     * 空值安全的 trim。
     *
     * @param text 原文，可为 null
     * @return 去首尾空白后的文本，null 归一为空串
     */
    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * WinRM 登录凭据。
     *
     * <p>与 SSH 通道同源：取主机绑定身份里第一组带口令的凭据。主机实体的
     * {@code serverHostAccount/serverHostPassword} 是瞬态字段（不落库），只在新增/编辑请求的
     * 同一实例上有值，这里作为回落保留。</p>
     *
     * @param host 目标主机
     * @return 含登录名与口令的凭据
     * @throws IllegalStateException 既无绑定身份也无瞬态账号，避免用猜测账号反复试连
     */
    private SshConnectionCache.LoginCredential credential(MsServerHost host) {
        SshConnectionCache cache = resolvedCache;
        if (cache == null) {
            cache = SpringBeanUtils.getBean(SshConnectionCache.class);
            resolvedCache = cache;
        }
        if (cache != null) {
            for (SshConnectionCache.LoginCredential cred : cache.credentials(host)) {
                if (cred.password() != null && !cred.password().isBlank()) {
                    return cred;
                }
            }
        }
        String account = host.getServerHostAccount();
        String password = host.getServerHostPassword();
        if (account != null && !account.isBlank() && password != null && !password.isBlank()) {
            return new SshConnectionCache.LoginCredential(account, password, null);
        }
        throw new IllegalStateException("主机 " + host.getId() + " 无带口令的 WinRM 凭据，请先在主机资产绑定登录身份"
                + (account == null || account.isBlank() ? "" : "（WinRM 不支持公钥身份）"));
    }

    /**
     * 按 CSV 规则拆分行，支持带引号字段。
     *
     * @param line 原始行
     * @return 字段列表
     */
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
        // 保留 WinRM 实现但不做默认兜底：仅显式选择 WINRM 通道时才走 WinRM，
        // 选了 AGENT（未装）/OpenSSH/PROMETHEUS/NONE 时不再静默回退
        return "WINDOWS".equalsIgnoreCase(host == null ? null : host.getServerHostOsType())
                && "WINRM".equalsIgnoreCase(host.getServerHostCollectMode());
    }

    @Override
    public HostInfoVO hostInfo(MsServerHost host) {
        HostInfoVO info = new HostInfoVO();
        info.setHostname(ps(host, "$env:COMPUTERNAME"));
        info.setOsName("Windows");
        info.setOsVersion(ps(host, "(Get-WmiObject Win32_OperatingSystem).Caption"));
        info.setHostCode(ps(host, "(Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Cryptography').MachineGuid"));
        info.setCpuCores(toInt(ps(host, "(Get-WmiObject Win32_Processor).NumberOfLogicalProcessors")));
        info.setCpuName(ps(host, "(Get-WmiObject Win32_Processor).Name"));
        info.setMemoryMb(toLong(ps(host, "[math]::Round((Get-WmiObject Win32_ComputerSystem).TotalPhysicalMemory/1MB)")));
        info.setPrivateIp(firstText(ps(host,
                "(Get-WmiObject Win32_NetworkAdapterConfiguration | Where-Object {$_.IPEnabled}).IPAddress[0]"), ""));
        info.setMac(ps(host, "(Get-WmiObject Win32_NetworkAdapterConfiguration | Where-Object {$_.IPEnabled}).MACAddress[0]"));
        info.setGpu(ps(host, "(Get-WmiObject Win32_VideoController).Name -join ', '"));
        info.setCpuPercent(toDouble(ps(host, "(Get-WmiObject Win32_Processor).LoadPercentage")));
        Long totalKb = toLongOrNull(ps(host, "[math]::Round((Get-WmiObject Win32_ComputerSystem).TotalPhysicalMemory/1KB)"));
        Long freeKb = toLongOrNull(ps(host, "(Get-WmiObject Win32_OperatingSystem).FreePhysicalMemory"));
        if (totalKb != null && freeKb != null && totalKb >= freeKb) {
            info.setMemoryUsed((totalKb - freeKb) / 1024);
            info.setMemoryUsage(Math.round((totalKb - freeKb) * 1000.0 / totalKb) / 10.0);
        }
        info.setPackageManager("winget/choco");
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
     * 采集进程属主映射（pid → 域\账号）。
     *
     * <p>逐进程调 {@code Win32_Process.GetOwner} 实测要 6.7 秒，这里改用一条
     * {@code Get-Process -IncludeUserName}（实测 110 毫秒）；低版本 PowerShell 不认该参数时
     * 返回空映射，属主列留空而不影响其余字段。</p>
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
        String out = ps(host, "Get-WmiObject Win32_LogicalDisk | Where-Object {$_.DriveType -eq 3} "
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
        Map<String, String> names = new HashMap<>();
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
        String q = psQuote(path);
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
        try {
            byte[] bytes = data.readAllBytes();
            limitCheck(host, bytes.length, "上传", UPLOAD_LIMIT);
            String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
            String staging = tempPath("ms-up");
            List<String> commands = new ArrayList<>();
            commands.add("[IO.File]::WriteAllText('" + staging + "', '')");
            for (int at = 0; at < b64.length(); at += UPLOAD_B64_CHARS) {
                commands.add("[IO.File]::AppendAllText('" + staging + "', '"
                        + b64.substring(at, Math.min(at + UPLOAD_B64_CHARS, b64.length())) + "')");
            }
            String target = psQuote(remotePath);
            commands.add("[IO.File]::WriteAllBytes(" + target + ", [Convert]::FromBase64String("
                    + "[IO.File]::ReadAllText('" + staging + "')))");
            commands.add("[IO.File]::Delete('" + staging + "')");
            List<PsResult> results = pipeline(host, commands);
            // 逐片查退出码：只检最后一条会把「分片没写全」显示成「base64 解码失败」，真实原因被吞掉
            for (int i = 0; i < results.size() - 1; i++) {
                PsResult piece = results.get(i);
                if (piece.exitCode() != 0) {
                    throw new IllegalStateException("分片写入失败(第 " + i + " 条命令): " + describe(piece));
                }
            }
            return true;
        } catch (Exception e) {
            log.error("WinRM upload failed host={} path={}", host.getId(), remotePath, e);
            // 只回 false 会让调用方报出「上传失败: 路径」，体积闸门那句「改用 Agent 或 SSH 通道」永远没人看到
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public InputStream downloadFile(MsServerHost host, String remotePath) {
        try {
            String q = psQuote(remotePath);
            String probed = ps(host, "if (Test-Path -LiteralPath " + q + " -PathType Leaf) {"
                    + " (Get-Item -LiteralPath " + q + ").Length } else { '" + MISSING_MARK + "' }");
            if (probed == null || probed.contains(MISSING_MARK)) {
                log.warn("WinRM download skipped, no such file host={} path={}", host.getId(), remotePath);
                return null;
            }
            long length = parseLong(probed);
            if (length <= 0L) {
                return new java.io.ByteArrayInputStream(new byte[0]);
            }
            limitCheck(host, length, "下载", DOWNLOAD_LIMIT);
            List<String> scripts = new ArrayList<>();
            for (long at = 0; at < length; at += DOWNLOAD_CHUNK_BYTES) {
                scripts.add(sliceScript(q, at, (int) Math.min(DOWNLOAD_CHUNK_BYTES, length - at)));
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream((int) length);
            long got = 0L;
            for (PsResult r : pipeline(host, scripts)) {
                if (r.exitCode() != 0) {
                    throw new IllegalStateException("读取远端分片失败: " + describe(r));
                }
                byte[] piece = java.util.Base64.getDecoder().decode(r.stdout().replaceAll("\\s", ""));
                // 末片按整块长度分配，读不满时尾部是零，只能按远端真实长度截断
                int take = (int) Math.min(piece.length, length - got);
                bos.write(piece, 0, take);
                got += take;
            }
            if (got != length) {
                throw new IllegalStateException("下载不完整: 期望 " + length + " 字节，实得 " + got);
            }
            return new java.io.ByteArrayInputStream(bos.toByteArray());
        } catch (Exception e) {
            log.error("WinRM download failed host={} path={}", host.getId(), remotePath, e);
            return null;
        }
    }

    /**
     * 读取远端文件一段字节的脚本：共享读打开以避开被占用文件，读满或到文件尾为止。
     *
     * @param quoted 已转义的目标路径
     * @param offset 起始偏移
     * @param want   期望字节数
     * @return PowerShell 脚本
     */
    private static String sliceScript(String quoted, long offset, int want) {
        return "$fs=[IO.File]::Open(" + quoted + ",[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite);"
                + "if ($fs.Seek(" + offset + ",[IO.SeekOrigin]::Begin) -ne " + offset + ") { $fs.Close(); exit 2 }"
                + "$b=New-Object byte[] " + want + "; $got=0;"
                + "while ($got -lt " + want + ") { $r=$fs.Read($b,$got," + want + "-$got); if ($r -le 0) { break };"
                + " $got+=$r } $fs.Close(); [Convert]::ToBase64String($b)";
    }

    /**
     * 远端临时文件路径。
     *
     * @param prefix 文件名前缀
     * @return 正斜杠形式的绝对路径
     */
    private static String tempPath(String prefix) {
        return "C:/Windows/Temp/" + prefix + "-"
                + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ".b64";
    }

    /**
     * 单文件传输体积闸门。
     *
     * <p>WinRM 只有命令行通道，体积与往返次数成正比；不设上限会让一次上传占到分钟级的
     * 请求线程与连接，超限明确拒绝并指路到 Agent / SSH 通道。</p>
     *
     * @param host   目标主机
     * @param bytes  本次传输字节数
     * @param action 动作名，用于提示
     * @param limit  该方向的体积上限字节数
     * @throws IllegalStateException 超出上限
     */
    private static void limitCheck(MsServerHost host, long bytes, String action, long limit) {
        if (bytes > limit) {
            throw new IllegalStateException("主机 " + host.getId() + " 的 WinRM 通道不支持" + action
                    + "超过 " + (limit / MB) + "MB 的文件（实测分片往返耗时与体积成正比），"
                    + "请改用 Agent 或 SSH 通道");
        }
    }

    /**
     * 宽松取长：非数字与空串归零。
     *
     * @param text 文本
     * @return 数值
     */
    private static long parseLong(String text) {
        String digits = text == null ? "" : text.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 0L : Long.parseLong(digits);
    }

    @Override
    public String exec(MsServerHost host, String cmd) {
        PsResult r = run(host, cmd);
        if (r.exitCode() == 0) {
            return r.stderr().isEmpty() ? r.stdout() : append(r.stdout(), r.stderr());
        }
        // PowerShell 解析期错误不进 try/catch，探针行必须由通道自己补上，否则调用方看到空输出即成功
        return append(append(r.stdout(), ShellSupport.FAIL_MARK + ":WinRM 退出码 " + r.exitCode()), r.stderr());
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

    /**
     * 组装给用户看的失败说明：标准错误里的 CLIXML 外壳必须先剥掉。
     *
     * @param result 执行结果三元组
     * @return 可读的失败正文
     */
    private static String describe(PsResult result) {
        return append(result.stdout(), ShellSupport.readable(result.stderr()));
    }

    @Override
    public ShellDialect shellDialect(MsServerHost host) {
        // WinRM 走 PowerShell 管道，cmd.exe 语法在对端必然解析失败
        return ShellDialect.POWERSHELL;
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
     * 宽松整数转换。
     *
     * @param text 数字字符串，可为 null
     * @return 解析结果；非法时返回 null
     */
    private static Integer toInt(String text) {
        Long value = toLongOrNull(text);
        return value == null ? null : value.intValue();
    }

    /**
     * 宽松长整转换，非法时返回 0。
     *
     * @param text 数字字符串，可为 null
     * @return 解析结果
     */
    private static long toLong(String text) {
        Long value = toLongOrNull(text);
        return value == null ? 0L : value;
    }

    /**
     * 宽松长整转换，保留"取不到"与"0"的区别。
     *
     * @param text 数字字符串，可为 null
     * @return 解析结果；非法时返回 null
     */
    private static Long toLongOrNull(String text) {
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
     * 宽松浮点转换，非法时返回 null。
     *
     * @param text 数字字符串，可为 null
     * @return 解析结果
     */
    private static Double toDouble(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text.trim());
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
        Double value = toDouble(text);
        return value == null ? 0d : value;
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

    /**
     * 取第一个非空值。
     *
     * @param primary  首选
     * @param fallback 回落
     * @return 首选非空则用首选
     */
    private static String firstText(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }
}