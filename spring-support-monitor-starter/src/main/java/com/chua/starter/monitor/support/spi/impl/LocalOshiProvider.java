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
import com.chua.starter.monitor.support.model.ProcessSummaryVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.monitor.support.service.LocalSystemInfoService;
import com.chua.starter.monitor.support.spi.Platforms;
import com.chua.starter.monitor.support.spi.SystemInfoProvider;
import com.chua.starter.monitor.support.support.ConsoleOutput;
import com.chua.starter.monitor.support.support.ShellSupport;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.enums.HostCollectType;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 本机 OSHI 系统信息 Provider（SPI 优先级最高）。
 *
 * <p>仅当主机显式选择 LOCAL 采集方式且地址确属本机时命中，数据由 {@link LocalSystemInfoService} 采集；
 * 文件、环境变量与命令执行走 JDK 本地能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Spi(value = "local", order = 400)
public class LocalOshiProvider implements SystemInfoProvider {

    /**
     * 本机进程深翻页的取数上限：命令行与路径按进程补全（Windows 实测约 39ms/进程），
     * 不设上限会让深页请求退化成全量补全
     */
    private static final int MAX_PROCESS_ROWS = 2000;

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
     * 目标主机是否显式选择本机自采集通道。
     *
     * @param host 目标主机
     * @return 采集方式为 LOCAL 且地址确属本机时返回 true
     */
    @Override
    public boolean supports(MsServerHost host) {
        if (host == null || host.getServerHostAddress() == null) {
            return false;
        }
        // 本实现优先级最高，若不校验采集方式就会把同机注册的 AGENT/SSH/WINRM 主机全部顶掉，
        // 采回来的其实是管控机自己的数据
        if (!HostCollectType.LOCAL.getCode().equalsIgnoreCase(host.getServerHostCollectMode())) {
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
        // 按需画像刷新（点击刷新）：探测公网 IP；时间轮周期采集不探测
        return localService().getHostInfo(true);
    }

    @Override
    public List<ProcessRowVO> processes(MsServerHost host, int page, int pageSize) {
        int size = Math.max(pageSize, 1);
        int no = Math.max(page, 1);
        int need = Math.min(no * size, MAX_PROCESS_ROWS);
        List<ProcessRowVO> head = localService().getProcesses("", need);
        int from = (no - 1) * size;
        return from >= head.size() ? Collections.emptyList()
                : new ArrayList<>(head.subList(from, Math.min(from + size, head.size())));
    }

    @Override
    public ProcessSummaryVO processSummary(MsServerHost host) {
        return localService().getProcessSummary();
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
            // 空目录与读不到是两回事：回空列表会让不存在的路径在页面上显示成「空目录」
            throw new IllegalStateException((dir.isDirectory() ? "目录不可读: " : "目录不存在: ") + path);
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
            // 只回 false 会让用户看到「写入失败: 路径」，分不清是目录不存在还是磁盘不可写
            throw new IllegalStateException(e.getMessage(), e);
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
            // 有界读取：分离出去的子进程继承同一个管道写端，readAllBytes 会读到它退出为止
            Process process = builder.start();
            String out = ConsoleOutput.read(process);
            int code = process.exitValue();
            if (code != 0) {
                String mark = ShellSupport.FAIL_MARK + ":本机退出码 " + code;
                return out == null || out.isBlank() ? mark : out + "\r\n" + mark;
            }
            return out;
        } catch (Exception e) {
            log.warn("[local] 命令执行失败 cmd={}: {}", cmd, e.getMessage());
            throw new IllegalStateException("本机命令执行失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }

    @Override
    public List<EnvVarVO> env(MsServerHost host) {
        return localService().getEnvVars();
    }

    @Override
    public boolean mkdir(MsServerHost host, String path) {
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(path));
            return true;
        } catch (Exception e) {
            log.warn("[local] mkdir 失败 path={}: {}", path, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(MsServerHost host, String path) {
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(path);
            if (java.nio.file.Files.isDirectory(p)) {
                // 递归删除：深度优先删子项后再删目录本身
                try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(p)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(c -> {
                        try {
                            java.nio.file.Files.deleteIfExists(c);
                        } catch (Exception ignored) {
                            // 单个条目删除失败不阻断整体流程
                        }
                    });
                }
                return !java.nio.file.Files.exists(p);
            }
            java.nio.file.Files.deleteIfExists(p);
            return true;
        } catch (Exception e) {
            log.warn("[local] delete 失败 path={}: {}", path, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean rename(MsServerHost host, String from, String to) {
        try {
            java.nio.file.Path src = java.nio.file.Paths.get(from);
            java.nio.file.Path target = java.nio.file.Paths.get(to);
            java.nio.file.Path parent = target.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            java.nio.file.Files.move(src, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            log.warn("[local] rename 失败 {} -> {}: {}", from, to, e.getMessage());
            return false;
        }
    }
}
