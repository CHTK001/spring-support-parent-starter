package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsTsharkPacket;
import com.chua.starter.monitor.support.entity.MsTsharkSession;
import com.chua.starter.monitor.support.mapper.MsTsharkPacketMapper;
import com.chua.starter.monitor.support.mapper.MsTsharkSessionMapper;
import com.chua.starter.monitor.support.model.TsharkFlowVO;
import com.chua.starter.monitor.support.model.TsharkHandshakeVO;
import com.chua.starter.monitor.support.model.TsharkInterfaceVO;
import com.chua.starter.monitor.support.model.TsharkProtocolStatVO;
import com.chua.starter.monitor.support.tshark.TsharkDisplayFilter;
import com.chua.starter.monitor.support.tshark.TsharkPacketStreamRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * tshark 网络抓包服务
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Service
public class TsharkCaptureService {

    private static final String TSHARK_BIN = System.getenv().getOrDefault("TSHARK_BIN", "tshark"); // TSHARK_BIN

    /**
     * pcap 落盘目录。
     *
     * <p><b>为什么默认值放在用户主目录而不是 {@code /opt/spring-support/captures}</b>：
     * 原默认值假定服务以 root 运行。实际部署里平台由 systemd 以普通用户拉起
     * （{@code User=kali}），而 {@code /opt} 归 root，{@code Files.createDirectories}
     * 会以 AccessDeniedException 失败，异常消息只带上一级路径，最终表现为
     * 「启动抓包失败: /opt/spring-support」—— 功能已实现但在真实部署里完全不可用
     * （实测该目录当时甚至不存在，且普通用户无权在 /opt 下创建）。</p>
     *
     * <p>改为用户主目录后，任何非 root 部署都能直接跑通；需要固定路径时用环境变量
     * {@code TSHARK_CAPTURE_DIR} 覆盖即可（原先也是这个约定，只是不该把不可写路径
     * 设为默认）。</p>
     */
    private static final String CAPTURE_DIR = System.getenv()
            .getOrDefault("TSHARK_CAPTURE_DIR",
                    System.getProperty("user.home") + File.separator + "spring-support" + File.separator + "captures"); // CAPTURE_DIR

    /**
     * {@code tshark -T fields} 的取字段列表，<strong>顺序即输出列顺序</strong>。
     *
     * <p>逐列含义：0 帧号 · 1 时间戳(秒) · 2 协议 · 3 IPv4源 · 4 IPv6源 · 5 TCP源端口 ·
     * 6 UDP源端口 · 7 IPv4目的 · 8 IPv6目的 · 9 TCP目的端口 · 10 UDP目的端口 ·
     * 11 帧长 · 12 人可读摘要。IPv4/IPv6 与 TCP/UDP 成对给出，某层不存在时该列为空串，
     * 由 {@code firstNonBlank} 取非空的那个。</p>
     *
     * <p><strong>第 0 列 {@code frame.number} 是增量解析的游标，不入库</strong>：
     * 抓包期间要一边写 pcap 一边把新包读出来推给前端，尾随循环靠
     * {@code -Y "frame.number > N"} 只取新增的帧。没有单调帧号就没法判断「哪些是新的」，
     * 只能整份重读，那样既慢又会重复入库。</p>
     *
     * <p>用 {@code _ws.col.Info} 而非 {@code -x} 的十六进制展开：前者就是 tshark
     * 「Info」列（如 {@code GET /ms/... HTTP/1.1}），正是实体里「人可读摘要」要的东西。</p>
     */
    private static final String[] TSHARK_FIELDS = {
            "frame.number",
            "frame.time_epoch",
            "_ws.col.Protocol",
            "ip.src", "ipv6.src",
            "tcp.srcport", "udp.srcport",
            "ip.dst", "ipv6.dst",
            "tcp.dstport", "udp.dstport",
            "frame.len",
            "_ws.col.Info"
    };

    /** {@link #TSHARK_FIELDS} 的列数；列数不足说明输出异常，该行丢弃 */
    private static final int TSHARK_FIELD_COUNT = TSHARK_FIELDS.length;

    /** 会话来源：网卡实时抓包（{@code tshark -i} 抓出来的） */
    private static final String SOURCE_LIVE = "live";
    /** 会话来源：导入的 pcap 文件（{@code PCAP 分析} 页面上传的） */
    private static final String SOURCE_IMPORT = "import";

    /**
     * 抓包前要求的最小磁盘余量（MB）。
     *
     * <p>抓包是「往里写、满盘就整机受影响」的动作：磁盘写满不只是抓包失败，同机的
     * 其它服务一起遭殃。启动前先看一眼可用空间，不足直接拒绝建会话，比抓一半崩掉可控。</p>
     */
    private static final long MIN_FREE_DISK_MB = 128L;

    /** 抓包本身之外还要留的余量（MB）：pcap 之外还有入库、日志、临时文件 */
    private static final long DISK_HEADROOM_MB = 64L;

    /** 显示过滤在统计 / Top 流场景下允许扫描的最大报文数（超过则拒绝并提示缩小范围） */
    private static final long MAX_FILTER_SCAN = 200_000L;

    /** 生命周期默认返回的最大步骤数（超出只保留首尾与锚点包） */
    private static final int DEFAULT_HANDSHAKE_LIMIT = 600;

    /**
     * 已被用户主动停止 / 删除的会话。
     *
     * <p>tshark 被 {@code destroy()} 后退出码通常为非 0（Windows 上尤其如此），
     * 光看退出码会把正常的「点停止」判成异常。收尾线程靠这个集合区分
     * 「用户停的」与「进程自己崩的」。</p>
     */
    private final Set<Long> stopRequested = ConcurrentHashMap.newKeySet();

    private final Map<Long, Process> running = new ConcurrentHashMap<>(); // running
    private final MsTsharkSessionMapper sessionMapper; // 会话映射器
    private final MsTsharkPacketMapper packetMapper; // 数据包映射器
    /**
     * 实时推送服务（Socket 未启用时静默跳过）
    */
    private final MonitorPushService pushService;

    /**
     * 数据包实时推送（SSE，按会话分组）。
     *
     * <p>用 {@link ObjectProvider} 而不是直接注入：注册表是本包内新建的组件，
     * 与本服务无依赖环，但抓包并非所有部署都需要推送，直接注入会让
     * 「禁用抓包」的部署也强拉起SSE 相关 bean。</p>
     */
    private final ObjectProvider<TsharkPacketStreamRegistry> streamRegistryProvider;

    public TsharkCaptureService(MsTsharkSessionMapper sessionMapper,
                                MsTsharkPacketMapper packetMapper,
                                MonitorPushService pushService,
                                ObjectProvider<TsharkPacketStreamRegistry> streamRegistryProvider) {
        this.sessionMapper = sessionMapper;
        this.packetMapper = packetMapper;
        this.pushService = pushService;
        this.streamRegistryProvider = streamRegistryProvider;
    }

    /**
     * 取 SSE 注册表；未装配时返回 null（推送静默跳过）。
     */
    private TsharkPacketStreamRegistry streamRegistry() {
        return this.streamRegistryProvider.getIfAvailable();
    }

    /**
     * 启动时回收遗留的「进行中」会话。
     *
     * <p>进程内 {@code running} 表随 JVM 重启清空，但数据库里上一次运行留下的
     * {@code capture_status=1} 还挂着。若不回收，页面上会出现一条永远不会结束、
     * 包数也不再增长的「抓包中」，用户既停不掉（进程早没了）也删不干净（会被当成在跑）。
     * 这里统一标成异常并写明原因。</p>
     */
    @PostConstruct
    public void recoverInterruptedSessions() {
        try {
            List<MsTsharkSession> runningSessions = this.sessionMapper.selectList(
                    new LambdaQueryWrapper<MsTsharkSession>().eq(MsTsharkSession::getCaptureStatus, 1));
            if (runningSessions == null || runningSessions.isEmpty()) {
                return;
            }
            for (MsTsharkSession session : runningSessions) {
                session.setCaptureStatus(2);
                session.setLastError("服务重启，抓包 / 解析已中断");
                if (session.getEndTime() == null) {
                    session.setEndTime(LocalDateTime.now());
                }
                this.sessionMapper.updateById(session);
            }
            log.warn("recovered {} interrupted tshark session(s)", runningSessions.size());
        } catch (Exception e) {
            // 回收失败（如首次启动表还没建好）不能影响主流程
            log.debug("recover tshark sessions skipped: {}", e.getMessage());
        }
    }

