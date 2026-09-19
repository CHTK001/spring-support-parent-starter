package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

/**
 * WindowsWinrm提供者类，提供相关能力。
 *
 * @author CH
 * @since 1.0.0
 */
@Slf4j
@Component
public class WindowsWinrmProvider implements SystemInfoProvider {

    private static final String HDR = "\"";
    private static final String SEP = "|||";

    /**
     * ps。
     *
     * @param host 主机，不允许为 null
     * @param cmd 方法入参 cmd
     * @return 结果字符串
     */
    private String ps(MsServerHost host, String cmd) {
        try (com.chua.winrm.support.client.WinRmExecClient client = com.chua.winrm.support.client.WinRmExecClient.builder()
                .host(host.getServerHostAddress())
                .port(host.getServerHostPort() != null ? host.getServerHostPort() : 5985)
                .username("administrator").password("lvzhou2011")
                .connectTimeout(10000).sessionTimeout(30000)
                .build()) {
            client.connect();
            // Force UTF-8 codepage then run PowerShell
            String psCmd = "cmd /c chcp 65001 >nul & powershell -NoProfile -Command \"" + cmd.replace("\"", "\\\"") + "\"";
            com.chua.winrm.support.client.WinRmExecClient.ExecResult r = client.executeCommand(psCmd, 30);
            return r.stdout() != null ? r.stdout().trim() : "";
        } catch (Exception e) {
            log.error("WinRM exec failed host={}", host.getId(), e);
            return "";
        }
    }

