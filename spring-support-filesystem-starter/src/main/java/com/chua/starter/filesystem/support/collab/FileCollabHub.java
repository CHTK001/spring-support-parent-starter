package com.chua.starter.filesystem.support.collab;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时协同 hub（sequencer）。
 *
 * <p><b>这是 {@code @p10node/office-engine} 的 {@code CollabHub} 的服务端等价实现。</b>
 * 编辑器里每个客户端通过 {@code Transport} 连到这里，由本类给每一批操作分配全局
 * 递增的 {@code seq}，保存整条操作日志、转发 presence；真正的 OT 变换与冲突消解
 * 都发生在客户端，所以只要所有人都按同一顺序应用这份日志，文档就必然收敛。</p>
 *
 * <p><b>为什么必须与前端库逐字对齐。</b>客户端 {@code CollabSession} 的握手、
 * 去重、断线重连、快照 {@code base} 校验全部是硬编码语义：
 * {@code batch.seq} 必须严格等于 {@code rev + 1}，否则直接
 * {@code "Missed operations"}；{@code welcome.reset} 一出就是
 * {@code "The shared document history was reset"}。所以本类不发明协议，
 * 只把 {@code CollabHub} 的行为逐条复刻：</p>
 * <ol>
 *   <li>{@code room()} 首次使用时从磁盘装载；</li>
 *   <li>{@code snapshot(seq, ctx)} 校验 {@code seq} 必须落在
 *       {@code [start, seq]} 区间，保留最近 3 个快照点，并按 {@code retain=1000}
 *       裁掉过老日志；</li>
 *   <li>{@code connect()} 首次 {@code from = base ?? 0}，重连 {@code from = have}，
 *       空日志或快照缺失一律 {@code reset=true}；</li>
 *   <li>{@code ops} 按客户端生成的 {@code id} 去重，{@code base} 被夹到
 *       {@code min(base, seq-1)}（作者自己都没应用到的操作不允许被当作他人上下文）；</li>
 *   <li>{@code presence} 只转发给别人，不回声给发送者。</li>
 * </ol>
 *
 * <p><b>落盘</b>：每个房间一份 {@code <hub-dir>/<sha256(id)>.json}，内容是最新快照
 * （{@code seq/ctx/sites}）加上快照之后的日志。进程重启后房间按「快照 + 日志」
 * 重建，新客户端加入不会误判 {@code reset}。写盘仍是先写临时文件再原子替换。</p>
 *
 * @author Buffy
 * @since 2026/11/09
 */
@Slf4j
public class FileCollabHub {

    /**
     * 参与者颜色池（与 office-engine {@code PEER_COLORS} 完全一致）。
     *
     * <p>服务端负责分配：客户端只传名字，颜色如果由各客户端自己随机，
     * 同一场会议里两个人都可能抽到同一种蓝，光标就分不清了。</p>
     */
    public static final List<String> PEER_COLORS = List.of(
            "#1a73e8", "#e8710a", "#188038", "#d93025", "#9334e6",
            "#c5221f", "#007b83", "#b06000", "#3f51b5", "#e52592");

    /**
     * 保留的快照代数（更老的快照点不再接受新加入者）
     */
    private static final int KEEP_SNAPSHOTS = 3;

    /**
     * 快照之下保留的日志条数（供旧状态重连者续接）
     */
    private static final int RETAIN_BATCHES = 1000;

    /**
     * 落盘格式版本
     */
    private static final int FORMAT_VERSION = 1;

    /**
     * 房间表：id → 房间（只有连接进来才装载）
     */
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    /**
     * 装载串行化：同一个房间并发首次访问只读一次盘
     */
    private final Map<String, Object> loadLocks = new ConcurrentHashMap<>();

    /**
     * hub 落盘目录（房间文件放这里）
     */
    private final Path dir;

    /**
     * 是否启用落盘（关掉等于纯内存，重启即丢日志）
     */
    private final boolean persistEnabled;

    /**
     * 是否空闲即卸载房间（默认 true；房间日志已落盘，重新连上会再装载）
     */
    private final boolean unloadOnIdle;

