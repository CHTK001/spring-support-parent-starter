package com.chua.starter.sse.support.server;

import com.chua.common.support.network.http.HttpMethod;
import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.nio.NioHttpServer;
import com.chua.common.support.network.server.nio.NioServerResponse;
import com.chua.socket.support.session.SocketSession;
import com.chua.socket.support.session.SocketSessionTemplate;
import com.chua.socket.support.session.SocketUser;
import com.chua.starter.common.support.text.json.Json;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 独立端口 SSE 会话模板（Standalone 模式）。
 *
 * <p>与嵌入式 {@code SseSocketSessionTemplate} 的区别只在“连接从哪来”：</p>
 * <ul>
 *   <li>嵌入：Spring MVC 的 {@code POST /sse/connect}，连接由主应用 Tomcat 承载；</li>
 *   <li>独立：本类用 {@code NioHttpServer} 在 {@code plugin.socket.room[0].port}
 *       单独监听，不占主应用 Tomcat 的连接与读写事件。</li>
 * </ul>
 *
 * <p>两种模式对业务是透明的：都实现 {@link SocketSessionTemplate}，都按
 * {@code broadcast("message", json)} 扇出；前端也只需把 {@code socket.urls} 指向
 * 独立端口，路径仍是 {@code POST /sse/connect}。</p>
 *
 * <p>连接线程用虚拟线程（NioHttpServer 默认），每条连接 park 在队列上等心跳/关闭，
 * 不占平台线程、不轮询 CPU。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
public class SseStandaloneSessionTemplate implements SocketSessionTemplate {

    /** SSE 事件流入口路径 */
    public static final String CONNECT_PATH = "/sse/connect";

    /** 心跳间隔（秒）：SSE 注释帧，避免中间设备按空闲断开 */
    private static final long HEARTBEAT_SECONDS = 15L;

    /** HTTP 500：SSE 必须走 NIO 响应 */
    private static final int HTTP_INTERNAL_ERROR = 500;

    /** 独立 HTTP 服务 */
    private final NioHttpServer server;

    /** clientId → 会话 */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /** userId → clientId（定向推送） */
    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();

    /**
     * 构造独立 SSE 服务。
     *
     * @param setting 服务器配置（host / port）
     */
    public SseStandaloneSessionTemplate(ServerSetting setting) {
        this.server = new NioHttpServer(setting);
    }

    @Override
    public void start() {
        // 只开 POST：token 走请求头，不把令牌写进 URL；GET 仅作为旧客户端兼容时才在网关侧放行。
        server.registerMapping(CONNECT_PATH, HttpMethod.POST, this::onConnect);
        server.start();
        log.info("[SSE-Standalone] 已启动: http://{}:{}{}",
                server.getSetting().getHost(), server.getSetting().getPort(), CONNECT_PATH);
    }

    /**
     * 建立一条 SSE 连接。
     *
     * <p>本方法会一直阻塞到连接关闭：NioHttpServer 用虚拟线程跑 handler，
     * park 在队列上不消耗平台线程，也避免 handler 返回后连接被回收。</p>
     *
     * @param request  请求
     * @param response 响应
     */
    private void onConnect(com.chua.common.support.network.server.request.ServerRequest request,
                           com.chua.common.support.network.server.response.ServerResponse response)
            throws InterruptedException {
        if (!(response instanceof NioServerResponse sseResponse)) {
            response.setStatus(HTTP_INTERNAL_ERROR).setBody("SSE requires NIO server");
            return;
        }
        String clientId = request.getParam("clientId");
        if (clientId == null || clientId.isBlank()) {
            clientId = UUID.randomUUID().toString().replace("-", "");
        }

        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.sse();

        Session session = new Session(clientId, sseResponse);
        Session previous = sessions.put(clientId, session);
        if (previous != null) {
            previous.close();
        }
        session.send("connected", "{\"clientId\":\"" + clientId + "\",\"message\":\"连接成功\"}");

        try {
            while (true) {
                // park 等待指令；超时即心跳。虚拟线程上 TimeUnit 阻塞不占平台线程。
                Runnable task = session.poll(HEARTBEAT_SECONDS, TimeUnit.SECONDS);
                if (task == null) {
                    if (!session.ping()) {
                        break;
                    }
                    continue;
                }
                task.run();
                if (!session.isConnected()) {
                    break;
                }
            }
        } finally {
            sessions.remove(clientId, session);
            session.close();
        }
    }

    @Override
    public void stop() {
        sessions.values().forEach(Session::close);
        sessions.clear();
        userSessionMap.clear();
        server.stop();
        log.info("[SSE-Standalone] 已停止");
    }

    @Override
    public String protocol() {
        return "sse";
    }

    @Override
    public boolean isReady() {
        return server.isRunning();
    }

    @Override
    public boolean supportsUserTargeting() {
        return true;
    }

