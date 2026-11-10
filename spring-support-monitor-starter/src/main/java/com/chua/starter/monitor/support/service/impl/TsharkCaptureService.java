// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.network.support.tshark.PacketParserService;
import com.chua.network.support.tshark.PacketRecord;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import com.chua.starter.monitor.support.entity.MsTsharkPacket;
import com.chua.starter.monitor.support.entity.MsTsharkSession;
import com.chua.starter.monitor.support.mapper.MsTsharkPacketMapper;
import com.chua.starter.monitor.support.mapper.MsTsharkSessionMapper;
import com.chua.starter.monitor.support.model.PacketHttpFrame;
import com.chua.starter.monitor.support.model.PacketRestoreFrame;
import com.chua.starter.monitor.support.model.TsharkAnomalyVO;
import com.chua.starter.monitor.support.model.TsharkFlowVO;
import com.chua.starter.monitor.support.model.TsharkHandshakeVO;
import com.chua.starter.monitor.support.model.TsharkInterfaceVO;
import com.chua.starter.monitor.support.model.TsharkProtocolStatVO;
import com.chua.starter.monitor.support.tshark.TsharkHttpStreamRestorer;
import com.chua.starter.monitor.support.tshark.TsharkPacketStreamRegistry;
import com.chua.starter.monitor.support.tshark.TsharkRuntime;
import com.chua.starter.monitor.support.tshark.TsharkSqliteSourceRegistry;
import com.chua.starter.monitor.support.config.TsharkInstallProperties;
import com.chua.starter.monitor.support.config.TsharkRetentionProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
       * tshark 运行时。
       *
       * <p>可执行文件不再只认环境变量：{@link TsharkRuntime} 会自动探测本机安装、必要时
       * 在虚拟线程里补齐便携版。抓包命令必须走它解析出来的路径，否则「装好了却还在用
       * 一个不存在的 tshark」——这是最容易踩的一处。</p>
       */
    private final TsharkRuntime tsharkRuntime;

      /**
       * 当前可执行文件。
       *
       * @return 运行时解析出的路径，未探测到结果时回落到 {@link #TSHARK_BIN}
       */
    private String bin() {
        return this.tsharkRuntime == null ? TSHARK_BIN : this.tsharkRuntime.bin();
    }

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
       * 11 帧长 · 12 人可读摘要 · 13-16 HTTP 请求（方法 / URI / 版本 / 头行）·
       * 17-20 HTTP 响应（状态码 / 原因短语 / 版本 / 头行）·
       * 21-24 HTTP 实体（类型 / 内容编码 / 长度 / 正文十六进制）。IPv4/IPv6 与 TCP/UDP 成对给出，
       * 某层不存在时该列为空串，由 {@code firstNonBlank} 取非空的那个。</p>
       *
       * <p><b>HTTP 头行是「多值字段」</b>：一个报文可能带十几行头，必须用
       * {@code -E occurrence=a} 才能全取到（默认 {@code f} 只取第一行，页面上就只剩
       * {@code Host: ...}）。多值之间被 tshark 用逗号连接、每个值以字面量 {@code \\r\\n}
       * 结尾，{@link #normalizeHttpLines(String)} 再按这个边界还原成一行一条。</p>
       *
       * <p><strong>第 0 列 {@code frame.number} 是 pcap 内自然递增的帧号</strong>：
       * 抓包期间要一边写 pcap 一边把新包读出来推给前端，尾随循环靠
       * {@code -Y "frame.number > N"} 只取新增的帧。它不仅需要用来判定「哪些是新的」、
       * 已经是唯一键落进 {@code ms_tshark_packet_frame_no}（与注册表里
       * {@code idx_tshark_packet_frame_no} 配套），也是后续 -T json 通道回填
       * {@code rawData} / {@code restoredText} 时对齐 SQLite 行的锚点——
       * 同一份文件两个通道顺序相同，按帧号对结果不会被弄混。</p>
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
            "_ws.col.Info",
            // 13-16 HTTP 请求：方法 / URI / 版本 / 头行（多值，见类注释）
            "http.request.method", "http.request.uri", "http.request.version", "http.request.line",
            // 17-20 HTTP 响应：状态码 / 原因短语 / 版本 / 头行（多值）
            "http.response.code", "http.response.phrase", "http.response.version", "http.response.line",
            // 21-24 HTTP 实体：类型 / 内容编码 / 长度 / 正文（tshark 把 FT_BYTES 输出成十六进制）
            "http.content_type", "http.content_encoding", "http.content_length", "http.file_data",
            // 25-28 TCP 重组原料：流号 / 绝对序列号 / 标志位 / 段载荷（十六进制）。
            // 有了它们才能把被切碎的请求响应拼回来再做协议解析，而不是只看单帧的 http.* 字段
            "tcp.stream", "tcp.seq_raw", "tcp.flags", "tcp.payload",
            // 29-32 报文详情补充字段：网络层跳数（v4 TTL / v6 跳数上限）、TLS 域名、DNS 查询名。
            // 都是「打开详情就想第一眼看到」的元信息，不必再展开几十 KB 的原始数据去找
            "ip.ttl", "ipv6.hlim",
            "tls.handshake.extensions_server_name",
            "dns.qry.name"
    };

      /** {@link #TSHARK_FIELDS} 的列数；列数不足说明输出异常，该行丢弃 */
    private static final int TSHARK_FIELD_COUNT = TSHARK_FIELDS.length;

      /** 列下标：TCP 流号 */
    private static final int COL_TCP_STREAM = 25;
      /** 列下标：TCP 绝对序列号（用于流重组定位字节） */
    private static final int COL_TCP_SEQ_RAW = 26;
      /** 列下标：TCP 标志位（SYN / FIN / RST 判定） */
    private static final int COL_TCP_FLAGS = 27;
      /** 列下标：TCP 段载荷十六进制（重组后交给 HTTP 解析） */
    private static final int COL_TCP_PAYLOAD = 28;
      /** 列下标：IPv4 TTL */
    private static final int COL_IP_TTL = 29;
      /** 列下标：IPv6 跳数上限（与 TTL 语义一致，取第一个非空） */
    private static final int COL_IPV6_HLIM = 30;
      /** 列下标：TLS SNI（握手扩展里的服务器名） */
    private static final int COL_TLS_SNI = 31;
      /** 列下标：DNS 查询名 */
    private static final int COL_DNS_QUERY = 32;

      /** 会话来源：网卡实时抓包（{@code tshark -i} 抓出来的） */
    private static final String SOURCE_LIVE = "live";
      /** 会话来源：导入的 pcap 文件（{@code PCAP 分析} 页面上传的） */
    private static final String SOURCE_IMPORT = "import";

      /**
       * 生命周期一次返回的最大步骤数。单条流可能抓进几十万包（如长连 SSH 隧道），
       * 全量返回动辄几十 MB、耗时数十秒，前端根本等不到弹框就超时。超出后
       * 只保留关键帧与首尾，并用 totalSteps / truncated 告知前端真实长度。
       */
    private static final int HANDSHAKE_MAX_STEPS = 500;

      /**
       * HTTP 事务配对时，同一 TCP 流里最多取回的带 HTTP 头的帧数。
       *
       * <p>一条长连接上可能有成百上千个请求，但详情面板只关心离当前帧最近的那一对；
       * 限制规模避免长连 SSH / 下载流把整张表读进内存。</p>
       */
    private static final int HTTP_TX_MAX_FRAMES = 2000;

      /**
       * 没有 TCP 流号（老库 / 非标准解析）时的兜底窗口：以当前帧号为中心，
       * 只在该范围内按四元组找请求 / 响应。
       */
    private static final long HTTP_TX_FRAME_WINDOW = 2000L;

      /**
       * HTTP 事务查询只取这些列：rawData 是几十 KB 的 tshark JSON，
       * 配对用不上，全列 select 会把详情接口拖慢一个数量级。
       */
    private static final Set<String> HTTP_TX_COLUMNS = Set.of(
            "id", "ms_tshark_packet_session_id", "ms_tshark_packet_time",
            "ms_tshark_packet_frame_no", "ms_tshark_packet_source_ip", "ms_tshark_packet_dest_ip",
            "ms_tshark_packet_source_port", "ms_tshark_packet_dest_port",
            "ms_tshark_packet_protocol", "ms_tshark_packet_info", "ms_tshark_packet_tcp_stream",
            "ms_tshark_packet_http_req", "ms_tshark_packet_http_resp", "ms_tshark_packet_http_body",
            "ms_tshark_packet_http_hex",
            "ms_tshark_packet_http_type", "ms_tshark_packet_http_encoding", "ms_tshark_packet_http_size",
            "ms_tshark_packet_http_method", "ms_tshark_packet_http_uri", "ms_tshark_packet_http_status",
            "ms_tshark_packet_restored");

      /**
       * 协议还原回填批次大小：-T json 通道逐包拿到的记录攒起，每 {@code RESTORE_BATCH} 条
       * 按帧号对回 SQLite 行做一次 {@code CASE WHEN frame_no = ? THEN ?} 批量更新。
       */
    private static final int RESTORE_BATCH = 250;

      /**
       * tshark 解析时额外开启 json 通道的开关。
       */
    private final TsharkInstallProperties installProperties;

    private final Map<Long, Process> running = new ConcurrentHashMap<>(); // running

      /**
       * 会话的显示过滤（{@code -Y}）表达式，原样保存用户输入。
       *
       * <p><b>为什么抓包阶段不直接用显示过滤</b>：tshark 在「抓包 + {@code -w}」模式下
       * 明确不支持 {@code -Y}（会直接退出），所以捕获阶段不能带上它。</p>
       *
       * <p><b>捕获阶段也不做任何翻译</b>：用户写的是显示过滤，就原样留给解析期的
       * {@code -Y}，捕获阶段不加 {@code -f}，先照单全收。老实现把 {@code http} 翻成
       * {@code tcp port 80/8080/8000/8888} 并当作最终过滤，8082、8443 这类端口上的
       * HTTP 一个包都进不来 —— 这正是「设置 http 就抓不到包」的根因。任何
       * 「显示过滤 → 捕获过滤」的猜测都会改变用户表达式的含义，一律不做。</p>
       *
       * <p>只在实时抓包的生命周期内有效；导入会话没有这一层过滤。</p>
       */
    private final Map<Long, String> displayFilters = new ConcurrentHashMap<>(); // 显示过滤

      /**
        * 每个会话一个「TCP 重组 + HTTP 协议还原」器。
        *
        * <p>必须跨解析批次保留：实时尾随每秒只读新增帧，一条响应可能上一批只有头、
        * 下一批才有正文；每批新建一个还原器会把跨批消息截断。会话收尾后移除。</p>
        */
    private final Map<Long, TsharkHttpStreamRestorer> httpRestorers = new ConcurrentHashMap<>();

    private final MsTsharkSessionMapper sessionMapper; // 会话映射器
    private final MsTsharkPacketMapper packetMapper; // 数据包映射器
    private final TsharkSqliteSourceRegistry sqliteRegistry;
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

      /**
       * 抓包数据保留策略（过期天数 / 目录空间上限 / 是否启用）。
       *
       * <p>过期清理与空间清理是两条独立规则，任一条满足就删；页面上的
       * 「一键清理」与定时任务走同一份策略，避免两处口径不一致。</p>
       */
    private final TsharkRetentionProperties retentionProperties;

    public TsharkCaptureService(MsTsharkSessionMapper sessionMapper,
                                MsTsharkPacketMapper packetMapper,
                                MonitorPushService pushService,
                                ObjectProvider<TsharkPacketStreamRegistry> streamRegistryProvider,
                                TsharkRuntime tsharkRuntime,
                                TsharkSqliteSourceRegistry sqliteRegistry,
                                TsharkInstallProperties installProperties,
                                TsharkRetentionProperties retentionProperties) {
        this.sessionMapper = sessionMapper;
        this.packetMapper = packetMapper;
        this.pushService = pushService;
        this.streamRegistryProvider = streamRegistryProvider;
        this.tsharkRuntime = tsharkRuntime;
        this.sqliteRegistry = sqliteRegistry;
        this.installProperties = installProperties;
        this.retentionProperties = retentionProperties;
    }

      /**
       * 是否启用 tshark json 通道回填（rawData / restoredText）。
       * @return 开关
       */
    private boolean restoreJsonEnabled() {
        return this.installProperties != null && this.installProperties.isJsonRestore();
    }

      /**
       * 取 SSE 注册表；未装配时返回 null（推送静默跳过）。
       * @return 返回值
      */
    private TsharkPacketStreamRegistry streamRegistry() {
        return this.streamRegistryProvider.getIfAvailable();
    }

      /**
       * 幂等确保该会话的 SQLite 报文库已就绪。
       *
       * <p>读路径没有 pcap 路径参数：报文的库文件与 pcap 同目录同名，先回 master
       * 查会话登记的文件路径（主记录仍在 MySQL），再交给注册表建池建表。
       * 写路径（抓包 / 解析）在入库前调用，pcap 路径一定已落盘。</p>
       *
       * @param sessionId 会话标识
       */
    private void ensurePacketDb(Long sessionId) {
        if (this.sqliteRegistry.has(sessionId)) {
            return;
        }
        MsTsharkSession session = this.sessionMapper.selectById(sessionId);
        if (session == null || session.getFilePath() == null || session.getFilePath().isBlank()) {
              // 会话不存在且无法推导 .db 时显式失败：DynamicDataSource 非严格模式会把
              // 未注册的 key 静默回退到 master，报文查询会落到 MySQL 老表上读脏数据
            throw new IllegalStateException("会话报文库不可用（会话不存在或未落盘）: " + sessionId);
        }
        this.sqliteRegistry.ensure(sessionId, Paths.get(session.getFilePath()));
    }

      /**
       * 在指定会话的 SQLite 数据源上下文中执行一次报文查询。
       *
       * <p>setDbType / clearDbType 是 ThreadLocal，尾随解析跑在专属的
       * {@code tshark-tail-<sessionId>} 线程、runAsync 任务各线程独立，
       * 用完后必须恢复调用前的上下文（可能为 null），不能简单地 clear。</p>
       *
       * @param sessionId 会话标识
       * @param supplier  报文查询
       * @param <T>       返回类型
       * @return 查询结果
       */
    private <T> T withPacketDb(Long sessionId, Supplier<T> supplier) {
        this.ensurePacketDb(sessionId);
        String previous = DataSourceContextSupport.getDbType();
        if (previous != null && previous.equals(this.sqliteRegistry.dbName(sessionId))) {
            return supplier.get();
        }
        DataSourceContextSupport.setDbType(this.sqliteRegistry.dbName(sessionId));
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                DataSourceContextSupport.clearDbType();
            } else {
                DataSourceContextSupport.setDbType(previous);
            }
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
            ProcessBuilder pb = new ProcessBuilder(bin(), "-D");
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
       * <p>顺带解决「选不出哪个是本地网络」：同一张物理网卡在 OSHI 里会重复出现多行
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

      /**
       * rank Of
       * @param i 参数
       * @return 返回值
      */
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

      /**
       * blank To Empty
       * @param s 字符串
       * @return 返回值
      */
    private static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

      /**
       * 检查tshark。
       * @return 检查tshark的结果
       */
    public ReturnResult<Boolean> checkTshark() {
        try {
            ProcessBuilder pb = new ProcessBuilder(bin(), "--version");
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
            return "未找到 tshark 可执行文件（" + bin() + "），请安装 Wireshark 或设置环境变量 TSHARK_BIN 指向绝对路径";
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
     * 空 pcap 文件头（24 字节：2.4 版本、以太网链路层）。
     *
     * <p>只用于让 tshark 编译一遍显示过滤语法 —— {@code -Y} 只有在读文件时才会被解析，
     * 而抓包刚启动时目标 pcap 还是空的。给一个合法的空 pcap，tshark 会先校验过滤表达式，
     * 语法错误时以非 0 退出。</p>
     */
    private static final byte[] EMPTY_PCAP = {
            (byte) 0xd4, (byte) 0xc3, (byte) 0xb2, (byte) 0xa1, 0x02, 0x00, 0x04, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            (byte) 0xff, (byte) 0xff, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00
    };

    /**
     * 校验显示过滤（{@code -Y}）语法是否合法。
     *
     * <p>显示过滤原样交给 {@code -Y}（抓包阶段不下 {@code -f}，全量照单全收）。
     * 可这样一来，手滑写错的表达式会表现为「抓完了但 0 条」，用户没法区分是没流量
     * 还是表达式错了。所以起抓前先用 tshark 编译一遍：合法才建会话，不合法当场报
     * 语法错误。</p>
     *
     * @param filter 显示过滤表达式
     * @return true 表示 tshark 能解析
     */
    private boolean isDisplayFilterValid(String filter) {
        Path probe = Paths.get(CAPTURE_DIR, ".display-filter-check.pcap");
        try {
            Files.createDirectories(Paths.get(CAPTURE_DIR));
            if (!Files.exists(probe) || Files.size(probe) != EMPTY_PCAP.length) {
                Files.write(probe, EMPTY_PCAP);
            }
            ProcessBuilder pb = new ProcessBuilder(bin(), "-r", probe.toString(), "-Y", filter,
                    "-T", "fields", "-e", "frame.number");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (java.io.InputStream in = p.getInputStream()) {
                in.readAllBytes();
            }
            if (!p.waitFor(5L, TimeUnit.SECONDS)) {
                p.destroy();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            // 校验器自身故障不该挡住抓包：退回到旧行为，交给后续解析
            log.debug("display filter check failed, filter={} reason={}", filter, e.getMessage());
            return true;
        }
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
            return "过滤器「" + filter + "」不是合法的捕获过滤语法。"
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
       * @param maxSizeMb 会话文件大小上限（MB），为空或非正数表示不限
       * @return 启动capture的结果
       */
    public ReturnResult<MsTsharkSession> startCapture(String name, String iface, String filter,
                                                      String filterType, Integer count, Integer timeout,
                                                      Integer maxSizeMb) {
        try {
            if (iface == null || iface.isBlank()) {
                return ReturnResult.error("请指定网卡");
            }
            Files.createDirectories(Paths.get(CAPTURE_DIR));
            String fileName = UUID.randomUUID().toString().replace("-", "") + ".pcap";
            Path filePath = Paths.get(CAPTURE_DIR, fileName);
            List<String> cmd = new ArrayList<>();
            cmd.add(bin());
            cmd.add("-i");
            cmd.add(iface);
            String displayFilter = null;
            if (filter != null && !filter.isBlank()) {
                  // BPF（-f）与显示过滤（-Y）不是同一套语法；而且 tshark 在「抓包 + -w」
                  // 模式下明确不支持 -Y（会直接退出：Display filters aren't supported when
                  // capturing and saving the captured packets）。所以拆成两步：
                  //   ① 捕获阶段：用户填的是显示过滤时，既不下 -f 也不翻译，全量照单全收；
                  //   ② 解析阶段：用户填的表达式原样交给 -Y 精确筛选。
                  // 前端写什么就执行什么：任何「显示过滤 → 捕获过滤」的猜测都会改变语义。
                  // 老实现把 http 翻成 tcp port 80/8080/8000/8888 当最终过滤，8082、8443
                  // 这类端口上的 HTTP 一个包都进不来 —— 这正是「设置 http 抓不到包」的根因。
                if (useDisplayFilter(filter, filterType)) {
                    String df = filter.trim();
                    if (!isDisplayFilterValid(df)) {
                        return ReturnResult.error("显示过滤「" + filter + "」语法有误，tshark 无法解析。"
                                  + "请检查括号、比较符与字段名，或改用 tcp port 80、host 10.0.0.1 这类捕获过滤。");
                    }
                    displayFilter = df;
                } else {
                    cmd.add("-f");
                    cmd.add(filter.trim());
                }
            }
            if (count != null && count > 0) {
                cmd.add("-c");
                cmd.add(String.valueOf(count));
            }
            // tshark 只允许一个 -a，多个停止条件必须用逗号拼在同一条里；
            // filesize 的单位是 KB，前端填的是 MB。
            List<String> autostop = new ArrayList<>();
            if (timeout != null && timeout > 0) {
                autostop.add("duration:" + timeout);
            }
            if (maxSizeMb != null && maxSizeMb > 0) {
                autostop.add("filesize:" + ((long) maxSizeMb * 1024L));
            }
            if (!autostop.isEmpty()) {
                cmd.add("-a");
                cmd.add(String.join(",", autostop));
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
            session.setStartTime(LocalDateTime.now());
            this.sessionMapper.insert(session);
            this.running.put(session.getId(), proc);
            if (displayFilter != null) {
                this.displayFilters.put(session.getId(), displayFilter);
            }
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
// [P3C C1 豁免] 守护/后台线程：有独立生命周期，不适合交给通用执行器
            Thread tail = new Thread(() -> tailParseLoop(sessionId, proc, filePath), "tshark-tail-" + sessionId);
            tail.setDaemon(true);
            tail.start();

            boolean finished = proc.waitFor(60L, TimeUnit.MINUTES);
            if (!finished) {
                proc.destroy();
                proc.waitFor(5L, TimeUnit.SECONDS);
            }
              // 抓包已退出，尾随循环的存活标志自然消失；等它最后一轮收尾后做一次全量兜底
            tail.join(3_000L);
              // 收尾兜底走推送：尾随循环可能漏掉最后一两包，这些包页面正等着看
            this.parsePcapToDb(sessionId, filePath, true);
            MsTsharkSession session = this.sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setCaptureStatus(0);
                session.setEndTime(LocalDateTime.now());
                this.sessionMapper.updateById(session);
                this.pushService.pushTsharkSession(session, sessionId);
                TsharkPacketStreamRegistry reg = streamRegistry();
                if (reg != null) {
                    reg.broadcast(sessionId, TsharkPacketStreamRegistry.EVENT_SESSION,
                            Map.of("sessionId", sessionId, "phase", "finished",
                                    "packetCount", session.getPacketCount() == null ? 0 : session.getPacketCount()));
                }
            }
        } catch (Exception e) {
            log.error("finalize capture failed, session={}", sessionId, e);
        } finally {
            this.running.remove(sessionId);
            this.displayFilters.remove(sessionId);
            this.httpRestorers.remove(sessionId);
        }
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
        try {
            this.parsePcapToDb(sessionId, filePath, false);
        } catch (Exception e) {
            log.error("parse imported pcap failed, session={}", sessionId, e);
        }
          // 无论解析成败都要落状态：失败时若一直停在「解析中」，页面上会永远转圈
        MsTsharkSession session = null;
        try {
            fillTimeRange(sessionId);
            session = this.sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setCaptureStatus(0);
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
                reg.broadcast(sessionId, TsharkPacketStreamRegistry.EVENT_SESSION,
                        Map.of("sessionId", sessionId, "phase", "finished",
                                "packetCount", session == null || session.getPacketCount() == null ? 0 : session.getPacketCount()));
            }
        } catch (Exception e) {
            log.error("finalize imported pcap failed, session={}", sessionId, e);
              // 二次兜底：回填本身失败（例如报文库读写异常）时也要把状态落成已停止，
              // 否则页面永远停在「解析中」，用户既看不到结果也删不掉这条会话。
            try {
                MsTsharkSession fallback = new MsTsharkSession();
                fallback.setId(sessionId);
                fallback.setCaptureStatus(0);
                this.sessionMapper.updateById(fallback);
            } catch (Exception ignore) {
                log.warn("clear imported session status failed, session={}", sessionId, ignore);
            }
        } finally {
            this.httpRestorers.remove(sessionId);
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
            if (!this.sqliteRegistry.has(sessionId)) {
                  // 从未创建过报文库（如解析 0 条）时不做无谓的 master 探测
                return;
            }
            List<MsTsharkPacket> firsts = this.withPacketDb(sessionId, () -> this.packetMapper.selectList(
                    new LambdaQueryWrapper<MsTsharkPacket>()
                            .select(MsTsharkPacket::getPacketTime)
                            .eq(MsTsharkPacket::getSessionId, sessionId)
                            .orderByAsc(MsTsharkPacket::getId)
                            .last("LIMIT 1")));
            if (firsts.isEmpty() || firsts.get(0).getPacketTime() == null) {
                  // 解析出 0 条（空文件 / 非 pcap）时不动时间，避免把会话写成没有时间范围
                return;
            }
            LocalDateTime first = firsts.get(0).getPacketTime();
            List<MsTsharkPacket> lasts = this.withPacketDb(sessionId, () -> this.packetMapper.selectList(
                    new LambdaQueryWrapper<MsTsharkPacket>()
                            .select(MsTsharkPacket::getPacketTime)
                            .eq(MsTsharkPacket::getSessionId, sessionId)
                            .orderByDesc(MsTsharkPacket::getId)
                            .last("LIMIT 1")));
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
                lastFrame = parsePcapIncrementally(sessionId, filePath, lastFrame);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("尾随解析中断 session={} reason={}", sessionId, e.getMessage());
        }
    }

    /**
     * 把「帧号游标」与「显示过滤」拼成一条 -Y 表达式。
     *
     * <p>尾随解析与收尾解析共用：既要只读新帧，又只保留命中显示过滤的帧，两者是 AND 关系。</p>
     *
     * @param display    显示过滤；null 表示不筛
     * @param afterFrame 已读到的最大帧号；0 表示从头
     * @return -Y 表达式
     */
    private static String frameCursorFilter(String display, long afterFrame) {
        String cursor = afterFrame > 0 ? "frame.number > " + afterFrame : null;
        if (display == null || display.isBlank()) {
            return cursor;
        }
        return cursor == null ? display : "(" + cursor + ") && (" + display + ")";
    }

    /**
     * 已入库报文的最大帧号。
     *
     * <p>显示过滤生效时「条数」与「最大帧号」不再相等（只有命中的帧才入库），
     * 收尾解析必须按帧号续读，不能再拿条数当游标。</p>
     *
     * @param sessionId 会话标识
     * @return 最大帧号；没有报文返回 0
     */
    private long maxFrameNo(Long sessionId) {
        try {
            return this.withPacketDb(sessionId, () -> {
                MsTsharkPacket last = this.packetMapper.selectOne(
                        new LambdaQueryWrapper<MsTsharkPacket>()
                                .eq(MsTsharkPacket::getSessionId, sessionId)
                                .orderByDesc(MsTsharkPacket::getFrameNo)
                                .last("LIMIT 1"));
                return last == null || last.getFrameNo() == null ? 0L : last.getFrameNo();
            });
        } catch (Exception e) {
            log.debug("read max frame failed, session={} reason={}", sessionId, e.getMessage());
            return 0L;
        }
    }

    /**
     * 增量解析：只取帧号大于 {@code afterFrame} 的帧，入库并推送，返回本轮读到的最大帧号。
     *
     * @param sessionId  会话 id
     * @param filePath   pcap 路径
     * @param afterFrame 已入库的最大帧号（0 表示从头）
     * @return 本轮读到的最大帧号（不是条数：启用显示过滤后条数与帧号不再相等）
     */
    private long parsePcapIncrementally(Long sessionId, Path filePath, long afterFrame) {
        this.sqliteRegistry.ensure(sessionId, filePath);
        List<String> cmd = new ArrayList<>(List.of(bin(), "-r", filePath.toString(),
                "-T", "fields", "-E", "separator=\t", "-E", "quote=n", "-E", "occurrence=a"));
        for (String field : TSHARK_FIELDS) {
            cmd.add("-e");
            cmd.add(field);
        }
        String display = this.displayFilters.get(sessionId);
        if (afterFrame > 0 || display != null) {
            cmd.add("-Y");
            cmd.add(frameCursorFilter(display, afterFrame));
        }
        long maxSeen = afterFrame;
        long added = 0L;
        TsharkHttpStreamRestorer restorer = this.httpRestorer(sessionId);
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
                      // 只有「列数与字段表一致」的行才是报文行。tshark 的告警被
                      // redirectErrorStream 混进了同一个流，残行 / 告警行的首列可能恰好是数字，
                      // 一旦拿它当帧号，游标会被一次推到几千万，之后每轮
                      // `frame.number > 大数` 都取不到帧，页面就永久停在最初那几条。
                    if (cols.length == TSHARK_FIELD_COUNT) {
                        try {
                            maxSeen = Math.max(maxSeen, Long.parseLong(cols[0].trim()));
                        } catch (NumberFormatException ignore) {
                              // 帧号非数字（不该发生），保持游标不变避免死循环
                        }
                    }
                    if (this.savePacket(sessionId, cols, true)) {
                        added++;
                    }
                      // 实时尾随同样做 TCP 重组，否则跨批次的响应正文只能看到半截。
                      // 这里只取已切全的消息，不 flush：还在传输中的响应不能被截断。
                    this.feedHttpRestorer(restorer, cols);
                }
            }
              // 每轮（约 1 秒）落一次，避免逐条事务都打一次 UPDATE
            this.applyRestoredHttp(sessionId, restorer.drain());
            p.waitFor(20L, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("增量解析失败 session={} reason={}", sessionId, e.getMessage());
        }
        if (added > 0) {
            bumpPacketCount(sessionId, added);
        }
        return Math.max(maxSeen, afterFrame);
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
                this.sessionMapper.updateById(session);
            }
            this.pushService.pushTsharkSession(this.sessionMapper.selectById(sessionId), sessionId);
            return ReturnResult.success();
        }
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
    private void parsePcapToDb(Long sessionId, Path filePath, boolean push) {
        try {
            if (!Files.exists(filePath) || Files.size(filePath) == 0L) {
                log.warn("capture file empty, session={}", sessionId);
                return;
            }
            this.sqliteRegistry.ensure(sessionId, filePath);
            List<String> cmd = new ArrayList<>(List.of(bin(), "-r", filePath.toString(),
                    "-T", "fields", "-E", "separator=\t", "-E", "quote=n", "-E", "occurrence=a"));
              // 字段顺序即输出列顺序，逐列含义见 TSHARK_FIELDS 的注释
            for (String field : TSHARK_FIELDS) {
                cmd.add("-e");
                cmd.add(field);
            }
            // 尾随循环期间已经增量入库了一部分，这里必须跳过，否则同一批包会被插两次。
            // 游标取「已入库的最大帧号」而不是「已入库条数」：启用显示过滤后只有命中的帧
            // 才入库，条数不再等于帧号（如 8 条 http 的帧号可能是 1..43），拿条数当游标
            // 会把已看过的区段反复重读。
            long already = this.maxFrameNo(sessionId);
            String display = this.displayFilters.get(sessionId);
            if (already > 0 || display != null) {
                cmd.add("-Y");
                cmd.add(frameCursorFilter(display, already));
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int batch = 0;
            int flushed = 0;
            int lines = 0;
            long lastFrame = already;
            TsharkHttpStreamRestorer restorer = this.httpRestorer(sessionId);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8), 0x100000)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] cols = line.split("\t", -1);
                    if (this.savePacket(sessionId, cols, push)) {
                        batch++;
                          // 每 2000 条落一次包数：导入几十万包的 pcap 要好几十秒，
                          // 若等整份解析完才写包数，前端整段时间都停在 0，看着像卡死
                        if (batch - flushed >= 2000) {
                            bumpPacketCount(sessionId, batch - flushed);
                            flushed = batch;
                        }
                    }
                      // TCP 重组必须在报文行落库之后投喂：一条消息收全的那一帧
                      // 可能正是当前帧，先写后回填才不会落空
                    this.feedHttpRestorer(restorer, cols);
                    Long frameNo = parseFrameNo(cols[0]);
                    if (frameNo != null) {
                        lastFrame = Math.max(lastFrame, frameNo);
                    }
                    if (++lines % 200 == 0) {
                        this.applyRestoredHttp(sessionId, restorer.drain());
                    }
                }
            }
            if (batch > flushed) {
                bumpPacketCount(sessionId, batch - flushed);
            }
              // 解析收尾：把因空洞滞留、以及靠连接关闭界定的消息强制切出
            this.applyRestoredHttp(sessionId, restorer.flush(lastFrame));
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
                long total = this.withPacketDb(sessionId, () -> this.packetMapper.selectCount(
                        new LambdaQueryWrapper<MsTsharkPacket>().eq(MsTsharkPacket::getSessionId, sessionId)));
                if (total == 0) {
                    log.warn("capture parsed 0 packet, session={}, file={}", sessionId, filePath);
                }
            }
            p.waitFor(60L, TimeUnit.SECONDS);
              // 骨架落成，回填完整协议层 JSON 与协议还原：离线导入 / 实时抓包归同一条通道
            this.restorePacketPayloads(sessionId, filePath);
        } catch (Exception e) {
            log.error("parse pcap failed, session={}", sessionId, e);
        }
    }

      /**
       * 用 tshark 的 {@code -T json} 通道回填报文的 {@code rawData} / {@code restoredText}。
       *
       * <p><b>做了什么</b>：{-T fields} 只给协议列、IP、端口、长度与 Info 摘要，包不住每一层协议
       * 实际长的样子，所以离线导回的抓包在「协议还原」上一直是空的。这里额外跑一次
       * {@code tshark -T json -x}，逐包把完整 JSON 交给 {@code PacketParserService} 解析——
       * 不是只有 HTTP 才能还原：DNS / TLS / SSH / FTP / SMTP / MQTT / Redis / Kafka / SQL ...
       * 所有带还原器的协议各自出结果，写入报文表的 {@code ms_tshark_packet_raw}（完整 JSON）
       * 与 {@code ms_tshark_packet_restored}（还原出的明文，第一命中优先）。</p>
       *
       * <p><b>为什么用第二遍 tshark</b>：-T fields 与 -T json 只能二选一，前者不产出十六进制展开的
       * payload（还原器要的是原始字节），后者才把每层协议完整包起来。</p>
       *
       * <p><b>如何对齐</b>：按帧号。-T fields 已经把 frame.number 存进
       * {@code ms_tshark_packet_frame_no}，-T json 通道的 {@code PacketMeta.frameNumber()}
       * 是同一个 tshark 输出里的同一个帧号——第二条链与第一条链顺序一致，借
       * {@code CASE WHEN ms_tshark_packet_frame_no = ? THEN ?} 的批量更新精确对回 SQLite 行。</p>
       *
       * <p><b>内存与失败</b>：每包整份 JSON 展开后可能几十 KB 起，逐包攒到 {@code RESTORE_BATCH}
       * 条发一次更新，绝不吃满整份文件。<b>失败只记日志</b>：tshark 不在 / 输出不是 JSON
       * / 超时止于一次 warn，已入库骨架报文不受影响。</p>
       *
       * @param sessionId 会话标识
       * @param filePath  pcap 路径
       * @return 还原成功的包数；未启用 / 无文件 / 失败时返回 -1
       */
    private long restorePacketPayloads(Long sessionId, Path filePath) throws IOException {
        if (!restoreJsonEnabled()) {
            return -1L;
        }
        if (filePath == null || !Files.exists(filePath) || Files.size(filePath) == 0L) {
            return -1L;
        }
        long restored = 0L;
        try {
              // stderr 不能并到 stdout 上：tshark 的告警行混进 JSON 数组，整份文件就废了
            Path stderr = Files.createTempFile("tshark-restore", ".log");
            List<String> restoreCmd = new ArrayList<>(List.of(bin(), "-r", filePath.toString(),
                    "-T", "json", "-x"));
            // 启用显示过滤时 pcap 里还躺着未命中的帧（捕获阶段不下 -f，全量抓）。
            // 这一遍只用于回填已入库报文，同样只读命中的帧，避免白解析几十万条。
            String display = this.displayFilters.get(sessionId);
            if (display != null && !display.isBlank()) {
                restoreCmd.add("-Y");
                restoreCmd.add(display);
            }
            ProcessBuilder pb = new ProcessBuilder(restoreCmd);
            pb.redirectError(stderr.toFile());
            Process p = pb.start();
            try {
                List<PacketRestoreFrame> batch = new ArrayList<>(RESTORE_BATCH);
                try (JsonParser parser = new ObjectMapper().getFactory().createParser(p.getInputStream())) {
                    if (parser.nextToken() == JsonToken.START_ARRAY) {
                        while (parser.nextToken() == JsonToken.START_OBJECT) {
                            PacketRecord record = PacketParserService.parseNode(new ObjectMapper().readTree(parser));
                            if (record != null) {
                                batch.add(new PacketRestoreFrame(record.meta().frameNumber(),
                                        record.rawData(), record.restoredText()));
                                if (batch.size() >= RESTORE_BATCH) {
                                    restored += this.withPacketDb(sessionId,
                                            () -> this.packetMapper.updateBatchRawRestored(sessionId, batch));
                                    batch.clear();
                                }
                            }
                        }
                    }
                    if (!batch.isEmpty()) {
                        restored += this.withPacketDb(sessionId,
                                () -> this.packetMapper.updateBatchRawRestored(sessionId, batch));
                    }
                }
                p.waitFor(60L, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("json restore aborted session={} reason={}", sessionId, e.getMessage());
            } finally {
                Files.deleteIfExists(stderr);
            }
        } catch (Exception e) {
            log.warn("restore packets from json failed session={}", sessionId, e);
            return -1L;
        }
        log.info("restore packets from json session={} packets={}", sessionId, restored);
        return restored;
    }

      /**
       * 按 frame.number（pcap 内自然序、同一会话唯一）解析帧号，解析失败返回 null。
       */
    private static Long parseFrameNo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("frame number is not a number: {}", value);
            return null;
        }
    }

      /**
       * 取会话的 HTTP 流还原器（跨解析批次复用）。
       *
       * @param sessionId 会话标识
       * @return 还原器
       */
    private TsharkHttpStreamRestorer httpRestorer(Long sessionId) {
        return this.httpRestorers.computeIfAbsent(sessionId, k -> new TsharkHttpStreamRestorer());
    }

      /**
       * 把一个报文行的 TCP 列投给流还原器。
       *
       * <p>只投 TCP 段（有 {@code tcp.seq_raw}）。非 TCP 行直接忽略，
       * 免得把 UDP / ICMP 的端口当成连接。</p>
       *
       * @param restorer 还原器
       * @param cols     按 {@link #TSHARK_FIELDS} 切出的列
       */
    private void feedHttpRestorer(TsharkHttpStreamRestorer restorer, String[] cols) {
        if (restorer == null || cols.length < TSHARK_FIELD_COUNT) {
            return;
        }
        Long seq = parseLongOrNull(cols[COL_TCP_SEQ_RAW]);
        if (seq == null) {
            return;
        }
        String rawPayload = cols[COL_TCP_PAYLOAD];
        byte[] payload = rawPayload == null || rawPayload.isBlank() ? null : hexToBytes(rawPayload);
        Long frameNo = parseFrameNo(cols[0]);
        restorer.accept(frameNo == null ? 0L : frameNo,
                firstNonBlank(cols[3], cols[4]),
                parseIntOrNull(firstNonBlank(cols[5], cols[6])),
                firstNonBlank(cols[7], cols[8]),
                parseIntOrNull(firstNonBlank(cols[9], cols[10])),
                blankToNull(cols[COL_TCP_STREAM]),
                seq,
                blankToNull(cols[COL_TCP_FLAGS]),
                payload);
    }

      /**
       * 把流还原结果按帧号回写到报文表。
       *
       * <p>同一帧可能同时收全一条请求与一条响应（同段合并发送），因此先按帧号合并，
       * 再分批写：SQLite 的 {@code CASE WHEN} 每批 100 帧，参数数量远低于驱动上限。</p>
       *
       * @param sessionId 会话标识
       * @param messages  还原消息
       */
    private void applyRestoredHttp(Long sessionId, List<TsharkHttpStreamRestorer.RestoredHttp> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Map<Long, PacketHttpFrame> merged = new LinkedHashMap<>();
        for (TsharkHttpStreamRestorer.RestoredHttp message : messages) {
            if (message.frameNo() <= 0) {
                continue;
            }
            // 归属帧取「消息起始帧」：起始行/头一定在这一帧，tshark 的 Info 也把这一帧标成 HTTP，
            // 生命周期右栏据此配对请求/响应；收全帧往往只是 TCP 数据帧（Info 里没有 HTTP），
            // 把正文挂到那里用户点了 HTTP 行反而看不到响应体
            long primary = message.startFrame() > 0 ? message.startFrame() : message.frameNo();
            PacketHttpFrame frame = merged.computeIfAbsent(primary, k -> {
                PacketHttpFrame value = new PacketHttpFrame();
                value.setFrameNo(k);
                return value;
            });
            String head = normalizeHttpRestoredHead(message.head());
            DecodedHttpBody decodedBody = decodeHttpBody(message.body(), message.contentType(), message.encoding(),
                    message.truncated());
            String bodyText = decodedBody == null ? null : decodedBody.text();
            if (message.request()) {
                frame.setRequestHead(head);
            } else {
                frame.setResponseHead(head);
            }
            fillHttpStartLine(frame, message.request(), head);
            if (bodyText != null) {
                frame.setBody(bodyText);
            }
            if (decodedBody != null) {
                frame.setBodyHex(decodedBody.hex());
            }
            if (message.contentType() != null && !message.contentType().isBlank()) {
                frame.setContentType(message.contentType().trim());
            }
            if (message.encoding() != null && !message.encoding().isBlank()) {
                frame.setContentEncoding(message.encoding().trim());
            }
            frame.setContentLength((int) Math.min(Integer.MAX_VALUE, Math.max(0L, message.bodyLength())));
            frame.setRestoredText(restoredHttpText(message.request(), head, bodyText));
            if (message.frameNo() != primary) {
                // 收全帧（往往是 TCP 数据帧）也留一份头 + 还原文本：正文已经在起始帧上，
                // 这里不重复，避免同一段正文在库里存两份
                PacketHttpFrame done = merged.computeIfAbsent(message.frameNo(), k -> {
                    PacketHttpFrame value = new PacketHttpFrame();
                    value.setFrameNo(k);
                    return value;
                });
                if (message.request()) {
                    done.setRequestHead(head);
                } else {
                    done.setResponseHead(head);
                }
                fillHttpStartLine(done, message.request(), head);
                done.setRestoredText(restoredHttpText(message.request(), head, null));
            }
        }
        List<PacketHttpFrame> batch = new ArrayList<>(RESTORE_HTTP_BATCH);
        for (PacketHttpFrame frame : merged.values()) {
            batch.add(frame);
            if (batch.size() >= RESTORE_HTTP_BATCH) {
                flushRestoredHttp(sessionId, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            flushRestoredHttp(sessionId, batch);
        }
    }

      /**
       * 写一批 HTTP 还原结果。
       *
       * @param sessionId 会话标识
       * @param batch     本批记录
       */
    private void flushRestoredHttp(Long sessionId, List<PacketHttpFrame> batch) {
        try {
            this.withPacketDb(sessionId,
                    () -> this.packetMapper.updateBatchHttpRestored(sessionId, new ArrayList<>(batch)));
        } catch (Exception e) {
            log.debug("write restored http failed session={} reason={}", sessionId, e.getMessage());
        }
    }

      /**
       * 生成报文「协议还原」文本：标明来自 TCP 重组，再给完整头与正文。
       *
       * @param request  是否请求
       * @param head     头部
       * @param bodyText 正文
       * @return 还原文本
       */
    private static String restoredHttpText(boolean request, String head, String bodyText) {
        StringBuilder sb = new StringBuilder();
        sb.append("[HTTP ").append(request ? "请求" : "响应").append(" · TCP 重组]\n");
        if (head != null) {
            sb.append(head);
        }
        if (bodyText != null && !bodyText.isBlank()) {
            sb.append("\n\n").append(bodyText);
        }
        return sb.toString();
    }

      /**
       * 规范化还原出的头部：CRLF 转 LF、去尾空白、限长。
       *
       * @param head 原始头部
       * @return 规范化头部；空返回 null
       */
    private static String normalizeHttpRestoredHead(String head) {
        if (head == null || head.isBlank()) {
            return null;
        }
        return clipHttpHead(new StringBuilder(head.replace("\r\n", "\n").trim()));
    }

      /**
       * 从 HTTP 起始行解析方法 / URI / 状态码并写进回写记录。
       *
       * <p>必须走还原出来的头，而不是逐帧的 {@code http.request.method}：请求行被 TCP
       * 切在两个段里时，tshark 往往要到后一段才认出 HTTP，起始帧上那几个字段是空的。
       * 用还原头解析才能保证「正文所在的那一帧」也带方法 / URI / 状态码。</p>
       *
       * @param frame   回写记录
       * @param request true = 请求（方法 + URI）；false = 响应（状态码）
       * @param head    HTTP 头
       */
    private static void fillHttpStartLine(PacketHttpFrame frame, boolean request, String head) {
        String first = firstLineOf(head);
        if (first.isBlank()) {
            return;
        }
        String[] parts = first.trim().split("\\s+");
        if (parts.length < 2) {
            return;
        }
        if (request) {
            frame.setHttpMethod(parts[0]);
            frame.setHttpUri(parts[1]);
        } else {
            Integer code = parseIntOrNull(parts[1]);
            if (code != null) {
                frame.setHttpStatus(code);
            }
        }
    }

      /**
       * 取文本第一行（HTTP 起始行）。
       *
       * @param text 文本
       * @return 第一行；空文本返回空串
       */
    private static String firstLineOf(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int eol = text.indexOf('\n');
        return (eol < 0 ? text : text.substring(0, eol)).trim();
    }

      /**
       * 取 HTTP 起始行里第 {@code index} 个空格分隔的 token。
       *
       * @param head  HTTP 头
       * @param index 0 = 方法 / 协议版本；1 = URI / 状态码
       * @return token；缺失返回 null
       */
    private static String startLineToken(String head, int index) {
        String first = firstLineOf(head);
        if (first.isEmpty()) {
            return null;
        }
        String[] parts = first.split("\\s+");
        return index < parts.length ? parts[index] : null;
    }

      /**
       * 把 tshark 的 {@code tcp.flags} 十六进制值转成可读标志。
       *
       * <p>tshark {@code -T fields} 输出的是 {@code 0x0018} 这样的位掩码，直接摆到详情里
       * 没人看得懂；转成 {@code PSH,ACK} 才符合 Wireshark 的标志列习惯。已经是文本形式
       * （如人工写入的 {@code SYN,ACK}）时原样保留。</p>
       *
       * @param raw 原始标志位
       * @return 可读标志；无标志返回 null
       */
    private static String tcpFlagsText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        int flags;
        try {
            flags = text.startsWith("0x") || text.startsWith("0X")
                    ? Integer.parseInt(text.substring(2), 16) : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return text;
        }
        List<String> parts = new ArrayList<>(8);
        if ((flags & 0x01) != 0) {
            parts.add("FIN");
        }
        if ((flags & 0x02) != 0) {
            parts.add("SYN");
        }
        if ((flags & 0x04) != 0) {
            parts.add("RST");
        }
        if ((flags & 0x08) != 0) {
            parts.add("PSH");
        }
        if ((flags & 0x10) != 0) {
            parts.add("ACK");
        }
        if ((flags & 0x20) != 0) {
            parts.add("URG");
        }
        if ((flags & 0x40) != 0) {
            parts.add("ECE");
        }
        if ((flags & 0x80) != 0) {
            parts.add("CWR");
        }
        return parts.isEmpty() ? null : String.join(",", parts);
    }

      /**
       * 解析 Long，失败返回 null。
       *
       * @param value 文本
       * @return 值；空或非法返回 null
       */
    private static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

      /**
       * 每批回写的帧数。SQL 是 7 段 CASE WHEN，100 帧约 800 个参数，远低于 SQLite 上限。
       */
    private static final int RESTORE_HTTP_BATCH = 100;

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
       * 协议名归一化：把 tshark 的原始协议名收敛成少数几个规范名。 <p><strong>为什么要归一</strong>：{@code _ws.col.Protocol} 给的是最内层协议， 且带版本后缀 —— TLS 握手是 {@code TLSv1.2
          }、MQTT 是 {@code MQTTv3.1.1}、 HTTP/2 是 {@code HTTP2}。直接入库的话「协议分布」饼图会把 TLSv1.2 / TLSv1.3 / MQTTv3.1
          .1 各切成一瓣，一个 TLS 会话在图上碎成好几块，既不好看也不便于 判断谁占大头。</p> <p>归一放在<b>入库时</b>而不是展示层：协议统计是按库里的 protocol 聚合的， 只在前端换显示的话图表仍然是碎的。</p>
       * @param raw 字符串
       * @return 返回值
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
       * @param sessionId 会话标识
       * @param packetId 数据包 id
       * @return 生命周期；包不存在 / 非 TCP 时返回 null
       */
    public TsharkHandshakeVO handshakeOf(Long sessionId, Long packetId) {
        MsTsharkPacket anchor = this.withPacketDb(sessionId, () -> this.packetMapper.selectById(packetId));
        if (anchor == null || anchor.getSessionId() == null || !isTcpBased(anchor)) {
              // 非 TCP 系协议谈不上握手；UDP/ICMP/ARP 直接不返回，前端据此提示不支持
            return null;
        }

          // 不按协议过滤：同一条 TCP 流里 SYN / FIN 会被 simplifyProtocol 归一成 TCP，
          // 而数据帧可能是 HTTP / TLS / SSH。按 anchor 的协议过滤会把握手与挥手全丢掉，
          // 页面上就只剩中间几个数据帧 —— 看起来正是「一采到数据生命周期就结束了」。
          //
          // 两次往返定位这条流的边界：先在 SQL 里按「精确四元组 + 两种方向」收窄
          // （避免一次抓了几十万包的会话把整张报文表读进内存），再按时间序取头尾
          // 两个窗口。长连隧道（SSH / 数据库连接池）一条流可能抓进几十万到上百万
          // 包，全量 select 会阻塞 8082 数十秒、把连接池占满（实测 Recv-Q 堆到 100+
          // 全部假死），公网网关随即 502 —— 所以只取时间轴上「头尾两个固定窗口」
          // （各 ORACLE_WINDOW 包），中间用完整体包数判级、用均匀抽样补一段，
          // 既保证首尾的 SYN / FIN 完整，又把单次查询压到 O(固定常数)。
        final int oracleLimit = HANDSHAKE_MAX_STEPS * 6;
          // 头窗口：时间正序取最早的一批（含 SYN 建连）
        List<MsTsharkPacket> head = this.withPacketDb(sessionId, () -> this.packetMapper.selectList(
                buildFlowQuery(anchor, false)
                        .last("LIMIT " + oracleLimit)));
          // 尾窗口：时间倒序取最新的一批（含 FIN 挥手），再按 id 去重合并
        List<MsTsharkPacket> tail = this.withPacketDb(sessionId, () -> this.packetMapper.selectList(
                buildFlowQuery(anchor, true)
                        .last("LIMIT " + oracleLimit)));
        Set<Long> seenIds = new LinkedHashSet<>();
        List<MsTsharkPacket> merged = new ArrayList<>(head.size() + tail.size());
        for (MsTsharkPacket p : head) {
            if (seenIds.add(p.getId())) {
                merged.add(p);
            }
        }
        for (MsTsharkPacket p : tail) {
            if (seenIds.add(p.getId())) {
                merged.add(p);
            }
        }
        merged.sort((a, b) -> {
            int c = a.getPacketTime() == null || b.getPacketTime() == null
                    ? 0 : a.getPacketTime().compareTo(b.getPacketTime());
            return c != 0 ? c : Long.compare(a.getId(), b.getId());
        });
          // 中间段若仍超过固定上限，再均匀抽样（不会命中头尾窗口已保留的目标流报文）
        if (merged.size() > HANDSHAKE_MAX_STEPS * 4) {
            List<MsTsharkPacket> sampled = new ArrayList<>(HANDSHAKE_MAX_STEPS * 4);
            double step = (double) merged.size() / (HANDSHAKE_MAX_STEPS * 4);
            for (int i = 0; i < HANDSHAKE_MAX_STEPS * 4; i++) {
                sampled.add(merged.get((int) (i * step)));
            }
            merged = sampled;
        }

          // 端口与方向已由 SQL 四元组锁定，这里再按同一流核对一遍（防御数据角差）
        List<MsTsharkPacket> flow = merged.stream()
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

        return buildHandshake(anchor.getSessionId(), clientIp, clientPort, serverIp, serverPort, flow);
    }

      /**
       * 该报文是否跑在 TCP 之上（能还原握手 / 挥手）。
       *
       * @param packet 报文
       * @return true 表示支持生命周期
       */
    private static boolean isTcpBased(MsTsharkPacket packet) {
        String proto = packet.getProtocol() == null ? "" : packet.getProtocol().trim().toUpperCase(Locale.ROOT);
        if (proto.isEmpty()) {
            return false;
        }
          // 协议列是 simplifyProtocol 归一后的名字，HTTP 还会带子类型（HTTP/JSON、HTTP/XML）；
          // 只认等值会把「服务端 JSON 响应」这类帧判成非 TCP，生命周期直接 404 ——
          // 用户点响应包就一行报文都看不到。按前缀归族。
        if (proto.startsWith("HTTP") || proto.startsWith("TLS") || proto.startsWith("SSL")
                || proto.startsWith("SSH") || proto.startsWith("SMB") || proto.startsWith("FTP")
                || proto.startsWith("SMTP") || proto.startsWith("MQTT") || proto.startsWith("TELNET")) {
            return true;
        }
        return "TCP".equals(proto) || "HTTPS".equals(proto);
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

      /**
       * eq
       * @param a 对象
       * @param b 对象
       * @return 返回值
      */
    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

      /**
       * 构建「同一会话 + 该流的精确四元组（两种方向）」的查询条件。
       *
       * <p>必须带端口：一条长连 SSH 隧道两端只有一个固定端口，但同一对 IP 之间往往
       * 还跑着几十条瞬时连接（同 IP 不同端口）。只按两端 IP 收窄会把这些无关流也
       * 拉进来，头/尾窗口（各 3000 包）可能整个被别人的突发流量占满，点中的那条流
       * 反而一帧都取不到。带上端口后 SQL 直接命中目标流，窗口容量都给这一条流用。</p>
       *
       * <p>排序在方法内完成：时间正序（头部窗口）或时间倒序 + id 倒序（尾部窗口），
       * 保证 LIMIT 取到的确实是时间上最早 / 最新的一批。</p>
       *
       * @param anchor 锚点报文（定位会话、四元组）
       * @param newest true = 尾部窗口（时间倒序取最新）；false = 头部窗口（正序取最早）
       * @return 查询条件（不含 LIMIT）
       */
    private static LambdaQueryWrapper<MsTsharkPacket> buildFlowQuery(MsTsharkPacket anchor, boolean newest) {
        LambdaQueryWrapper<MsTsharkPacket> w = new LambdaQueryWrapper<MsTsharkPacket>()
                .eq(MsTsharkPacket::getSessionId, anchor.getSessionId())
                .and(q -> q
                        .nested(a -> a.eq(MsTsharkPacket::getSourceIp, anchor.getSourceIp())
                                .eq(MsTsharkPacket::getSourcePort, anchor.getSourcePort())
                                .eq(MsTsharkPacket::getDestIp, anchor.getDestIp())
                                .eq(MsTsharkPacket::getDestPort, anchor.getDestPort()))
                        .or()
                        .nested(b -> b.eq(MsTsharkPacket::getSourceIp, anchor.getDestIp())
                                .eq(MsTsharkPacket::getSourcePort, anchor.getDestPort())
                                .eq(MsTsharkPacket::getDestIp, anchor.getSourceIp())
                                .eq(MsTsharkPacket::getDestPort, anchor.getSourcePort())));
        if (newest) {
            w.orderByDesc(MsTsharkPacket::getPacketTime).orderByDesc(MsTsharkPacket::getId);
        } else {
            w.orderByAsc(MsTsharkPacket::getPacketTime).orderByAsc(MsTsharkPacket::getId);
        }
        return w;
    }

      /**
       * 把一条流按时间排好、解析标志位、判定握手次数与关闭状态。
       */
    private TsharkHandshakeVO buildHandshake(Long sessionId, String cIp, Integer cPort,
                                            String sIp, Integer sPort, List<MsTsharkPacket> flow) {
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
            String phase;
            if (flags.contains("FIN")) {
                phase = "CLOSE";
            } else if (info.contains("TLS") || info.contains("Application Data")) {
                phase = "TLS";
            } else if (info.contains("HTTP")) {
                phase = "HTTP";
            } else if (flags.contains("SYN") || "ACK".equals(flags)) {
                phase = "TCP";
            } else {
                phase = "DATA";
            }
            boolean retransmission = info.contains("Retransmission")
                    || info.contains("DUP ACK")
                    || info.contains("Out-Of-Order")
                    || info.contains("Spurious");
            boolean reset = flags.contains("RST") || info.contains("[RST]");
            steps.add(new TsharkHandshakeVO.Step(p.getId(),
                    p.getPacketTime() == null ? null : p.getPacketTime().format(hhmm),
                    eq(p.getSourceIp(), cIp) && eq(p.getSourcePort(), cPort),
                    flags, extractAfter(info, "Seq="), extractAfter(info, "Ack="),
                    p.getPacketLength(), info, relMs, phase, retransmission, reset,
                      // 每个包自己的协议还原明文（来自 -T json 通道的回填）：有还原器命中的协议会非空
                    p.getRestoredText()));
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

          // RTT：首个客户端 SYN 到服务端 SYN+ACK 的时间差（毫秒）
        Long rttMs = null;
        if (synIdx >= 0) {
            for (int i = synIdx + 1; i < flow.size(); i++) {
                String fl = steps.get(i).flags() == null ? "" : steps.get(i).flags();
                if (fl.contains("SYN") && fl.contains("ACK")) {
                    rttMs = msBetween(flow.get(synIdx).getPacketTime(), flow.get(i).getPacketTime());
                    break;
                }
            }
        }

          // TTFB：首个客户端 HTTP 请求到服务端首个 HTTP 响应的时间差（毫秒）
        Long ttfbMs = null;
        LocalDateTime firstRequestAt = null;
        for (int i = 0; i < flow.size(); i++) {
            String info = flow.get(i).getPacketInfo() == null ? "" : flow.get(i).getPacketInfo();
            boolean fromClient = eq(flow.get(i).getSourceIp(), cIp) && eq(flow.get(i).getSourcePort(), cPort);
            if (fromClient && isHttpRequestLine(info)) {
                firstRequestAt = flow.get(i).getPacketTime();
                for (int j = i + 1; j < flow.size(); j++) {
                    String rinfo = flow.get(j).getPacketInfo() == null ? "" : flow.get(j).getPacketInfo();
                    boolean serverResponse = !eq(flow.get(j).getSourceIp(), cIp)
                            && (rinfo.startsWith("HTTP/") || rinfo.startsWith("HTTP/1."));
                    if (serverResponse && flow.get(j).getPacketTime() != null
                            && firstRequestAt != null) {
                        ttfbMs = msBetween(firstRequestAt, flow.get(j).getPacketTime());
                        break;
                    }
                }
                break;
            }
        }

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
          // 超长流截断：只保留首尾与关键帧（SYN/FIN/RST/重传/HTTP/TLS），其余均匀抽样，
          // 避免一条长连隧道把 47MB / 11 万步整个返回给浏览器，前端等不到弹框就超时；
          // totalSteps / truncated 让页面如实显示「报文过多，仅保留首尾与关键帧」。
        boolean truncated = steps.size() > HANDSHAKE_MAX_STEPS;
        List<TsharkHandshakeVO.Step> shown = truncated ? trimSteps(steps) : steps;
        return new TsharkHandshakeVO(sessionId, cIp, cPort, sIp, sPort,
                new TsharkHandshakeVO.Handshake(kind, label, detail, completed, closed), shown,
                steps.size(), truncated, rttMs, ttfbMs);
    }

      /**
       * 步骤过多时截断：保留首尾与关键帧（SYN / FIN / RST / 重传 / HTTP / TLS 语义帧），
       * 剩余配额在普通帧里均匀抽样补足；保持原时间顺序。阶段耗时的分段边界（建连 /
       * 首字节 / 传输 / 收尾）都落在关键帧上，截断不会把一次请求的阶段抹掉。
       *
       * @param steps 完整步骤（已按时间排好）
       * @return 截断后的步骤
       */
    private static List<TsharkHandshakeVO.Step> trimSteps(List<TsharkHandshakeVO.Step> steps) {
        int n = steps.size();
        if (n <= HANDSHAKE_MAX_STEPS) {
            return steps;
        }
        Set<Integer> keep = new LinkedHashSet<>();
        keep.add(0);
        keep.add(n - 1);
        for (int i = 0; i < n; i++) {
            TsharkHandshakeVO.Step s = steps.get(i);
            String flags = s.flags() == null ? "" : s.flags();
            String info = s.info() == null ? "" : s.info();
            String phase = s.phase() == null ? "" : s.phase();
            boolean isHttp = "HTTP".equals(phase)
                    || info.startsWith("GET ")
                    || info.startsWith("POST ")
                    || info.startsWith("HTTP/1.")
                    || info.startsWith("HTTP/2");
            boolean isTls = "TLS".equals(phase)
                    || info.contains("Application Data")
                    || info.contains("TLS");
            if (flags.contains("SYN") || flags.contains("FIN") || flags.contains("RST")
                    || s.retransmission() || isHttp || isTls) {
                keep.add(i);
            }
        }
          // 关键帧本身已超上限：直接在关键帧上均匀抽样
        if (keep.size() > HANDSHAKE_MAX_STEPS) {
            return pick(steps, sampleEvenly(new ArrayList<>(keep), HANDSHAKE_MAX_STEPS));
        }
          // 剩余配额在普通帧里均匀补足
        int budget = HANDSHAKE_MAX_STEPS - keep.size();
        if (budget > 0) {
            List<Integer> plain = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!keep.contains(i)) {
                    plain.add(i);
                }
            }
            keep.addAll(sampleEvenly(plain, budget));
        }
        List<Integer> ordered = new ArrayList<>(keep);
        if (ordered.size() > HANDSHAKE_MAX_STEPS) {
            ordered = sampleEvenly(ordered, HANDSHAKE_MAX_STEPS);
        }
        ordered.sort(Integer::compareTo); // 下标有序 = 保持原时间顺序
        return pick(steps, ordered);
    }

      /**
       * sample Evenly
       * @param idx 参数
       * @param max 整数
       * @return 返回值
      */
    private static List<Integer> sampleEvenly(List<Integer> idx, int max) {
        if (idx.size() <= max) {
            return new ArrayList<>(idx);
        }
        List<Integer> out = new ArrayList<>(max);
        for (int k = 0; k < max; k++) {
            int i = (int) Math.round((double) (idx.size() - 1) * k / (max - 1));
            out.add(idx.get(i));
        }
        return out;
    }

      /**
       * pick
       * @param idx 参数
       * @param steps 参数
       * @return 返回值
      */
    private static List<TsharkHandshakeVO.Step> pick(List<TsharkHandshakeVO.Step> steps, List<Integer> idx) {
        List<TsharkHandshakeVO.Step> out = new ArrayList<>(idx.size());
        for (Integer i : idx) {
            out.add(steps.get(i));
        }
        return out;
    }

      /**
       * is Http Request Line
       * @param info 字符串
       * @return 返回值
      */
    private static boolean isHttpRequestLine(String info) {
        if (info == null) {
            return false;
        }
        String up = info.toUpperCase();
        return up.startsWith("GET ")
                || up.startsWith("POST ")
                || up.startsWith("PUT ")
                || up.startsWith("DELETE ")
                || up.startsWith("HEAD ")
                || up.startsWith("OPTIONS ")
                || up.startsWith("PATCH ");
    }

      /**
       * ms Between
       * @param from 参数
       * @param to 参数
       * @return 返回值
      */
    private static Long msBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return null;
        }
        long ms = java.time.Duration.between(from, to).toMillis();
        return ms < 0 ? 0L : ms;
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
              // cols[0] 是 frame.number（pcap 内自然递增的帧号），入库为 ms_tshark_packet_frame_no。
              // 两个点：（1）它是对齐 -T json 通道回填记录的锚点——同一份文件两个通道顺序相同，
              // 按帧号对回 SQLite 行，不会被弄混；（2）它让注册表里的那枚
              // idx_tshark_packet_frame_no 有了实际意义。
            packet.setFrameNo(parseFrameNo(cols[0]));
            packet.setPacketTime(epochToDateTime(cols[1]));
            packet.setProtocol(simplifyProtocol(blankToNull(cols[2])));
              // IPv4 / IPv6 各取第一个非空；端口同理（TCP 优先，其次 UDP）
            packet.setSourceIp(firstNonBlank(cols[3], cols[4]));
            packet.setSourcePort(parseIntOrNull(firstNonBlank(cols[5], cols[6])));
            packet.setDestIp(firstNonBlank(cols[7], cols[8]));
            packet.setDestPort(parseIntOrNull(firstNonBlank(cols[9], cols[10])));
            packet.setPacketLength(parseIntOrNull(cols[11]));
            packet.setPacketInfo(blankToNull(cols[12]));
              // HTTP 请求 / 响应头：解析期一并落库。详情弹层要的是「几行头」，
              // 打开时再回头扫几百 MB 的 pcap 不划算（实测单帧 -T json 在 289MB 上要 3 分钟）
            String[] httpHeads = buildHttpHeads(cols);
            packet.setHttpRequestHead(httpHeads[0]);
            packet.setHttpResponseHead(httpHeads[1]);
              // HTTP 实体：类型 / 内容编码 / 长度 / 正文。正文在解析期就解码并截断，
              // 生命周期与详情弹层直接读，不再回头扫 pcap（实测单帧回扫要分钟级）
            if (cols.length > 24) {
                packet.setHttpContentType(blankToNull(cols[21]));
                packet.setHttpContentEncoding(blankToNull(cols[22]));
                packet.setHttpContentLength(parseIntOrNull(cols[23]));
                DecodedHttpBody decoded = decodeHttpBody(cols[24], cols[21], cols[22]);
                packet.setHttpBody(decoded == null ? null : decoded.text());
                packet.setHttpBodyHex(decoded == null ? null : decoded.hex());
            }
              // 详情面板的补充字段：传输层（流号 / 序列号 / 标志位）、网络层跳数、
              // HTTP 起始行、TLS 域名、DNS 查询名。缺失的列留 null，前端按行隐藏。
            if (cols.length > COL_TCP_FLAGS) {
                packet.setTcpStream(parseIntOrNull(cols[COL_TCP_STREAM]));
                packet.setTcpSeq(parseLongOrNull(cols[COL_TCP_SEQ_RAW]));
                packet.setTcpFlags(tcpFlagsText(cols[COL_TCP_FLAGS]));
                packet.setHttpMethod(firstNonBlank(blankToNull(cols[13]), startLineToken(httpHeads[0], 0)));
                packet.setHttpUri(firstNonBlank(blankToNull(cols[14]), startLineToken(httpHeads[0], 1)));
                packet.setHttpStatus(parseIntOrNull(firstNonBlank(cols[17], startLineToken(httpHeads[1], 1))));
            }
            if (cols.length > COL_DNS_QUERY) {
                packet.setIpTtl(parseIntOrNull(firstNonBlank(cols[COL_IP_TTL], cols[COL_IPV6_HLIM])));
                packet.setTlsSni(blankToNull(cols[COL_TLS_SNI]));
                packet.setDnsQueryName(blankToNull(cols[COL_DNS_QUERY]));
            }
              // 报文落库切到本会话的 SQLite 数据源；插入在 tail / runAsync 线程上执行，
              // ThreadLocal 上下文相互独立，finally 恢复调用前上下文（通常为 null）
            this.withPacketDb(sessionId, () -> {
                this.packetMapper.insert(packet);
                return Boolean.TRUE;
            });
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
       * 从解析列中拼出 HTTP 请求头 / 响应头文本。
       *
       * @param cols 按 {@link #TSHARK_FIELDS} 顺序切出的列
       * @return 长度 2 的数组：[请求头, 响应头]；没有的部分为 null
       */
    private static String[] buildHttpHeads(String[] cols) {
        if (cols.length <= 20) {
            return new String[]{null, null};
        }
        return new String[]{
                httpRequestHead(cols[13], cols[14], cols[15], cols[16]),
                httpResponseHead(cols[17], cols[18], cols[19], cols[20])
        };
    }

      /**
       * 请求头：起始行 {@code GET /api/x HTTP/1.1} + 逐行头字段。
       */
    private static String httpRequestHead(String method, String uri, String version, String lines) {
        String m = blankToNull(method);
        String u = blankToNull(uri);
        String v = blankToNull(version);
        String body = normalizeHttpLines(lines);
        if (m == null && u == null && body == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (m != null) {
            sb.append(m);
            if (u != null) {
                sb.append(' ').append(u);
            }
            if (v != null) {
                sb.append(' ').append(v);
            }
            sb.append('\n');
        }
        if (body != null) {
            sb.append(body);
        }
        return clipHttpHead(sb);
    }

      /**
       * 响应头：状态行 {@code HTTP/1.1 200 OK} + 逐行头字段。
       */
    private static String httpResponseHead(String code, String phrase, String version, String lines) {
        String c = blankToNull(code);
        String p = blankToNull(phrase);
        String v = blankToNull(version);
        String body = normalizeHttpLines(lines);
        if (c == null && body == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (c != null) {
            if (v != null) {
                sb.append(v).append(' ');
            }
            sb.append(c);
            if (p != null) {
                sb.append(' ').append(p);
            }
            sb.append('\n');
        }
        if (body != null) {
            sb.append(body);
        }
        return clipHttpHead(sb);
    }

      /**
       * 还原 tshark 多值字段里的头行。
       *
       * <p>tshark 把值里的换行转义成字面量 {@code \r\n}，多值之间再用逗号连接，
       * 所以边界是 {@code \r\n,}；按它切开即一行一条，头字段值自身的逗号不受影响
       * （{@code Accept-Encoding: gzip, deflate} 原样保留）。</p>
       *
       * @param raw tshark 输出的一列（可能为 null / 空）
       * @return 一行一条的头字段；没有返回 null
       */
    private static String normalizeHttpLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.replace("\\r\\n,", "\n").replace("\\r\\n", "\n");
        s = s.replace("\r\n", "\n").trim();
        return s.isEmpty() ? null : s;
    }

      /**
       * 头部文本上限：Cookie / Set-Cookie 可能很长，不设限会把 SQLite 行撑爆。
       *
       * @param sb 已拼好的头部文本
       * @return 去尾空白后的文本；超长截断并标注
       */
    private static String clipHttpHead(StringBuilder sb) {
        String s = sb.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return s.length() > 4000 ? s.substring(0, 4000) + "\n…" : s;
    }

      /** 正文最多保留的十六进制字符数（≈24KB 原始字节）：一个下载就能上 MB，必须设硬上限 */
    private static final int HTTP_BODY_HEX_LIMIT = 48000;
      /** 正文解码后最多展示的字符数 */
    private static final int HTTP_BODY_TEXT_LIMIT = 16000;
      /** 原始字节解码前最多取的字节数（256KB）：只影响展示，真实长度另存 */
    private static final int HTTP_BODY_BYTES_LIMIT = 256 * 1024;
      /**
       * 入库的「正文字节 hex」最多保留的字节数（32KB → 64K 个十六进制字符）。
       *
       * <p>文本视图上限 16K 字符，hex 视图给到 32K 字节足够看清编码 / 结构；
       * 再往上加只会把每个报文行撑大，而 hex 视图本身也渲染不了那么多。</p>
       */
    private static final int HTTP_BODY_HEX_KEEP_BYTES = 32 * 1024;

      /** 十六进制字符表：toHex 走查表，避免 32KB 正文做几万次 String.format */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

      /**
       * 正文解码结果：同一份字节的两种视图。
       *
       * <p>文本/JSON 与 HEX 必须是同源字节 —— 前端拿 {@code text} 做文本与 JSON 美化，
       * 拿 {@code hex} 做十六进制视图，两边的行数 / 字节数才对得上。</p>
       *
       * @param text      可展示正文（二进制时为 {@code HEX } 前缀的预览）
       * @param hex       同一份字节的十六进制串；空正文为 null
       * @param binary    是否被判为二进制（文本视图不适合直接展示）
       * @param truncated 字节或字符是否已被截断
       */
    private record DecodedHttpBody(String text, String hex, boolean binary, boolean truncated) {
    }

      /**
       * 把 {@code http.file_data} 的十六进制还原成可读正文。
       *
       * <p>tshark 对 {@code FT_BYTES} 字段统一输出十六进制，所以不用处理原始二进制混进
       * 逐行解析的问题。顺序是：截断 → 按 Content-Encoding 解压（gzip / deflate）→ 按
       * 头里的 charset 解码 → 不可打印比例过高就退回十六进制预览。</p>
       *
       * @param hex         十六进制正文；空表示该帧没有实体
       * @param contentType 头里的 Content-Type（取 charset）
       * @param encoding    头里的 Content-Encoding（gzip / deflate / br）
       * @return 可展示正文；没有返回 null
       */
    private static DecodedHttpBody decodeHttpBody(String hex, String contentType, String encoding) {
        String h = hex == null ? "" : hex.trim();
        if (h.isEmpty()) {
            return null;
        }
        boolean truncated = h.length() > HTTP_BODY_HEX_LIMIT;
        if (truncated) {
            h = h.substring(0, HTTP_BODY_HEX_LIMIT);
        }
        return decodeHttpBody(hexToBytes(h), contentType, encoding, truncated);
    }

      /**
       * 把原始实体字节解码成可读正文（TCP 重组路径直接用字节，不经过十六进制）。
       *
       * <p>顺序：按 Content-Encoding 解压（gzip / deflate）→ 按头里的 charset 解码 →
       * 不可打印比例过高就退回十六进制预览。展示层只取前 {@link #HTTP_BODY_BYTES_LIMIT}
       * 字节，避免在几 MB 实体上做字符集与可打印判定。</p>
       *
       * @param raw         实体原始字节
       * @param contentType 头里的 Content-Type（取 charset）
       * @param encoding    头里的 Content-Encoding（gzip / deflate / br）
       * @param truncated   实体是否已被上游截断
       * @return 可展示正文；没有返回 null
       */
    private static DecodedHttpBody decodeHttpBody(byte[] raw, String contentType, String encoding, boolean truncated) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        byte[] bytes = raw;
        String enc = encoding == null ? "" : encoding.trim().toLowerCase(Locale.ROOT);
          // 压缩体：截断过的半截流解不开，直接走十六进制预览，别把异常当正文
        if (!truncated && (enc.contains("gzip") || enc.contains("deflate"))) {
            byte[] inf = inflate(raw, enc.contains("gzip"));
            if (inf != null && inf.length > 0) {
                bytes = inf;
            }
        }
        if (bytes.length > HTTP_BODY_BYTES_LIMIT) {
            bytes = java.util.Arrays.copyOf(bytes, HTTP_BODY_BYTES_LIMIT);
            truncated = true;
        }
        String text = new String(bytes, charsetOf(contentType));
        if (isProbablyBinary(text)) {
            return new DecodedHttpBody("HEX " + toHex(bytes, 1024) + (truncated ? " …" : ""),
                    toHex(bytes, HTTP_BODY_HEX_KEEP_BYTES), true, truncated || bytes.length > 1024);
        }
        text = text.replace("\u0000", "");
        if (text.length() > HTTP_BODY_TEXT_LIMIT) {
            text = text.substring(0, HTTP_BODY_TEXT_LIMIT);
            truncated = true;
        }
        return new DecodedHttpBody(truncated ? text + "\n…（正文已截断）" : text,
                toHex(bytes, HTTP_BODY_HEX_KEEP_BYTES), false,
                truncated || bytes.length > HTTP_BODY_HEX_KEEP_BYTES);
    }

      /**
       * 十六进制转字节。
       *
       * @param hex 十六进制串（允许夹空白）
       * @return 字节数组；非法字符跳过
       */
    private static byte[] hexToBytes(String hex) {
        StringBuilder sb = new StringBuilder(hex.length());
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (Character.digit(c, 16) >= 0) {
                sb.append(c);
            }
        }
        int n = sb.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(sb.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

      /**
       * 解压 gzip / deflate 正文。
       *
       * @param raw  压缩字节
       * @param gzip true = gzip，false = deflate(zlib)
       * @return 解压结果；失败或异常膨胀返回 null
       */
    private static byte[] inflate(byte[] raw, boolean gzip) {
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(raw);
             java.io.InputStream z = gzip
                     ? new java.util.zip.GZIPInputStream(in)
                     : new java.util.zip.InflaterInputStream(in);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int r;
            while ((r = z.read(buf)) > 0) {
                total += r;
                  // 解压炸弹保护：正文展示超过 256KB 就没有意义了
                if (total > 262144) {
                    break;
                }
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

      /**
       * 从 Content-Type 里取 charset。
       *
       * @param contentType 头值
       * @return 字符集；缺失或非法时 UTF-8
       */
    private static java.nio.charset.Charset charsetOf(String contentType) {
        if (contentType != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("charset\\s*=\\s*\"?([\\w\\-]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(contentType);
            if (m.find()) {
                try {
                    return java.nio.charset.Charset.forName(m.group(1));
                } catch (Exception ignore) {
                      // 头里写了个系统不认识的 charset：落回 UTF-8
                }
            }
        }
        return java.nio.charset.StandardCharsets.UTF_8;
    }

      /**
       * 粗略判断解出来的文本是不是二进制。
       *
       * @param text 解码结果
       * @return true 表示不可打印比例过高，应当改用十六进制预览
       */
    private static boolean isProbablyBinary(String text) {
        if (text.isEmpty()) {
            return false;
        }
        int bad = 0;
        int n = Math.min(text.length(), 4096);
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c == 0xFFFD || (c < 0x20 && c != '\n' && c != '\r' && c != '\t')) {
                bad++;
            }
        }
        return bad * 100 > n * 5;
    }

      /**
       * 字节转十六进制预览。
       *
       * <p>逐个 {@code String.format("%02x")} 在 32KB 正文上是几十万次格式化调用，
       * 抓包高峰期会明显拖慢解析线程；这里改用查表，结果完全一致。</p>
       *
       * @param bytes 原始字节
       * @param limit 最多取多少字节
       * @return 连续十六进制串
       */
    private static String toHex(byte[] bytes, int limit) {
        int n = Math.min(bytes.length, limit);
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            int v = bytes[i] & 0xFF;
            sb.append(HEX_CHARS[v >>> 4]).append(HEX_CHARS[v & 0x0F]);
        }
        return sb.toString();
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
       * @param sessionId 会话标识
       * @param page page
       * @param size 大小
       * @param keyword 关键字（可空）。在协议 / 源目的 IP / 源目的端口 / 摘要里做模糊匹配。
       *                端口是整型列，无法直接 like，只能先按文本匹配其余列，
       *                再对「端口字符串」单独补一次 eq —— 纯数字关键字才当端口用。
       * @param startTime 开始时间（可空），格式：yyyy-MM-dd HH:mm:ss
       * @param endTime 结束时间（可空），格式：yyyy-MM-dd HH:mm:ss
       * @return page数据包的结果
       */
    public ReturnResult<IPage<MsTsharkPacket>> pagePackets(Long sessionId, int page, int size, String keyword, String startTime
        , String endTime) {
        String kw = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<MsTsharkPacket> w =
                new LambdaQueryWrapper<MsTsharkPacket>().eq(sessionId != null, MsTsharkPacket::getSessionId, sessionId);
        if (!kw.isEmpty()) {
            w.and(q -> q.like(MsTsharkPacket::getProtocol, kw)
                    .or().like(MsTsharkPacket::getSourceIp, kw)
                    .or().like(MsTsharkPacket::getDestIp, kw)
                    .or().like(MsTsharkPacket::getPacketInfo, kw));
              // 端口是整型列，上面 like 不到；纯数字关键字再补一次端口等值匹配
            if (kw.chars().allMatch(Character::isDigit)) {
                int port = Integer.parseInt(kw);
                w.or(q2 -> q2.eq(MsTsharkPacket::getSourcePort, port)
                        .or().eq(MsTsharkPacket::getDestPort, port));
            }
        }
        if (startTime != null && !startTime.isBlank()) {
            w.ge(MsTsharkPacket::getPacketTime, LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (endTime != null && !endTime.isBlank()) {
            w.le(MsTsharkPacket::getPacketTime, LocalDateTime.parse(endTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        w.orderByDesc(MsTsharkPacket::getId);
          // 分页列表不取大字段（rawData / restoredText / HTTP 头与正文，KB 级起步），详情接口按需取
        w.select(MsTsharkPacket.class, column -> !"ms_tshark_packet_raw".equals(column.getColumn())
                && !"ms_tshark_packet_restored".equals(column.getColumn())
                && !"ms_tshark_packet_http_req".equals(column.getColumn())
                && !"ms_tshark_packet_http_resp".equals(column.getColumn())
                && !"ms_tshark_packet_http_body".equals(column.getColumn()));
        IPage<MsTsharkPacket> p = this.withPacketDb(sessionId,
                () -> this.packetMapper.selectPage(new Page<>(page, size), w));
        return ReturnResult.ok(p);
    }

      /**
       * 取首行（tshark 的报错文案常带换行，全塞进一条消息会很难读）。
       * @param s 字符串
       * @return 返回值
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
       * @param sessionId 会话标识
       * @param id 标识
       * @return 获取数据包的结果
       */
    public ReturnResult<MsTsharkPacket> getPacket(Long sessionId, Long id) {
        MsTsharkPacket packet = this.withPacketDb(sessionId, () -> this.packetMapper.selectById(id));
        return packet == null ? ReturnResult.error("数据包不存在: " + id) : ReturnResult.ok(packet);
    }

      /**
       * 取某个报文所属的 HTTP 事务（请求方向 + 响应方向两帧）。
       *
       * <p><b>为什么需要这个接口：</b>一帧只能承载一个方向 —— 请求头和请求体在请求起始帧，
       * 响应头和响应体在响应起始帧。详情抽屉只拿到当前帧时，用户看到的永远是「半条事务」：
       * 点请求帧没有响应，点响应帧没有请求，点中间的数据分片更是一段都没有。这里按 TCP 流
       * 把相邻的请求 / 响应配成一对，让「请求头 · 请求体 · 响应头 · 响应体」四段在一个
       * 面板里同时出现。</p>
       *
       * <p>配对优先用 {@code tcp.stream}（同一四元组可能被复用成多条连接），老库没有流号
       * 时退回四元组 + 帧号窗口。只取 HTTP 头非空的帧，且限制条数与列，避免长连接把
       * 整条流读进内存、把 rawData 一起搬出来。</p>
       *
       * @param sessionId 会话 id（报文按会话分库）
       * @param packetId  当前报文 id
       * @return request / response 两帧（没有的方向为 null）
       */
    public ReturnResult<Map<String, Object>> httpTransaction(Long sessionId, Long packetId) {
        MsTsharkPacket anchor = this.withPacketDb(sessionId, () -> this.packetMapper.selectById(packetId));
        if (anchor == null) {
            return ReturnResult.error("数据包不存在: " + packetId);
        }
        List<MsTsharkPacket> frames = this.withPacketDb(sessionId, () -> this.packetMapper.selectList(
                buildHttpTransactionQuery(anchor, true).last("LIMIT " + HTTP_TX_MAX_FRAMES)));
        if (frames.isEmpty() && anchor.getTcpStream() != null) {
              // 老会话：流号列是后加的，历史行全为 null，按四元组兜底再找一次
            frames = this.withPacketDb(sessionId, () -> this.packetMapper.selectList(
                    buildHttpTransactionQuery(anchor, false).last("LIMIT " + HTTP_TX_MAX_FRAMES)));
        }

          // 按帧号升序把「请求帧 + 紧随其后的响应帧」配成事务。
          //
          // <p>同一段消息会在两帧上留痕：起始帧带「头 + 正文」，收全帧只补一份头
          // （见 applyRestoredHttp）。如果直接把每一行都当成新消息，后出现的收全帧会把
          // 起始帧覆盖掉，配对结果就成了「有头没正文」—— 这正是四段数据看起来缺了
          // 请求体 / 响应体的原因。这里按「头文本相同即同一条消息」合并，并保留正文更
          // 完整的那一帧。</p>
        List<MsTsharkPacket[]> pairs = new ArrayList<>();
        MsTsharkPacket pending = null;
        for (MsTsharkPacket frame : frames) {
            if (notBlank(frame.getHttpRequestHead())) {
                if (pending != null && sameHttpHead(pending.getHttpRequestHead(), frame.getHttpRequestHead())) {
                    pending = richerBody(pending, frame);
                } else {
                    pending = frame;
                }
            }
            if (!notBlank(frame.getHttpResponseHead())) {
                continue;
            }
            if (pending == null && !pairs.isEmpty()) {
                  // 响应侧同样有「起始帧 + 收全帧」两份：头相同就并进上一对
                MsTsharkPacket[] last = pairs.get(pairs.size() - 1);
                if (last[1] != null && sameHttpHead(last[1].getHttpResponseHead(), frame.getHttpResponseHead())) {
                    last[1] = richerBody(last[1], frame);
                    continue;
                }
            }
            pairs.add(new MsTsharkPacket[]{pending, frame});
            pending = null;
        }
        MsTsharkPacket[] pick = null;
        for (MsTsharkPacket[] pair : pairs) {
            if (isSamePacket(pair[0], packetId) || isSamePacket(pair[1], packetId)) {
                pick = pair;
                break;
            }
        }
        if (pick == null) {
              // 当前是数据 / 握手 / 挥手帧：取请求帧号不晚于当前帧的最后一对，
              // 这样点「响应体后面那个 ACK」也能看到刚发生的事务
            for (MsTsharkPacket[] pair : pairs) {
                if (pair[0] != null && pair[0].getFrameNo() != null && anchor.getFrameNo() != null
                        && pair[0].getFrameNo() <= anchor.getFrameNo()) {
                    pick = pair;
                }
            }
        }
        if (pick == null && !pairs.isEmpty()) {
            pick = pairs.get(0);
        }

        MsTsharkPacket request = pick == null ? null : pick[0];
        MsTsharkPacket response = pick == null ? null : pick[1];
          // 没有成对时退回当前帧自己的方向：实时抓包里响应还没到时，至少先把请求显示出来
        if (request == null && notBlank(anchor.getHttpRequestHead())) {
            request = anchor;
        }
        if (response == null && notBlank(anchor.getHttpResponseHead())) {
            response = anchor;
        }

        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put("anchorId", packetId);
        out.put("request", httpTxFrame(request));
        out.put("response", httpTxFrame(response));
        return ReturnResult.ok(out);
    }

      /**
       * 构造 HTTP 事务查询：优先按 TCP 流号，退回按四元组 + 帧号窗口。
       *
       * @param anchor   当前报文
       * @param byStream true = 按 tcp.stream（新库）；false = 按四元组（老库兜底）
       * @return 查询条件
       */
    private static LambdaQueryWrapper<MsTsharkPacket> buildHttpTransactionQuery(MsTsharkPacket anchor, boolean byStream) {
        LambdaQueryWrapper<MsTsharkPacket> w = new LambdaQueryWrapper<MsTsharkPacket>()
                .eq(MsTsharkPacket::getSessionId, anchor.getSessionId());
        if (byStream && anchor.getTcpStream() != null) {
            w.eq(MsTsharkPacket::getTcpStream, anchor.getTcpStream());
        } else {
            w.and(q -> q
                    .nested(a -> a.eq(MsTsharkPacket::getSourceIp, anchor.getSourceIp())
                            .eq(MsTsharkPacket::getSourcePort, anchor.getSourcePort())
                            .eq(MsTsharkPacket::getDestIp, anchor.getDestIp())
                            .eq(MsTsharkPacket::getDestPort, anchor.getDestPort()))
                    .or()
                    .nested(b -> b.eq(MsTsharkPacket::getSourceIp, anchor.getDestIp())
                            .eq(MsTsharkPacket::getSourcePort, anchor.getDestPort())
                            .eq(MsTsharkPacket::getDestIp, anchor.getSourceIp())
                            .eq(MsTsharkPacket::getDestPort, anchor.getSourcePort())));
            if (anchor.getFrameNo() != null) {
                w.ge(MsTsharkPacket::getFrameNo, Math.max(1L, anchor.getFrameNo() - HTTP_TX_FRAME_WINDOW))
                        .le(MsTsharkPacket::getFrameNo, anchor.getFrameNo() + HTTP_TX_FRAME_WINDOW);
            }
        }
        w.and(q -> q.isNotNull(MsTsharkPacket::getHttpRequestHead)
                .or().isNotNull(MsTsharkPacket::getHttpResponseHead));
        w.select(MsTsharkPacket.class, column -> HTTP_TX_COLUMNS.contains(column.getColumn()));
        w.orderByAsc(MsTsharkPacket::getFrameNo).orderByAsc(MsTsharkPacket::getId);
        return w;
    }

      /**
       * 把一帧压成事务展示用的轻量结构（不带 rawData）。
       *
       * @param packet 报文；null 原样返回 null
       * @return 前端可直接渲染的字段集合
       */
    private static Map<String, Object> httpTxFrame(MsTsharkPacket packet) {
        if (packet == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", packet.getId());
        m.put("frameNo", packet.getFrameNo());
        m.put("packetTime", packet.getPacketTime());
        m.put("protocol", packet.getProtocol());
        m.put("sourceIp", packet.getSourceIp());
        m.put("sourcePort", packet.getSourcePort());
        m.put("destIp", packet.getDestIp());
        m.put("destPort", packet.getDestPort());
        m.put("packetInfo", packet.getPacketInfo());
        m.put("httpRequestHead", packet.getHttpRequestHead());
        m.put("httpResponseHead", packet.getHttpResponseHead());
        m.put("httpBody", packet.getHttpBody());
        m.put("httpBodyHex", packet.getHttpBodyHex());
        m.put("httpContentType", packet.getHttpContentType());
        m.put("httpContentEncoding", packet.getHttpContentEncoding());
        m.put("httpContentLength", packet.getHttpContentLength());
        m.put("httpMethod", packet.getHttpMethod());
        m.put("httpUri", packet.getHttpUri());
        m.put("httpStatus", packet.getHttpStatus());
        m.put("restoredText", packet.getRestoredText());
        return m;
    }

      /**
       * 是否就是指定 id 的报文。
       */
    private static boolean isSamePacket(MsTsharkPacket packet, Long id) {
        return packet != null && packet.getId() != null && packet.getId().equals(id);
    }

      /**
       * 两段 HTTP 头是否描述同一条消息（起始帧与收全帧存的是同一份头）。
       *
       * @param a 头文本
       * @param b 头文本
       * @return 规范化后是否一致
       */
    private static boolean sameHttpHead(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equals(b.trim());
    }

      /**
       * 同一消息的两份落点里取正文更完整的那一帧。
       *
       * <p>起始帧通常带正文，收全帧只补头；反过来（正文被拆到后续帧）时取后者。
       * 两份都没有正文时保留先到的，保证 id / 帧号稳定。</p>
       *
       * @param a 先到的帧
       * @param b 后到的帧
       * @return 正文更完整的一帧
       */
    private static MsTsharkPacket richerBody(MsTsharkPacket a, MsTsharkPacket b) {
        int la = a.getHttpBody() == null ? 0 : a.getHttpBody().length();
        int lb = b.getHttpBody() == null ? 0 : b.getHttpBody().length();
        return lb > la ? b : a;
    }

      /**
       * 非空且非空白。
       */
    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

      /**
       * 协议stats。
       * @param sessionId 会话标识
       * @return 协议stats的结果
       */
    public ReturnResult<List<TsharkProtocolStatVO>> protocolStats(Long sessionId) {
          // SQL 层聚合，不再全量加载报文到内存
        List<Map<String, Object>> rows = this.withPacketDb(sessionId,
                () -> this.packetMapper.selectProtocolStatsBySessionId(sessionId));
        List<TsharkProtocolStatVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String proto = row.get("protocol") == null ? "OTHER" : String.valueOf(row.get("protocol"));
            int count = ((Number) row.get("count")).intValue();
            long bytes = ((Number) row.get("bytes")).longValue();
            result.add(new TsharkProtocolStatVO(proto, count, bytes));
        }
        return ReturnResult.ok(result);
    }

      /**
       * top流。
       * @param sessionId 会话标识
       * @param limit 限制
       * @return top流的结果
       */
    public ReturnResult<List<TsharkFlowVO>> topFlows(Long sessionId, int limit) {
          // SQL 层聚合，不再全量加载报文到内存；SQL 已带 LIMIT 20
        List<Map<String, Object>> rows = this.withPacketDb(sessionId,
                () -> this.packetMapper.selectTopFlowsBySessionId(sessionId));
        List<TsharkFlowVO> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String flow = row.get("flow") == null ? "" : String.valueOf(row.get("flow"));
            int count = ((Number) row.get("count")).intValue();
            long bytes = ((Number) row.get("bytes")).longValue();
            String sourceIp = row.get("sourceIp") == null ? "" : String.valueOf(row.get("sourceIp"));
            Integer sourcePort = row.get("sourcePort") == null ? null : ((Number) row.get("sourcePort")).intValue();
            String destIp = row.get("destIp") == null ? "" : String.valueOf(row.get("destIp"));
            Integer destPort = row.get("destPort") == null ? null : ((Number) row.get("destPort")).intValue();
            result.add(new TsharkFlowVO(flow, count, bytes, sourceIp, sourcePort, destIp, destPort));
        }
        return ReturnResult.ok(result.subList(0, Math.min(Math.max(0, limit), result.size())));
    }

      /**
       * 时间轴统计：按时间桶聚合报文的数量与总字节数。
       *
       * @param sessionId     会话标识
       * @param bucketSeconds 桶大小（秒），默认 10
       * @return 时间轴数据列表，格式：[{"t": "2026-10-19 10:00:00", "packets": 123, "bytes": 45678}, ...]
       */
    public List<Map<String, Object>> timeline(Long sessionId, Integer bucketSeconds) {
        int bucket = bucketSeconds == null || bucketSeconds <= 0 ? 10 : bucketSeconds;
        return this.withPacketDb(sessionId, () -> this.packetMapper.selectTimelineBySessionId(sessionId, bucket));
    }

      /**
       * 会话常见异常诊断。
       *
       * <p>聚合口径见 {@link MsTsharkPacketMapper#selectAnomalyCountsBySessionId}；
       * 计数为 0 的项不返回，前端「很干净」的判据是有没有行，而不是行的计数。</p>
       *
       * @param sessionId 会话标识
       * @return 异常诊断列表；每项含 key/label/severity/count/filter
       */
    public ReturnResult<List<TsharkAnomalyVO>> anomalies(Long sessionId) {
        Map<String, Object> row = this.withPacketDb(sessionId,
                () -> this.packetMapper.selectAnomalyCountsBySessionId(sessionId));
        List<TsharkAnomalyVO> result = new ArrayList<>(9);
        if (row != null) {
            addAnomaly(result, row, "reset", "连接重置 (RST)", "danger",
                    "tcp.flags.reset == 1");
            addAnomaly(result, row, "unreachable", "ICMP 不可达", "danger",
                    "icmp.type == 3");
            addAnomaly(result, row, "retransmission", "TCP 重传", "warning",
                    "tcp.analysis.retransmission");
            addAnomaly(result, row, "outOfOrder", "TCP 乱序", "warning",
                    "tcp.analysis.out-of-order");
            addAnomaly(result, row, "zeroWindow", "TCP 零窗口", "warning",
                    "tcp.analysis.zero_window");
            addAnomaly(result, row, "dnsFail", "DNS 解析失败", "warning",
                    "dns.flags.rcode != 0 && dns.flags.response == 1");
            addAnomaly(result, row, "http4xx", "HTTP 4xx 响应", "info",
                    "http.response.code >= 400 && http.response.code < 500");
            addAnomaly(result, row, "http5xx", "HTTP 5xx 响应", "warning",
                    "http.response.code >= 500 && http.response.code < 600");
            addAnomaly(result, row, "tlsAlert", "TLS 告警", "info",
                    "tls.record.content_type == 21");
        }
        return ReturnResult.ok(result);
    }

    /** addAnomaly 方法 */
    private static void addAnomaly(List<TsharkAnomalyVO> out, Map<String, Object> row, String key,
                                   String label, String severity, String filter) {
        Object v = row.get(key);
        long count = v instanceof Number n ? n.longValue() : 0L;
        if (count > 0) {
            out.add(new TsharkAnomalyVO(key, label, severity, count, filter));
        }
    }

      /**
       * 按保留策略清理抓包数据（过期天数 / 目录空间）。
       *
       * <p>两条规则各成一条独立判据，任一满足即删：</p>
       * <ol>
       *   <li><b>过期</b>：会话创建时间早于 {@code days} 天；{@code days <= 0} 时关闭该规则。</li>
       *   <li><b>目录超量</b>：{@code TSHARK_CAPTURE_DIR} 总占用超过 {@code sizeMb}，
       *       从最老的会话开始删，直到回落到上限以内；{@code sizeMb <= 0} 时关闭该规则。</li>
       * </ol>
       *
       * <p>删的是整条会话：会话记录 + 报文 SQLite + 原始 pcap + 抓包日志，
       * 复用 {@link #deleteSession(Long)} 的口径，不另写一套清理逻辑。正在抓包 /
       * 正在解析的会话一律跳过，交给用户先停止，避免把还在写的文件删掉。</p>
       *
       * <p>{@code enabled=false} 时只统计不删，返回 0。</p>
       *
       * @return 实际删除的会话数
       */
    public int cleanupRetention() {
        TsharkRetentionProperties policy = this.retentionProperties;
        if (policy == null || !policy.isEnabled()) {
            return 0;
        }
        List<MsTsharkSession> sessions = this.sessionMapper.selectList(
                new LambdaQueryWrapper<MsTsharkSession>()
                        .orderByAsc(MsTsharkSession::getCreateTime)
                        .orderByAsc(MsTsharkSession::getId));
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        Set<Long> removed = new LinkedHashSet<>();
        // 规则一：过期
        if (policy.getDays() > 0) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(policy.getDays());
            for (MsTsharkSession session : sessions) {
                if (session == null || session.getId() == null || isCapturing(session)) {
                    continue;
                }
                LocalDateTime created = session.getCreateTime();
                if (created != null && created.isBefore(cutoff)) {
                    if (removeSessionQuietly(session.getId())) {
                        deleted++;
                        removed.add(session.getId());
                    }
                }
            }
        }
        // 规则二：目录空间
        long limitBytes = (long) policy.getSizeMb() * 1024L * 1024L;
        if (limitBytes > 0) {
            long used = captureDirBytes();
            for (MsTsharkSession session : sessions) {
                if (used <= limitBytes) {
                    break;
                }
                if (session == null || session.getId() == null
                        || isCapturing(session) || removed.contains(session.getId())) {
                    continue;
                }
                if (removeSessionQuietly(session.getId())) {
                    deleted++;
                    removed.add(session.getId());
                    used = captureDirBytes();
                }
            }
        }
        if (deleted > 0) {
            log.info("[tshark] 保留清理删除 {} 条会话（天数={} 空间上限={}MB）",
                    deleted, policy.getDays(), policy.getSizeMb());
        }
        return deleted;
    }

      /**
       * 会话是否仍在抓包 / 解析中（状态 1）。
       *
       * @param session 会话
       * @return 是则跳过清理
       */
    private static boolean isCapturing(MsTsharkSession session) {
        return Integer.valueOf(1).equals(session.getCaptureStatus());
    }

      /**
       * 删除单条会话，失败只记日志不中断整轮清理。
       *
       * @param sessionId 会话标识
       * @return 是否删除成功
       */
    private boolean removeSessionQuietly(Long sessionId) {
        try {
            ReturnResult<Void> result = deleteSession(sessionId);
            return result != null && result.isOk();
        } catch (Exception e) {
            log.warn("[tshark] 保留清理删除会话失败 sessionId={} reason={}", sessionId, e.getMessage());
            return false;
        }
    }

      /**
       * 抓包目录当前占用字节数（递归统计，目录不存在按 0）。
       *
       * @return 字节数
       */
    private static long captureDirBytes() {
        Path dir = Paths.get(CAPTURE_DIR);
        if (!Files.isDirectory(dir)) {
            return 0L;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (Exception e) {
                    return 0L;
                }
            }).sum();
        } catch (Exception e) {
            return 0L;
        }
    }

      /**
       * 删除会话。
       * @param sessionId 会话标识
       * @return 删除会话的结果
       */
    public ReturnResult<Void> deleteSession(Long sessionId) {
        MsTsharkSession current = this.sessionMapper.selectById(sessionId);
        if (current == null) {
            return ReturnResult.error("会话不存在或已被删除");
        }
        // 抓包未停止时不允许删：老实现会先 destroy 进程再删库，前端一个误点就把正在抓的
        // 会话连同 pcap 一起抹掉，连「停止 → 下载原件」的补救机会都没有。前端在 running
        // 时也不显示「删除」（见 TsharkSessionActions.vue），这里再兜一层。
        if (Integer.valueOf(1).equals(current.getCaptureStatus())) {
            if (SOURCE_IMPORT.equals(current.getSource())) {
                return ReturnResult.error("该会话正在解析中，请等待解析完成后再删除");
            }
            return ReturnResult.error("该会话正在抓包中，请先停止抓包再删除");
        }
        this.displayFilters.remove(sessionId);
        this.httpRestorers.remove(sessionId);
        Process proc = this.running.get(sessionId);
        if (proc != null) {
            proc.destroy();
            this.running.remove(sessionId);
        }
          // SSE 订阅全部摘除：会话没了，挂着的心跳与重连都是浪费
        TsharkPacketStreamRegistry registry = streamRegistry();
        if (registry != null) {
            registry.removeAll(sessionId);
        }
          // 报文不再在 MySQL 里，整套迁移到会话自己的 SQLite 文件：关池拿路径后随文件一起删
        Path dbPath = this.sqliteRegistry.close(sessionId);
        MsTsharkSession session = this.sessionMapper.selectById(sessionId);
          // 跨重启的旧会话没有进过本进程的连接池，close() 取不到路径；
          // 报文库与 pcap 同目录同名，按 pcap 路径反推，否则 .db 永远删不掉，
          // 保留清理的空间规则会一直判定「超量」，把新会话也一起删了。
        if (dbPath == null && session != null && session.getFilePath() != null) {
            dbPath = TsharkSqliteSourceRegistry.dbPathForPcap(Paths.get(session.getFilePath()));
        }
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
        if (dbPath != null) {
            try {
                Files.deleteIfExists(dbPath);
                Files.deleteIfExists(Paths.get(dbPath + "-wal"));
                Files.deleteIfExists(Paths.get(dbPath + "-shm"));
            } catch (Exception ignored) {
                  // 库文件清理失败同样不阻断删会话
            }
        }
        this.sessionMapper.deleteById(sessionId);
          // 会话列表同步：其它打开的页面立即移除这一行，不必等下一次轮询
        this.pushService.pushTsharkSessionDeleted(sessionId);
        return ReturnResult.success();
    }}