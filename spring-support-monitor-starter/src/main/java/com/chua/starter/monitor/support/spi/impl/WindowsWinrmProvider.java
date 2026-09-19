package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.server.support.entity.MsServerHost;
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
     * 通过 WinRM 执行 PowerShell 并取回标准输出。
     *
     * @param host 目标主机
     * @param cmd  PowerShell 脚本
     * @return 输出文本（已 trim）；失败返回空串
     */
    private String ps(MsServerHost host, String cmd) {
        try (com.chua.winrm.support.client.WinRmExecClient client = com.chua.winrm.support.client.WinRmExecClient.builder()
                .host(host.getServerHostAddress())
                .port(host.getServerHostPort() != null ? host.getServerHostPort() : 5985)
                .username(account(host))
                .password(password(host))
                .connectTimeout(10000).sessionTimeout(30000)
                .build()) {
            client.connect();
            String psCmd = "cmd /c chcp 65001 >nul & powershell -NoProfile -Command \"" + cmd.replace("\"", "\\\"") + "\"";
            com.chua.winrm.support.client.WinRmExecClient.ExecResult r = client.executeCommand(psCmd, 30);
            return r.stdout() != null ? r.stdout().trim() : "";
        } catch (Exception e) {
            log.error("WinRM exec failed host={}", host.getId(), e);
            return "";
        }
    }

    /**
     * WinRM 登录名：优先主机账密，缺省回落 administrator。
     *
     * @param host 目标主机
     * @return 登录名
     */
    private static String account(MsServerHost host) {
        String account = host.getServerHostAccount();
        return account == null || account.isBlank() ? "administrator" : account;
    }

    /**
     * WinRM 登录密码：优先主机账密。
     *
     * @param host 目标主机
     * @return 密码
     */
    private static String password(MsServerHost host) {
        String password = host.getServerHostPassword();
        return password == null || password.isBlank() ? "lvzhou2011" : password;
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
        return "WINDOWS".equalsIgnoreCase(host == null ? null : host.getServerHostOsType());
    }

    @Override
    public HostInfoVO hostInfo(MsServerHost host) {
        HostInfoVO info = new HostInfoVO();
        info.setHostname(ps(host, "$env:COMPUTERNAME"));
        info.setOsName("Windows");
        info.setOsVersion(ps(host, "(Get-WmiObject Win32_OperatingSystem).Caption"));
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
        String out = ps(host, "Get-WmiObject Win32_Process | Select ProcessId,ParentProcessId,WorkingSetSize,"
                + "KernelModeTime,UserModeTime,Name,CommandLine | ConvertTo-Csv -NoTypeInformation");
        List<ProcessRowVO> list = new ArrayList<>();
        List<Double> cpuTimes = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "ProcessId") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 7) {
                continue;
            }
            ProcessRowVO vo = new ProcessRowVO();
            vo.setPid(toLong(p.get(0)));
            vo.setPpid(toLong(p.get(1)));
            vo.setName(p.get(5).trim());
            vo.setCommand(p.get(6).trim());
            try {
                vo.setMemory(Long.parseLong(p.get(2).trim()) / MB);
            } catch (NumberFormatException e) {
                vo.setMemory(0);
            }
            cpuTimes.add(time(p.get(3)) + time(p.get(4)));
            list.add(vo);
        }
        double sum = cpuTimes.stream().mapToDouble(Double::doubleValue).sum();
        if (sum > 0) {
            for (int i = 0; i < list.size(); i++) {
                list.get(i).setCpu(Math.round(cpuTimes.get(i) / sum * 1000) / 10.0);
            }
        }
        return paginate(list, page, sz);
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
            ServiceRowVO vo = new ServiceRowVO();
            vo.setName(p.get(0).trim());
            vo.setDisplayName(p.get(1).trim());
            vo.setState(p.get(2).trim().toUpperCase(Locale.ROOT));
            vo.setStartType(p.get(3).trim());
            vo.setPid(toLong(p.get(4)));
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
            DiskRowVO vo = new DiskRowVO();
            vo.setMount(p.get(0).trim());
            vo.setName(volume.isEmpty() ? p.get(0).trim() + "\\" : volume + " (" + p.get(0).trim() + ")");
            vo.setFsType("NTFS");
            vo.setTotalGb(round1(size / GB));
            vo.setUsedGb(round1((size - free) / GB));
            vo.setAvailGb(round1(free / GB));
            vo.setUsage(size > 0 ? round1((size - free) * 100.0 / size) : 0);
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<NetworkInterfaceRowVO> networkInterfaces(MsServerHost host) {
        String out = ps(host, "Get-WmiObject Win32_NetworkAdapterConfiguration | Where {$_.IPEnabled} "
                + "| Select Description,MACAddress,IPAddress,MTU | ConvertTo-Csv -NoTypeInformation");
        List<NetworkInterfaceRowVO> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "Description") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 4) {
                continue;
            }
            NetworkInterfaceRowVO vo = new NetworkInterfaceRowVO();
            vo.setName(p.get(0).trim());
            vo.setDisplayName(p.get(0).trim());
            vo.setStatus("UP");
            vo.setMac(p.get(1).trim());
            vo.setIpv4(splitAddresses(p.get(2), true));
            vo.setIpv6(splitAddresses(p.get(2), false));
            vo.setMtu(toLong(p.get(3)));
            list.add(vo);
        }
        return list;
    }

    /**
     * 从 {@code System.String[]} 形态的 IPAddress 列里挑出指定族地址。
     *
     * @param text   CSV 单元格原文
     * @param wantV4 true 取 IPv4，false 取 IPv6
     * @return 分号连接的地址串，无匹配时返回空串
     */
    private static String splitAddresses(String text, boolean wantV4) {
        List<String> picked = new ArrayList<>();
        for (String token : text.replaceAll("[{}\"\\\\]", "").split("[;,\\s]+")) {
            String addr = token.trim();
            if (addr.isEmpty()) {
                continue;
            }
            boolean v4 = addr.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$");
            if (v4 == wantV4) {
                picked.add(addr);
            }
        }
        return String.join(";", picked);
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
        String out = ps(host, "Get-NetTCPConnection | Select LocalAddress,LocalPort,RemoteAddress,RemotePort,"
                + "State,OwningProcess | ConvertTo-Csv -NoTypeInformation");
        List<NetProcessRowVO> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "LocalAddress") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 6) {
                continue;
            }
            String pid = p.get(5).trim();
            list.add(NetProcessRowVO.of("tcp", p.get(0).trim(), toPort(p.get(1)), p.get(2).trim(), toPort(p.get(3)),
                    p.get(4).trim().toUpperCase(Locale.ROOT), toPort(pid), names.getOrDefault(pid, "")));
        }
        return list;
    }

    @Override
    public List<FileEntryVO> files(MsServerHost host, String path) {
        String out = ps(host, "Get-ChildItem -Force '" + path.replace("'", "''") + "' | Select Name,Length,"
                + "LastWriteTime,Mode | ConvertTo-Csv -NoTypeInformation");
        List<FileEntryVO> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "Name") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 4) {
                continue;
            }
            FileEntryVO vo = new FileEntryVO();
            vo.setName(p.get(0).trim());
            vo.setSize(toLong(p.get(1)));
            vo.setModified(p.get(2).trim());
            vo.setDirectory(p.get(3).contains("d"));
            list.add(vo);
        }
        list.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return list;
    }

    @Override
    public boolean uploadFile(MsServerHost host, String remotePath, InputStream data, long size) {
        try {
            byte[] bytes = data.readAllBytes();
            String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
            String cmd = "[IO.File]::WriteAllBytes('" + remotePath.replace("'", "''")
                    + "', [Convert]::FromBase64String('" + b64 + "'))";
            ps(host, cmd);
            return true;
        } catch (Exception e) {
            log.error("WinRM upload failed host={} path={}", host.getId(), remotePath, e);
            return false;
        }
    }

    @Override
    public InputStream downloadFile(MsServerHost host, String remotePath) {
        try {
            String cmd = "[Convert]::ToBase64String([IO.File]::ReadAllBytes('" + remotePath.replace("'", "''") + "'))";
            String b64 = ps(host, cmd).replaceAll("\\s", "");
            byte[] bytes = java.util.Base64.getDecoder().decode(b64);
            return new java.io.ByteArrayInputStream(bytes);
        } catch (Exception e) {
            log.error("WinRM download failed host={} path={}", host.getId(), remotePath, e);
            return null;
        }
    }

    @Override
    public String exec(MsServerHost host, String cmd) {
        return ps(host, cmd);
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
     * 宽松端口/PID 转换。
     *
     * @param text 数字字符串，可为 null
     * @return 解析结果；非法时返回 0
     */
    private static int toPort(String text) {
        try {
            return text == null || text.isEmpty() ? 0 : Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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
