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
import com.chua.starter.monitor.support.model.TsharkProtocolStatVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * <p>逐列含义：0 时间戳(秒) · 1 协议 · 2 IPv4源 · 3 IPv6源 · 4 TCP源端口 ·
     * 5 UDP源端口 · 6 IPv4目的 · 7 IPv6目的 · 8 TCP目的端口 · 9 UDP目的端口 ·
     * 10 帧长 · 11 人可读摘要。IPv4/IPv6 与 TCP/UDP 成对给出，某层不存在时该列为空串，
     * 由 {@code firstNonBlank} 取非空的那个。</p>
     *
     * <p>用 {@code _ws.col.Info} 而非 {@code -x} 的十六进制展开：前者就是 tshark
     * 「Info」列（如 {@code GET /ms/... HTTP/1.1}），正是实体里「人可读摘要」要的东西。</p>
     */
    private static final String[] TSHARK_FIELDS = {
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

    private final Map<Long, Process> running = new ConcurrentHashMap<>(); // running
    private final MsTsharkSessionMapper sessionMapper; // 会话映射器
    private final MsTsharkPacketMapper packetMapper; // 数据包映射器
    /**
     * 实时推送服务（Socket 未启用时静默跳过）
    */
    private final MonitorPushService pushService;

    public TsharkCaptureService(MsTsharkSessionMapper sessionMapper,
                                MsTsharkPacketMapper packetMapper,
                                MonitorPushService pushService) {
        this.sessionMapper = sessionMapper;
        this.packetMapper = packetMapper;
        this.pushService = pushService;
    }

    /**
     * 列表接口。
     * @return 列表接口的结果
     */
    public ReturnResult<List<String>> listInterfaces() {
        try {
            ProcessBuilder pb = new ProcessBuilder(TSHARK_BIN, "-D");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            List<String> ifaces = new ArrayList<>();
            /**
             * 尝试。
             * @param InputStreamReader(p.getInputStream( 输入流读取(p.获取输入流(
             */
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.split("\\.");
                    if (parts.length >= 2) {
                        String name = parts[1].trim().split("\\s+")[0];
                        ifaces.add(name);
                    } else {
                        ifaces.add(line.trim());
                    }
                }
            }
            p.waitFor(10L, TimeUnit.SECONDS);
            return ReturnResult.ok(ifaces);
        } catch (Exception e) {
            log.error("tshark list interfaces failed", e);
            return ReturnResult.error("无法列出网卡: " + execHint(e));
        }
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
     * 启动capture。
     * @param name 名称
     * @param iface iface
     * @param filter 过滤器
     * @param count 数量
     * @param timeout 超时
     * @return 启动capture的结果
     */
    public ReturnResult<MsTsharkSession> startCapture(String name, String iface, String filter, Integer count, Integer timeout) {
        try {
            if (iface == null || iface.isBlank()) {
                return ReturnResult.error("请指定网卡");
            }
            Files.createDirectories(Paths.get(CAPTURE_DIR));
            String fileName = UUID.randomUUID().toString().replace("-", "") + ".pcap";
            Path filePath = Paths.get(CAPTURE_DIR, fileName);
            List<String> cmd = new ArrayList<>();
            cmd.add(TSHARK_BIN);
            cmd.add("-i");
            cmd.add(iface);
            if (filter != null && !filter.isBlank()) {
                cmd.add("-f");
                cmd.add(filter);
            }
            if (count != null && count > 0) {
                cmd.add("-c");
                cmd.add(String.valueOf(count));
            }
            if (timeout != null && timeout > 0) {
                cmd.add("-a");
                cmd.add("duration:" + timeout);
            }
            cmd.add("-w");
            cmd.add(filePath.toString());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File(filePath + ".log"));
            Process proc = pb.start();
            MsTsharkSession session = new MsTsharkSession();
            session.setSessionName(name == null || name.isBlank() ? iface + " 抓包" : name);
            session.setCaptureInterface(iface);
            session.setCaptureFilter(filter);
            session.setCaptureStatus(1);
            session.setPacketCount(0);
            session.setFilePath(filePath.toString());
            session.setCaptureDuration(timeout);
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
            boolean finished = proc.waitFor(60L, TimeUnit.MINUTES);
            if (!finished) {
                proc.destroy();
                proc.waitFor(5L, TimeUnit.SECONDS);
            }
            this.parsePcapToDb(sessionId, filePath);
            MsTsharkSession session = this.sessionMapper.selectById(sessionId);
            if (session != null) {
                session.setCaptureStatus(0);
                session.setEndTime(LocalDateTime.now());
                this.sessionMapper.updateById(session);
                this.pushService.pushTsharkSession(session, sessionId);
            }
        } catch (Exception e) {
            log.error("finalize capture failed, session={}", sessionId, e);
        } finally {
            this.running.remove(sessionId);
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
     */
    private void parsePcapToDb(Long sessionId, Path filePath) {
        try {
            if (!Files.exists(filePath) || Files.size(filePath) == 0L) {
                log.warn("capture file empty, session={}", sessionId);
                return;
            }
            List<String> cmd = new ArrayList<>(List.of(TSHARK_BIN, "-r", filePath.toString(),
                    "-T", "fields", "-E", "separator=\t", "-E", "quote=n", "-E", "occurrence=f"));
            // 字段顺序即输出列顺序，逐列含义见 TSHARK_FIELDS 的注释
            for (String field : TSHARK_FIELDS) {
                cmd.add("-e");
                cmd.add(field);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int batch = 0;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8), 0x100000)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    if (this.savePacket(sessionId, line.split("\t", -1))) {
                        batch++;
                    }
                }
            }
            if (batch > 0) {
                MsTsharkSession session = this.sessionMapper.selectById(sessionId);
                if (session != null) {
                    session.setPacketCount(batch);
                    this.sessionMapper.updateById(session);
                }
            } else {
                // 0 条必须出声：否则页面只显示「抓完了但一个包都没有」，
                // 用户无从判断是没流量还是解析坏了
                log.warn("capture parsed 0 packet, session={}, file={}", sessionId, filePath);
            }
            p.waitFor(60L, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("parse pcap failed, session={}", sessionId, e);
        }
    }

    /**
     * 落库一个数据包。
     *
     * @param sessionId 会话标识
     * @param cols      按 {@link #TSHARK_FIELDS} 顺序切出的列（制表符分隔）
     * @return 是否成功入库
     */
    private boolean savePacket(Long sessionId, String[] cols) {
        try {
            if (cols.length < TSHARK_FIELD_COUNT) {
                return false;
            }
            MsTsharkPacket packet = new MsTsharkPacket();
            packet.setSessionId(sessionId);
            packet.setPacketTime(epochToDateTime(cols[0]));
            packet.setProtocol(blankToNull(cols[1]));
            // IPv4 / IPv6 各取第一个非空；端口同理（TCP 优先，其次 UDP）
            packet.setSourceIp(firstNonBlank(cols[2], cols[3]));
            packet.setSourcePort(parseIntOrNull(firstNonBlank(cols[4], cols[5])));
            packet.setDestIp(firstNonBlank(cols[6], cols[7]));
            packet.setDestPort(parseIntOrNull(firstNonBlank(cols[8], cols[9])));
            packet.setPacketLength(parseIntOrNull(cols[10]));
            packet.setPacketInfo(blankToNull(cols[11]));
            this.packetMapper.insert(packet);
            // 实时推送：每包广播（数据 id = 会话 id，前端按当前会话过滤）
            this.pushService.pushTsharkPacket(packet, sessionId);
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
     * @param page page
     * @param size 大小
     * @return page会话的结果
     */
    public ReturnResult<IPage<MsTsharkSession>> pageSessions(int page, int size) {
        IPage<MsTsharkSession> p = this.sessionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<MsTsharkSession>().orderByDesc(MsTsharkSession::getId));
        return ReturnResult.ok(p);
    }

    /**
     * page数据包。
     * @param sessionId 会话标识
     * @param page page
     * @param size 大小
     * @return page数据包的结果
     */
    public ReturnResult<IPage<MsTsharkPacket>> pagePackets(Long sessionId, int page, int size) {
        IPage<MsTsharkPacket> p = this.packetMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<MsTsharkPacket>().eq(sessionId != null, MsTsharkPacket::getSessionId, sessionId)
                        .orderByDesc(MsTsharkPacket::getId));
        return ReturnResult.ok(p);
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
     * @param sessionId 会话标识
     * @return 协议stats的结果
     */
    public ReturnResult<List<TsharkProtocolStatVO>> protocolStats(Long sessionId) {
        List<MsTsharkPacket> packets = this.packetMapper.selectList(
                new LambdaQueryWrapper<MsTsharkPacket>().eq(sessionId != null, MsTsharkPacket::getSessionId, sessionId));
        Map<String, long[]> counter = new LinkedHashMap<>();
        for (MsTsharkPacket p : packets) {
            String proto = (p.getProtocol() == null || p.getProtocol().isBlank()) ? "OTHER" : p.getProtocol();
            long[] c = counter.computeIfAbsent(proto, k -> new long[2]);
            c[0]++;
            c[1] += p.getPacketLength() != null ? p.getPacketLength() : 0;
        }
        List<TsharkProtocolStatVO> result = new ArrayList<>(counter.size());
        for (Map.Entry<String, long[]> e : counter.entrySet()) {
            result.add(new TsharkProtocolStatVO(e.getKey(), (int) e.getValue()[0], e.getValue()[1]));
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
        List<MsTsharkPacket> packets = this.packetMapper.selectList(
                new LambdaQueryWrapper<MsTsharkPacket>().eq(sessionId != null, MsTsharkPacket::getSessionId, sessionId));
        Map<String, long[]> counter = new LinkedHashMap<>();
        for (MsTsharkPacket p : packets) {
            String src = p.getSourceIp() != null ? p.getSourceIp() : "?";
            String dst = p.getDestIp() != null ? p.getDestIp() : "?";
            String flow = src + ":" + p.getSourcePort() + " -> " + dst + ":" + p.getDestPort();
            long[] c = counter.computeIfAbsent(flow, k -> new long[2]);
            c[0]++;
            c[1] += p.getPacketLength() != null ? p.getPacketLength() : 0;
        }
        List<TsharkFlowVO> result = new ArrayList<>(counter.size());
        for (Map.Entry<String, long[]> e : counter.entrySet()) {
            result.add(new TsharkFlowVO(e.getKey(), (int) e.getValue()[0], e.getValue()[1]));
        }
        result.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return ReturnResult.ok(result.subList(0, Math.min(Math.max(0, limit), result.size())));
    }

    /**
     * 删除会话。
     * @param sessionId 会话标识
     * @return 删除会话的结果
     */
    public ReturnResult<Void> deleteSession(Long sessionId) {
        Process proc = this.running.get(sessionId);
        if (proc != null) {
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
