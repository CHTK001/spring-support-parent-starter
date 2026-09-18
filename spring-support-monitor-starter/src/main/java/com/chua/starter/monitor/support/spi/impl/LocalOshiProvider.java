package com.chua.starter.monitor.support.spi.impl;

import com.chua.starter.monitor.support.service.LocalSystemInfoService;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

/**
 * 本机 OSHI 系统信息 Provider（优先级最高）。
 *
 * <p>当目标主机地址为回环地址或本机网卡地址时命中，数据由
 * {@link LocalSystemInfoService} 直接采集。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Component
@RequiredArgsConstructor
public class LocalOshiProvider implements SystemInfoProvider {

    /** 本机系统信息采集服务 */
    private final LocalSystemInfoService localService;

    /**
    * 目标主机是否为本机。
    *
    * @param host 目标主机
    * @return 127.0.0.1 / localhost 或命中本机网卡地址时返回 true
    */
    @Override
    public boolean supports(MsServerHost host) {
        if (host.getServerHostAddress() == null) {
            return false;
        }
        try {
            String addr = host.getServerHostAddress();
            if (addr.equals("127.0.0.1") || addr.equals("localhost")) {
                return true;
            }
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress ia : Collections.list(ni.getInetAddresses())) {
                    if (addr.equals(ia.getHostAddress())) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public Map<String, Object> hostInfo(MsServerHost host) {
        return localService.getHostInfo();
    }

    @Override
    public List<Map<String, Object>> processes(MsServerHost host, int page, int pageSize) {
        return localService.getProcesses("", Math.max(pageSize, 500));
    }

    @Override
    public List<Map<String, Object>> services(MsServerHost host, int page, int pageSize) {
        List<Map<String, Object>> all = localService.getServices();
        int from = (page - 1) * pageSize;
        return from >= all.size() ? Collections.emptyList() : all.subList(from, Math.min(from + pageSize, all.size()));
    }

    @Override
    public List<Map<String, Object>> disks(MsServerHost host) {
        return localService.getDisks();
    }

    @Override
    public List<Map<String, Object>> networkInterfaces(MsServerHost host) {
        return localService.getNetworkInterfaces();
    }

    @Override
    public List<Map<String, Object>> netProcesses(MsServerHost host) {
        return localService.getNetworkConnections();
    }

    @Override
    public List<Map<String, Object>> files(MsServerHost host, String path) {
        List<Map<String, Object>> list = new ArrayList<>();
        File dir = new File(path);
        File[] files = dir.listFiles();
        if (files == null) {
            return list;
        }
        for (File f : files) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", f.getName());
            m.put("isDir", f.isDirectory());
            m.put("size", f.length());
            m.put("modified", new Date(f.lastModified()).toString());
            m.put("owner", System.getProperty("user.name"));
            list.add(m);
        }
        list.sort((a, b) -> {
            boolean ad = (boolean) a.get("isDir"), bd = (boolean) b.get("isDir");
            if (ad != bd) {
                return ad ? -1 : 1;
            }
            return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
        });
        return list;
    }

    @Override
    public boolean uploadFile(MsServerHost host, String remotePath, InputStream data, long size) {
        try (FileOutputStream fos = new FileOutputStream(remotePath)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = data.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public InputStream downloadFile(MsServerHost host, String remotePath) {
        try {
            return new FileInputStream(remotePath);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String exec(MsServerHost host, String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            byte[] out = p.getInputStream().readAllBytes();
            return new String(out);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public List<Map<String, Object>> env(MsServerHost host) {
        List<Map<String, Object>> list = new ArrayList<>();
        System.getenv().forEach((k, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", k);
            m.put("value", v);
            list.add(m);
        });
        return list;
    }
}
