package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.spi.SshConnectionCache;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

/**
 * Unix SSH Provider (Linux + macOS)
 * 复用 utils-support-ssh-starter，命令按 OS 区分
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnixSshProvider implements SystemInfoProvider {

    private final SshConnectionCache sshCache;

    @Override
    public boolean supports(MsServerHost host) {
        String os = host.getServerHostOsType();
        return "LINUX".equalsIgnoreCase(os) || "MAC".equalsIgnoreCase(os);
    }

    private boolean isMac(MsServerHost host) {
        return "MAC".equalsIgnoreCase(host.getServerHostOsType());
    }

    @Override
    public Map<String, Object> hostInfo(MsServerHost host) {
        Map<String, Object> info = new LinkedHashMap<>();
        boolean mac = isMac(host);
        String cpuCmd = mac ? "sysctl -n hw.ncpu" : "nproc";
        String memCmd = mac ? "sysctl -n hw.memsize" : "grep MemTotal /proc/meminfo | awk '{print $2}'";
        String hostCmd = mac ? "scutil --get ComputerName" : "hostname";
        String ipCmd = mac ? "ipconfig getifaddr en0" : "hostname -I | awk '{print $1}'";
        String macCmd = mac ? "ifconfig en0 | grep ether | awk '{print $2}'" : "cat /sys/class/net/$(ip route show default | awk '/default/ {print $5; exit}')/address";
        String osCmd = mac ? "sw_vers -productVersion" : "grep PRETTY_NAME /etc/os-release | cut -d'\"' -f2";

        info.put("cpuCores", sshCache.exec(host, cpuCmd, 10000));
        info.put("cpuName", sshCache.exec(host, mac ? "sysctl -n machdep.cpu.brand_string" : "grep 'model name' /proc/cpuinfo | head -1 | cut -d: -f2 | xargs", 10000));
        info.put("hostname", sshCache.exec(host, hostCmd, 10000));
        info.put("privateIp", sshCache.exec(host, ipCmd, 10000));
        info.put("mac", sshCache.exec(host, macCmd, 10000));
        info.put("osVersion", sshCache.exec(host, osCmd, 10000));
        info.put("packageManager", sshCache.exec(host, "which apt >/dev/null 2>&1 && echo apt || (which yum >/dev/null 2>&1 && echo yum || (which dnf >/dev/null 2>&1 && echo dnf || (which brew >/dev/null 2>&1 && echo brew || echo unknown)))", 10000));
        info.put("gpu", sshCache.exec(host, "lspci 2>/dev/null | grep -i vga | cut -d: -f3 | xargs || echo 'unknown'", 10000));
        String memOut = sshCache.exec(host, memCmd, 10000);
        try {
            long bytes = Long.parseLong(memOut.trim());
            if (memOut.trim().length() > 8) info.put("memoryMb", bytes / 1024 / 1024);
            else info.put("memoryMb", bytes / 1024);
        } catch (Exception ignored) {}
        info.put("publicIp", sshCache.exec(host, "curl -s --connect-timeout 5 ifconfig.me 2>/dev/null", 10000));
        // CPU usage (idle from top, 1 sample)
        try {
            String cpuOut = sshCache.exec(host, "top -bn1 | grep 'Cpu(s)' | awk '{print 100 - $8}'", 10000);
            info.put("cpuPercent", Double.parseDouble(cpuOut.trim().replace(",", ".")));
        } catch (Exception ignored) {}
        // Memory used
        try {
            String memUsed = sshCache.exec(host, "grep MemAvailable /proc/meminfo | awk '{print $2}'", 10000);
            long availKb = Long.parseLong(memUsed.trim());
            long totalKb = Long.parseLong(memCmd.contains("sysctl") ? "0" : sshCache.exec(host, "grep MemTotal /proc/meminfo | awk '{print $2}'", 10000).trim());
            if (totalKb > 0) {
                long usedKb = totalKb - availKb;
                info.put("memoryUsed", usedKb / 1024);
            }
        } catch (Exception ignored) {}
        return info;
    }

    @Override
    public List<Map<String, Object>> processes(MsServerHost host, int page, int pageSize) {
        String out = sshCache.exec(host, "ps -eo pid,ppid,pcpu,pmem,rss,comm,args --no-headers", 30000);
        List<Map<String, Object>> all = new ArrayList<>();
        if (out == null || out.isBlank()) return all;
        for (String line : out.split("\\r?\\n")) {
            String[] p = line.trim().split("\\s+", 8);
            if (p.length < 8) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pid", p[0]); m.put("ppid", p[1]); m.put("cpu", p[2]);
            m.put("mem", p[3]); m.put("rss", p[4]); m.put("name", p[5]); m.put("args", p[7]);
            all.add(m);
        }
        int from = (page - 1) * pageSize;
        return from >= all.size() ? Collections.emptyList() : all.subList(from, Math.min(from + pageSize, all.size()));
    }

    @Override
    public List<Map<String, Object>> services(MsServerHost host, int page, int pageSize) {
        boolean mac = isMac(host);
        String cmd = mac ? "launchctl list" : "systemctl list-units --type=service --no-pager --no-legend";
        String out = sshCache.exec(host, cmd, 30000);
        List<Map<String, Object>> all = new ArrayList<>();
        if (out == null || out.isBlank()) return all;
        for (String line : out.split("\\r?\\n")) {
            String[] p = line.trim().split("\\s+");
            if (p.length < 2) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            if (mac) {
                m.put("name", p[2]); m.put("status", p[0].equals("-") ? "stopped" : "running"); m.put("pid", p[0]);
            } else {
                m.put("name", p[0]); m.put("load", p[1]); m.put("status", p[2]);
            }
            all.add(m);
        }
        int from = (page - 1) * pageSize;
        return from >= all.size() ? Collections.emptyList() : all.subList(from, Math.min(from + pageSize, all.size()));
    }

    @Override
    public List<Map<String, Object>> disks(MsServerHost host) {
        String out = sshCache.exec(host, "df -Pk", 15000);
        List<Map<String, Object>> list = new ArrayList<>();
        if (out == null || out.isBlank()) return list;
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith("Filesystem") || line.isBlank()) continue;
            String[] p = line.trim().split("\\s+");
            if (p.length < 6) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("filesystem", p[0]);
            // df -Pk gives 1K-blocks; convert to bytes
            try { m.put("size", Long.parseLong(p[1]) * 1024); } catch (Exception e) { m.put("size", 0L); }
            try { m.put("used", Long.parseLong(p[2]) * 1024); } catch (Exception e) { m.put("used", 0L); }
            try { m.put("avail", Long.parseLong(p[3]) * 1024); } catch (Exception e) { m.put("avail", 0L); }
            m.put("use%", p[4]); m.put("mount", p[5]);
            list.add(m);
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> networkInterfaces(MsServerHost host) {
        boolean mac = isMac(host);
        String cmd = mac ? "ifconfig" : "ip -j addr";
        String out = sshCache.exec(host, cmd, 15000);
        List<Map<String, Object>> list = new ArrayList<>();
        if (out == null || out.isBlank()) return list;
        if (!mac) {
            // JSON 输出，简单解析
            list.add(Map.of("ifname", "eth0", "raw", out));
        } else {
            list.add(Map.of("ifname", "en0", "raw", out));
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> netProcesses(MsServerHost host) {
        boolean mac = isMac(host);
        String cmd = mac ? "lsof -i -n -P" : "ss -tulnp";
        String out = sshCache.exec(host, cmd, 15000);
        List<Map<String, Object>> list = new ArrayList<>();
        if (out == null || out.isBlank()) return list;
        for (String line : out.split("\\r?\\n")) {
            if (line.startsWith("State") || line.startsWith("COMMAND") || line.isBlank()) continue;
            list.add(Map.of("raw", line));
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> files(MsServerHost host, String path) {
        String out = sshCache.exec(host, "ls -la --time-style=long-iso \"" + path.replace("\"", "") + "\"", 15000);
        List<Map<String, Object>> list = new ArrayList<>();
        if (out == null || out.isBlank()) return list;
        String[] lines = out.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            String[] p = line.split("\\s+", 8);
            if (p.length < 8) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p[7].split(" -> ")[0]);
            m.put("isDir", p[0].startsWith("d"));
            m.put("perms", p[0]);
            m.put("size", p[4]);
            m.put("modified", p[5] + " " + p[6]);
            m.put("owner", p[2]);
            m.put("group", p[3]);
            list.add(m);
        }
        return list;
    }

    @Override
    public boolean uploadFile(MsServerHost host, String remotePath, InputStream data, long size) {
        try {
            java.io.File tmp = java.io.File.createTempFile("up_", "_tmp");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = data.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                }
            }
            sshCache.getSftp(host).upload().local(tmp.getAbsolutePath()).remote(remotePath).exec();
            tmp.delete();
            return true;
        } catch (Exception e) {
            log.error("upload failed", e);
            return false;
        }
    }

    @Override
    public InputStream downloadFile(MsServerHost host, String remotePath) {
        try {
            java.io.File tmp = java.io.File.createTempFile("dl_", "_tmp");
            sshCache.getSftp(host).download().remote(remotePath).local(tmp.getAbsolutePath()).exec();
            return new java.io.FileInputStream(tmp);
        } catch (Exception e) {
            log.error("download failed", e);
            return null;
        }
    }

    @Override
    public String exec(MsServerHost host, String cmd) {
        return sshCache.exec(host, cmd, 30000);
    }

    @Override
    public List<Map<String, Object>> env(MsServerHost host) {
        String out = sshCache.exec(host, "env", 10000);
        List<Map<String, Object>> list = new ArrayList<>();
        if (out == null) return list;
        for (String line : out.split("\\r?\\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", line.substring(0, eq));
                m.put("value", line.substring(eq + 1));
                list.add(m);
            }
        }
        return list;
    }
}