    /**
     * 落盘调度器（单线程守护线程：批量写盘与请求线程解耦）
     *
     * <p><b>为什么不在 ops 请求里同步写盘。</b>一次编辑会话每秒可能产生几十批操作，
     * 每批都整房重写等于用磁盘 IO 给协同定序设上限。这里做 300ms 合并：一个窗口内
     * 的 N 批只写一次；快照、空闲、关停仍走同步写，保证不丢关键状态。</p>
     */
    private final java.util.concurrent.ScheduledExecutorService flusher =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "file-collab-hub-flush");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * 已排队的合并写盘任务：room id → 句柄（避免同一房间重复排队）
     */
    private final Map<String, java.util.concurrent.ScheduledFuture<?>> pendingFlush =
            new ConcurrentHashMap<>();

    /**
     * JSON 序列化器：操作体原样透传，所以关闭所有改写类特性
     */
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    /**
     * 构造（默认落盘目录 {@code data/file-collab/hub}）。
     *
     * @param dir 落盘目录
     */
    public FileCollabHub(Path dir) {
        this(dir, true, true);
    }

    /**
     * 构造。
     *
     * @param dir             落盘目录（null 或空 = 纯内存）
     * @param persistEnabled  是否落盘
     * @param unloadOnIdle    最后一个连接离开后是否卸载房间
     */
    public FileCollabHub(Path dir, boolean persistEnabled, boolean unloadOnIdle) {
        this.dir = dir;
        this.persistEnabled = persistEnabled && dir != null;
        this.unloadOnIdle = unloadOnIdle;
    }

    /**
     * 对外暴露的序列化器（控制器把出站消息转成 SSE data）。
     *
     * @return ObjectMapper
     */
    public ObjectMapper mapper() {
        return mapper;
    }

    /**
     * 房间当前状态（{@code GET /{id}/hub/state}）。
     *
     * @param id 房间号（= 协同文档号）
     * @return {@code {seq, snapshotSeq, start, logSize, peers}}
     */
    public Map<String, Object> state(String id) {
        Room room = room(id);
        synchronized (room.lock) {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("seq", room.seq);
            // snapshotSeq 是「文档当前内容对应的 seq」：新客户端必须用它作为 base，
            // 否则它会从 0 重放整条日志，把已经写进文件的操作再应用一遍。
            state.put("snapshotSeq", room.contentSeq);
            state.put("start", room.start);
            state.put("logSize", room.log.size());
            state.put("peers", room.peers.size());
            return state;
        }
    }

    /**
     * 注册一份快照（客户端保存文档后调用）。
     *
     * <p>校验口径与 office-engine 一致：快照 seq 必须落在
     * {@code [start, seq]} 内，否则说明房间已经重置过，这份快照不再适用。</p>
     *
     * @param id  房间号
     * @param seq 快照对应的序列号（客户端 {@code session.rev}）
     * @param ctx 快照点的规范化批次上下文（客户端 {@code session.context()}）
     * @return 是否接受
     */
    public boolean registerSnapshot(String id, long seq, List<Entry> ctx) {
        Room room = room(id);
        boolean accepted;
        synchronized (room.lock) {
            if (seq <= 0) {
                return false;
            }
            if (seq > room.seq) {
                // 文档内容比 hub 日志更靠前：hub 侧日志丢了（进程重启且目录被清、
                // 或者服务端换了机器）。已经写进文件的那些操作不需要再重放，
                // 因此把内容基线前移到 seq，并清空无法解释的旧日志。
                room.seq = seq;
                room.log.clear();
                room.ids.clear();
                room.start = seq;
                room.snaps.clear();
                log.warn("[FileCollabHub] 内容基线前移到 seq={}（hub 日志落后，旧日志已清空）: room={}",
                        seq, id);
            } else if (seq < room.start) {
                log.debug("[FileCollabHub] 拒绝过期快照: room={} seq={} range=[{},{}]",
                        id, seq, room.start, room.seq);
                return false;
            }
            List<Entry> safeCtx = ctx == null ? Collections.emptyList() : ctx;
            room.snaps.put(seq, safeCtx);
            // 文档内容 = 这份快照；新加入者从这里开始
            room.contentSeq = seq;
            // 只留最近 keep 个快照点
            List<Long> keys = new ArrayList<>(room.snaps.keySet());
            Collections.sort(keys);
            int excess = keys.size() - KEEP_SNAPSHOTS;
            for (int i = 0; i < excess; i++) {
                room.snaps.remove(keys.get(i));
            }
            List<Long> kept = new ArrayList<>(room.snaps.keySet());
            Collections.sort(kept);
            long oldest = kept.isEmpty() ? seq : kept.get(0);
            long newest = kept.isEmpty() ? seq : kept.get(kept.size() - 1);
            long cut = Math.min(oldest, newest - RETAIN_BATCHES);
            if (cut > room.start) {
                int drop = 0;
                while (drop < room.log.size() && room.log.get(drop).seq() <= cut) {
                    room.ids.remove(room.log.get(drop).id());
                    drop++;
                }
                room.log.subList(0, drop).clear();
                room.start = cut;
            }
            room.sites = Math.max(room.sites, room.nextSite - 1);
            accepted = true;
        }
        // 保存是低频动作，直接同步落盘：快照丢了会让新客户端少一段上下文
        persist(room, true);
        return accepted;
    }

    /**
     * 房间的下一可用站点号（调试 / 状态接口用）。
     *
     * @param id 房间号
     * @return 下一 site
     */
    public int nextSite(String id) {
        Room room = room(id);
        synchronized (room.lock) {
            return room.nextSite;
        }
    }

    /**
     * 房间是否存在（内存中）。
     *
     * @param id 房间号
     * @return 是否已装载
     */
    public boolean loaded(String id) {
        return rooms.containsKey(id);
    }

    /**
     * 强制忘记房间（例如文档被彻底删除或重新上传了基线文件）。
     *
     * @param id 房间号
     */
    public void reset(String id) {
        Room room = rooms.remove(id);
        if (room != null) {
            synchronized (room.lock) {
                for (Peer peer : room.peers.values()) {
                    peer.send().accept(error("Room was reset"));
                }
                room.peers.clear();
            }
            deleteRoomFile(id);
        }
    }

    /**
     * 卸载空闲房间（仍有连接时返回 false）。
     *
     * @param id 房间号
     * @return 是否卸载
     */
    public boolean unload(String id) {
        Room room = rooms.get(id);
        if (room == null) {
            return false;
        }
        synchronized (room.lock) {
            if (!room.peers.isEmpty()) {
                return false;
            }
        }
        rooms.remove(id);
        return true;
    }

    /**
     * 建立一条客户端连接。
     *
     * <p>{@code send} 是出站通道（SSE 写事件）；返回的句柄负责接收客户端消息与关闭。
     * 语义与 {@code CollabHub.connect} 一致：先 {@code hello} 入房，再收发
     * {@code ops}/{@code presence}，关闭时广播 {@code leave}。</p>
     *
     * @param roomId 房间号
     * @param send   出站消息消费者（已序列化前的对象）
     * @return 连接句柄
     */
    public HubConnection connect(String roomId, Consumer<Object> send) {
        return new Connection(roomId, send);
    }

    /**
     * 连接句柄。
     */
    public interface HubConnection {

        /**
         * 接收一条客户端消息（{@code ClientMsg} 的 JSON 树）。
         *
         * @param msg 消息
         */
        void receive(JsonNode msg);

        /**
         * 关闭连接（幂等）。
         */
        void close();

        /**
         * 当前分配到的 site（未入房时为 0）。
         *
         * @return site
         */
        int site();
    }

    /**
     * 单条连接：承载一个客户端的握手与消息分发。
     */
    private final class Connection implements HubConnection {

        /**
         * 房间号
         */
        private final String roomId;

        /**
         * 出站通道
         */
        private final Consumer<Object> send;

        /**
         * 已加入的房间；null = 还没 hello
         */
        private Room room;

        /**
         * 本连接分配到的站点号
         */
        private int site;

        /**
         * 是否已关闭
         */
        private volatile boolean closed;

        /**
         * 构造。
         *
         * @param roomId 房间号
         * @param send   出站通道
         */
        Connection(String roomId, Consumer<Object> send) {
            this.roomId = roomId;
            this.send = send;
        }

        @Override
        public int site() {
            return site;
        }

        /**
         * 收到消息。
         *
         * @param msg ClientMsg
         */
        @Override
        public void receive(JsonNode msg) {
            if (closed || msg == null) {
                return;
            }
            String type = text(msg, "t");
            if (room == null) {
                if (!"hello".equals(type)) {
                    // 与库一致：没入房就发 ops / presence 一律报错
                    send.accept(error("Not joined"));
                    return;
                }
                join(msg);
                return;
            }
            if ("hello".equals(type)) {
                // 库语义：已入房后的第二次 hello 直接忽略
                return;
            }
            if ("ops".equals(type)) {
                receiveOps(msg);
            } else if ("presence".equals(type)) {
                receivePresence(msg);
            }
        }

        /**
         * 入房：分配站点与颜色、回 welcome、广播 join。
         *
         * @param msg hello 消息
         */
        private void join(JsonNode msg) {
            Room loaded = FileCollabHub.this.room(roomId);
            JsonNode user = msg.get("user");
            String userId = user == null ? null : text(user, "id");
            String name = user == null ? null : text(user, "name");
            String wantedColor = user == null ? null : text(user, "color");
            JsonNode haveNode = msg.get("have");
            JsonNode baseNode = msg.get("base");
            JsonNode siteNode = msg.get("site");
            synchronized (loaded.lock) {
                int requestedSite = siteNode == null || siteNode.isNull() ? 0 : siteNode.asInt(0);
                site = requestedSite > 0 && requestedSite < loaded.nextSite && !loaded.peers.containsKey(requestedSite)
                        ? requestedSite : loaded.nextSite++;
                Set<String> used = new HashSet<>();
                for (Peer peer : loaded.peers.values()) {
                    if (peer.info().color() != null) {
                        used.add(peer.info().color());
                    }
                }
                String color = wantedColor != null && !wantedColor.isBlank()
                        ? wantedColor : pickColor(used, site);
                PeerInfo info = new PeerInfo(userId, name, color, site, null);

                long from;
                boolean reset;
                List<Entry> ctx = null;
                if (haveNode == null || haveNode.isNull()) {
                    // 首次加入：base 是客户端打开的文件对应的 seq
                    from = baseNode == null || baseNode.isNull() ? 0L : baseNode.asLong(0L);
                    ctx = from == 0 ? null : loaded.snaps.get(from);
                    reset = from > loaded.seq || from < loaded.start || (from > 0 && ctx == null);
                } else {
                    // 重连：have 是客户端最后应用的 seq
                    from = haveNode.asLong(0L);
                    reset = from > loaded.seq || from < loaded.start;
                }
                List<Batch> log = new ArrayList<>();
                if (!reset) {
                    for (Batch batch : loaded.log) {
                        if (batch.seq() > from) {
                            log.add(batch);
                        }
                    }
                }
                List<PeerInfo> peers = new ArrayList<>();
                for (Peer peer : loaded.peers.values()) {
                    peers.add(peer.info());
                }
                Map<String, Object> welcome = new LinkedHashMap<>();
                welcome.put("t", "welcome");
                welcome.put("site", site);
                welcome.put("seq", loaded.seq);
                welcome.put("from", from);
                welcome.put("reset", reset);
                welcome.put("log", log);
                welcome.put("peers", peers);
                if (ctx != null && !reset) {
                    welcome.put("ctx", ctx);
                }
                // 先发 welcome（此时 peers 不含自己，与库一致），再登记并广播 join
                send.accept(welcome);
                loaded.peers.put(site, new Peer(info, send));
                room = loaded;
                Map<String, Object> joinMsg = new LinkedHashMap<>();
                joinMsg.put("t", "join");
                joinMsg.put("peer", info);
                broadcast(loaded, joinMsg, site);
            }
        }

        /**
         * 接收操作批：去重、定序、写日志、广播。
         *
         * @param msg ops 消息
         */
        private void receiveOps(JsonNode msg) {
            String id = text(msg, "id");
            if (id == null || id.isBlank()) {
                return;
            }
            Room loaded = room;
            synchronized (loaded.lock) {
                if (loaded.ids.contains(id)) {
                    // 重发（断线重连后客户端会重发在飞批次）：已经有 seq 了，不能再排一次
                    return;
                }
                Peer peer = loaded.peers.get(site);
                long seq = ++loaded.seq;
                long base = msg.hasNonNull("base") ? msg.get("base").asLong(0L) : 0L;
                JsonNode opsNode = msg.get("ops");
                List<JsonNode> ops = new ArrayList<>();
                if (opsNode != null && opsNode.isArray()) {
                    for (JsonNode op : opsNode) {
                        ops.add(op);
                    }
                }
                Batch batch = new Batch(seq, site, Math.min(base, seq - 1), id, ops,
                        System.currentTimeMillis(),
                        peer == null ? null : (peer.info().id() != null ? peer.info().id() : peer.info().name()));
                loaded.log.add(batch);
                loaded.ids.add(id);
                loaded.dirty = true;
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("t", "batch");
                out.put("b", batch);
                // 广播给所有人（含发送者）：发送者的 ack 也走这条消息
                broadcast(loaded, out, 0);
            }
            schedulePersist(room);
        }

        /**
         * 接收 presence：只转给别人，不回声。
         *
         * @param msg presence 消息
         */
        private void receivePresence(JsonNode msg) {
            Room loaded = room;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("t", "presence");
            out.put("site", site);
            out.put("data", msg.get("data"));
            synchronized (loaded.lock) {
                Peer peer = loaded.peers.get(site);
                if (peer == null) {
                    return;
                }
                // 记录在 peer 上，后来者通过 welcome.peers 拿到最新 presence
                loaded.peers.put(site, new Peer(
                        new PeerInfo(peer.info().id(), peer.info().name(), peer.info().color(),
                                peer.info().site(), msg.get("data")), peer.send()));
                broadcast(loaded, out, site);
            }
        }

        /**
         * 关闭：摘除站点、广播 leave、空闲时落盘并卸载。
         */
        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            Room loaded = room;
            if (loaded == null) {
                return;
            }
            boolean idle;
            synchronized (loaded.lock) {
                if (loaded.peers.remove(site) != null) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("t", "leave");
                    out.put("site", site);
                    broadcast(loaded, out, 0);
                }
                idle = loaded.peers.isEmpty();
            }
            if (idle) {
                // 最后一个连接离开：把日志补上再卸载，保证重进时还能接上
                persist(loaded, true);
                if (unloadOnIdle) {
                    unloadRoom(loaded);
                }
            }
        }

        /**
         * 广播给房间内除 {@code exceptSite} 外的所有连接。
         *
         * @param loaded     房间
         * @param msg        消息
         * @param exceptSite 例外站点（0 = 不排除）
         */
        private void broadcast(Room loaded, Object msg, int exceptSite) {
            List<Peer> targets;
            synchronized (loaded.lock) {
                targets = new ArrayList<>(loaded.peers.values());
            }
            for (Peer peer : targets) {
                if (exceptSite != 0 && peer.info().site() == exceptSite) {
                    continue;
                }
                try {
                    peer.send().accept(msg);
                } catch (Exception e) {
                    log.debug("[FileCollabHub] 推送失败（连接可能已断）: room={} site={} ({})",
                            roomId, peer.info().site(), e.getMessage());
                }
            }
        }
    }

    /**
     * 房间（内存态）。
     */
    public static final class Room {

        /**
         * 房间号
         */
        private final String id;

        /**
         * 锁：房间内所有状态都以它为准，保证定序与广播顺序一致
         */
        private final Object lock = new Object();

        /**
         * 落盘互斥（同一房间的文件不能被两个线程同时写临时文件）
         */
        private final Object flushLock = new Object();

        /**
         * 已分配的最大序列号
         */
        private long seq;

        /**
         * 快照之后的日志（按 seq 升序）
         */
        private final List<Batch> log = new ArrayList<>();

        /**
         * {@code log} 之前的序列号都被裁掉了
         */
        private long start;

        /**
         * 日志里出现过的批 id（去重）
         */
        private final Set<String> ids = new HashSet<>();

        /**
         * 在线连接：site → 连接
         */
        private final Map<Integer, Peer> peers = new HashMap<>();

        /**
         * 下一站点号
         */
        private int nextSite = 1;

        /**
         * 可加入的快照点：seq → 上下文
         */
        private final Map<Long, List<Entry>> snaps = new HashMap<>();

        /**
         * 文档内容对应的 seq（新客户端加入时的 base）
         */
        private long contentSeq;

        /**
         * 快照记录里的最大站点号（重启后保证不复用）
         */
        private int sites = 1;

        /**
         * 是否有未落盘的改动
         */
        private boolean dirty;

        /**
         * 构造。
         *
         * @param id 房间号
         */
        Room(String id) {
            this.id = id;
        }

        /**
         * 房间号。
         *
         * @return id
         */
        public String id() {
            return id;
        }
    }

    /**
     * 一条已知在线连接。
     *
     * @param info 对外的参与者信息
     * @param send 出站通道
     */
    private record Peer(PeerInfo info, Consumer<Object> send) {
    }

    /**
     * 参与者信息（协议 {@code PeerInfo}）。
     *
     * @param id       用户标识
     * @param name     显示名
     * @param color    光标颜色
     * @param site     本次连接站点号
     * @param presence 在线状态载荷
     */
    public record PeerInfo(String id, String name, String color, int site, JsonNode presence) {
    }

    /**
     * 一条已定序的操作批（协议 {@code Batch}）。
     *
     * @param seq  全局序列号
     * @param site 作者站点
     * @param base 作者创建这批操作时最后应用的 seq
     * @param id   客户端生成的批 id（重发去重）
     * @param ops  操作体（原样透传，服务端不解码）
     * @param time 定序时间
     * @param user 作者用户标识
     */
    public record Batch(long seq, int site, long base, String id, List<JsonNode> ops, Long time, String user) {
    }

    /**
     * 规范化后的日志条目（快照上下文）。
     *
     * @param seq  序列号
     * @param site 作者站点
     * @param ops  操作体
     */
    public record Entry(long seq, int site, List<JsonNode> ops) {
    }

    /**
     * 落盘文件结构。
     *
     * @param format   格式版本
     * @param savedAt  落盘时间
     * @param snapshot 最新快照（可能为 null）
     * @param log      快照之后的日志
     * @param contentSeq 文档内容对应的 seq
     * @param sites    已用过的最大站点号
     */
    public record RoomFile(int format, long savedAt, StoredSnapshot snapshot,
                           List<Batch> log, long contentSeq, int sites) {
    }

    /**
     * 落盘快照。
     *
     * @param seq 快照点
     * @param ctx 上下文批次
     */
    public record StoredSnapshot(long seq, List<Entry> ctx) {
    }

    /**
     * 取房间；不存在则从磁盘装载（并发首次访问只装载一次）。
     *
     * @param id 房间号
     * @return 房间
     */
    private Room room(String id) {
        Room exist = rooms.get(id);
        if (exist != null) {
            return exist;
        }
        Object lock = loadLocks.computeIfAbsent(id, key -> new Object());
        try {
            synchronized (lock) {
                Room again = rooms.get(id);
                if (again != null) {
                    return again;
                }
                Room loaded = load(id);
                rooms.put(id, loaded);
                return loaded;
            }
        } finally {
            loadLocks.remove(id, lock);
        }
    }

    /**
     * 从磁盘装载房间（不存在的房间按空日志处理）。
     *
     * @param id 房间号
     * @return 房间
     */
    private Room load(String id) {
        Room room = new Room(id);
        Path file = roomFile(id);
        if (!persistEnabled || file == null || !Files.exists(file)) {
            return room;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return room;
            }
            RoomFile data = mapper.readValue(json, RoomFile.class);
            List<Batch> batches = data.log() == null ? new ArrayList<>() : new ArrayList<>(data.log());
            batches.sort((a, b) -> Long.compare(a.seq(), b.seq()));
            StoredSnapshot snapshot = data.snapshot();
            long snapshotSeq = snapshot == null ? 0L : snapshot.seq();
            List<Entry> snapshotCtx = snapshot == null || snapshot.ctx() == null
                    ? new ArrayList<>() : snapshot.ctx();
            long contentSeq = data.contentSeq() > 0 ? data.contentSeq() : snapshotSeq;
            // 只保留快照之后的日志：快照之前的内容已经在文档文件里了
            List<Batch> kept = new ArrayList<>();
            for (Batch batch : batches) {
                if (batch.seq() > snapshotSeq) {
                    kept.add(batch);
                }
            }
            // 日志必须连续，遇到断点就截断（防止落盘时半截写入造成空洞）
            kept.sort((a, b) -> Long.compare(a.seq(), b.seq()));
            List<Batch> contiguous = new ArrayList<>();
            long expect = -1;
            for (Batch batch : kept) {
                if (expect >= 0 && batch.seq() != expect) {
                    break;
                }
                contiguous.add(batch);
                expect = batch.seq() + 1;
            }
            if (contiguous.isEmpty() && !kept.isEmpty()) {
                contiguous.add(kept.get(0));
                expect = kept.get(0).seq() + 1;
                for (int i = 1; i < kept.size(); i++) {
                    if (kept.get(i).seq() != expect) {
                        break;
                    }
                    contiguous.add(kept.get(i));
                    expect = kept.get(i).seq() + 1;
                }
            }
            room.log.addAll(contiguous);
            room.start = contiguous.isEmpty() ? snapshotSeq : contiguous.get(0).seq() - 1;
            long lastSeq = contiguous.isEmpty() ? snapshotSeq : contiguous.get(contiguous.size() - 1).seq();
            room.seq = Math.max(lastSeq, snapshotSeq);
            room.contentSeq = contentSeq;
            room.sites = Math.max(1, data.sites());
            for (Batch batch : contiguous) {
                room.ids.add(batch.id());
            }
            if (snapshot != null) {
                room.snaps.put(snapshotSeq, snapshotCtx);
            }
            for (Batch batch : contiguous) {
                room.sites = Math.max(room.sites, batch.site());
            }
            for (Entry entry : snapshotCtx) {
                room.sites = Math.max(room.sites, entry.site());
            }
            room.nextSite = Math.max(1, room.sites + 1);
            room.dirty = false;
            log.info("[FileCollabHub] 已装载房间: room={} seq={} start={} log={} contentSeq={}",
                    id, room.seq, room.start, room.log.size(), room.contentSeq);
        } catch (Exception e) {
            log.warn("[FileCollabHub] 房间落盘文件解析失败，按空房间启动: room={} ({})", id, e.getMessage());
        }
        return room;
    }

    /**
     * 落盘房间。
     *
     * @param room  房间
     * @param force true = 无论脏否都写（快照 / 空闲 / 关停）；false = 只在脏时写
     */
    private void persist(Room room, boolean force) {
        if (!persistEnabled) {
            return;
        }
        Path file = roomFile(room.id());
        if (file == null) {
            return;
        }
        RoomFile data;
        synchronized (room.lock) {
            if (!force && !room.dirty) {
                return;
            }
            List<Batch> log = new ArrayList<>();
            for (Batch batch : room.log) {
                log.add(batch);
            }
            StoredSnapshot snapshot = room.snaps.isEmpty() ? null
                    : new StoredSnapshot(room.contentSeq, room.snaps.get(room.contentSeq));
            if (snapshot != null && snapshot.ctx() == null) {
                snapshot = new StoredSnapshot(room.contentSeq, Collections.emptyList());
            }
            data = new RoomFile(FORMAT_VERSION, System.currentTimeMillis(), snapshot, log,
                    room.contentSeq, Math.max(room.sites, room.nextSite - 1));
            room.dirty = false;
        }
        Path tmp = null;
        synchronized (room.flushLock) {
            try {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(tmp, mapper.writeValueAsString(data), StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicUnsupported) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
                tmp = null;
            } catch (Exception e) {
                synchronized (room.lock) {
                    room.dirty = true;
                }
                log.error("[FileCollabHub] 房间落盘失败: room={} ({})", room.id(), e.getMessage());
            } finally {
                if (tmp != null) {
                    try {
                        Files.deleteIfExists(tmp);
                    } catch (IOException ignored) {
                        // 临时文件残留无害
                    }
                }
            }
        }
    }

    /**
     * 合并写盘：一个 300ms 窗口内的多批操作只写一次。
     *
     * @param room 房间
     */
    private void schedulePersist(Room room) {
        if (!persistEnabled) {
            return;
        }
        pendingFlush.computeIfAbsent(room.id(), id -> {
            try {
                return flusher.schedule(() -> {
                    pendingFlush.remove(id);
                    persist(room, false);
                }, 300L, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // 已关停：直接同步写一次，别把改动留在内存里
                persist(room, false);
                return null;
            }
        });
    }

    /**
     * 关停：取消排队的合并写盘，把所有已装载房间同步落盘。
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        for (java.util.concurrent.ScheduledFuture<?> future : pendingFlush.values()) {
            try {
                future.cancel(false);
            } catch (Exception ignored) {
                // 已执行完的句柄取消失败可忽略
            }
        }
        pendingFlush.clear();
        for (Room room : new ArrayList<>(rooms.values())) {
            persist(room, true);
        }
        flusher.shutdown();
    }

    /**
     * 卸载房间（调用方已确认无连接）。
     *
     * @param room 房间
     */
    private void unloadRoom(Room room) {
        boolean idle;
        synchronized (room.lock) {
            idle = room.peers.isEmpty();
        }
        if (idle) {
            rooms.remove(room.id(), room);
            if (log.isDebugEnabled()) {
                log.debug("[FileCollabHub] 空闲房间已卸载: room={}", room.id());
            }
        }
    }

    /**
     * 房间落盘文件路径。
     *
     * <p>房间号来自 URL，直接拼成文件名会留下路径穿越（{@code ../../etc}）风险，
     * 所以取 SHA-256 前 32 位十六进制。可读性由日志里的 room 字段保留。</p>
     *
     * @param id 房间号
     * @return 路径；未启用落盘时返回 null
     */
    private Path roomFile(String id) {
        if (!persistEnabled) {
            return null;
        }
        return dir.resolve(sha256(id).substring(0, 32) + ".json");
    }

    /**
     * 删除房间落盘文件。
     *
     * @param id 房间号
     */
    private void deleteRoomFile(String id) {
        Path file = roomFile(id);
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("[FileCollabHub] 删除房间文件失败: room={} ({})", id, e.getMessage());
        }
    }

    /**
     * 分配颜色：先找没被占用的，全占用再按站点轮转。
     *
     * @param used 已用颜色
     * @param site 站点号
     * @return 颜色
     */
    private static String pickColor(Set<String> used, int site) {
        for (String color : PEER_COLORS) {
            if (!used.contains(color)) {
                return color;
            }
        }
        return PEER_COLORS.get(Math.floorMod(site, PEER_COLORS.size()));
    }

    /**
     * 构造 error 消息。
     *
     * @param message 文案
     * @return 消息
     */
    private static Map<String, Object> error(String message) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("t", "error");
        msg.put("message", message);
        return msg;
    }

    /**
     * 取文本字段。
     *
     * @param node 节点
     * @param name 字段名
     * @return 值；不存在或 null 返回 null
     */
    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * SHA-256（仅用于文件名，不涉密）。
     *
     * @param value 输入
     * @return 十六进制串
     */
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            // JVM 必然提供 SHA-256；退化成 hashCode 只为不中断请求
            return Integer.toHexString(value.hashCode());
        }
    }

    /**
     * 把任意出站对象转成 JSON（控制器写 SSE data 用）。
     *
     * @param value 对象
     * @return JSON 字符串
     */
    public String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[FileCollabHub] 出站消息序列化失败: {}", e.getMessage());
            return "{\"t\":\"error\",\"message\":\"serialize failed\"}";
        }
    }

    /**
     * 把客户端发来的 JSON 解析成树（控制器收 POST 用）。
     *
     * @param json JSON 字符串
     * @return 节点
     */
    public JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 把客户端发来的 {@code ctx}（Entry 数组）解析成列表。
     *
     * @param json 数组 JSON
     * @return 列表（解析失败返回空）
     */
    public List<Entry> parseContext(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = mapper.readTree(json);
            if (node == null || !node.isArray()) {
                return Collections.emptyList();
            }
            List<Entry> entries = new ArrayList<>();
            for (JsonNode item : node) {
                List<JsonNode> ops = new ArrayList<>();
                JsonNode opsNode = item.get("ops");
                if (opsNode != null && opsNode.isArray()) {
                    for (JsonNode op : opsNode) {
                        ops.add(op);
                    }
                }
                entries.add(new Entry(item.path("seq").asLong(0L), item.path("site").asInt(0), ops));
            }
            return entries;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 把出站对象转成 {@code ObjectNode}（控制器按事件名分发时用）。
     *
     * @param value 对象
     * @return 节点
     */
    public ObjectNode toObjectNode(Object value) {
        return mapper.valueToTree(value);
    }

    /**
     * 空数组节点（welcome 里 log 为空时复用）。
     *
     * @return 数组
     */
    public ArrayNode emptyArray() {
        return mapper.createArrayNode();
    }
}
