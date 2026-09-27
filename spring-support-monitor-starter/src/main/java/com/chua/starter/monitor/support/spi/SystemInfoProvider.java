package com.chua.starter.monitor.support.spi;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.monitor.support.enums.ServiceAction;
import com.chua.starter.monitor.support.enums.ShellDialect;
import com.chua.starter.monitor.support.model.DiskPartitionRowVO;
import com.chua.starter.monitor.support.model.DiskRowVO;
import com.chua.starter.monitor.support.model.DiskUsageNodeVO;
import com.chua.starter.monitor.support.model.DriverRowVO;
import com.chua.starter.monitor.support.model.EnvVarVO;
import com.chua.starter.monitor.support.model.FileEntryVO;
import com.chua.starter.monitor.support.model.FirewallRuleInputVO;
import com.chua.starter.monitor.support.model.FirewallRuleRowVO;
import com.chua.starter.monitor.support.model.HostActionVO;
import com.chua.starter.monitor.support.model.HostInfoVO;
import com.chua.starter.monitor.support.model.NetProcessRowVO;
import com.chua.starter.monitor.support.model.NetworkConfigVO;
import com.chua.starter.monitor.support.model.NetworkInterfaceRowVO;
import com.chua.starter.monitor.support.model.ProcessRowVO;
import com.chua.starter.monitor.support.model.ProcessSummaryVO;
import com.chua.starter.monitor.support.model.ProcessThreadRowVO;
import com.chua.starter.monitor.support.model.ServiceRowVO;
import com.chua.starter.server.support.entity.MsServerHost;

import java.io.InputStream;
import java.util.List;

