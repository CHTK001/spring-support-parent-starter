package com.chua.starter.monitor.support.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.spring.support.configuration.SpringBeanUtils;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.service.LocalSystemInfoService;
import com.chua.starter.monitor.support.spi.Platforms;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 本机 OSHI 系统信息 Provider（SPI 优先级最高）。
 *
 * <p>当目标主机地址为回环地址或本机网卡地址时命中，数据由 {@link LocalSystemInfoService} 采集；
 * 文件、环境变量与命令执行走 JDK 本地能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "local", order = 400)
public class LocalOshiProvider implements SystemInfoProvider {

    /**
     * 本机采集服务，首次使用时从 Spring 容器解析
     */
    private volatile LocalSystemInfoService resolvedService;

    /**
     * 懒取本机系统信息采集服务。
     *
     * @return 采集服务
     */
    private LocalSystemInfoService localService() {
        LocalSystemInfoService service = resolvedService;
        if (service == null) {
            service = SpringBeanUtils.getBean(LocalSystemInfoService.class);
            resolvedService = service;
        }
        return service;
    }

    /**
     * 目标主机是否为本机。
     *
     * @param host 目标主机
     * @return 127.0.0.1 / localhost 或命中本机网卡地址时返回 true
     */
    @Override
    public boolean supports(MsServerHost host) {
        if (host == null || host.getServerHostAddress() == null) {
            return false;
        }
        try {
            String addr = host.getServerHostAddress();
            if ("127.0.0.1".equals(addr) || "localhost".equals(addr)) {
                return true;
            }
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress ia : Collections.list(ni.getInetAddresses())) {
                    if (addr.equals(ia.getHostAddress())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // 枚举本机网卡失败只影响本机判定，不影响其他通道
        }
        return false;
    }

    /**
     * 本机通道直接读 JVM 的操作系统属性。
     *
     * @param host 目标主机
     * @return WINDOWS / LINUX / MAC
     */
    @Override
    public String platform(MsServerHost host) {
        return Platforms.normalize(System.getProperty("os.name"));
    }

    @Override
    public HostInfoVO hostInfo(MsServerHost host) {
        return localService().getHostInfo();
    }

    @Override
    public List<ProcessRowVO> processes(MsServerHost host, int page, int pageSize) {
        return localService().getProcesses("", Math.max(pageSize, 500));
    }

    @Override
    public List<ServiceRowVO> services(MsServerHost host, int page, int pageSize) {
        List<ServiceRowVO> all = localService().getServices();
        int from = (page - 1) * pageSize;
        return from >= all.size() ? Collections.emptyList()
                : new ArrayList<>(all.subList(from, Math.min(from + pageSize, all.size())));
    }

    @Override
    public List<DiskRowVO> disks(MsServerHost host) {
        return localService().getDisks();
    }

    @Override
    public List<NetworkInterfaceRowVO> networkInterfaces(MsServerHost host) {
        return localService().getNetworkInterfaces();
    }

    @Override
    public List<NetProcessRowVO> netProcesses(MsServerHost host) {
        return localService().getNetworkConnections();
    }

    @Override
    public List<FileEntryVO> files(MsServerHost host, String path) {
        List<FileEntryVO> list = new ArrayList<>();
        File dir = new File(path);
        File[] children = dir.listFiles();
        if (children == null) {
            return list;
        }
        for (File f : children) {
            FileEntryVO vo = new FileEntryVO();
            vo.setName(f.getName());
            vo.setDirectory(f.isDirectory());
            vo.setSize(f.length());
            vo.setModified(new Date(f.lastModified()).toString());
            vo.setOwner(System.getProperty("user.name"));
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
        // 经 shell 执行：管控端下发的命令普遍带管道、&& 与引号
        String[] shell;
        if (Platforms.WINDOWS.equals(platform(host))) {
            shell = new String[]{"cmd.exe", "/c", "chcp 65001 >nul && " + cmd};
        } else {
            shell = new String[]{"/bin/sh", "-c", cmd};
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(shell);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return output;
        } catch (Exception e) {
            log.warn("[local] 命令执行失败 cmd={}: {}", cmd, e.getMessage());
            return "";
        }
    }

    @Override
    public List<EnvVarVO> env(MsServerHost host) {
        return localService().getEnvVars();
    }
}