    /**
     * 抓包前的磁盘余量检查。
     *
     * @param incomingBytes 本次预计写入的字节数（导入文件用；实时抓包传 0）
     * @param maxSizeMb     会话大小上限（MB，0 或空表示不限）
     * @return 不足时返回给用户看的错误；充足返回 null
     */
    private String diskGuardError(long incomingBytes, Integer maxSizeMb) {
        try {
            Files.createDirectories(Paths.get(CAPTURE_DIR));
            FileStore store = Files.getFileStore(Paths.get(CAPTURE_DIR));
            long usableMb = store.getUsableSpace() / (1024L * 1024L);
            long required = MIN_FREE_DISK_MB;
            if (maxSizeMb != null && maxSizeMb > 0) {
                required = Math.max(required, maxSizeMb + DISK_HEADROOM_MB);
            }
            if (incomingBytes > 0) {
                required = Math.max(required, incomingBytes / (1024L * 1024L) + DISK_HEADROOM_MB);
            }
            if (usableMb < required) {
                return "磁盘可用空间不足：当前可用约 " + usableMb + "MB，抓包至少需要 " + required
                        + "MB（抓包目录 " + CAPTURE_DIR + "）。请清理磁盘后重试。";
            }
            return null;
        } catch (Exception e) {
            log.debug("disk guard skipped: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 列表接口。
     * @return 列表接口的结果
     */
    public ReturnResult<List<TsharkInterfaceVO>> listInterfaces() {
        Path errFile = null;
        try {
            // stderr 重定向到临时文件而不是合并进 stdout：
            // 合并后 tshark 的报错文案会和网卡行混在一起，分不出哪些是网卡；
            // 单独落文件既能读到报错，又不会因为两个流互相堵塞而死锁。
            errFile = Files.createTempFile("tshark-iface-err", ".txt");
            ProcessBuilder pb = new ProcessBuilder(TSHARK_BIN, "-D");
            pb.redirectError(errFile.toFile());
            Process p = pb.start();
            List<TsharkInterfaceVO> ifaces = new ArrayList<>();
            // tshark -D 的行格式：序号. 设备名 (友好名)
            // Windows 上设备名是 \Device\NPF_{GUID} 这种不可读的 GUID，友好名在括号里。
            java.util.regex.Pattern line = java.util.regex.Pattern
                    .compile("^\\s*\\d+\\.\\s+(\\S+?)\\s*(?:\\((.*)\\))?\\s*$");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String s;
                while ((s = r.readLine()) != null) {
                    if (s.isBlank()) {
                        continue;
                    }
                    java.util.regex.Matcher m = line.matcher(s);
                    if (m.find()) {
                        ifaces.add(new TsharkInterfaceVO(m.group(1), blankToEmpty(m.group(2)),
                                List.of(), "", false, false, false));
                    }
                }
            }
            boolean exited = p.waitFor(10L, TimeUnit.SECONDS);
            if (!exited) {
                p.destroyForcibly();
            }
            int code = exited ? p.exitValue() : -1;
            String err = errFile == null ? "" : Files.readString(errFile, StandardCharsets.UTF_8);

            // 退出码非 0 或 stderr 提到 Npcap，一律判失败。
            // 实测缺 Npcap 时 tshark -D 退出码 13，stderr 是
            //   Unable to load Npcap (wpcap.dll); you will not be able to capture packets.
            // 但 stdout 末尾仍会打印一行「1. etwdump (...)」——那是个抓不了包的 ETW 读取器。
            // 只看「解析到几条」会被这一行骗过去、把不可用的网卡当成可用，所以必须看退出码。
            if (code != 0 || err.contains("Npcap") || err.contains("wpcap")) {
                String detail = err.trim().isEmpty() ? ("tshark -D 退出码 " + code) : firstLine(err.trim());
                String msg = "无法列出网卡：" + detail;
                if (err.contains("Npcap") || err.contains("wpcap")) {
                    msg += "（未安装 Npcap 抓包驱动，请安装后重启服务；tshark 本身已就位）";
                }
                return ReturnResult.error(msg);
            }
            if (ifaces.isEmpty()) {
                return ReturnResult.error("tshark -D 未返回任何网卡");
            }
            return ReturnResult.ok(annotateLocalAddresses(ifaces));
        } catch (Exception e) {
            log.error("tshark list interfaces failed", e);
            return ReturnResult.error("无法列出网卡: " + execHint(e));
        } finally {
            if (errFile != null) {
                try {
                    Files.deleteIfExists(errFile);
                } catch (Exception ignore) {
                    // 临时文件删不掉不影响主流程
                }
            }
        }
    }

    /**
     * 给网卡补上本机 IPv4 / MAC，并标出虚拟网卡与本机出口。
     *
     * <p><strong>为什么用 OSHI 而不是 JDK 的 {@code NetworkInterface}</strong>：
     * 实测 Windows 上 JDK 的 {@code getDisplayName()} 返回的是<b>驱动名</b>
     * （{@code Realtek PCIe GbE Family Controller-WFP Native MAC Layer LightWeight Filter-0000}），
     * 而 tshark 给的是<b>连接名</b>（{@code 以太网 3}），两边根本对不上，按名字 join 恒为空。
     * OSHI 走原生调用，{@code getIfAlias()} 返回的正是「连接名 + "-" + 驱动后缀」
     * （{@code 以太网 3-WFP Native MAC Layer LightWeight Filter-0000}），
     * 取前缀就能和 tshark 的括号名对上。这是实测出来的唯一可用键。</p>
     *
     * <p>顺带解决「选不��哪个是本地网络」：同一张物理网卡在 OSHI 里会重复出现多行
     * （WFP / 802.3 / Npcap / QoS 各一个 filter），按 MAC 归并成一条；
     * 虚拟网卡（VPN / 蒲公英 / Hyper-V）的 displayName 含 Virtual / Tunnel 字样，据此标记出来 ——
     * 用户挑到这类网卡必然抓不到流量（本机实测「以太网 2」就是 OrayBoxVpnEnt 虚拟网卡，0 包）。</p>
     *
     * @param ifaces 已解析出设备名与友好名的网卡
     * @return 补齐后的列表
     */
    private List<TsharkInterfaceVO> annotateLocalAddresses(List<TsharkInterfaceVO> ifaces) {
        String exitIp = defaultRouteAddress();
        // 连接名（tshark 括号名）→ 该卡的 IP / MAC / 是否虚拟
        Map<String, List<String>> ipByName = new LinkedHashMap<>();
        Map<String, String> macByName = new LinkedHashMap<>();
        Map<String, Boolean> virtualByName = new LinkedHashMap<>();
        try {
            HardwareAbstractionLayer hal = new SystemInfo().getHardware();
            for (oshi.hardware.NetworkIF n : hal.getNetworkIFs()) {
                String alias = n.getIfAlias() == null ? "" : n.getIfAlias().trim();
                String display = n.getDisplayName() == null ? "" : n.getDisplayName();
                if (alias.isEmpty()) {
                    continue;
                }
                // 同一张卡的多个 filter 行以相同 MAC 归并，只处理一次
                String mac = n.getMacaddr() == null ? "" : n.getMacaddr().toLowerCase(Locale.ROOT);
                boolean isVirtual = isVirtualAdapter(display, alias);
                for (TsharkInterfaceVO i : ifaces) {
                    String desc = i.description() == null ? "" : i.description();
                    if (desc.isEmpty() || !alias.startsWith(desc)) {
                        continue;
                    }
                    // 别名必须正好在连接名后结束或接 '-'，否则「以太网 2」会误匹配「以太网 20」
                    if (alias.length() > desc.length() && alias.charAt(desc.length()) != '-') {
                        continue;
                    }
                    List<String> ips = ipByName.computeIfAbsent(desc, k -> new ArrayList<>());
                    if (n.getIPv4addr() != null) {
                        for (String s : n.getIPv4addr()) {
                            if (s != null && !s.isBlank() && !s.contains(":")) {
                                String v = s.replaceFirst("^.*/", "");
                                if (!"127.0.0.1".equals(v) && !ips.contains(v)) {
                                    ips.add(v);
                                }
                            }
                        }
                    }
                    if (!mac.isBlank()) {
                        macByName.putIfAbsent(desc, mac);
                    }
                    virtualByName.putIfAbsent(desc, isVirtual);
                    break;
                }
            }
        } catch (Throwable t) {
            log.warn("网卡信息补全失败，只显示友好名: {}", t.getMessage());
        }

        List<TsharkInterfaceVO> out = new ArrayList<>(ifaces.size());
        for (TsharkInterfaceVO i : ifaces) {
            String desc = i.description() == null ? "" : i.description();
            List<String> ips = ipByName.getOrDefault(desc, List.of());
            boolean isVirtual = Boolean.TRUE.equals(virtualByName.get(desc));
            boolean loopback = desc.toLowerCase(Locale.ROOT).contains("loopback");
            boolean local = exitIp != null && ips.contains(exitIp);
            out.add(new TsharkInterfaceVO(i.name(), desc, ips, macByName.get(desc), local, isVirtual, loopback));
        }
        // 排序：能连上外网的物理网卡最前，然后普通物理网卡，再虚拟网卡，环回垫底
        out.sort((a, b) -> {
            int ra = rankOf(a);
            int rb = rankOf(b);
            return ra != rb ? Integer.compare(ra, rb)
                    : String.valueOf(a.description()).compareToIgnoreCase(String.valueOf(b.description()));
        });
        return out;
    }

    /** 下拉排序权重：越小越靠前。 */
    private static int rankOf(TsharkInterfaceVO i) {
        if (i.loopback()) {
            return 3;
        }
        if (i.virtualCard()) {
            return 2;
        }
        return i.local() ? 0 : 1;
    }

    /**
     * 判断是否为虚拟网卡。
     *
     * <p>依据是驱动名里的特征词：VPN / 虚拟网卡 / 隧道 / 环回。用户挑到这类网卡
     * 基本抓不到真实流量，必须在选择器里标出来而不是让人自己踩。</p>
     *
     * @param display OSHI 的驱动显示名
     * @param alias   OSHI 的连接别名
     * @return true 表示虚拟/隧道类网卡
     */
    private static boolean isVirtualAdapter(String display, String alias) {
        String s = (display + " " + alias).toLowerCase(Locale.ROOT);
        return s.contains("virtual") || s.contains("vpn") || s.contains("tunnel")
                || s.contains("loopback") || s.contains("tap-") || s.contains("pseudo");
    }

    /**
     * 取本机默认出口 IPv4：UDP 连一个不会真发包的地址，内核会选出走哪张网卡，
     * {@code getLocalAddress()} 就是该网卡绑定的 IP。全程无 IO。
     *
     * @return 出口 IP；取不到返回 null
     */
    private static String defaultRouteAddress() {
        try (java.net.DatagramSocket s = new java.net.DatagramSocket()) {
            // 203.0.113.0/24 是 RFC 5737 保留的文档用网段，不会真的发包
            s.connect(java.net.InetAddress.getByName("203.0.113.1"), 9);
            java.net.InetAddress la = s.getLocalAddress();
            return la instanceof java.net.Inet4Address ? la.getHostAddress() : null;
        } catch (Exception e) {
            log.debug("取默认出口地址失败: {}", e.getMessage());
            return null;
        }
    }

    /** null → 空串（别处习惯用空串表示「没有」）。 */
    private static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * 检查tshark。
     * @return 检查tshark的结果
     */
    public ReturnResult<Boolean> checkTshark() {
        try {
            ProcessBuilder pb = new ProcessBuilder(TSHARK_BIN, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            /**
             * 尝试。
             * @param InputStreamReader(p.getInputStream( 输入流读取(p.获取输入流(
             */
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null && sb.length() < 300) {
                    sb.append(line).append('\n');
                }
            }
            boolean ok = p.waitFor(10L, TimeUnit.SECONDS) && sb.length() > 0;
            return ok ? ReturnResult.ok(true) : ReturnResult.error("tshark 不可用");
        } catch (Exception e) {
            return ReturnResult.error("tshark 不可用: " + execHint(e));
        }
    }

    /**
     * 把进程启动异常还原成运维可读的原因。
     *
     * <p>JVM 在可执行文件缺失时抛出的是 {@code Cannot run program "tshark": CreateProcess error=2, ...}，
     * 直接把原文回显给使用者既看不懂也无处可查，这里识别「找不到文件」一类并给出处置建议。</p>
     * @param e 进程调用异常
     * @return 面向使用者的原因描述
     */
    private String execHint(Exception e) {
        String raw = String.valueOf(e.getMessage());
        if (raw.contains("error=2") || raw.contains("No such file or directory")) {
            return "未找到 tshark 可执行文件（" + TSHARK_BIN + "），请安装 Wireshark 或设置环境变量 TSHARK_BIN 指向绝对路径";
        }
        return raw;
    }

    /**
     * 过滤器是否按显示过滤（-Y）执行。
     *
     * <p>{@code filterType} 显式给了就照办；{@code auto} 时按文本猜：带比较符、
     * 字段点号（{@code http.request.method}）或协议名（{@code http}/{@code dns}/
     * {@code tls} 等 BPF 不认识的词）都当显示过滤。{@code tcp port 8080}、
     * {@code host 10.0.0.1} 这类仍走捕获过滤。</p>
     *
     * @param filter     过滤文本
     * @param filterType auto / bpf / display
     * @return true 表示用 -Y
     */
    private static boolean useDisplayFilter(String filter, String filterType) {
        if (filter == null || filter.isBlank()) {
            return false;
        }
        String type = filterType == null ? "auto" : filterType.trim().toLowerCase(Locale.ROOT);
        if ("display".equals(type) || "y".equals(type)) {
            return true;
        }
        if ("bpf".equals(type) || "f".equals(type)) {
            return false;
        }
        String f = filter.trim().toLowerCase(Locale.ROOT);
        if (f.contains("==") || f.contains("!=") || f.contains("&&") || f.contains("||")
                || f.contains(" contains ") || f.contains(" matches ")) {
            return true;
        }
        // 字段名一定带点（http.request.uri / ip.addr / tcp.flags.syn）；BPF 里的点只出现在
        // 网络掩码（net 10.0.0.0/8）与主机名里，不会形成 a.b 这种字段形态
        if (f.matches(".*\\b[a-z_][a-z0-9_]*\\.[a-z_][a-z0-9_.]*\\b.*")) {
            return true;
        }
        String first = f.split("[\\s()]")[0];
        return switch (first) {
            case "http", "http2", "https", "tls", "ssl", "dns", "ssh", "ftp", "smtp",
                 "imap", "pop", "telnet", "mqtt", "smb", "quic", "dhcp", "ntp" -> true;
            default -> false;
        };
    }

    /**
     * 把常见的显示过滤翻译成等价的捕获过滤（BPF）。
     *
     * <p>tshark 在抓包并写文件时不能用 -Y，而用户习惯直接写 {@code http}。
     * 这里覆盖最常用的协议名与几种字段表达式；翻译不了返回 null，由调用方
     * 明确提示改用 BPF，而不是建一条立刻退出的会话。</p>
     *
     * @param display 显示过滤文本
     * @return 等价 BPF；无法翻译返回 null
     */
    private static String displayToBpf(String display) {
        String f = display.trim();
        String lower = f.toLowerCase(Locale.ROOT);
        // 纯协议名 / 逻辑组合
        switch (lower) {
            case "http", "http2":
                // 没有 BPF 的 http 关键字，只能按常用端口近似；80 之外再带几个开发端口
                return "(tcp port 80 or tcp port 8080 or tcp port 8000 or tcp port 8888)";
            case "https", "tls", "ssl":
                return "tcp port 443";
            case "dns":
                return "port 53";
            case "ssh":
                return "tcp port 22";
            case "ftp":
                return "tcp port 21";
            case "smtp":
                return "tcp port 25 or tcp port 465 or tcp port 587";
            case "telnet":
                return "tcp port 23";
            case "mqtt":
                return "tcp port 1883 or tcp port 8883";
            case "smb":
                return "tcp port 445";
            case "ntp":
                return "udp port 123";
            case "dhcp":
                return "udp port 67 or udp port 68";
            case "icmp":
                return "icmp";
            case "arp":
                return "arp";
            case "tcp", "udp", "ip":
                return lower;
            default:
                break;
        }

        // http.request.method == "GET" → 匹配 TCP 负载前 4 个字节（GET␠ / POST / HEAD…）
        java.util.regex.Matcher method = java.util.regex.Pattern
                .compile("^http\\.request\\.method\\s*==\\s*[\"']?([a-z]+)[\"']?$",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(f);
        if (method.matches()) {
            String m = method.group(1).toUpperCase(Locale.ROOT);
            String probe = (m + "    ").substring(0, 4);
            String hex = String.format("%02x%02x%02x%02x",
                    (int) probe.charAt(0), (int) probe.charAt(1), (int) probe.charAt(2), (int) probe.charAt(3));
            return "tcp[((tcp[12] & 0xf0) >> 2):4] = 0x" + hex;
        }

        // ip.addr == 1.2.3.4 / ip.src == / ip.dst ==
        java.util.regex.Matcher ip = java.util.regex.Pattern
                .compile("^ip\\.(addr|src|dst)\\s*==\\s*([0-9a-fA-F:.]+)$")
                .matcher(f);
        if (ip.matches()) {
            String prefix = switch (ip.group(1).toLowerCase(Locale.ROOT)) {
                case "src" -> "src host ";
                case "dst" -> "dst host ";
                default -> "host ";
            };
            return prefix + ip.group(2);
        }

        // tcp.port == 8080 / udp.port == 53 / port == 53
        java.util.regex.Matcher port = java.util.regex.Pattern
                .compile("^(?:(tcp|udp)\\.)?port\\s*==\\s*(\\d{1,5})$",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(f);
        if (port.matches()) {
            String proto = port.group(1) == null ? "" : port.group(1).toLowerCase(Locale.ROOT) + " ";
            return proto + "port " + port.group(2);
        }
        return null;
    }

    /**
     * 进程启动失败时，从 tshark 日志里提炼一句人话。
     *
     * @param filePath  新建的 pcap 路径（日志是同名 .log）
     * @param filter    用户填的过滤器
     * @param exitCode  进程退出码
     * @return 面向使用者的原因
     */
    private static String startupFailure(Path filePath, String filter, int exitCode) {
        String logText = "";
        try {
            Path logPath = Paths.get(filePath + ".log");
            if (Files.exists(logPath)) {
                logText = Files.readString(logPath, StandardCharsets.UTF_8);
            }
        } catch (Exception ignore) {
            // 日志读不到不影响把退出码报出去
        }
        String lower = logText.toLowerCase(Locale.ROOT);
        if (lower.contains("invalid capture filter") || lower.contains("valid display filter")) {
            return "过滤器「" + filter + "」不是捕获过滤语法，且无法自动转换成等价 BPF。"
                    + "请改用 tcp port 80、host 10.0.0.1 这类捕获过滤写法。";
        }
        if (lower.contains("display filters aren't supported")) {
            return "实时抓包不能直接使用显示过滤（tshark 在抓包写文件时不支持 -Y），请改用捕获过滤如 tcp port 80。";
        }
        String text = logText.trim();
        if (!text.isEmpty()) {
            return firstLine(text);
        }
        return "tshark 启动后立即退出（退出码 " + exitCode + "）";
    }

    /**
     * 启动capture。
     * @param name 名称
     * @param iface iface
     * @param filter 过滤器
     * @param filterType 过滤语法：auto / bpf / display
     * @param count 数量
     * @param timeout 超时
     * @param maxSizeMb 会话大小上限（MB，0 或空表示不限）
     * @return 启动capture的结果
     */
    public ReturnResult<MsTsharkSession> startCapture(String name, String iface, String filter,
                                                      String filterType, Integer count, Integer timeout,
                                                      Integer maxSizeMb) {
        try {
            if (iface == null || iface.isBlank()) {
                return ReturnResult.error("请指定网卡");
            }
            // 磁盘余量保护：抓包是持续写盘的动作，写满磁盘会波及整机
            String diskError = diskGuardError(0L, maxSizeMb);
            if (diskError != null) {
                return ReturnResult.error(diskError);
            }
            Files.createDirectories(Paths.get(CAPTURE_DIR));
            String fileName = UUID.randomUUID().toString().replace("-", "") + ".pcap";
            Path filePath = Paths.get(CAPTURE_DIR, fileName);
            List<String> cmd = new ArrayList<>();
            cmd.add(TSHARK_BIN);
            cmd.add("-i");
            cmd.add(iface);
            if (filter != null && !filter.isBlank()) {
                // BPF（-f）与显示过滤（-Y）不是同一套语法；而且 tshark 在「抓包 + -w」
                // 模式下明确不支持 -Y（会直接退出：Display filters aren't supported when
                // capturing and saving the captured packets）。所以实时抓包只能走捕获过滤：
                // 用户写 http 这类显示过滤时，先翻译成等价的 BPF，翻不了就明确拒绝，
                // 绝不带着一条注定失败的 -Y 去建会话。
                String bpf = filter.trim();
                if (useDisplayFilter(filter, filterType)) {
                    bpf = displayToBpf(filter.trim());
                    if (bpf == null) {
                        return ReturnResult.error("实时抓包只支持捕获过滤（BPF）。显示过滤「" + filter
                                + "」无法自动转换，请改用 tcp port 80、host 10.0.0.1 这类写法。");
                    }
                }
                cmd.add("-f");
                cmd.add(bpf);
            }
            if (count != null && count > 0) {
                cmd.add("-c");
                cmd.add(String.valueOf(count));
            }
            if (timeout != null && timeout > 0) {
                cmd.add("-a");
                cmd.add("duration:" + timeout);
            }
            if (maxSizeMb != null && maxSizeMb > 0) {
                // tshark 的 filesize 单位是 KB；达到后自动停止，收尾线程照常解析已有内容
                cmd.add("-a");
                cmd.add("filesize:" + (maxSizeMb * 1024L));
            }
            cmd.add("-w");
            cmd.add(filePath.toString());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(filePath + ".log"));
            Process proc = pb.start();
            // tshark 对过滤器/网卡的抱怨发生在启动后的几百毫秒内。这里等一小会儿：
            // 非 0 退出直接回错误并清掉临时文件，不再落一条「刚建就停」的会话。
            // 退出码 0 是 -c 1 这类迅速抓完的正常情况，继续走建会话 + 收尾解析。
            if (proc.waitFor(600L, TimeUnit.MILLISECONDS) && proc.exitValue() != 0) {
                String detail = startupFailure(filePath, filter, proc.exitValue());
                Files.deleteIfExists(filePath);
                Files.deleteIfExists(Paths.get(filePath + ".log"));
                return ReturnResult.error("抓包启动失败: " + detail);
            }
            MsTsharkSession session = new MsTsharkSession();
            session.setSessionName(name == null || name.isBlank() ? iface + " 抓包" : name);
            session.setCaptureInterface(iface);
            session.setCaptureFilter(filter);
            // 来源显式落库，不靠 DDL 默认值：MyBatis-Plus 的字段策略一旦被改成
            // 插入 NULL，默认值就失效，届时「抓包分析」页按 source=live 过滤会看不到自己的会话
            session.setSource(SOURCE_LIVE);
            session.setCaptureStatus(1);
            session.setPacketCount(0);
            session.setFilePath(filePath.toString());
            session.setCaptureDuration(timeout);
            session.setMaxSizeMb(maxSizeMb == null || maxSizeMb <= 0 ? 0 : maxSizeMb);
            session.setFileSize(0L);
            session.setLastError(null);
            session.setStartTime(LocalDateTime.now());
            this.sessionMapper.insert(session);
            this.running.put(session.getId(), proc);
            this.pushService.pushTsharkSession(session, session.getId());
            // 自调用不会走 Spring 代理，@Async 在这里不生效，必须显式切线程（见 asyncFinalize 的注释）
            CompletableFuture.runAsync(() -> this.asyncFinalize(session.getId(), proc, filePath));
            return ReturnResult.ok(session);
        } catch (Exception e) {
            log.error("start capture failed", e);
            return ReturnResult.error("启动抓包失败: " + execHint(e));
        }
    }

    /**
     * 等待抓包进程结束并把 pcap 解析入库。
     *
     * <p><b>这里刻意用 {@code CompletableFuture.runAsync} 而不是 {@code @Async}：</b>
     * 原实现给本方法标了 {@code @Async}，但调用方 {@link #startCapture} 是**同一个对象
     * 的自调用**（{@code this.asyncFinalize(...)}）。Spring 的 {@code @Async} 靠代理
     * 生效，自调用不经过代理 → 注解完全失效 → 方法在调用线程上同步执行，
     * {@code proc.waitFor(...)} 直接把 {@code startCapture} 阻塞住。</p>
     *
     * <p>实测症状：{@code POST /tshark/capture} 的响应时长跟着抓包时长走
     * （timeout=20s 时响应 24.6s、timeout=5s 时响应 7.8s），页面「开始抓包」按钮
     * 整段抓包期间都转圈；不限时长（timeout 为空）时更会占住 HTTP 线程直到
     * waitFor 的 60 分钟上限。副作用是抓包窗口里没有业务流量，包数为 0。</p>
     *
     * @param sessionId 会话标识
     * @param proc 抓包进程
     * @param filePath pcap 路径
     */
    public void asyncFinalize(Long sessionId, Process proc, Path filePath) {
        try {
            // 尾随循环：抓包进程还活着的时候，每 1 秒把**已经写进 pcap 的新帧**读出来入库并推送。
            // 没有它的话，抓包过程中数据库与 SSE 永远是空的 —— 页面只能等抓包结束才见数据，
            // 用户看到的就是「一直在抓但 0 包」。
            Thread tail = new Thread(() -> tailParseLoop(sessionId, proc, filePath), "tshark-tail-" + sessionId);
            tail.setDaemon(true);
            tail.start();

            boolean finished = proc.waitFor(60L, TimeUnit.MINUTES);
            if (!finished) {
                proc.destroy();
                proc.waitFor(5L, TimeUnit.SECONDS);
            }
            int exitCode = finished ? proc.exitValue() : -1;
            // 抓包已退出，尾随循环的存活标志自然消失；等它最后一轮收尾后做一次全量兜底
            tail.join(3_000L);
            // 收尾兜底走推送：尾随循环可能漏掉最后一两包，这些包页面正等着看
            String parseError = this.parsePcapToDb(sessionId, filePath, true);
            boolean stoppedByUser = stopRequested.remove(sessionId);
            MsTsharkSession session = this.sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setFileSize(fileSizeOf(filePath));
                session.setEndTime(LocalDateTime.now());
                if (session.getStartTime() != null) {
                    session.setCaptureDuration((int) Math.max(0,
                            java.time.Duration.between(session.getStartTime(), session.getEndTime()).getSeconds()));
                }
                if (parseError != null) {
                    session.setCaptureStatus(2);
                    session.setLastError(parseError);
                } else if (!stoppedByUser && exitCode != 0) {
                    session.setCaptureStatus(2);
                    session.setLastError(exitDescription(filePath, exitCode));
                } else {
                    session.setCaptureStatus(0);
                    // 达到大小上限自动停止属于「预期的正常停止」，留一条说明给用户，不算异常
                    if (reachedSizeLimit(session, filePath)) {
                        session.setLastError("已达到会话大小上限（" + session.getMaxSizeMb() + " MB），抓包已自动停止");
                    } else {
                        session.setLastError(null);
                    }
                }
                this.sessionMapper.updateById(session);
                this.pushService.pushTsharkSession(session, sessionId);
                TsharkPacketStreamRegistry reg = streamRegistry();
                if (reg != null) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("sessionId", sessionId);
                    payload.put("phase", "finished");
                    payload.put("status", session.getCaptureStatus());
                    payload.put("packetCount", session.getPacketCount() == null ? 0 : session.getPacketCount());
                    if (session.getLastError() != null) {
                        payload.put("lastError", session.getLastError());
                    }
                    reg.broadcast(sessionId, TsharkPacketStreamRegistry.EVENT_SESSION, payload);
                }
            }
        } catch (Exception e) {
            log.error("finalize capture failed, session={}", sessionId, e);
        } finally {
            this.running.remove(sessionId);
        }
    }