    /**
     * quote。
     *
     * @param s 方法入参 s
     * @return 结果字符串
     */
    private String quote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    /**
     * 按 CSV 规则拆分行，支持带引号字段
     * @param line 方法入参 line
     * @return 结果列表，无数据时为空列表
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
        return "WINDOWS".equalsIgnoreCase(host.getServerHostOsType());
    }

    @Override public Map<String, Object> hostInfo(MsServerHost host) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hostname", ps(host, "$env:COMPUTERNAME"));
        m.put("osVersion", ps(host, "(Get-WmiObject Win32_OperatingSystem).Caption"));
        m.put("cpuCores", ps(host, "(Get-WmiObject Win32_Processor).NumberOfLogicalProcessors"));
        m.put("cpuName", ps(host, "(Get-WmiObject Win32_Processor).Name"));
        m.put("memoryMb", ps(host, "[math]::Round((Get-WmiObject Win32_ComputerSystem).TotalPhysicalMemory/1MB)"));
        m.put("privateIp", ps(host, "(Get-WmiObject Win32_NetworkAdapterConfiguration | Where-Object {$_.IPEnabled}).IPAddress[0]"));
        m.put("mac", ps(host, "(Get-WmiObject Win32_NetworkAdapterConfiguration | Where-Object {$_.IPEnabled}).MACAddress[0]"));
        m.put("gpu", ps(host, "(Get-WmiObject Win32_VideoController).Name -join ', '"));
        // CPU usage
        try {
            m.put("cpuPercent", Double.parseDouble(ps(host, "(Get-WmiObject Win32_Processor).LoadPercentage")));
        } catch (Exception ignored) {
        }
        // Memory used
        try {
            long total = Long.parseLong(ps(host, "[math]::Round((Get-WmiObject Win32_ComputerSystem).TotalPhysicalMemory/1KB)"));
            long free = Long.parseLong(ps(host, "(Get-WmiObject Win32_OperatingSystem).FreePhysicalMemory"));
            m.put("memoryUsed", (total - free) / 1024);
        } catch (Exception ignored) {}
        return m;
    }

    @Override public List<Map<String, Object>> processes(MsServerHost host, int page, int sz) {
        String out = ps(host, "Get-WmiObject Win32_Process | Select ProcessId,ParentProcessId,WorkingSetSize,KernelModeTime,UserModeTime,Name,CommandLine | ConvertTo-Csv -NoTypeInformation");
        List<Map<String, Object>> list = new ArrayList<>();
        double totalCpu = 0;
        List<double[]> cpuTimes = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "ProcessId") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 7) {
                continue;
            }
            try {
                long ws = Long.parseLong(p.get(2));
                double kt = Double.parseDouble(p.get(3));
                double ut = Double.parseDouble(p.get(4));
                cpuTimes.add(new double[]{kt + ut});
            } catch (Exception ignored) {}
        }
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "ProcessId") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 7) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pid", p.get(0));
            m.put("ppid", p.get(1));
            try {
                m.put("rss", Long.parseLong(p.get(2)) / 1024 / 1024);
            } catch (Exception ignored) {
            }
            m.put("name", p.get(5));
            m.put("args", p.get(6));
            list.add(m);
        }
        // CPU% estimate (proportional to total CPU time)
        double sum = cpuTimes.stream().mapToDouble(a -> a[0]).sum();
        if (sum > 0) {
            for (int i = 0; i < list.size() && i < cpuTimes.size(); i++) {
                list.get(i).put("cpu", String.format("%.1f", cpuTimes.get(i)[0] / sum * 100));
            }
        }
        return paginate(list, page, sz);
    }

    @Override public List<Map<String, Object>> services(MsServerHost host, int page, int sz) {
        String out = ps(host, "Get-Service | Select Name,DisplayName,Status | ConvertTo-Csv -NoTypeInformation");
        List<Map<String, Object>> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "Name") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 3) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.get(0));
            m.put("displayName", p.get(1));
            m.put("status", p.get(2));
            list.add(m);
        }
        return paginate(list, page, sz);
    }

    @Override public List<Map<String, Object>> disks(MsServerHost host) {
        String out = ps(host, "Get-WmiObject Win32_LogicalDisk | Where-Object {$_.DriveType -eq 3} | Select DeviceID,Size,FreeSpace,VolumeName | ConvertTo-Csv -NoTypeInformation");
        List<Map<String, Object>> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "DeviceID") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 4) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            String volName = p.get(3);
            m.put("mount", p.get(0));
            m.put("name", volName.isEmpty() ? p.get(0) + "\\" : volName + " (" + p.get(0) + ")");
            long size = 0, free = 0;
            try {
                size = Long.parseLong(p.get(1));
                free = Long.parseLong(p.get(2));
            } catch (Exception ignored) {
            }
            m.put("size", String.format("%.2f G", size / 1073741824.0));
            m.put("avail", String.format("%.2f G", free / 1073741824.0));
            m.put("use%", size > 0 ? (int)((size - free) * 100 / size) + "%" : "0%");
            list.add(m);
        }
        return list;
    }

    @Override public List<Map<String, Object>> networkInterfaces(MsServerHost host) {
        String out = ps(host, "Get-WmiObject Win32_NetworkAdapterConfiguration | Where {$_.IPEnabled} | Select Description,MACAddress,IPAddress,MTU | ConvertTo-Csv -NoTypeInformation");
        List<Map<String, Object>> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "Description") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 4) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ifname", p.get(0));
            m.put("mac", p.get(1));
            m.put("ipv4", p.get(2));
            m.put("mtu", p.get(3));
            list.add(m);
        }
        return list;
    }

    @Override public List<Map<String, Object>> netProcesses(MsServerHost host) {
        String out = ps(host, "Get-NetTCPConnection | Select LocalAddress,LocalPort,OwningProcess,State | ConvertTo-Csv -NoTypeInformation");
        List<Map<String, Object>> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "LocalAddress") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 4) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("local", p.get(0) + ":" + p.get(1));
            m.put("pid", p.get(2));
            m.put("state", p.get(3));
            list.add(m);
        }
        return list;
    }

    @Override public List<Map<String, Object>> files(MsServerHost host, String path) {
        String out = ps(host, "Get-ChildItem -Force '" + path.replace("'", "''") + "' | Select Name,Length,LastWriteTime,Mode | ConvertTo-Csv -NoTypeInformation");
        List<Map<String, Object>> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "Name") || line.isBlank()) {
                continue;
            }
            List<String> p = csvSplit(line);
            if (p.size() < 4) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.get(0));
            m.put("size", p.get(1));
            m.put("modified", p.get(2));
            m.put("isDir", p.get(3).contains("d"));
            list.add(m);
        }
        return list;
    }

    @Override public boolean uploadFile(MsServerHost host, String remotePath, InputStream data, long size) {
        try {
            byte[] bytes = data.readAllBytes();
            String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
            String cmd = "[IO.File]::WriteAllBytes('" + remotePath + "', [Convert]::FromBase64String('" + b64 + "'))";
            ps(host, cmd);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override public InputStream downloadFile(MsServerHost host, String remotePath) {
        try {
            String cmd = "[Convert]::ToBase64String([IO.File]::ReadAllBytes('" + remotePath + "'))";
            String b64 = ps(host, cmd).replaceAll("\\s", "");
            byte[] bytes = java.util.Base64.getDecoder().decode(b64);
            return new java.io.ByteArrayInputStream(bytes);
        } catch (Exception e) {
            return null;
        }
    }
    @Override public String exec(MsServerHost host, String cmd) {
        return ps(host, cmd);
    }
    @Override public List<Map<String, Object>> env(MsServerHost host) {
        String out = ps(host, "Get-ChildItem env: | Select Name,Value | ConvertTo-Csv -NoTypeInformation");
        List<Map<String, Object>> list = new ArrayList<>();
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith(HDR + "Name") || line.isBlank()) {
                continue;
            }
            String[] p = line.replace(HDR, "").split(",", 2);
            if (p.length < 2) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p[0]);
            m.put("value", p[1]);
            list.add(m);
        }
        return list;
    }

    /**
     * paginate。
     *
     * @param all 全部，不允许为 null
     * @param page 页，不允许为 null
     * @param sz 方法入参 sz
     * @return 结果列表，无数据时为空列表
     */
    private List<Map<String, Object>> paginate(List<Map<String, Object>> all, int page, int sz) {
        int from = (page - 1) * sz;
        return from >= all.size() ? Collections.emptyList() : all.subList(from, Math.min(from + sz, all.size()));
    }
}