/**
 * 系统信息提供者 SPI 扩展点。
 *
 * <p>按主机接入方式提供统一的系统信息采集与动作能力，内置 4 种实现：
 * Local(OSHI) / Agent-HTTP / Linux-Mac SSH / Windows WinRM。返回值一律为强类型视图，
 * 通道间的键名与量纲差异在实现内部消化，调用方不再接触散列结构。</p>
 *
 * <p>动作（杀进程、重启进程、服务启停、网络配置）与平台专属采集（防火墙、驱动、线程、分区）
 * 的命令文本不在通道实现里，也不在调用方，统一由 {@link HostOperations} 的平台子类给出：
 * 通道只提供 {@code exec} 与 {@link #shellDialect}，平台差异由能力集按真实平台解析。</p>
 *
 * <p>扩展方式：实现本接口并标注 {@code @Spi(value = "唯一别名", order = N)}，把实现类全限定名追加到
 * {@code META-INF/extensions/com.chua.starter.monitor.support.spi.SystemInfoProvider}（同包及子包内的
 * 实现也会被自动扫描到）。注意三点：</p>
 * <ul>
 *   <li>实例由 SPI 框架自行创建，不进 Spring 容器，因此必须保留无参构造，依赖用
 *       {@code SpringBeanUtils.getBean(...)} 懒查找；</li>
 *   <li>定义按别名归并，别名重复会相互覆盖，每个通道必须用不同别名；</li>
 *   <li>{@code order} 决定路由优先级（大者优先），路由器按降序取第一个 {@code supports} 的实现。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Spi(SystemInfoProvider.SPI_NAME)
public interface SystemInfoProvider {

    /**
     * 系统信息采集 SPI 扩展名
     */
    String SPI_NAME = "system-info-provider";

    /**
     * 是否支持采集该主机。
     *
     * <p>路由器按 SPI 优先级取第一个返回 true 的实现，因此实现必须以用户所选的采集方式为判据，
     * 不得凭地址、平台等旁证抢接其他通道的宿主——高优先级实现一旦放宽判据，就会静默顶掉真实通道，
     * 采回来的是管控机自己的数据。</p>
     *
     * @param host 目标主机
     * @return 支持返回 true
     */
    boolean supports(MsServerHost host);

    /**
     * 目标主机的真实操作系统平台。
     *
     * <p>默认按 {@code serverHostOsType} 解析；当该字段承载的是接入方式（AGENT / LOCAL）
     * 而非平台时，由具体 Provider 用自己的采集结果覆写。</p>
     *
     * @param host 目标主机
     * @return WINDOWS / LINUX / MAC，无法判定时返回空串
     */
    default String platform(MsServerHost host) {
        return Platforms.normalize(host == null ? null : host.getServerHostOsType());
    }

    /**
     * 主机基础信息（CPU / 内存 / 操作系统等）。
     *
     * @param host 目标主机
     * @return 基础信息视图，不为 null
     */
    HostInfoVO hostInfo(MsServerHost host);

    /**
     * 分页查询进程列表。
     *
     * @param host     目标主机
     * @param page     页码
     * @param pageSize 每页条数
     * @return 进程视图列表，永不为 null
     */
    List<ProcessRowVO> processes(MsServerHost host, int page, int pageSize);

    /**
     * 进程与线程总数（概览用）。
     *
     * <p>默认复用一次全量进程列表；命令行、路径按进程补全的通道应覆写本方法走轻量快照，
     * 否则概览会被进程明细的补全耗时拖垮。</p>
     *
     * @param host 目标主机
     * @return 计数结果，永不为 null
     */
    default ProcessSummaryVO processSummary(MsServerHost host) {
        List<ProcessRowVO> rows = processes(host, 1, 10000);
        long threads = 0L;
        int count = 0;
        if (rows != null) {
            for (ProcessRowVO row : rows) {
                count++;
                threads += row.getThreads();
            }
        }
        return new ProcessSummaryVO(count, threads);
    }

    /**
     * 分页查询服务列表。
     *
     * @param host     目标主机
     * @param page     页码
     * @param pageSize 每页条数
     * @return 服务视图列表，永不为 null
     */
    List<ServiceRowVO> services(MsServerHost host, int page, int pageSize);

    /**
     * 磁盘列表。
     *
     * @param host 目标主机
     * @return 磁盘视图列表，永不为 null
     */
    List<DiskRowVO> disks(MsServerHost host);

    /**
     * 网络接口列表。
     *
     * @param host 目标主机
     * @return 网卡视图列表，永不为 null
     */
    List<NetworkInterfaceRowVO> networkInterfaces(MsServerHost host);

    /**
     * 网络端口占用列表。
     *
     * @param host 目标主机
     * @return 端口占用视图列表，永不为 null
     */
    List<NetProcessRowVO> netProcesses(MsServerHost host);

    /**
     * 目录文件列表。
     *
     * @param host 目标主机
     * @param path 目录路径
     * @return 文件条目列表，永不为 null
     */
    List<FileEntryVO> files(MsServerHost host, String path);

    /**
     * 磁盘占用分析默认深度。
     */
    int DISK_USAGE_DEPTH = 4;

    /**
     * 单次分析最多扫描的目录数（防止整盘递归在远端通道上无限放大请求数）。
     */
    int DISK_USAGE_MAX_DIRS = 12000;

    /**
     * 每个目录中拆成独立矩形块的最大文件数（按大小取 Top N，其余小文件合并为一个块）。
     */
    int DISK_USAGE_TOP_FILES = 12;

    /**
     * 磁盘占用树（WizTree 风格）：递归列出目录并累计字节，目录大小为全部子孙之和。
     *
     * <p>默认实现复用 {@link #files} 逐目录递归，对所有通道通用；受 {@link #DISK_USAGE_DEPTH}
     * 与 {@link #DISK_USAGE_MAX_DIRS} 双重约束。逐目录在远端会放大成多次往返，SSH / Agent
     * 通道可用一条原生 {@code du} / 远端遍历覆写本方法提速。</p>
     *
     * @param host  目标主机
     * @param path  起始路径，为空时由 Provider 决定
     * @param depth 递归深度，小于等于 0 时取默认深度
     * @return 占用树根节点，不为 null
     */
    default DiskUsageNodeVO diskUsage(MsServerHost host, String path, int depth) {
        int maxDepth = depth <= 0 ? DISK_USAGE_DEPTH : Math.min(depth, 8);
        boolean windows = Platforms.isWindows(platform(host));
        char sep = windows ? '\\' : '/';
        long[] scanned = {0L};
        return walkUsage(host, path, maxDepth, sep, scanned);
    }

    /**
     * 单层递归：列目录 → 建节点 → 对子目录继续递归 → 汇总大小并按大小降序。
     *
     * @param host    目标主机
     * @param path    当前目录
     * @param remain  剩余深度
     * @param sep     路径分隔符
     * @param scanned 已扫描目录计数（可变）
     * @return 当前层节点
     */
    private DiskUsageNodeVO walkUsage(MsServerHost host, String path, int remain, char sep, long[] scanned) {
        DiskUsageNodeVO node = new DiskUsageNodeVO();
        node.setDirectory(true);
        node.setPath(path);
        node.setName(basename(path, sep));
        List<FileEntryVO> entries;
        try {
            entries = files(host, path);
        } catch (Exception e) {
            node.setSize(0L);
            return node;
        }
        scanned[0]++;
        long total = 0L;
        long fileCount = 0L;
        long dirCount = 0L;
        // 本层散文件先收集，稍后只把最大的若干个拆成独立块（WizTree 为大文件单独分块）
        List<FileEntryVO> looseFiles = new java.util.ArrayList<>();
        String latestModified = null;
        if (entries != null) {
            for (FileEntryVO e : entries) {
                if (e.getModified() != null && !e.getModified().isBlank()
                        && (latestModified == null || e.getModified().compareTo(latestModified) > 0)) {
                    latestModified = e.getModified();
                }
                total += e.getSize();
                if (e.isDirectory()) {
                    dirCount++;
                    if (remain > 1 && scanned[0] < DISK_USAGE_MAX_DIRS) {
                        DiskUsageNodeVO child = walkUsage(host, joinUsage(path, e.getName(), sep),
                                remain - 1, sep, scanned);
                        node.addChild(child);
                        // 子目录累计值可能比单条目录 size 更准确（目录自身 size 常为 0）
                        total += Math.max(0L, child.getSize() - e.getSize());
                        fileCount += child.getFileCount();
                        dirCount += child.getDirCount();
                    } else {
                        DiskUsageNodeVO leaf = new DiskUsageNodeVO();
                        leaf.setDirectory(true);
                        leaf.setName(e.getName());
                        leaf.setPath(joinUsage(path, e.getName(), sep));
                        leaf.setSize(e.getSize());
                        leaf.setModified(e.getModified());
                        leaf.setDirCount(1L);
                        node.addChild(leaf);
                    }
                } else {
                    fileCount++;
                    looseFiles.add(e);
                }
            }
        }
        // 散文件按大小降序：Top N 拆成独立文件块，其余小文件合并成一个“其它文件”块，
        // 既保留大文件的文件级粒度，又不被海量小文件切碎
        looseFiles.sort(java.util.Comparator.comparingLong(FileEntryVO::getSize).reversed());
        long restSize = 0L;
        long restCount = 0L;
        for (int i = 0; i < looseFiles.size(); i++) {
            FileEntryVO e = looseFiles.get(i);
            if (i < DISK_USAGE_TOP_FILES) {
                DiskUsageNodeVO fileLeaf = new DiskUsageNodeVO();
                fileLeaf.setDirectory(false);
                fileLeaf.setName(e.getName());
                fileLeaf.setPath(joinUsage(path, e.getName(), sep));
                fileLeaf.setSize(e.getSize());
                fileLeaf.setModified(e.getModified());
                fileLeaf.setFileCount(1L);
                node.addChild(fileLeaf);
            } else {
                restSize += e.getSize();
                restCount++;
            }
        }
        if (restCount > 0) {
            DiskUsageNodeVO rest = new DiskUsageNodeVO();
            rest.setDirectory(false);
            rest.setName("__files__");
            rest.setPath(path);
            rest.setSize(restSize);
            rest.setFileCount(restCount);
            node.addChild(rest);
        }
        node.setSize(total);
        node.setFileCount(fileCount);
        node.setDirCount(dirCount);
        node.setModified(latestModified);
        if (node.getChildren() != null) {
            node.getChildren().sort(java.util.Comparator.comparingLong(DiskUsageNodeVO::getSize).reversed());
        }
        return node;
    }

    /**
     * 拼接父路径与子名称。
     *
     * @param parent 父路径
     * @param name   子名称
     * @param sep    分隔符
     * @return 完整路径
     */
    private static String joinUsage(String parent, String name, char sep) {
        if (parent == null || parent.isEmpty()) {
            return name;
        }
        return parent.endsWith(String.valueOf(sep)) ? parent + name : parent + sep + name;
    }

    /**
     * 取路径最后一段作为名称。
     *
     * @param path 路径
     * @param sep  分隔符
     * @return 名称
     */
    private static String basename(String path, char sep) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        int i = Math.max(path.lastIndexOf(sep), path.lastIndexOf(sep == '\\' ? '/' : '\\'));
        return i >= 0 && i + 1 < path.length() ? path.substring(i + 1) : path;
    }

    /**
     * 上传文件到目标主机。
     *
     * @param host       目标主机
     * @param remotePath 远端路径
     * @param data       数据流
     * @param size       数据长度
     * @return 成功返回 true
     */
    boolean uploadFile(MsServerHost host, String remotePath, InputStream data, long size);

    /**
     * 下载目标主机文件。
     *
     * @param host       目标主机
     * @param remotePath 远端路径
     * @return 文件输入流，失败返回 null
     */
    InputStream downloadFile(MsServerHost host, String remotePath);

    /**
     * 在目标主机执行命令。
     *
     * @param host 目标主机
     * @param cmd  命令
     * @return 标准输出内容
     */
    String exec(MsServerHost host, String cmd);

    /**
     * {@link #exec} 下发的命令在目标主机上实际运行的 shell 方言。
     *
     * <p>默认本机与 Agent 通道按 cmd.exe（Windows）或 /bin/sh（其它）执行；SSH 与 WinRM
     * 通道在 Windows 上以 PowerShell 承载命令，必须覆写本方法，否则控制层拼出的
     * {@code cd /d} 与 {@code &&} 会被对端当成语法错误。</p>
     *
     * @param host 目标主机
     * @return shell 方言，不为 null
     */
    default ShellDialect shellDialect(MsServerHost host) {
        return Platforms.isWindows(platform(host)) ? ShellDialect.CMD : ShellDialect.SH;
    }

    /**
     * 关闭进程：下发平台杀进程命令，并回查进程确实消失后才算成功。
     *
     * <p>命令文本由 {@link HostOperations} 的平台实现给出，通道只负责把命令送进目标机的 shell。</p>
     *
     * @param host 目标主机
     * @param pid  进程 ID
     * @param port 端口，仅用于回显，可为空
     * @return 动作结果，永不为 null
     */
    default HostActionVO killProcess(MsServerHost host, int pid, Integer port) {
        return HostOperations.of(this, host).killProcess(pid, port);
    }

    /**
     * 重启进程：按原命令行与工作目录重新拉起。
     *
     * @param host 目标主机
     * @param pid  进程 ID
     * @return 动作结果，成功时可能带新进程号
     */
    default HostActionVO restartProcess(MsServerHost host, int pid) {
        return HostOperations.of(this, host).restartProcess(pid);
    }

    /**
     * 服务启停：下发动作命令后复查真实状态。
     *
     * @param host   目标主机
     * @param name   服务名称
     * @param action 动作
     * @return 动作结果，永不为 null
     */
    default HostActionVO controlService(MsServerHost host, String name, ServiceAction action) {
        return HostOperations.of(this, host).controlService(name, action);
    }

    /**
     * 应用网络配置（IP / 网关 / DNS）。
     *
     * @param host   目标主机
     * @param config 配置项
     * @return 动作结果；平台不具备该能力时返回失败并说明原因
     */
    default HostActionVO applyNetworkConfig(MsServerHost host, NetworkConfigVO config) {
        return HostOperations.of(this, host).applyNetworkConfig(config);
    }

    /**
     * 设置系统级环境变量，下发后回读确认。
     *
     * @param host  目标主机
     * @param name  变量名
     * @param value 变量值
     * @return 动作结果
     */
    default HostActionVO setEnvVar(MsServerHost host, String name, String value) {
        return HostOperations.of(this, host).setEnvVar(name, value);
    }

    /**
     * 删除系统级环境变量，下发后回读确认。
     *
     * @param host 目标主机
     * @param name 变量名
     * @return 动作结果
     */
    default HostActionVO deleteEnvVar(MsServerHost host, String name) {
        return HostOperations.of(this, host).deleteEnvVar(name);
    }

    /**
     * 新增防火墙规则。
     *
     * @param host 目标主机
     * @param rule 规则入参
     * @return 动作结果
     */
    default HostActionVO addFirewallRule(MsServerHost host, FirewallRuleInputVO rule) {
        return HostOperations.of(this, host).addFirewallRule(rule);
    }

    /**
     * 删除防火墙规则。
     *
     * @param host  目标主机
     * @param name  规则名（Windows）
     * @param chain 链名（Linux）
     * @param num   序号（Linux）
     * @return 动作结果
     */
    default HostActionVO deleteFirewallRule(MsServerHost host, String name, String chain, Integer num) {
        return HostOperations.of(this, host).deleteFirewallRule(name, chain, num);
    }

    /**
     * 防火墙规则列表。
     *
     * @param host 目标主机
     * @return 规则列表，采集失败时抛出带目标机原因的异常
     */
    default List<FirewallRuleRowVO> firewallRules(MsServerHost host) {
        return HostOperations.of(this, host).firewallRules();
    }

    /**
     * 驱动列表。
     *
     * @param host 目标主机
     * @return 驱动列表，采集失败时抛出带目标机原因的异常
     */
    default List<DriverRowVO> drivers(MsServerHost host) {
        return HostOperations.of(this, host).drivers();
    }

    /**
     * 进程的线程列表。
     *
     * @param host 目标主机
     * @param pid  进程 ID
     * @return 线程列表
     */
    default List<ProcessThreadRowVO> processThreads(MsServerHost host, int pid) {
        return HostOperations.of(this, host).processThreads(pid);
    }

    /**
     * 磁盘分区列表。
     *
     * @param host 目标主机
     * @return 分区列表
     */
    default List<DiskPartitionRowVO> diskPartitions(MsServerHost host) {
        return HostOperations.of(this, host).diskPartitions();
    }

    /**
     * 环境变量列表。
     *
     * @param host 目标主机
     * @return 环境变量条目列表，永不为 null
     */
    List<EnvVarVO> env(MsServerHost host);

    /**
     * 创建目录（自动补齐父目录）。
     *
     * @param host 目标主机
     * @param path 目录路径
     * @return 成功返回 true
     */
    boolean mkdir(MsServerHost host, String path);

    /**
     * 删除文件或目录（目录按递归处理）。
     *
     * @param host 目标主机
     * @param path 路径
     * @return 成功返回 true
     */
    boolean delete(MsServerHost host, String path);

    /**
     * 重命名 / 移动。
     *
     * @param host 目标主机
     * @param from 原路径
     * @param to   目标路径
     * @return 成功返回 true
     */
    boolean rename(MsServerHost host, String from, String to);

    /**
     * 读取文本文件内容（UTF-8）。默认复用下载通道，通道实现可覆写。
     *
     * @param host 目标主机
     * @param path 路径
     * @return 文本内容；读取失败返回 null
     */
    default String readText(MsServerHost host, String path) {
        try (InputStream in = downloadFile(host, path)) {
            if (in == null) {
                return null;
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toString(java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 写入文本文件内容（UTF-8）。默认复用上传通道，通道实现可覆写。
     *
     * <p>失败以异常上报而不是返回 false：吞掉异常会让调用方只能报出「写入失败: 路径」，
     * 「目录不存在」「通道不支持这个体积」这类可执行的真实原因无人能读到。</p>
     *
     * @param host 目标主机
     * @param path 路径
     * @param text 文本内容
     * @return 成功返回 true
     */
    default boolean writeText(MsServerHost host, String path, String text) {
        byte[] data = (text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return uploadFile(host, path, new java.io.ByteArrayInputStream(data), data.length);
    }
}