    @Override
    public SocketSession save(String clientId, SocketSession session) {
        if (session instanceof Session target) {
            sessions.put(clientId, target);
        }
        return session;
    }

    @Override
    public void remove(String clientId, SocketSession session) {
        sessions.remove(clientId, session);
    }

    @Override
    public SocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public void send(String sessionId, String event, String msg) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            session.send(event, msg);
        }
    }

    @Override
    public void broadcast(String event, String msg) {
        sessions.forEach((clientId, session) -> {
            if (!session.send(event, msg)) {
                sessions.remove(clientId, session);
            }
        });
    }

    @Override
    public void sendToUser(String userId, String event, String msg) {
        String clientId = userSessionMap.get(userId);
        if (clientId != null) {
            send(clientId, event, msg);
        }
    }

    @Override
    public List<SocketSession> getOnlineSessions() {
        return new ArrayList<>(sessions.values());
    }

    @Override
    public List<SocketSession> getOnlineSession(String type, String roomId) {
        // 独立 SSE 没有房间概念，与嵌入式实现保持一致：返回全部在线会话
        return getOnlineSessions();
    }

    @Override
    public List<SocketUser> getOnlineUsers(String type) {
        List<SocketUser> users = new ArrayList<>();
        sessions.values().forEach(session -> {
            SocketUser user = session.getUser();
            if (user != null) {
                users.add(user);
            }
        });
        return users;
    }

    @Override
    public int getOnlineCount() {
        return sessions.size();
    }

    /**
     * 绑定用户到会话（供 REST 端点调用）。
     *
     * @param userId   用户标识
     * @param clientId 客户端标识
     */
    public void bindUser(String userId, String clientId) {
        userSessionMap.put(userId, clientId);
        Session session = sessions.get(clientId);
        if (session != null) {
            SocketUser user = new SocketUser();
            user.setUserId(userId);
            session.setUser(user);
        }
    }

    /**
     * 单条 SSE 连接。
     *
     * <p>发送动作被投递到所属连接自己的队列，由连接线程串行写出，
     * 避免多线程同时写同一条 socket。</p>
     */
    public static final class Session implements SocketSession {
        /** clientId */
        private final String id;
        /** SSE 响应 */
        private final NioServerResponse response;
        /** 写指令队列 */
        private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        /** 用户 */
        private volatile SocketUser user;
        /** 是否已关闭 */
        private volatile boolean closed;

        Session(String id, NioServerResponse response) {
            this.id = id;
            this.response = response;
        }

        /**
         * 投递写出任务，由连接线程执行。
         *
         * @param event 事件名
         * @param data  数据（已序列化）
         * @return 已投递返回 true
         */
        boolean send(String event, String data) {
            if (closed || response.isChannelClosed()) {
                closed = true;
                return false;
            }
            queue.offer(() -> write(event, data));
            return true;
        }

        /**
         * 真正的写出（只在连接线程执行）。
         *
         * @param event 事件名
         * @param data  数据
         */
        private void write(String event, String data) {
            if (closed || response.isChannelClosed()) {
                closed = true;
                return;
            }
            try {
                response.sseEvent(event, data);
            } catch (Exception e) {
                closed = true;
            }
        }

        /**
         * 取下一个写指令，超时返回 null。
         *
         * @param timeout 超时
         * @param unit    时间单位
         * @return 任务或 null
         * @throws InterruptedException 线程中断
         */
        Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }

        /**
         * 心跳：SSE 注释帧。
         *
         * @return 连接可用返回 true
         */
        boolean ping() {
            if (closed || response.isChannelClosed()) {
                closed = true;
                return false;
            }
            try {
                response.writeRaw(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                response.flush();
                return true;
            } catch (Exception e) {
                closed = true;
                return false;
            }
        }

        void close() {
            if (!closed) {
                closed = true;
                try {
                    response.sseClose();
                } catch (Exception ignored) {
                    // 连接已断，忽略
                }
            }
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getClientId() {
            return id;
        }

        @Override
        public void setClientId(String clientId) {
            // clientId 即 id，不允许改名
        }

        @Override
        public SocketUser getUser() {
            return user;
        }

        @Override
        public void setUser(SocketUser user) {
            this.user = user;
        }

        @Override
        public void setAttribute(String key, Object value) {
            // 独立 SSE 无属性表需求
        }

        @Override
        public <T> T getAttribute(String key) {
            return null;
        }

        @Override
        public void removeAttribute(String key) {
            // 无属性表
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public boolean isConnected() {
            return !closed;
        }

        @Override
        public void disconnect() {
            close();
        }

        @Override
        public void send(String event, Object data) {
            send(event, data instanceof String ? (String) data : Json.toJson(data));
        }

        @Override
        public <T> T getConnection() {
            return (T) response;
        }
    }
}