    /**
     * 读 pcap 文件大小（字节）；读不到返回 0。
     *
     * @param filePath pcap 路径
     * @return 文件大小
     */
    private static long fileSizeOf(Path filePath) {
        try {
            return Files.exists(filePath) ? Files.size(filePath) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 抓包进程异常退出时，从日志里提炼一句可读原因。
     *
     * @param filePath pcap 路径（同名 .log 是 tshark 输出）
     * @param exitCode 退出码
     * @return 面向用户的说明
     */
    private static String exitDescription(Path filePath, int exitCode) {
        String logText = "";
        try {
            Path logPath = Paths.get(filePath + ".log");
            if (Files.exists(logPath)) {
                logText = Files.readString(logPath, StandardCharsets.UTF_8);
            }
        } catch (Exception ignore) {
            // 日志读不到不影响把退出码报出去
        }
        String text = logText.trim();
        if (text.isEmpty()) {
            return "抓包进程异常退出（退出码 " + exitCode + "）";
        }
        String last = text;
        int i = text.lastIndexOf('\n');
        if (i >= 0 && i + 1 < text.length()) {
            last = text.substring(i + 1).trim();
        }
        return "抓包进程异常退出（退出码 " + exitCode + "）：" + firstLine(last);
    }

    /**
     * 会话是否是因为达到大小上限而停止。
     *
     * @param session  会话
     * @param filePath pcap 路径
     * @return true 表示文件已接近配置上限
     */
    private static boolean reachedSizeLimit(MsTsharkSession session, Path filePath) {
        Integer max = session.getMaxSizeMb();
        if (max == null || max <= 0) {
            return false;
        }
        long size = fileSizeOf(filePath);
        return size > 0 && size >= max * 1024L * 1024L * 95L / 100L;
    }

    /**
     * 导入 pcap 文件：落盘 → 建会话 → 异步解析入库。
     *
     * <p><b>为什么导入复用「会话 + 报文」这两张表</b>：pcap 文件与它解析出的报文本来就是
     * 「一份文件、一批报文」的关系 —— 现成的 {@code ms_tshark_session}（记文件路径、包数、
     * 起止时间）和 {@code ms_tshark_packet}（用 {@code session_id} 关联）正好就是这套关系。
     * 复用之后，报文分页、详情、协议分布、Top 流、握手生命周期以及<b>原件下载</b>
     * （{@code /sessions/{id}/pcap}）一个都不用另写，页面上只是换个入口。
     * 用 {@link #SOURCE_IMPORT} 与实时抓包区分，免得两类数据在同一张列表里混着显示。</p>
     *
     * <p><b>为什么磁盘文件名不用原名</b>：原始名可能重复（互相覆盖）、可能带 {@code ../}
     * （借路径穿越写出目录之外）、也可能是中文。所以磁盘上落 UUID，原名只作为会话名落库，
     * 下载时由 {@link #pcapFileName} 用它还给用户。</p>
     *
     * <p><b>为什么是异步</b>：几百 MB 的 pcap 解析要几十秒，同步等在这里会把 HTTP 请求
     * 拖到超时，前端只能干转圈。落库并置状态 1（解析中）后立即返回，包数在后台边解析边累加、
     * 走既有的会话推送 —— 页面列表里能直接看到包数在涨。</p>
     *
     * @param originalName 会话名（可空，空则用上传的原始文件名）
     * @param file         pcap 文件（multipart）
     * @return 新建的会话（状态 1 = 解析中）
     */
    public ReturnResult<MsTsharkSession> importPcap(String originalName, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ReturnResult.error("请选择要导入的 pcap 文件");
        }
        String name = blankToNull(originalName);
        if (name == null) {
            name = blankToNull(file.getOriginalFilename());
        }
        try {
            // 导入同样要过磁盘保护：几百 MB 的 pcap 落盘 + 解析中间产物，磁盘打满会波及整机
            String diskError = diskGuardError(file.getSize(), 0);
            if (diskError != null) {
                return ReturnResult.error(diskError);
            }
            Files.createDirectories(Paths.get(CAPTURE_DIR));
            Path filePath = Paths.get(CAPTURE_DIR, UUID.randomUUID().toString().replace("-", "") + ".pcap");
            // transferTo 而不是读进 byte[]：几百 MB 的文件读进内存再写盘，堆会被打穿
            file.transferTo(filePath.toFile());
            MsTsharkSession session = new MsTsharkSession();
            session.setSessionName(name == null ? filePath.getFileName().toString() : name);
            session.setSource(SOURCE_IMPORT);
            session.setCaptureStatus(1);
            session.setPacketCount(0);
            session.setFilePath(filePath.toString());
            session.setFileSize(file.getSize());
            session.setMaxSizeMb(0);
            session.setLastError(null);
            session.setStartTime(LocalDateTime.now());
            this.sessionMapper.insert(session);
            this.pushService.pushTsharkSession(session, session.getId());
            // 自调用不走代理，@Async 在这里不生效，必须显式切线程（同 startCapture 的注释）
            CompletableFuture.runAsync(() -> this.asyncParseImported(session.getId(), filePath));
            return ReturnResult.ok(session);
        } catch (Exception e) {
            log.error("import pcap failed", e);
            return ReturnResult.error("导入 pcap 失败: " + execHint(e));
        }
    }

    /**
     * 异步解析导入的 pcap：整份解析入库 → 回填包数与起止时间 → 状态置为已停止。
     *
     * <p>解析直接复用实时抓包收尾用的 {@link #parsePcapToDb} —— 同一套字段列定义、
     * 同一个 {@code savePacket} 落库，保证导入与实时两条路产出的数据形态完全一致
     * （只是导入不逐包推送，见 {@code push} 参数）。</p>
     *
     * @param sessionId 会话标识
     * @param filePath  文件路径
     */
    private void asyncParseImported(Long sessionId, Path filePath) {
        String parseError = null;
        try {
            parseError = this.parsePcapToDb(sessionId, filePath, false);
        } catch (Exception e) {
            log.error("parse imported pcap failed, session={}", sessionId, e);
            parseError = "解析 pcap 失败：" + e.getMessage();
        }
        // 无论解析成败都要落状态：失败时若一直停在「解析中」，页面上会永远转圈
        MsTsharkSession session = null;
        try {
            fillTimeRange(sessionId);
            session = this.sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setFileSize(fileSizeOf(filePath));
                if (parseError == null) {
                    session.setCaptureStatus(0);
                    session.setLastError(null);
                } else {
                    session.setCaptureStatus(2);
                    session.setLastError(parseError);
                }
                // 结束时间必须保留「报文时间」，不能用当前时刻覆盖：
                // 导入会话的 endTime 语义是「最后一个报文的时间」（fillTimeRange 已按报文回填），
                // 覆盖成 now 会让页面上的「报文时间范围」跨度变成十几个小时（实测 10:00 的包显示到 21:54）。
                // 只有一条报文都没解析出来时（空文件 / 不是 pcap）才用 now 收尾。
                if (session.getEndTime() == null) {
                    session.setEndTime(LocalDateTime.now());
                }
                this.sessionMapper.updateById(session);
                this.pushService.pushTsharkSession(session, sessionId);
            }
            TsharkPacketStreamRegistry reg = streamRegistry();
            if (reg != null) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("sessionId", sessionId);
                payload.put("phase", "finished");
                payload.put("status", session == null ? 0 : session.getCaptureStatus());
                payload.put("packetCount", session == null || session.getPacketCount() == null ? 0 : session.getPacketCount());
                if (session != null && session.getLastError() != null) {
                    payload.put("lastError", session.getLastError());
                }
                reg.broadcast(sessionId, TsharkPacketStreamRegistry.EVENT_SESSION, payload);
            }
        } catch (Exception e) {
            log.error("finalize imported pcap failed, session={}", sessionId, e);
        }
    }

    /**
     * 用报文的首末时间回填会话的起止时间与时长。
     *
     * <p>取「按主键排序的第一条 / 最后一条」而不是按时间列排序：解析严格按帧序插入，
     * 主键顺序就是时间顺序；按主键取一条走的是主键索引，不必对整批报文做排序。</p>
     *
     * @param sessionId 会话标识
     */
    private void fillTimeRange(Long sessionId) {
        try {
            List<MsTsharkPacket> firsts = this.packetMapper.selectList(new LambdaQueryWrapper<MsTsharkPacket>()
                    .select(MsTsharkPacket::getPacketTime)
                    .eq(MsTsharkPacket::getSessionId, sessionId)
                    .orderByAsc(MsTsharkPacket::getId)
                    .last("LIMIT 1"));
            if (firsts.isEmpty() || firsts.get(0).getPacketTime() == null) {
                // 解析出 0 条（空文件 / 非 pcap）时不动时间，避免把会话写成没有时间范围
                return;
            }
            LocalDateTime first = firsts.get(0).getPacketTime();
            List<MsTsharkPacket> lasts = this.packetMapper.selectList(new LambdaQueryWrapper<MsTsharkPacket>()
                    .select(MsTsharkPacket::getPacketTime)
                    .eq(MsTsharkPacket::getSessionId, sessionId)
                    .orderByDesc(MsTsharkPacket::getId)
                    .last("LIMIT 1"));
            LocalDateTime last = lasts.isEmpty() || lasts.get(0).getPacketTime() == null ? first : lasts.get(0).getPacketTime();
            MsTsharkSession session = this.sessionMapper.selectById(sessionId);
            if (session == null) {
                return;
            }
            session.setStartTime(first);
            session.setEndTime(last);
            session.setCaptureDuration((int) Math.max(0, java.time.Duration.between(first, last).getSeconds()));
            this.sessionMapper.updateById(session);
        } catch (Exception e) {
            log.debug("fill time range failed, session={} reason={}", sessionId, e.getMessage());
        }
    }

    /**
     * 尾随解析循环：抓包进程存活期间，增量读取 pcap 中新增的帧并入库 + 推 SSE。
     *
     * <p><strong>为什么必须这么做</strong>：抓包用的是 {@code tshark -w 文件}，
     * 而 {@code -w} 与 {@code -T fields} 的 stdout 输出<b>互斥</b>（实测加 -w 后
     * stdout 只有横幅与「N packets captured」，逐包字段一行都没有）。
     * 也就是说实时看到包只有两条路：改成 stdout 流式（就没有 pcap 可下载了），
     * 或者像现在这样保持 -w 再回头增量读文件。这里选后者，保住 pcap 下载能力。</p>
     *
     * <p>增量靠 {@code frame.number}：每轮用 {@code -Y "frame.number > 已入库最大帧号"}
     * 只取新增帧，既不会重复入库，也不必整份重读。</p>
     *
     * <p>tshark 读一个正在被写的 pcap 是安全的：文件头已落盘且长度单调增长，
     * 读到的最后一帧可能不完整，但 {@code -Y frame.number > N} 配合下一轮补读能收敛。</p>
     *
     * @param sessionId 会话 id
     * @param proc      抓包进程，用于判断是否还活着
     * @param filePath  pcap 路径
     */
    private void tailParseLoop(Long sessionId, Process proc, Path filePath) {
        long lastFrame = 0L;
        long lastSize = -1L;
        try {
            while (proc.isAlive()) {
                Thread.sleep(1000L);
                if (!Files.exists(filePath)) {
                    continue;
                }
                long size = Files.size(filePath);
                // 文件没长就别白跑一趟 tshark
                if (size <= 0L || size == lastSize) {
                    continue;
                }
                lastSize = size;
                lastFrame += parsePcapIncrementally(sessionId, filePath, lastFrame);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("尾随解析中断 session={} reason={}", sessionId, e.getMessage());
        }
    }

    /**
     * 增量解析：只取帧号大于 {@code afterFrame} 的帧，入库并推送，返回本次新增的条数。
     *
     * @param sessionId  会话 id
     * @param filePath   pcap 路径
     * @param afterFrame 已入库的最大帧号（0 表示从头）
     * @return 本次新增条数
     */
    private long parsePcapIncrementally(Long sessionId, Path filePath, long afterFrame) {
        List<String> cmd = new ArrayList<>(List.of(TSHARK_BIN, "-r", filePath.toString(),
                "-T", "fields", "-E", "separator=\t", "-E", "quote=n", "-E", "occurrence=f"));
        for (String field : TSHARK_FIELDS) {
            cmd.add("-e");
            cmd.add(field);
        }
        if (afterFrame > 0) {
            cmd.add("-Y");
            cmd.add("frame.number > " + afterFrame);
        }
        long maxSeen = afterFrame;
        long added = 0L;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8), 0x100000)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] cols = line.split("\t", -1);
                    if (cols.length > 0) {
                        try {
                            maxSeen = Math.max(maxSeen, Long.parseLong(cols[0].trim()));
                        } catch (NumberFormatException ignore) {
                            // 帧号非数字（不该发生），保持游标不变避免死循环
                        }
                    }
                    if (this.savePacket(sessionId, cols, true)) {
                        added++;
                    }
                }
            }
            p.waitFor(20L, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("增量解析失败 session={} reason={}", sessionId, e.getMessage());
        }
        if (added > 0) {
            bumpPacketCount(sessionId, added);
        }
        return Math.max(maxSeen, afterFrame + added);
    }

    /**
     * 累加会话的包数（尾随解析会分多轮到达，包数必须实时跟上，不能等结束才写）。
     *
     * @param sessionId 会话 id
     * @param delta     本轮新增
     */
    private void bumpPacketCount(Long sessionId, long delta) {
        try {
            MsTsharkSession session = this.sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setPacketCount((session.getPacketCount() == null ? 0 : session.getPacketCount())
                        + (int) delta);
                // 顺带回填文件大小：实时抓包时列表上的「大小」列要跟着涨，
                // 只在收尾写的话运行期间一直显示 0
                if (session.getFilePath() != null) {
                    session.setFileSize(fileSizeOf(Paths.get(session.getFilePath())));
                }
                this.sessionMapper.updateById(session);
                this.pushService.pushTsharkSession(session, sessionId);
            }
        } catch (Exception e) {
            log.debug("更新包数失败 session={} reason={}", sessionId, e.getMessage());
        }
    }

    /**
     * 停止capture。
     * @param sessionId 会话标识
     * @return 停止capture的结果
     */
    public ReturnResult<Void> stopCapture(Long sessionId) {
        Process proc = this.running.get(sessionId);
        if (proc == null) {
            MsTsharkSession session = this.sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setCaptureStatus(0);
                session.setEndTime(LocalDateTime.now());
                if (session.getFilePath() != null) {
                    session.setFileSize(fileSizeOf(Paths.get(session.getFilePath())));
                }
                this.sessionMapper.updateById(session);
            }
            this.pushService.pushTsharkSession(this.sessionMapper.selectById(sessionId), sessionId);
            return ReturnResult.success();
        }
        // 先登记「用户主动停止」：destroy 后退出码在 Windows 上通常非 0，
        // 收尾线程据此区分正常停止与进程崩溃
        this.stopRequested.add(sessionId);
        proc.destroy();
        try {
            proc.waitFor(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.running.remove(sessionId);
        MsTsharkSession session = this.sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setCaptureStatus(0);
            session.setEndTime(LocalDateTime.now());
            session.setLastError(null);
            if (session.getFilePath() != null) {
                session.setFileSize(fileSizeOf(Paths.get(session.getFilePath())));
            }
            this.sessionMapper.updateById(session);
        }
        this.pushService.pushTsharkSession(session, sessionId);
        return ReturnResult.success();
    }

    /**
     * 解析 pcap 文件入库。
     *
     * <p><b>为什么不用 {@code -T json}：</b>原实现用 {@code tshark -r <pcap> -T json
     * -E separator=, -x --no-duplicate-keys}，再按行读取、只把以 {@code &#123;} 开头的
     * 行当 JSON 交给解析器。但 {@code -T json} 的输出<strong>不是行分隔的</strong>：
     * 一个包是一个跨很多行的嵌套对象，整体还包在 {@code [ ... ]} 里。实测 60 个包的
     * pcap 会输出 56291 行，其中只有 136 行以 {@code &#123;} 开头，且那 136 行全是嵌套
     * 对象的<strong>起始行</strong>（去掉空白后内容就是几个字符的 {@code &#123;}），
     * 逐行读到的永远是残缺对象，解析必然失败 —— 最终入库 0 条、包数恒为 0，
     * 而抓包本身其实是成功的（pcap 有真实报文）。</p>
     *
     * <p>改用 {@code -T fields}：它保证<strong>每个包一行</strong>、字段用制表符分隔，
     * 不需要任何 JSON 解析，顺带省掉展开十六进制带来的巨量输出。</p>
     *
     * @param sessionId 会话标识
     * @param filePath 文件路径
     * @param push     是否逐包推送。实时抓包要推（页面正在看这条流）；
     *                 导入 pcap 不推 —— 几十万包逐个推会把 socket 通道冲垮，前端也不需要
     */
    private String parsePcapToDb(Long sessionId, Path filePath, boolean push) {
        try {
            if (!Files.exists(filePath) || Files.size(filePath) == 0L) {
                log.warn("capture file empty, session={}", sessionId);
                // 空文件不算错误：可能确实没有流量（实时抓包），也可能是个空的 pcap（导入），
                // 前端按「无报文」展示即可
                return null;
            }
            List<String> cmd = new ArrayList<>(List.of(TSHARK_BIN, "-r", filePath.toString(),
                    "-T", "fields", "-E", "separator=\t", "-E", "quote=n", "-E", "occurrence=f"));
            // 字段顺序即输出列顺序，逐列含义见 TSHARK_FIELDS 的注释
            for (String field : TSHARK_FIELDS) {
                cmd.add("-e");
                cmd.add(field);
            }
            // 尾随循环期间已经增量入库了一部分，这里必须跳过，否则同一批包会被插两次。
            // 用已入库条数当帧号游标：尾随解析严格按 frame.number 递增插入，
            // 所以「条数」就等于「最大帧号」。
            long already = this.packetMapper.selectCount(
                    new LambdaQueryWrapper<MsTsharkPacket>().eq(MsTsharkPacket::getSessionId, sessionId));
            if (already > 0) {
                cmd.add("-Y");
                cmd.add("frame.number > " + already);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int batch = 0;
            int flushed = 0;
            // tshark 的报错不带制表符，与正常字段行天然可分；留一行给退出码非 0 时回显
            String diagnostic = null;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8), 0x100000)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] cols = line.split("\t", -1);
                    if (cols.length < TSHARK_FIELD_COUNT) {
                        if (diagnostic == null) {
                            diagnostic = line.trim();
                        }
                        continue;
                    }
                    if (this.savePacket(sessionId, cols, push)) {
                        batch++;
                        // 每 2000 条落一次包数：导入几十万包的 pcap 要好几十秒，
                        // 若等整份解析完才写包数，前端整段时间都停在 0，看着像卡死
                        if (batch - flushed >= 2000) {
                            bumpPacketCount(sessionId, batch - flushed);
                            flushed = batch;
                        }
                    }
                }
            }
            if (batch > flushed) {
                bumpPacketCount(sessionId, batch - flushed);
            }
            if (batch > 0) {
                // 解析收尾：前端据此重拉协议分布 / Top 流（逐包增量只够更新明细表）
                TsharkPacketStreamRegistry registry = streamRegistry();
                if (registry != null) {
                    registry.broadcast(sessionId, TsharkPacketStreamRegistry.EVENT_SESSION,
                            Map.of("sessionId", sessionId, "phase", "parsed"));
                }
            } else {
                // 0 条必须出声：否则页面只显示「抓完了但一个包都没有」，
                // 用户无从判断是没流量还是解析坏了
                long total = this.packetMapper.selectCount(
                        new LambdaQueryWrapper<MsTsharkPacket>().eq(MsTsharkPacket::getSessionId, sessionId));
                if (total == 0) {
                    log.warn("capture parsed 0 packet, session={}, file={}", sessionId, filePath);
                }
            }
            boolean exited = p.waitFor(60L, TimeUnit.SECONDS);
            int exitCode = exited ? p.exitValue() : -1;
            if (!exited) {
                p.destroyForcibly();
            }
            if (exitCode != 0) {
                return diagnostic == null ? "解析失败：tshark 退出码 " + exitCode : "解析失败：" + diagnostic;
            }
            return null;
        } catch (Exception e) {
            log.error("parse pcap failed, session={}", sessionId, e);
            return "解析失败：" + e.getMessage();
        }
    }

    /**
     * 协议别名表：只放泛化规则覆盖不到的那些。键一律大写（查询前会 toUpperCase）。
     */
    private static final Map<String, String> PROTOCOL_ALIAS = Map.ofEntries(
            Map.entry("HTTP2", "HTTP"),
            Map.entry("HTTP/2", "HTTP"),
            Map.entry("HTTP2 SERVER PUSH", "HTTP"),
            Map.entry("MDNS", "DNS"),
            Map.entry("LLMNR", "DNS"),
            Map.entry("SSL", "TLS"),
            Map.entry("SSL3", "TLS"),
            Map.entry("TLSV1", "TLS"),
            Map.entry("TLSV1.1", "TLS"),
            Map.entry("TLSV1.2", "TLS"),
            Map.entry("TLSV1.3", "TLS"),
            Map.entry("MQTTV3.1", "MQTT"),
            Map.entry("MQTTV3.1.1", "MQTT"),
            Map.entry("MQTTV5", "MQTT"),
            Map.entry("BOOTP", "DHCP"),
            Map.entry("NETBIOS", "SMB"),
            Map.entry("SMB2", "SMB"),
            Map.entry("ICMPV6", "ICMP"),
            Map.entry("TCP (SYN,ACK)", "TCP"),
            Map.entry("TCP (RST,ACK)", "TCP"),
            Map.entry("TCP (ACK)", "TCP")
    );

    /**
     * 协议名归一化：把 tshark 的原始协议名收敛成少数几个规范名。
     *
     * <p><strong>为什么要归一</strong>：{@code _ws.col.Protocol} 给的是最内层协议，
     * 且带版本后缀 —— TLS 握手是 {@code TLSv1.2}、MQTT 是 {@code MQTTv3.1.1}、
     * HTTP/2 是 {@code HTTP2}。直接入库的话「协议分布」饼图会把 TLSv1.2 / TLSv1.3 /
     * MQTTv3.1.1 各切成一瓣，一个 TLS 会话在图上碎成好几块，既不好看也不便于
     * 判断谁占大头。</p>
     *
     * <p>归一放在<b>入库时</b>而不是展示层：协议统计是按库里的 protocol 聚合的，
     * 只在前端换显示的话图表仍然是碎的。</p>
     */
    private static String simplifyProtocol(String raw) {
        if (raw == null || raw.isBlank()) {
            return "OTHER";
        }
        String p = raw.trim();
        String alias = PROTOCOL_ALIAS.get(p.toUpperCase(Locale.ROOT));
        if (alias != null) {
            return alias;
        }
        // 剥版本后缀：先去掉 v1.2 / V3.1.1 这种带 v 的版本段，再去掉裸数字尾巴（HTTP2）
        String base = p.replaceAll("[vV]\\d+(\\.\\d+)*$", "").replaceAll("\\d+$", "").trim();
        // 剥空说明原名里没有字母（如 802.1Q 这类），保留原样大写即可
        if (base.isEmpty() || base.chars().noneMatch(Character::isLetter)) {
            return p.toUpperCase(Locale.ROOT);
        }
        return base.toUpperCase(Locale.ROOT);
    }

    /**
     * 还原某个包所属 TCP 流的握手 / 关闭生命周期。
     *
     * <p><strong>为什么不加 tcp_flags 列</strong>：tshark 的 Info 列（{@code packet_info}）里已经带了
     * 标志位 —— {@code [SYN]}、{@code [SYN, ACK]}、{@code [PSH, ACK]}、{@code [RST, ACK]}、
     * {@code [FIN, ACK]}。从方括号里解析就够画握手时序用了。为此新增一列要动 schema 迁移，
     * 收益不抵成本；等真要做「按标志位过滤」时再落库。</p>
     *
     * @param packetId 数据包 id
     * @param limit    返回的最大步骤数（超出只保留首尾与锚点包）
     * @return 生命周期；包不存在 / 非 TCP 时返回 null
     */
    public TsharkHandshakeVO handshakeOf(Long packetId, Integer limit) {
        MsTsharkPacket anchor = this.packetMapper.selectById(packetId);
        if (anchor == null || anchor.getSessionId() == null || !isTcpBased(anchor)) {
            // 非 TCP 系协议谈不上握手；UDP/ICMP/ARP 直接不返回，前端据此提示不支持
            return null;
        }

        // 不按协议过滤：同一条 TCP 流里 SYN / FIN 会被 simplifyProtocol 归一成 TCP，
        // 而数据帧可能是 HTTP / TLS / SSH。按 anchor 的协议过滤会把握手与挥手全丢掉，
        // 页面上就只剩中间几个数据帧 —— 看起来正是「一采到数据生命周期就结束了」。
        // SQL 里先按「两端 IP 的两种方向」收窄，端口与方向仍由 isSameFlow 精确过滤，
        // 避免一次抓了几十万包的会话把整张报文表读进内存。
        List<MsTsharkPacket> all = this.packetMapper.selectList(
                new LambdaQueryWrapper<MsTsharkPacket>()
                        .eq(MsTsharkPacket::getSessionId, anchor.getSessionId())
                        .and(q -> q
                                .nested(a -> a.eq(MsTsharkPacket::getSourceIp, anchor.getSourceIp())
                                        .eq(MsTsharkPacket::getDestIp, anchor.getDestIp()))
                                .or()
                                .nested(b -> b.eq(MsTsharkPacket::getSourceIp, anchor.getDestIp())
                                        .eq(MsTsharkPacket::getDestIp, anchor.getSourceIp())))
                        .orderByAsc(MsTsharkPacket::getPacketTime)
                        .orderByAsc(MsTsharkPacket::getId));

        List<MsTsharkPacket> flow = all.stream()
                .filter(p -> isSameFlow(p, anchor.getSourceIp(), anchor.getSourcePort(),
                        anchor.getDestIp(), anchor.getDestPort()))
                .toList();
        if (flow.isEmpty()) {
            return null;
        }

        // 方向以第一个裸 SYN 为准：发出它的一侧是客户端（服务端回 SYN+ACK）。
        // 抓包开始前连接就建好了时没有 SYN，退而用 SYN+ACK 的发送方反推，最后才用
        // anchor 的源端兜底。原实现比较的是 anchor 的源与目的（恒为 false），
        // 于是 client/server 永远按固定方向取，命中响应包时两条泳道会整个反过来。
        MsTsharkPacket syn = findFirst(flow, true);
        MsTsharkPacket synAck = findFirst(flow, false);
        MsTsharkPacket origin;
        boolean originIsServer;
        if (syn != null) {
            origin = syn;
            originIsServer = false;
        } else if (synAck != null) {
            origin = synAck;
            originIsServer = true;
        } else {
            // 连 SYN+ACK 都没抓到（抓包开始前就建好连）：按「低端口一般是服务端」猜一次，
            // 端口缺失时再退回 anchor 源端。比原来恒把目的端当客户端要少错很多。
            origin = anchor;
            originIsServer = anchor.getSourcePort() != null && anchor.getDestPort() != null
                    && anchor.getSourcePort() < anchor.getDestPort();
        }
        String clientIp = originIsServer ? origin.getDestIp() : origin.getSourceIp();
        Integer clientPort = originIsServer ? origin.getDestPort() : origin.getSourcePort();
        String serverIp = originIsServer ? origin.getSourceIp() : origin.getDestIp();
        Integer serverPort = originIsServer ? origin.getSourcePort() : origin.getDestPort();

        return buildHandshake(anchor.getSessionId(), clientIp, clientPort, serverIp, serverPort, flow,
                packetId, limit == null ? DEFAULT_HANDSHAKE_LIMIT : Math.max(40, Math.min(limit, 5000)));
    }

    /**
     * 该报文是否跑在 TCP 之上（能还原握手 / 挥手）。
     *
     * @param packet 报文
     * @return true 表示支持生命周期
     */
    private static boolean isTcpBased(MsTsharkPacket packet) {
        String proto = packet.getProtocol() == null ? "" : packet.getProtocol().trim().toUpperCase(Locale.ROOT);
        return switch (proto) {
            case "TCP", "HTTP", "HTTPS", "TLS", "SSH", "FTP", "SMTP", "SMB", "MQTT", "TELNET" -> true;
            default -> false;
        };
    }

    /**
     * 找出第一条握手包。
     *
     * @param flow     按时间排好的整条流
     * @param bareSyn  true = 裸 SYN（客户端发起）；false = SYN+ACK（服务端回应）
     * @return 命中的报文；没有返回 null
     */
    private static MsTsharkPacket findFirst(List<MsTsharkPacket> flow, boolean bareSyn) {
        for (MsTsharkPacket p : flow) {
            String flags = extractFlags(p.getPacketInfo() == null ? "" : p.getPacketInfo());
            boolean syn = flags.contains("SYN");
            boolean ack = flags.contains("ACK");
            if (bareSyn ? (syn && !ack) : (syn && ack)) {
                return p;
            }
        }
        return null;
    }

    /** 报文属于该流：正向或反向均可。 */
    private static boolean isSameFlow(MsTsharkPacket p, String cIp, Integer cPort,
                                      String sIp, Integer sPort) {
        return (eq(p.getSourceIp(), cIp) && eq(p.getSourcePort(), cPort)
                && eq(p.getDestIp(), sIp) && eq(p.getDestPort(), sPort))
                || (eq(p.getSourceIp(), sIp) && eq(p.getSourcePort(), sPort)
                && eq(p.getDestIp(), cIp) && eq(p.getDestPort(), cPort));
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    /**
     * 把一条流按时间排好、解析标志位、判定握手次数与关闭状态。
     */
    private TsharkHandshakeVO buildHandshake(Long sessionId, String cIp, Integer cPort,
                                            String sIp, Integer sPort, List<MsTsharkPacket> flow,
                                            Long anchorId, int limit) {
        java.time.format.DateTimeFormatter hhmm = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
        List<TsharkHandshakeVO.Step> steps = new ArrayList<>();
        LocalDateTime t0 = flow.get(0).getPacketTime();
        for (MsTsharkPacket p : flow) {
            long relMs = 0L;
            if (t0 != null && p.getPacketTime() != null) {
                relMs = java.time.Duration.between(t0, p.getPacketTime()).toMillis();
            }
            String info = p.getPacketInfo() == null ? "" : p.getPacketInfo();
            String flags = extractFlags(info);
            steps.add(new TsharkHandshakeVO.Step(p.getId(),
                    p.getPacketTime() == null ? null : p.getPacketTime().format(hhmm),
                    eq(p.getSourceIp(), cIp) && eq(p.getSourcePort(), cPort),
                    flags, extractAfter(info, "Seq="), extractAfter(info, "Ack="),
                    p.getPacketLength(), info, relMs,
                    phaseOf(p, flags, info),
                    isRetransmission(info),
                    flags.contains("RST")));
        }

        // 握手判定：第一个 SYN（不带 ACK）定客户端方向，其后 SYN+ACK 与裸 ACK
        int synIdx = -1;
        boolean completed = false;
        boolean fourWay = false;
        String fourWayReason = null;
        for (int i = 0; i < steps.size(); i++) {
            TsharkHandshakeVO.Step f = steps.get(i);
            String flags = f.flags() == null ? "" : f.flags();
            boolean syn = flags.contains("SYN");
            boolean ack = flags.contains("ACK");
            if (synIdx < 0) {
                if (syn && !ack && f.fromClient()) {
                    synIdx = i;
                    // SYN 里已经带数据 = TCP Fast Open：报文数仍是 3，但握手与首批数据合并
                    String info = f.info() == null ? "" : f.info();
                    if (info.contains("Len=") && !info.contains("Len=0")) {
                        fourWay = true;
                        fourWayReason = "SYN 携带数据（TCP Fast Open），握手与首批数据合并";
                    }
                }
                continue;
            }
            if (syn && ack) {
                // 服务端回应 —— 继续等客户端的裸 ACK
            } else if (syn && !ack) {
                fourWay = true;
                fourWayReason = "出现第二个不带 ACK 的 SYN（SYN 重传或重发）";
            } else if (!syn && ack && !completed) {
                completed = true;
                break;
            }
        }
        // SYN / SYN-ACK / 裸 ACK 都要在，才算三次握手走完
        boolean synAckSeen = false;
        if (synIdx >= 0) {
            for (int i = synIdx + 1; i < steps.size(); i++) {
                String fl = steps.get(i).flags() == null ? "" : steps.get(i).flags();
                if (fl.contains("SYN") && fl.contains("ACK")) {
                    synAckSeen = true;
                }
                if (synAckSeen && !fl.contains("SYN")) {
                    completed = true;
                    break;
                }
            }
        }

        boolean clientFin = false;
        boolean serverFin = false;
        for (TsharkHandshakeVO.Step f : steps) {
            if (f.flags() != null && f.flags().contains("FIN")) {
                if (f.fromClient()) {
                    clientFin = true;
                } else {
                    serverFin = true;
                }
            }
        }
        boolean closed = clientFin && serverFin;

        String kind;
        String label;
        String detail;
        if (!completed) {
            kind = "NONE";
            label = "未完成握手";
            detail = synIdx < 0 ? "本流内没有抓到 SYN，连接可能建立于抓包开始之前"
                    : "只抓到部分握手包，可能已停止抓包或中途丢包";
        } else if (fourWay) {
            kind = "FOUR_WAY";
            label = "四次握手";
            detail = fourWayReason;
        } else {
            kind = "THREE_WAY";
            label = "三次握手";
            detail = null;
        }
        // 关键节点指标：RTT = SYN → SYN-ACK；TTFB = 首个 HTTP 请求 → 首个 HTTP 响应
        Long rttMs = computeRtt(steps);
        Long ttfbMs = computeTtfb(steps);

        int totalSteps = steps.size();
        List<TsharkHandshakeVO.Step> finalSteps = steps;
        boolean truncated = false;
        if (totalSteps > limit) {
            finalSteps = truncateSteps(steps, limit, anchorId);
            truncated = true;
        }
        return new TsharkHandshakeVO(sessionId, cIp, cPort, sIp, sPort,
                new TsharkHandshakeVO.Handshake(kind, label, detail, completed, closed),
                finalSteps, totalSteps, truncated, rttMs, ttfbMs);
    }

    /**
     * 报文所处阶段：TCP 握手 / TLS 握手 / HTTP 请求响应 / 数据传输 / 关闭。
     *
     * @param p     报文
     * @param flags 解析出的标志位
     * @param info  Info 列文本
     * @return 阶段名
     */
    private static String phaseOf(MsTsharkPacket p, String flags, String info) {
        // 关闭优先：带 FIN / RST 的帧即使协议是 HTTP 也属于挥手
        if (flags.contains("FIN") || flags.contains("RST")) {
            return "CLOSE";
        }
        if (flags.contains("SYN")) {
            return "TCP";
        }
        String proto = p.getProtocol() == null ? "" : p.getProtocol().toUpperCase(Locale.ROOT);
        String lower = info == null ? "" : info.toLowerCase(Locale.ROOT);
        if ("TLS".equals(proto) || lower.startsWith("client hello") || lower.startsWith("server hello")
                || lower.contains("certificate") || lower.startsWith("change cipher")
                || lower.startsWith("application data") || lower.startsWith("encrypted handshake")) {
            return "TLS";
        }
        if ("HTTP".equals(proto) || lower.startsWith("http/")
                || lower.matches("^(get|post|put|delete|head|options|patch|trace|connect)\\s.*")) {
            return "HTTP";
        }
        return "DATA";
    }

    /**
     * 是否为重传 / 乱序 / 重复 ACK。
     *
     * @param info Info 列文本
     * @return true 表示标注为异常
     */
    private static boolean isRetransmission(String info) {
        if (info == null) {
            return false;
        }
        String lower = info.toLowerCase(Locale.ROOT);
        return lower.contains("retransmission") || lower.contains("dup ack")
                || lower.contains("duplicate ack") || lower.contains("out-of-order")
                || lower.contains("out of order");
    }

    /**
     * 计算 TCP 建连 RTT（SYN → SYN-ACK）。
     *
     * @param steps 全部步骤
     * @return 毫秒；抓不到返回 null
     */
    private static Long computeRtt(List<TsharkHandshakeVO.Step> steps) {
        TsharkHandshakeVO.Step syn = null;
        for (TsharkHandshakeVO.Step s : steps) {
            String f = s.flags() == null ? "" : s.flags();
            if (s.fromClient() && f.contains("SYN") && !f.contains("ACK")) {
                syn = s;
                break;
            }
        }
        if (syn == null) {
            return null;
        }
        for (TsharkHandshakeVO.Step s : steps) {
            if (s.relativeMs() < syn.relativeMs()) {
                continue;
            }
            String f = s.flags() == null ? "" : s.flags();
            if (!s.fromClient() && f.contains("SYN") && f.contains("ACK")) {
                return s.relativeMs() - syn.relativeMs();
            }
        }
        return null;
    }

    /**
     * 计算 TTFB（首个 HTTP 请求 → 首个 HTTP 响应）。
     *
     * @param steps 全部步骤
     * @return 毫秒；没有 HTTP 请求/响应时返回 null
     */
    private static Long computeTtfb(List<TsharkHandshakeVO.Step> steps) {
        TsharkHandshakeVO.Step request = null;
        for (TsharkHandshakeVO.Step s : steps) {
            if (s.fromClient() && "HTTP".equals(s.phase()) && isHttpRequest(s.info())) {
                request = s;
                break;
            }
        }
        if (request == null) {
            return null;
        }
        for (TsharkHandshakeVO.Step s : steps) {
            if (s.relativeMs() < request.relativeMs()) {
                continue;
            }
            if (!s.fromClient() && "HTTP".equals(s.phase()) && !isHttpRequest(s.info())) {
                return s.relativeMs() - request.relativeMs();
            }
        }
        return null;
    }

    /** Info 是否是 HTTP 请求行。 */
    private static boolean isHttpRequest(String info) {
        return info != null
                && info.toUpperCase(Locale.ROOT).matches("^(GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH|TRACE|CONNECT)\\s.*");
    }

    /**
     * 步骤过多时截断：保留首尾若干步与用户点中的锚点包，中间省略。
     *
     * @param steps    全部步骤
     * @param limit    上限
     * @param anchorId 锚点报文 id（可空）
     * @return 截断后的步骤（仍按时间序）
     */
    private static List<TsharkHandshakeVO.Step> truncateSteps(List<TsharkHandshakeVO.Step> steps,
                                                              int limit, Long anchorId) {
        int total = steps.size();
        int head = Math.max(1, (limit + 1) / 2);
        int tail = Math.max(0, limit - head);
        java.util.TreeSet<Integer> indexes = new java.util.TreeSet<>();
        for (int i = 0; i < head && i < total; i++) {
            indexes.add(i);
        }
        for (int i = Math.max(0, total - tail); i < total; i++) {
            indexes.add(i);
        }
        if (anchorId != null) {
            for (int i = 0; i < total; i++) {
                if (anchorId.equals(steps.get(i).packetId())) {
                    indexes.add(i);
                    break;
                }
            }
        }
        List<TsharkHandshakeVO.Step> out = new ArrayList<>(indexes.size());
        for (int i : indexes) {
            out.add(steps.get(i));
        }
        return out;
    }

    /**
     * 从 tshark Info 列取方括号中的标志位，如 {@code [SYN, ACK]} → {@code SYN,ACK}。
     *
     * <p>去掉空格：画时序图与做 contains 判断都不需要，且前后端口径一致。</p>
     *
     * @param info Info 列文本
     * @return 标志位；解析不出返回空串
     */
    private static String extractFlags(String info) {
        int l = info == null ? -1 : info.indexOf('[');
        if (l < 0) {
            return "";
        }
        int r = info.indexOf(']', l);
        return r < 0 ? "" : info.substring(l + 1, r).replace(" ", "");
    }

    /**
     * 取 {@code key=} 之后到下一个空格为止的值（用于 Seq / Ack）。
     *
     * @param info Info 列文本
     * @param key  形如 {@code "Seq="}
     * @return 值的文本；没有则返回空串
     */
    private static String extractAfter(String info, String key) {
        if (info == null) {
            return "";
        }
        int i = info.indexOf(key);
        if (i < 0) {
            return "";
        }
        int from = i + key.length();
        int end = info.indexOf(' ', from);
        return end < 0 ? info.substring(from) : info.substring(from, end);
    }

    /**
     * 取会话；不存在返回 null（控制器用它决定 404）。
     *
     * @param sessionId 会话 id
     * @return 会话或 null
     */
    public MsTsharkSession getSession(Long sessionId) {
        return this.sessionMapper.selectById(sessionId);
    }

    /**
     * 建立数据包的 SSE 订阅。
     *
     * @param sessionId 会话 id
     * @return 待返回给前端的 SseEmitter
     */
    public SseEmitter subscribePacketStream(Long sessionId) {
        TsharkPacketStreamRegistry registry = streamRegistry();
        if (registry == null) {
            // 注册表没装配属于异常配置，抛出让前端明确看到「推送不可用」，而不是静默无数据
            throw new IllegalStateException("抓包 SSE 注册表未装配");
        }
        return registry.subscribe(sessionId);
    }

    /**
     * 推给前端的数据包精简载荷。
     *
     * <p>刻意<b>不带</b> {@code rawData}：那是 tshark 的完整 JSON，单包能到几 KB，
     * 抓包高峰每秒几十上百包，全量推会把连接和浏览器都拖垮 ——
     * 页面需要原始数据时按 id 走详情接口单取即可。</p>
     *
     * @param packet 已入库的数据包
     * @return 精简载荷
     */
    private Map<String, Object> toStreamPayload(MsTsharkPacket packet) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", packet.getId());
        m.put("sessionId", packet.getSessionId());
        m.put("packetTime", packet.getPacketTime());
        m.put("protocol", packet.getProtocol());
        m.put("sourceIp", packet.getSourceIp());
        m.put("sourcePort", packet.getSourcePort());
        m.put("destIp", packet.getDestIp());
        m.put("destPort", packet.getDestPort());
        m.put("packetLength", packet.getPacketLength());
        m.put("packetInfo", packet.getPacketInfo());
        return m;
    }

    /**
     * 落库一个数据包。
     *
     * @param sessionId 会话标识
     * @param cols      按 {@link #TSHARK_FIELDS} 顺序切出的列（制表符分隔）
     * @param push      是否逐包推送（导入 pcap 时为 false，避免几十万条推送把通道冲垮）
     * @return 是否成功入库
     */
    private boolean savePacket(Long sessionId, String[] cols, boolean push) {
        try {
            if (cols.length < TSHARK_FIELD_COUNT) {
                return false;
            }
            MsTsharkPacket packet = new MsTsharkPacket();
            packet.setSessionId(sessionId);
            // cols[0] 是 frame.number：既入 frame_no 列（供显示过滤的 frame.number 用），
            // 也作为增量解析的游标
            packet.setFrameNo(parseLongOrNull(cols[0]));
            packet.setPacketTime(epochToDateTime(cols[1]));
            packet.setProtocol(simplifyProtocol(blankToNull(cols[2])));
            // IPv4 / IPv6 各取第一个非空；端口同理（TCP 优先，其次 UDP）
            packet.setSourceIp(firstNonBlank(cols[3], cols[4]));
            packet.setSourcePort(parseIntOrNull(firstNonBlank(cols[5], cols[6])));
            packet.setDestIp(firstNonBlank(cols[7], cols[8]));
            packet.setDestPort(parseIntOrNull(firstNonBlank(cols[9], cols[10])));
            packet.setPacketLength(parseIntOrNull(cols[11]));
            packet.setPacketInfo(blankToNull(cols[12]));
            this.packetMapper.insert(packet);
            if (push) {
                // 实时推送：每包广播（数据 id = 会话 id，前端按当前会话过滤）
                this.pushService.pushTsharkPacket(packet, sessionId);
                // SSE 推送：按会话分组，只推给正在看这个会话的连接。
                // 载荷走精简字段（rawData 是整块 tshark JSON，逐包推会把带宽吃光），
                // 完整原始数据仍走详情接口按需取。
                TsharkPacketStreamRegistry registry = streamRegistry();
                if (registry != null) {
                    registry.broadcast(sessionId, TsharkPacketStreamRegistry.EVENT_PACKET, toStreamPayload(packet));
                }
            }
            return true;
        } catch (Exception e) {
            log.debug("skip packet save error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 取第一个非空值。
     *
     * @param values 候选值
     * @return 第一个非空值；全空返回 null
     */
    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /**
     * 空串转 null（避免把「无此字段」写成空字符串入库）。
     *
     * @param v 原始值
     * @return 去空白后的值；空白则 null
     */
    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /**
     * 宽松解析整数。
     *
     * @param v 原始值
     * @return 整数；无法解析返回 null
     */
    private static Integer parseIntOrNull(String v) {
        String t = blankToNull(v);
        if (t == null) {
            return null;
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 宽松解析长整数。
     *
     * @param v 原始值
     * @return 长整数；无法解析返回 null
     */
    private static Long parseLongOrNull(String v) {
        String t = blankToNull(v);
        if (t == null) {
            return null;
        }
        try {
            return Long.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 秒级时间戳转时间。
     *
     * <p>用包自带的时间戳而非入库时刻：抓包结束后会一次性解析整个文件，
     * 用 {@code LocalDateTime.now()} 会让同一批包全挤在同一秒上。</p>
     *
     * @param epochSeconds 秒级时间戳（可带小数）
     * @return 时间；无法解析返回当前时间
     */
    private static LocalDateTime epochToDateTime(String epochSeconds) {
        String t = blankToNull(epochSeconds);
        if (t == null) {
            return LocalDateTime.now();
        }
        try {
            long ms = (long) (Double.parseDouble(t) * 1000D);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            return LocalDateTime.now();
        }
    }

    /**
     * page会话。
     *
     * <p>按来源过滤：{@code live} 给「抓包分析」页、{@code import} 给「PCAP 分析」页。
     * 不传则全部返回（老前端与外部调用者的兼容路径）。</p>
     *
     * @param page   page
     * @param size   大小
     * @param source 会话来源（live / import，可空）
     * @return page会话的结果
     */
    public ReturnResult<IPage<MsTsharkSession>> pageSessions(int page, int size, String source) {
        LambdaQueryWrapper<MsTsharkSession> w = new LambdaQueryWrapper<>();
        if (source != null && !source.isBlank()) {
            w.eq(MsTsharkSession::getSource, source.trim().toLowerCase(Locale.ROOT));
        }
        w.orderByDesc(MsTsharkSession::getId);
        IPage<MsTsharkSession> p = this.sessionMapper.selectPage(new Page<>(page, size), w);
        return ReturnResult.ok(p);
    }

    /**
     * page数据包。
     *
     * @param sessionId     会话标识
     * @param page          page
     * @param size          大小
     * @param displayFilter 显示过滤表达式（可空）。支持协议名 / ip.addr / tcp.port /
     *                      frame contains / http.request.method 等常用字段与 and/or/not，
     *                      不认识的裸词退化为全文关键字匹配。
     * @return page数据包的结果
     */
    public ReturnResult<IPage<MsTsharkPacket>> pagePackets(Long sessionId, int page, int size, String displayFilter) {
        TsharkDisplayFilter.SqlFilter filter;
        try {
            filter = TsharkDisplayFilter.parse(displayFilter);
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        }
        LambdaQueryWrapper<MsTsharkPacket> w = new LambdaQueryWrapper<>();
        w.eq(sessionId != null, MsTsharkPacket::getSessionId, sessionId);
        applyFilter(w, filter);
        w.orderByDesc(MsTsharkPacket::getId);
        IPage<MsTsharkPacket> p = this.packetMapper.selectPage(new Page<>(page, size), w);
        return ReturnResult.ok(p);
    }

    /**
     * 取首行（tshark 的报错文案常带换行，全塞进一条消息会很难读）。
     */
    private static String firstLine(String s) {
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }

    /**
     * 取会话对应的 pcap 文件。
     *
     * <p>前端要的是「把原始 pcap 拿走去 Wireshark 里打开」，所以这里不返回文件内容，
     * 只把可读的文件句柄交出去由 Controller 流式输出。</p>
     *
     * @param sessionId 会话标识
     * @return pcap 文件；会话不存在 / 未落盘 / 文件已被清理时返回 null
     */
    public Resource pcapResource(Long sessionId) {
        MsTsharkSession session = this.sessionMapper.selectById(sessionId);
        if (session == null || session.getFilePath() == null || session.getFilePath().isBlank()) {
            return null;
        }
        Path p = Path.of(session.getFilePath());
        // 只认库里已登记、且仍在磁盘上的路径；文件不在了（被清理/磁盘拔了）按缺失处理
        if (!Files.isReadable(p)) {
            return null;
        }
        return new FileSystemResource(p);
    }

    /**
     * 会话 pcap 的下载文件名：优先用会话名，非法字符替换掉；没有名字就用网卡 + id。
     *
     * @param sessionId 会话标识
     * @return 安全的文件名（不含路径分隔符）
     */
    public String pcapFileName(Long sessionId) {
        MsTsharkSession session = this.sessionMapper.selectById(sessionId);
        String base = session == null ? null : session.getSessionName();
        if (base == null || base.isBlank()) {
            base = (session == null || session.getCaptureInterface() == null
                    ? "capture" : session.getCaptureInterface()) + "-" + sessionId;
        }
        // 去掉路径分隔符与控制字符，避免拼进 Content-Disposition 后出问题
        String safe = base.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").trim();
        return (safe.isEmpty() ? "capture-" + sessionId : safe) + ".pcap";
    }

    /**
     * 获取数据包。
     * @param id 标识
     * @return 获取数据包的结果
     */
    public ReturnResult<MsTsharkPacket> getPacket(Long id) {
        MsTsharkPacket packet = this.packetMapper.selectById(id);
        return packet == null ? ReturnResult.error("数据包不存在: " + id) : ReturnResult.ok(packet);
    }

    /**
     * 协议stats。
     *
     * @param sessionId     会话标识
     * @param displayFilter 显示过滤（可空）
     * @return 协议stats的结果
     */
    public ReturnResult<List<TsharkProtocolStatVO>> protocolStats(Long sessionId, String displayFilter) {
        TsharkDisplayFilter.SqlFilter filter;
        try {
            filter = TsharkDisplayFilter.parse(displayFilter);
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        }
        try {
            if (filter.isEmpty()) {
                // SQL 层聚合，不再全量加载报文到内存
                List<Map<String, Object>> rows = this.packetMapper.selectProtocolStatsBySessionId(sessionId);
                List<TsharkProtocolStatVO> result = new ArrayList<>(rows.size());
                for (Map<String, Object> row : rows) {
                    Object protoCell = cell(row, "protocol");
                    String proto = protoCell == null ? "OTHER" : String.valueOf(protoCell);
                    int count = (int) number(cell(row, "count"));
                    long bytes = (long) number(cell(row, "bytes"));
                    result.add(new TsharkProtocolStatVO(proto, count, bytes));
                }
                return ReturnResult.ok(result);
            }
            // 带显示过滤时在 Java 侧聚合：过滤表达式是动态的，SQL 聚合得拼动态 SQL，
            // 这里命中量有上限保护，且只需协议与长度两列
            List<MsTsharkPacket> packets = filteredPackets(sessionId, filter);
            Map<String, long[]> agg = new LinkedHashMap<>();
            for (MsTsharkPacket p : packets) {
                String proto = p.getProtocol() == null || p.getProtocol().isBlank() ? "OTHER" : p.getProtocol();
                long[] cell = agg.computeIfAbsent(proto, k -> new long[2]);
                cell[0]++;
                cell[1] += p.getPacketLength() == null ? 0L : p.getPacketLength();
            }
            List<TsharkProtocolStatVO> result = new ArrayList<>(agg.size());
            agg.forEach((proto, cell) -> result.add(new TsharkProtocolStatVO(proto, (int) cell[0], cell[1])));
            result.sort((a, b) -> Integer.compare(b.count(), a.count()));
            return ReturnResult.ok(result);
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * top流。
     *
     * @param sessionId     会话标识
     * @param limit         限制
     * @param displayFilter 显示过滤（可空）
     * @return top流的结果
     */
    public ReturnResult<List<TsharkFlowVO>> topFlows(Long sessionId, int limit, String displayFilter) {
        TsharkDisplayFilter.SqlFilter filter;
        try {
            filter = TsharkDisplayFilter.parse(displayFilter);
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        }
        int max = Math.max(0, limit);
        try {
            List<TsharkFlowVO> result = new ArrayList<>();
            if (filter.isEmpty()) {
                // SQL 层聚合，不再全量加载报文到内存；SQL 已带 LIMIT 20
                List<Map<String, Object>> rows = this.packetMapper.selectTopFlowsBySessionId(sessionId);
                for (Map<String, Object> row : rows) {
                    Object flowCell = cell(row, "flow");
                    Object sourceIpCell = cell(row, "sourceIp");
                    Object destIpCell = cell(row, "destIp");
                    result.add(new TsharkFlowVO(
                            flowCell == null ? "" : String.valueOf(flowCell),
                            (int) number(cell(row, "count")),
                            (long) number(cell(row, "bytes")),
                            sourceIpCell == null ? null : String.valueOf(sourceIpCell),
                            toInteger(cell(row, "sourcePort")),
                            destIpCell == null ? null : String.valueOf(destIpCell),
                            toInteger(cell(row, "destPort"))));
                }
            } else {
                List<MsTsharkPacket> packets = filteredPackets(sessionId, filter);
                Map<String, long[]> agg = new LinkedHashMap<>();
                Map<String, TsharkFlowVO> meta = new LinkedHashMap<>();
                for (MsTsharkPacket p : packets) {
                    String key = (p.getSourceIp() == null ? "" : p.getSourceIp()) + "|"
                            + (p.getSourcePort() == null ? "" : p.getSourcePort()) + "|"
                            + (p.getDestIp() == null ? "" : p.getDestIp()) + "|"
                            + (p.getDestPort() == null ? "" : p.getDestPort());
                    long[] cell = agg.computeIfAbsent(key, k -> new long[2]);
                    cell[0]++;
                    cell[1] += p.getPacketLength() == null ? 0L : p.getPacketLength();
                    meta.putIfAbsent(key, new TsharkFlowVO(
                            flowText(p.getSourceIp(), p.getSourcePort(), p.getDestIp(), p.getDestPort()),
                            0, 0L, p.getSourceIp(), p.getSourcePort(), p.getDestIp(), p.getDestPort()));
                }
                List<TsharkFlowVO> all = new ArrayList<>(agg.size());
                agg.forEach((key, cell) -> {
                    TsharkFlowVO m = meta.get(key);
                    all.add(new TsharkFlowVO(m.flow(), (int) cell[0], cell[1],
                            m.sourceIp(), m.sourcePort(), m.destIp(), m.destPort()));
                });
                all.sort((a, b) -> Integer.compare(b.count(), a.count()));
                result.addAll(all.subList(0, Math.min(max, all.size())));
            }
            return ReturnResult.ok(result.size() > max ? result.subList(0, max) : result);
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        }
    }

    /**
     * 时间轴统计：按时间桶聚合报文的数量与总字节数。
     *
     * @param sessionId     会话标识
     * @param bucketSeconds 桶大小（秒），默认 10
     * @param displayFilter 显示过滤（可空）
     * @return 时间轴数据列表，格式：[{"t": "2026-10-19 10:00:00", "packets": 123, "bytes": 45678}, ...]
     */
    public List<Map<String, Object>> timeline(Long sessionId, Integer bucketSeconds, String displayFilter) {
        int bucket = bucketSeconds == null || bucketSeconds <= 0 ? 10 : bucketSeconds;
        TsharkDisplayFilter.SqlFilter filter;
        try {
            filter = TsharkDisplayFilter.parse(displayFilter);
        } catch (IllegalArgumentException e) {
            // 时间轴接口返回 List，无法带回错误信封；空列表比 500 更友好，前端另有过滤错误提示
            log.debug("timeline filter rejected: {}", e.getMessage());
            return List.of();
        }
        if (filter.isEmpty()) {
            List<Map<String, Object>> rows = this.packetMapper.selectTimelineBySessionId(sessionId, bucket);
            // 同协议统计：H2 会把别名折成大写，这里统一回小写键，前端拿到的字段两边一致
            List<Map<String, Object>> normalized = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("t", cell(row, "t"));
                item.put("packets", number(cell(row, "packets")));
                item.put("bytes", number(cell(row, "bytes")));
                normalized.add(item);
            }
            return normalized;
        }
        try {
            List<MsTsharkPacket> packets = filteredPackets(sessionId, filter);
            Map<Long, long[]> agg = new TreeMap<>();
            for (MsTsharkPacket p : packets) {
                if (p.getPacketTime() == null) {
                    continue;
                }
                long epoch = p.getPacketTime().atZone(ZoneId.systemDefault()).toEpochSecond();
                long key = (epoch / bucket) * bucket;
                long[] cell = agg.computeIfAbsent(key, k -> new long[2]);
                cell[0]++;
                cell[1] += p.getPacketLength() == null ? 0L : p.getPacketLength();
            }
            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            List<Map<String, Object>> result = new ArrayList<>(agg.size());
            agg.forEach((key, cell) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("t", LocalDateTime.ofInstant(Instant.ofEpochSecond(key), ZoneId.systemDefault()).format(fmt));
                row.put("packets", cell[0]);
                row.put("bytes", cell[1]);
                result.add(row);
            });
            return result;
        } catch (IllegalArgumentException e) {
            log.debug("timeline filtered aggregation rejected: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 异常诊断：按常见故障特征统计报文条数。
     *
     * <p>页面上的「诊断」页用它做一张体检表：连接重置、ICMP 不可达、重传、乱序、
     * 零窗口、DNS 解析失败、HTTP 4xx/5xx、TLS 告警。每项都带上一条等价的显示过滤
     * 表达式，点一下就能筛到报文表 —— 只看计数、却不知道是哪几条包，对排查没有帮助。</p>
     *
     * <p>SQL 全部是常量片段（不含用户输入），显示过滤作为额外条件由
     * {@link #applyFilter} 参数化追加，因此不存在拼接注入面。</p>
     *
     * @param sessionId     会话标识
     * @param displayFilter 显示过滤（可空）：统计的是「当前过滤命中的报文里」的异常
     * @return 异常项列表，字段 key / label / severity / count / filter；没有命中的项不返回
     */
    public List<Map<String, Object>> anomalies(Long sessionId, String displayFilter) {
        TsharkDisplayFilter.SqlFilter filter;
        try {
            filter = TsharkDisplayFilter.parse(displayFilter);
        } catch (IllegalArgumentException e) {
            // 与时间轴一致：接口返回 List，空列表 + 前端过滤条上的错误提示比 500 更友好
            log.debug("anomalies filter rejected: {}", e.getMessage());
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (AnomalyRule rule : ANOMALY_RULES) {
            LambdaQueryWrapper<MsTsharkPacket> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MsTsharkPacket::getSessionId, sessionId);
            wrapper.apply(rule.sql());
            applyFilter(wrapper, filter);
            Long count = this.packetMapper.selectCount(wrapper);
            if (count == null || count <= 0) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", rule.key());
            item.put("label", rule.label());
            item.put("severity", rule.severity());
            item.put("count", count);
            item.put("filter", rule.filter());
            result.add(item);
        }
        // 危险项优先，同级别按数量降序：体检表第一眼就该是最该处理的那几行
        result.sort((a, b) -> {
            int bySeverity = Integer.compare(
                    severityRank(String.valueOf(a.get("severity"))),
                    severityRank(String.valueOf(b.get("severity"))));
            if (bySeverity != 0) {
                return bySeverity;
            }
            return Long.compare(((Number) b.get("count")).longValue(), ((Number) a.get("count")).longValue());
        });
        return result;
    }

    /**
     * 异常严重度排序权重：越小越靠前。
     *
     * @param severity danger / warning / info
     * @return 权重
     */
    private static int severityRank(String severity) {
        return switch (severity) {
            case "danger" -> 0;
            case "warning" -> 1;
            default -> 2;
        };
    }

    /**
     * 一条诊断规则：入库报文里的判定条件 + 可回填到显示过滤的等价表达式。
     *
     * @param key      稳定标识（前端可用作锚点）
     * @param label    展示名
     * @param severity danger / warning / info
     * @param sql      统计用 SQL 条件（常量片段，不含用户输入）
     * @param filter   等价的显示过滤表达式，空串表示不可回填
     */
    private record AnomalyRule(String key, String label, String severity, String sql, String filter) {
    }

    /**
     * 诊断规则表。
     *
     * <p>判定尽量落在 info 摘要上：这些列是 tshark `_ws.col.Info` 的原样落库，
     * 比协议列更细（重传 / 乱序 / 零窗口本身都是 TCP，只看协议分不出来）。
     * MySQL 与 H2 都在 LOWER() 后比较，避免大小写口径不一致。</p>
     */
    private static final List<AnomalyRule> ANOMALY_RULES = List.of(
            new AnomalyRule("reset", "连接重置（RST）", "danger",
                    "(LOWER(ms_tshark_packet_info) LIKE '%[rst%'"
                            + " OR LOWER(ms_tshark_packet_info) LIKE '%reset%')",
                    "tcp.flags.reset == 1"),
            new AnomalyRule("unreachable", "ICMP 不可达 / 超时", "danger",
                    "LOWER(ms_tshark_packet_protocol) = 'icmp'"
                            + " AND (LOWER(ms_tshark_packet_info) LIKE '%unreachable%'"
                            + " OR LOWER(ms_tshark_packet_info) LIKE '%time exceeded%')",
                    "icmp and (\"Unreachable\" or \"Time Exceeded\")"),
            new AnomalyRule("retransmission", "TCP 重传", "warning",
                    "LOWER(ms_tshark_packet_info) LIKE '%retransmission%'",
                    "\"Retransmission\""),
            new AnomalyRule("dup-ack", "重复 ACK", "warning",
                    "LOWER(ms_tshark_packet_info) LIKE '%dup ack%'",
                    "\"Dup ACK\""),
            new AnomalyRule("out-of-order", "乱序 / 失序", "warning",
                    "LOWER(ms_tshark_packet_info) LIKE '%out-of-order%'",
                    "\"Out-Of-Order\""),
            new AnomalyRule("zero-window", "零窗口", "warning",
                    "(LOWER(ms_tshark_packet_info) LIKE '%zerowindow%'"
                            + " OR LOWER(ms_tshark_packet_info) LIKE '%zero window%')",
                    "\"ZeroWindow\""),
            new AnomalyRule("dns-error", "DNS 解析失败", "warning",
                    "LOWER(ms_tshark_packet_protocol) = 'dns'"
                            + " AND (LOWER(ms_tshark_packet_info) LIKE '%no such name%'"
                            + " OR LOWER(ms_tshark_packet_info) LIKE '%servfail%'"
                            + " OR LOWER(ms_tshark_packet_info) LIKE '%nxdomain%')",
                    "dns and (\"No such name\" or \"ServFail\" or \"NXDomain\")"),
            new AnomalyRule("http-error", "HTTP 4xx / 5xx", "warning",
                    "(LOWER(ms_tshark_packet_info) LIKE 'http/1.1 4%'"
                            + " OR LOWER(ms_tshark_packet_info) LIKE 'http/1.1 5%'"
                            + " OR LOWER(ms_tshark_packet_info) LIKE 'http/2 4%'"
                            + " OR LOWER(ms_tshark_packet_info) LIKE 'http/2 5%')",
                    "(\"HTTP/1.1 4\" or \"HTTP/1.1 5\" or \"HTTP/2 4\" or \"HTTP/2 5\")"),
            new AnomalyRule("tls-alert", "TLS 告警", "info",
                    "LOWER(ms_tshark_packet_protocol) IN ('tls','ssl')"
                            + " AND LOWER(ms_tshark_packet_info) LIKE '%alert%'",
                    "tls and \"Alert\"")
    );

    /**
     * 在 wrapper 上追加显示过滤条件。
     *
     * @param wrapper 查询 wrapper
     * @param filter  解析结果
     */
    private static void applyFilter(LambdaQueryWrapper<MsTsharkPacket> wrapper, TsharkDisplayFilter.SqlFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return;
        }
        if (filter.args().isEmpty()) {
            wrapper.apply(filter.sql());
            return;
        }
        // MyBatis-Plus 的 apply(sql, args) 用的是 {0} / {1} 这种索引占位符，不是 JDBC 的 ?。
        // 解析器按 JDBC 习惯生成 ?，这里按顺序改成 {n}，避免「sql not contains {0}」。
        StringBuilder sb = new StringBuilder(filter.sql().length() + 16);
        int index = 0;
        for (int i = 0; i < filter.sql().length(); i++) {
            char c = filter.sql().charAt(i);
            if (c == '?') {
                sb.append('{').append(index++).append('}');
            } else {
                sb.append(c);
            }
        }
        wrapper.apply(sb.toString(), filter.args().toArray());
    }

    /**
     * 取显示过滤命中的报文（带上限保护），只 select 聚合需要的列。
     *
     * @param sessionId 会话标识
     * @param filter    显示过滤
     * @return 命中报文
     * @throws IllegalArgumentException 命中量超过 {@link #MAX_FILTER_SCAN}
     */
    private List<MsTsharkPacket> filteredPackets(Long sessionId, TsharkDisplayFilter.SqlFilter filter) {
        LambdaQueryWrapper<MsTsharkPacket> count = new LambdaQueryWrapper<>();
        count.eq(MsTsharkPacket::getSessionId, sessionId);
        applyFilter(count, filter);
        long total = this.packetMapper.selectCount(count);
        if (total > MAX_FILTER_SCAN) {
            throw new IllegalArgumentException("显示过滤命中 " + total + " 条报文，超过统计分析上限 "
                    + MAX_FILTER_SCAN + " 条，请缩小过滤范围后重试");
        }
        LambdaQueryWrapper<MsTsharkPacket> q = new LambdaQueryWrapper<>();
        q.eq(MsTsharkPacket::getSessionId, sessionId);
        applyFilter(q, filter);
        q.select(MsTsharkPacket::getProtocol, MsTsharkPacket::getSourceIp, MsTsharkPacket::getSourcePort,
                MsTsharkPacket::getDestIp, MsTsharkPacket::getDestPort, MsTsharkPacket::getPacketLength,
                MsTsharkPacket::getPacketTime);
        return this.packetMapper.selectList(q);
    }

    /** 流标识文本。 */
    private static String flowText(String sourceIp, Integer sourcePort, String destIp, Integer destPort) {
        return (sourceIp == null ? "" : sourceIp) + ":" + (sourcePort == null ? "" : sourcePort)
                + " -> " + (destIp == null ? "" : destIp) + ":" + (destPort == null ? "" : destPort);
    }

    /** 宽松转 Integer。 */
    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取聚合行里的列值，忽略大小写。
     *
     * <p>SQL 里写的别名是 {@code count} / {@code bytes} 这种小写，但 MySQL 会原样返回，
     * H2 把未加引号的标识符折成大写 —— 同一个查询在两种库上 {@code row.get("count")}
     * 只有一种能命中。这里统一按「原样 → 大写 → 小写 → 忽略大小写遍历」查找，
     * 免得统计接口换个库就 500。</p>
     */
    private static Object cell(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String upper = key.toUpperCase(Locale.ROOT);
        if (row.containsKey(upper)) {
            return row.get(upper);
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (row.containsKey(lower)) {
            return row.get(lower);
        }
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 把聚合行的计数 / 字节列转成数值，缺失按 0（统计接口不该因为一列为空就 500）。 */
    private static long number(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 删除会话。
     * @param sessionId 会话标识
     * @return 删除会话的结果
     */
    public ReturnResult<Void> deleteSession(Long sessionId) {
        Process proc = this.running.get(sessionId);
        if (proc != null) {
            // 删除即视为用户主动结束，收尾线程不要再把退出码判成异常
            this.stopRequested.add(sessionId);
            proc.destroy();
            this.running.remove(sessionId);
        }
        this.packetMapper.delete(new LambdaQueryWrapper<MsTsharkPacket>().eq(MsTsharkPacket::getSessionId, sessionId));
        MsTsharkSession session = this.sessionMapper.selectById(sessionId);
        if (session != null && session.getFilePath() != null) {
            try {
                Path pcap = Paths.get(session.getFilePath());
                Files.deleteIfExists(pcap);
                // tshark 的 stdout/stderr 被重定向到 <pcap>.log（见 startCapture）。
                // 原实现只删了 .pcap，.pcap.log 永久残留 —— 实测删掉 13 个会话后磁盘上
                // 仍留着 13 个 .log，每次抓包泄漏一个，长期运行必然撑爆目录。
                Files.deleteIfExists(Paths.get(pcap + ".log"));
            } catch (Exception ignored) {
                // 抓包文件清理失败不影响删会话的语义：会话与数据包已从库中移除
            }
        }
        this.sessionMapper.deleteById(sessionId);
        return ReturnResult.success();
    }
}
