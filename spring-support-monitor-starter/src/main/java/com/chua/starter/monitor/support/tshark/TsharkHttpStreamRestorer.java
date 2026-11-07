package com.chua.starter.monitor.support.tshark;

import com.chua.network.support.tshark.PacketMeta;
import com.chua.network.support.tshark.PacketRecord;
import com.chua.network.support.tshark.stream.ReassembledMessage;
import com.chua.network.support.tshark.stream.TcpStreamAssembler;
import com.chua.network.support.tshark.stream.TcpStreamKey;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于 TCP 流重组的 HTTP 请求 / 响应还原器。
 *
 * <h3>为什么不能只读逐帧 {@code http.*} 字段</h3>
 * <p>tshark 的 {@code http.request.line} / {@code http.file_data} 是它自己重组后的结果，
 * 但只在「一条消息收全」的那一帧上出现；真实抓包里请求头、请求体、响应头、响应体
 * 常常被拆到多个 TCP 段，甚至同一个方向上连续跑多轮请求响应（keep-alive、pipelining）。
 * 只看单帧字段会有三类缺口：</p>
 * <ol>
 *   <li><b>跨段正文</b> —— 正文被切成 3 段时，单帧视角只有片段；</li>
 *   <li><b>没有 Content-Length</b> —— 服务端以连接关闭界定正文（HTTP/1.0 常见），
 *       单帧永远不知道正文到哪里结束；</li>
 *   <li><b>多轮事务</b> —— 同一条连接上的第 2、3 次请求响应无法按帧归属。</li>
 * </ol>
 *
 * <h3>本类怎么做</h3>
 * <p>先用 {@link TcpStreamAssembler} 按序列号把两个方向各自拼回连续字节流
 * （重传、乱序、序列号回绕都由它处理），再对重组后的字节做 HTTP 消息切分：
 * 起始行 + 头 + 实体，实体长度按 {@code Transfer-Encoding: chunked} 或
 * {@code Content-Length} 判定，都没有时按连接关闭（FIN/RST 或解析收尾）判定。
 * 切出的每条消息归属到「让它收全的那一帧」，与 tshark 自己的归属口径一致。</p>
 *
 * <h3>内存上界</h3>
 * <p>抓包无界，因此单方向未切出的字节、单条实体、流状态数量都有硬上限；
 * 超限时丢弃最旧状态或截断实体并显式标记 {@link RestoredHttp#truncated()}，
 * 不静默把内存吃光。</p>
 *
 * <p>本类非线程安全，约定由单个解析循环串行调用。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public final class TsharkHttpStreamRestorer {

    /**
     * 单方向未切出的字节上限（4MB）。超过说明遇到了无法解析的协议或超长实体，
     * 保留下去只会持续吃内存，直接丢弃该方向缓冲。
     */
    private static final int MAX_CARRY_BYTES = 4 * 1024 * 1024;

    /**
     * 单条实体最多保留的原始字节数。展示层本身还会截断，这里只防内存。
     */
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    /**
     * 头部最大字节数。超长 Cookie / 异常流量不应把缓冲堵死。
     */
    private static final int MAX_HEAD_BYTES = 64 * 1024;

    /**
     * 同时保留的 HTTP 流方向状态上限，超过按最久未访问淘汰。
     */
    private static final int MAX_DIRECTIONS = 4096;

    /**
     * 十六进制解析基址（tshark 标志位以 0x 为前缀）
     */
    private static final int RADIX_16 = 16;

    /**
     * HTTP 状态码：100 继续
     */
    private static final int HTTP_STATUS_CONTINUE = 100;
    /**
     * HTTP 状态码：101 协议升级（WebSocket / h2c）
     */
    private static final int HTTP_STATUS_SWITCHING_PROTOCOLS = 101;
    /**
     * HTTP 状态码：200 成功
     */
    private static final int HTTP_STATUS_OK = 200;
    /**
     * HTTP 状态码：204 无内容
     */
    private static final int HTTP_STATUS_NO_CONTENT = 204;
    /**
     * HTTP 状态码：300 多种选择（只用作区间上界）
     */
    private static final int HTTP_STATUS_MULTIPLE_CHOICES = 300;
    /**
     * HTTP 状态码：304 未修改
     */
    private static final int HTTP_STATUS_NOT_MODIFIED = 304;

    /**
     * HTTP 起始行最小检测长度
     */
    private static final int HTTP_START_LINE_MIN = 12;

    /**
     * TCP 重组器存活上限（毫秒）
     */
    private static final long MAX_STREAM_LIFETIME_MS = 30_000L;

    /**
     * 已识别的 HTTP 请求方法。用于在字节流里定位消息起点（断流重连、抓包从中间开始时）。
     */
    private static final String[] METHODS = {
            "GET ", "POST ", "PUT ", "DELETE ", "HEAD ", "OPTIONS ", "PATCH ", "TRACE ", "CONNECT "};

    /**
     * TCP 重组器：只负责把字节按序列号拼连续，不做协议解析。
     */
    private final TcpStreamAssembler assembler =
            new TcpStreamAssembler(MAX_CARRY_BYTES, MAX_DIRECTIONS, MAX_STREAM_LIFETIME_MS);

    /**
     * 每个方向一个 HTTP 消息切分器。
     */
    private final Map<Direction, HttpMessageParser> parsers = new LinkedHashMap<>(64, 0.75f, true);

    /**
     * 每条连接共享的上下文（响应要据此判断 HEAD 请求没有实体）。
     */
    private final Map<TcpStreamKey, StreamContext> contexts = new LinkedHashMap<>(64, 0.75f, true);

    /**
     * 已切出、等待落库的消息。
     */
    private final List<RestoredHttp> ready = new ArrayList<>();

    /**
     * 投喂一个 TCP 段。
     *
     * @param frameNo    该段所在帧号
     * @param sourceIp   源 IP
     * @param sourcePort 源端口
     * @param destIp     目的 IP
     * @param destPort   目的端口
     * @param streamId   tshark 流号，可空
     * @param seq        TCP 绝对序列号（{@code tcp.seq_raw}），缺失表示非 TCP 帧
     * @param flags      TCP 标志位（{@code tcp.flags}，如 {@code 0x0018}），可空
     * @param payload    该段的载荷字节，空表示纯控制帧
     */
    public void accept(long frameNo, String sourceIp, Integer sourcePort, String destIp, Integer destPort,
                       String streamId, Long seq, String flags, byte[] payload) {
        int flagBits = parseFlags(flags);
        boolean fin = (flagBits & 0x01) != 0;
        boolean rst = (flagBits & 0x04) != 0;
        if (sourceIp == null || destIp == null || seq == null || payload == null || payload.length == 0) {
            if (fin || rst) {
                // 连接关闭：没有 Content-Length 的响应只能靠 FIN/RST 界定正文
                closeStream(sourceIp, sourcePort, destIp, destPort, frameNo);
            }
            return;
        }
        PacketMeta meta = new PacketMeta(frameNo, null, 0L, null, streamId, seq, null, null,
                "TCP", flagNames(flagBits), null);
        PacketRecord record = new PacketRecord(sourceIp, destIp, sourcePort, destPort, "TCP",
                payload.length, null, null, null, null, meta, payload, List.of());
        for (ReassembledMessage message : assembler.accept(record)) {
            long completeFrame = message.lastFrameNumber() == null ? frameNo : message.lastFrameNumber();
            long firstFrame = message.firstFrameNumber() == null ? frameNo : message.firstFrameNumber();
            parserFor(message).accept(message.payload(), firstFrame, completeFrame);
        }
        if (fin || rst) {
            closeStream(sourceIp, sourcePort, destIp, destPort, frameNo);
        }
    }

    /**
     * 取出本轮已还原的消息并清空暂存区。
     *
     * @return 消息列表，可能为空
     */
    public List<RestoredHttp> drain() {
        if (ready.isEmpty()) {
            return List.of();
        }
        List<RestoredHttp> out = List.copyOf(ready);
        ready.clear();
        return out;
    }

    /**
     * 解析收尾：把重组器里因空洞滞留的字节、以及所有未闭合的消息强制切出。
     *
     * <p>只在整份 pcap 解析结束（或抓包停止）时调用。实时尾随解析期间不能调用，
     * 否则一条还在传输中的响应会被提前截断。</p>
     *
     * @param frameNo 收尾帧号（没有更精确的归属时使用）
     * @return 新还原出的消息，未包含 {@link #drain()} 之外的暂存量
     */
    public List<RestoredHttp> flush(long frameNo) {
        for (ReassembledMessage message : assembler.flushAll(0L)) {
            long completeFrame = message.lastFrameNumber() == null ? frameNo : message.lastFrameNumber();
            long firstFrame = message.firstFrameNumber() == null ? frameNo : message.firstFrameNumber();
            parserFor(message).accept(message.payload(), firstFrame, completeFrame);
        }
        for (HttpMessageParser parser : parsers.values()) {
            parser.flushPending(frameNo);
        }
        return drain();
    }

    /**
     * 清空全部状态与计数器。
     */
    public void reset() {
        assembler.reset();
        parsers.clear();
        contexts.clear();
        ready.clear();
    }

    /**
     * 取某个方向的切分器，不存在时创建。
     *
     * @param message 重组消息
     * @return 切分器
     */
    private HttpMessageParser parserFor(ReassembledMessage message) {
        TcpStreamKey key = message.key().canonical();
        StreamContext ctx = contexts.computeIfAbsent(key, k -> new StreamContext());
        if (contexts.size() > MAX_DIRECTIONS) {
            Iterator<TcpStreamKey> it = contexts.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        Direction direction = new Direction(key, message.clientToServer());
        HttpMessageParser parser = parsers.get(direction);
        if (parser == null) {
            if (parsers.size() >= MAX_DIRECTIONS) {
                Iterator<Direction> it = parsers.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            parser = new HttpMessageParser(ctx);
            parsers.put(direction, parser);
        }
        return parser;
    }

    /**
     * 连接关闭：把两个方向上仍在缓冲、靠连接关闭界定正文的消息切出来。
     *
     * @param sourceIp   源 IP
     * @param sourcePort 源端口
     * @param destIp     目的 IP
     * @param destPort   目的端口
     * @param frameNo    归属帧号
     */
    private void closeStream(String sourceIp, Integer sourcePort, String destIp, Integer destPort, long frameNo) {
        if (sourceIp == null || destIp == null) {
            return;
        }
        TcpStreamKey key = new TcpStreamKey(sourceIp, sourcePort, destIp, destPort).canonical();
        for (Map.Entry<Direction, HttpMessageParser> entry : parsers.entrySet()) {
            if (entry.getKey().key().equals(key)) {
                entry.getValue().flushPending(frameNo);
            }
        }
    }

    /**
     * 解析 {@code tcp.flags} 的十六进制标志位。
     *
     * @param flags 标志位文本，如 {@code 0x0018}
     * @return 标志位数值；非法或缺失返回 0
     */
    private static int parseFlags(String flags) {
        if (flags == null || flags.isBlank()) {
            return 0;
        }
        String s = flags.trim();
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) {
                return Integer.parseInt(s.substring(2), RADIX_16);
            }
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 把数值标志位转成重组器认识的名字（重组器按 SYN / ACK 判断方向）。
     *
     * @param flags 标志位数值
     * @return 形如 {@code SYN,ACK} 的文本；无标志返回 {@code null}
     */
    private static String flagNames(int flags) {
        if (flags == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if ((flags & 0x02) != 0) {
            sb.append("SYN,");
        }
        if ((flags & 0x10) != 0) {
            sb.append("ACK,");
        }
        if ((flags & 0x01) != 0) {
            sb.append("FIN,");
        }
        if ((flags & 0x04) != 0) {
            sb.append("RST,");
        }
        if ((flags & 0x08) != 0) {
            sb.append("PSH,");
        }
        return sb.length() == 0 ? null : sb.substring(0, sb.length() - 1);
    }

    /**
     * 一条重组后切出的 HTTP 消息。
     *
     * @param frameNo     让它收全的帧号（正文完整处，也是消息归属的主帧）
     * @param startFrame  这条消息第一个字节所在的帧号；与 {@code frameNo} 相同表示没有被拆帧
     * @param request     true 表示请求，false 表示响应
     * @param head        起始行 + 逐行头字段（原始字节按 ISO-8859-1 解码，头字段是 ASCII）
     * @param body        实体原始字节（chunked 已去分块；已按上限截断）
     * @param contentType 头里的 Content-Type，缺失为 {@code null}
     * @param encoding    头里的 Content-Encoding，缺失为 {@code null}
     * @param bodyLength  实体真实字节数（chunked 为去分块后的长度；截断不影响该值）
     * @param truncated   实体是否被截断
     */
    public record RestoredHttp(long frameNo, long startFrame, boolean request, String head, byte[] body,
                               String contentType, String encoding, long bodyLength, boolean truncated) {

        /**
         * 规范构造器：实体数组做防御性拷贝。
         *
         * @param frameNo     帧号
         * @param startFrame  消息首字节帧号
         * @param request     是否请求
         * @param head        头部文本
         * @param body        实体字节
         * @param contentType 内容类型
         * @param encoding    内容编码
         * @param bodyLength  实体字节数
         * @param truncated   是否截断
         */
        public RestoredHttp {
            body = body == null ? null : body.clone();
        }

        /**
         * 取实体副本。
         *
         * @return 实体字节副本；无实体返回 {@code null}
         */
        @Override
        public byte[] body() {
            return body == null ? null : body.clone();
        }
    }

    /**
     * 一条连接的两个方向共用的上下文。
     */
    private static final class StreamContext {

        /**
         * 最近一条请求的方法。响应侧据此判断 HEAD 请求没有实体。
         */
        private String lastRequestMethod;
    }

    /**
     * 方向键：同一条连接的两个方向必须分开切分。
     *
     * @param key            归一化四元组
     * @param clientToServer 是否客户端到服务端
     */
    private record Direction(TcpStreamKey key, boolean clientToServer) {
    }

    /**
     * 单方向的 HTTP 消息切分器。
     *
     * <p>非静态内部类：切出的消息要直接放进外层的暂存列表。</p>
     */
    private final class HttpMessageParser {

        /**
         * 连接上下文（共享 lastRequestMethod）。
         */
        private final StreamContext context;

        /**
         * 缓冲字节。
         */
        private byte[] data = new byte[4096];

        /**
         * 有效字节数。
         */
        private int size;

        /**
         * 这条消息第一个字节所在的帧号；0 表示尚未记录（复用当前帧号）。
         */
        private long startFrame;

        /**
         * 协议已切换到隧道 / WebSocket，后续字节不再按 HTTP 解析。
         */
        private boolean opaque;

        /**
         * 构造切分器。
         *
         * @param context 连接上下文
         */
        HttpMessageParser(StreamContext context) {
            this.context = context;
        }

        /**
         * 投喂重组后的一段连续字节，能切出的消息立即产出。
         *
         * @param chunk         连续字节
         * @param firstFrame    这段字节里第一个帧的帧号（消息首字节归属用）
         * @param completeFrame 让这段字节收全的帧号
         */
        void accept(byte[] chunk, long firstFrame, long completeFrame) {
            if (opaque || chunk == null || chunk.length == 0) {
                return;
            }
            if (size == 0) {
                // 缓冲为空说明上一条消息已经切走，这里就是新消息的首字节
                startFrame = firstFrame;
            }
            append(chunk);
            parse(completeFrame, false);
        }

        /**
         * 连接关闭 / 解析收尾时把未闭合的消息强制切出。
         *
         * @param frameNo 归属帧号
         */
        void flushPending(long frameNo) {
            if (opaque || size == 0) {
                return;
            }
            parse(frameNo, true);
            if (size > 0) {
                // 头都不完整，说明不是 HTTP 或抓包从中间开始：不能留下尾巴
                size = 0;
            }
        }

        /**
         * 在实际缓冲上尽量多地切消息。
         *
         * @param completeFrame 归属帧号
         * @param eof           true 表示不会再有后续字节
         */
        private void parse(long completeFrame, boolean eof) {
            boolean emitted = true;
            while (emitted && size > 0 && !opaque) {
                emitted = false;
                long startAt = startFrame > 0 ? startFrame : completeFrame;
                int start = firstHttpStart(data, size);
                if (start < 0) {
                    if (size > MAX_HEAD_BYTES) {
                        size = 0;
                    }
                    return;
                }
                if (start > 0) {
                    drop(start);
                }
                int headEnd = indexOfCrlfCrlf(data, 0, size);
                if (headEnd < 0) {
                    if (size > MAX_HEAD_BYTES) {
                        size = 0;
                    }
                    return;
                }
                String head = new String(data, 0, headEnd, StandardCharsets.ISO_8859_1);
                String[] lines = head.split("\r\n", -1);
                String startLine = lines.length == 0 ? "" : lines[0];
                boolean request = isRequestLine(startLine);
                int status = request ? 0 : statusOf(startLine);
                if (!request) {
                    if ("HEAD".equalsIgnoreCase(context.lastRequestMethod)) {
                        // HEAD 响应只有头，没有实体
                        emit(startAt, completeFrame, false, head, EMPTY_BODY, null, null, 0L, false);
                        drop(headEnd + 4);
                        emitted = true;
                        continue;
                    }
                } else {
                    context.lastRequestMethod = methodOf(startLine);
                }
                String contentType = headerValue(lines, "content-type");
                String encoding = headerValue(lines, "content-encoding");
                String transferEncoding = headerValue(lines, "transfer-encoding");
                String contentLength = headerValue(lines, "content-length");
                int bodyStart = headEnd + 4;
                if (transferEncoding != null && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
                    Decoded decoded = decodeChunked(data, bodyStart, size);
                    if (decoded == null) {
                        if (eof) {
                            byte[] rest = copy(data, bodyStart, size);
                            emit(startAt, completeFrame, request, head, rest, contentType, encoding, rest.length, true);
                            drop(size);
                            emitted = true;
                        }
                        return;
                    }
                    emit(startAt, completeFrame, request, head, decoded.body(), contentType, encoding,
                            decoded.total(), decoded.truncated());
                    drop(decoded.consumed());
                    emitted = true;
                    continue;
                }
                long declared = parseLong(contentLength, -1L);
                if (declared >= 0) {
                    long available = size - (long) bodyStart;
                    if (available < declared && !eof) {
                        return;
                    }
                    byte[] body = copy(data, bodyStart, size);
                    boolean truncated = false;
                    long keep = Math.min(declared, body.length);
                    if (keep > MAX_BODY_BYTES) {
                        keep = MAX_BODY_BYTES;
                        truncated = true;
                    }
                    byte[] kept = new byte[(int) keep];
                    System.arraycopy(body, 0, kept, 0, (int) keep);
                    if (body.length > keep) {
                        truncated = true;
                    }
                    emit(startAt, completeFrame, request, head, kept, contentType, encoding, declared, truncated);
                    int consume = (int) Math.min(size, bodyStart + declared);
                    drop(consume);
                    emitted = true;
                    continue;
                }
                if (request || isBodylessResponse(status)) {
                    emit(startAt, completeFrame, request, head, EMPTY_BODY, contentType, encoding, 0L, false);
                    drop(bodyStart);
                    emitted = true;
                    continue;
                }
                // 响应既没有 Content-Length 也没有 chunked：只能靠连接关闭界定正文
                if (!eof) {
                    return;
                }
                byte[] body = copy(data, bodyStart, size);
                boolean truncated = body.length > MAX_BODY_BYTES;
                if (truncated) {
                    byte[] kept = new byte[MAX_BODY_BYTES];
                    System.arraycopy(body, 0, kept, 0, MAX_BODY_BYTES);
                    body = kept;
                }
                emit(startAt, completeFrame, request, head, body, contentType, encoding,
                        (long) (size - bodyStart), truncated);
                drop(size);
                emitted = true;
            }
            if (size > MAX_CARRY_BYTES) {
                size = 0;
            }
        }

        /**
         * 产出一条消息并收尾协议切换判断。
         *
         * @param startFrame  消息首字节帧号
         * @param frameNo     帧号
         * @param request     是否请求
         * @param head        头部
         * @param body        实体
         * @param contentType 类型
         * @param encoding    编码
         * @param bodyLength  实体字节数
         * @param truncated   是否截断
         */
        private void emit(long startFrame, long frameNo, boolean request, String head, byte[] body,
                          String contentType, String encoding, long bodyLength,
                          boolean truncated) {
            ready.add(new RestoredHttp(frameNo, startFrame <= 0 ? frameNo : startFrame, request, head, body,
                    contentType, encoding, bodyLength, truncated));
            if (!request) {
                int status = statusOf(firstLine(head));
                if (status == HTTP_STATUS_SWITCHING_PROTOCOLS) {
                    // 协议已升级（WebSocket / h2c），后面不是 HTTP 报文
                    opaque = true;
                    size = 0;
                } else if (status >= HTTP_STATUS_OK && status < HTTP_STATUS_MULTIPLE_CHOICES
                        && "CONNECT".equalsIgnoreCase(context.lastRequestMethod)) {
                    opaque = true;
                    size = 0;
                }
            }
        }

        /**
         * 追加字节。
         *
         * @param chunk 字节
         */
        private void append(byte[] chunk) {
            int needed = size + chunk.length;
            if (needed > data.length) {
                int capacity = Math.max(needed, data.length + (data.length >> 1));
                if (capacity < needed) {
                    capacity = needed;
                }
                byte[] grown = new byte[capacity];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            System.arraycopy(chunk, 0, data, size, chunk.length);
            size += chunk.length;
        }

        /**
         * 丢弃缓冲头部若干字节。
         *
         * @param count 字节数
         */
        private void drop(int count) {
            if (count <= 0) {
                return;
            }
            // 消息被整条切走后，下一条消息的首字节不再是原来的帧
            startFrame = 0;
            if (count >= size) {
                size = 0;
                return;
            }
            System.arraycopy(data, count, data, 0, size - count);
            size -= count;
        }
    }

    /**
     * chunked 解码结果。
     *
     * @param body      去分块后的实体
     * @param consumed  从缓冲起点算起消耗的字节数（消息总长）
     * @param total     实体真实长度
     * @param truncated 是否因上限而截断
     */
    private record Decoded(byte[] body, int consumed, long total, boolean truncated) {
    }

    /**
     * 空实体常量。
     */
    private static final byte[] EMPTY_BODY = new byte[0];

    /**
     * 解析 chunked 实体。
     *
     * @param data 缓冲
     * @param from 实体起点
     * @param to   有效字节终点
     * @return 解码结果；数据不完整返回 {@code null}
     */
    private static Decoded decodeChunked(byte[] data, int from, int to) {
        int pos = from;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        long total = 0L;
        boolean truncated = false;
        while (true) {
            int lineEnd = indexOfCrlf(data, pos, to);
            if (lineEnd < 0) {
                return null;
            }
            String sizeLine = new String(data, pos, lineEnd - pos, StandardCharsets.ISO_8859_1);
            int semicolon = sizeLine.indexOf(';');
            if (semicolon >= 0) {
                sizeLine = sizeLine.substring(0, semicolon);
            }
            long chunkSize;
            try {
                chunkSize = Long.parseLong(sizeLine.trim(), RADIX_16);
            } catch (NumberFormatException e) {
                return null;
            }
            pos = lineEnd + 2;
            if (chunkSize == 0L) {
                // 结尾 chunk 之后可能跟 trailer，直到空行
                while (true) {
                    int end = indexOfCrlf(data, pos, to);
                    if (end < 0) {
                        return null;
                    }
                    if (end == pos) {
                        pos = end + 2;
                        break;
                    }
                    pos = end + 2;
                }
                return new Decoded(out.toByteArray(), pos - from, total, truncated);
            }
            if (to - pos < chunkSize + 2L) {
                return null;
            }
            int size = (int) chunkSize;
            total += size;
            if (!truncated) {
                if (out.size() + size > MAX_BODY_BYTES) {
                    truncated = true;
                } else {
                    out.write(data, pos, size);
                }
            }
            pos += size;
            if (data[pos] != '\r' || data[pos + 1] != '\n') {
                return null;
            }
            pos += 2;
        }
    }

    /**
     * 在缓冲里找下一条 HTTP 消息的起点。
     *
     * <p>正常情况起点就是 0；抓包从中间开始或中间夹了非 HTTP 字节时，
     * 从行首找 {@code HTTP/} 或请求方法，避免整段流量被卡死。</p>
     *
     * @param data 缓冲
     * @param len  有效长度
     * @return 起点偏移；找不到返回 -1
     */
    private static int firstHttpStart(byte[] data, int len) {
        if (len <= 0) {
            return -1;
        }
        if (startsWithHttp(data, 0, len)) {
            return 0;
        }
        for (int i = 1; i < len; i++) {
            if (data[i - 1] == '\n' && startsWithHttp(data, i, len)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 判断指定偏移是否以 HTTP 起始行开头。
     *
     * @param data 缓冲
     * @param from 起点
     * @param len  有效长度
     * @return 是 HTTP 起始行返回 true
     */
    private static boolean startsWithHttp(byte[] data, int from, int len) {
        if (from + 5 > len) {
            return false;
        }
        String prefix = new String(data, from, Math.min(HTTP_START_LINE_MIN, len - from), StandardCharsets.ISO_8859_1);
        if (prefix.startsWith("HTTP/")) {
            return true;
        }
        for (String method : METHODS) {
            if (prefix.startsWith(method)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断起始行是否为请求行。
     *
     * @param startLine 起始行
     * @return 请求返回 true
     */
    private static boolean isRequestLine(String startLine) {
        if (startLine == null || startLine.startsWith("HTTP/")) {
            return false;
        }
        for (String method : METHODS) {
            if (startLine.startsWith(method)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取请求方法。
     *
     * @param startLine 起始行
     * @return 方法名；无法识别返回 {@code "GET"}
     */
    private static String methodOf(String startLine) {
        int space = startLine.indexOf(' ');
        return space > 0 ? startLine.substring(0, space) : "GET";
    }

    /**
     * 取响应状态码。
     *
     * @param startLine 起始行
     * @return 状态码；无法识别返回 0
     */
    private static int statusOf(String startLine) {
        if (startLine == null || !startLine.startsWith("HTTP/")) {
            return 0;
        }
        int first = startLine.indexOf(' ');
        if (first < 0 || startLine.length() < first + 4) {
            return 0;
        }
        try {
            return Integer.parseInt(startLine.substring(first + 1, first + 4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 判断响应是否按规范没有实体。
     *
     * @param status 状态码
     * @return 1xx / 204 / 304 返回 true
     */
    private static boolean isBodylessResponse(int status) {
        return (status >= HTTP_STATUS_CONTINUE && status < HTTP_STATUS_OK) || status == HTTP_STATUS_NO_CONTENT
                || status == HTTP_STATUS_NOT_MODIFIED;
    }

    /**
     * 取头字段值（大小写不敏感，取最后一次出现）。
     *
     * @param lines 头行（不含起始行）
     * @param name  小写字段名
     * @return 字段值；缺失返回 {@code null}
     */
    private static String headerValue(String[] lines, String name) {
        String value = null;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if (lines[i].substring(0, colon).trim().equalsIgnoreCase(name)) {
                value = lines[i].substring(colon + 1).trim();
            }
        }
        return value;
    }

    /**
     * 取起始行。
     *
     * @param head 头部文本
     * @return 第一行
     */
    private static String firstLine(String head) {
        int index = head.indexOf("\r\n");
        return index < 0 ? head : head.substring(0, index);
    }

    /**
     * 解析十进制长整型。
     *
     * @param text         文本
     * @param defaultValue 解析失败的默认值
     * @return 结果
     */
    private static long parseLong(String text, long defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 复制缓冲的一段。
     *
     * @param data 缓冲
     * @param from 起点
     * @param to   终点（不含）
     * @return 新数组
     */
    private static byte[] copy(byte[] data, int from, int to) {
        if (to <= from) {
            return EMPTY_BODY;
        }
        byte[] out = new byte[to - from];
        System.arraycopy(data, from, out, 0, out.length);
        return out;
    }

    /**
     * 查找 {@code \r\n\r\n}。
     *
     * @param data 缓冲
     * @param from 起点
     * @param to   终点（不含）
     * @return 头部结束位置（指向第一个 CR）；找不到返回 -1
     */
    private static int indexOfCrlfCrlf(byte[] data, int from, int to) {
        for (int i = from; i + 3 < to; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查找单个 {@code \r\n}。
     *
     * @param data 缓冲
     * @param from 起点
     * @param to   终点（不含）
     * @return CR 的位置；找不到返回 -1
     */
    private static int indexOfCrlf(byte[] data, int from, int to) {
        for (int i = from; i + 1 < to; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }
}
