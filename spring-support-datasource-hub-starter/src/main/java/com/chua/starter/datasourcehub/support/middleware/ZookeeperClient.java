package com.chua.starter.datasourcehub.support.middleware;

import com.chua.starter.datasourcehub.support.model.DataSourceVO;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZooKeeper 管理侧客户端：按数据源维护长会话，并把 Curator 的受检异常换成可读原因。
 *
 * <p>会话必须复用而不是「一次请求一个连接」：临时节点只在其会话存活期间存在，
 * 若每次操作都新建再关闭客户端，刚创建的临时节点会随会话结束立刻消失。
 * 因此连接按「数据源标识 + 配置更新时间」缓存，配置改动自然淘汰旧会话，
 * 空闲超时的会话由使用入口顺手回收。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZookeeperClient {

    /**
     * 数据源类型标识
     */
    public static final String DS_TYPE = "zookeeper";

    /**
     * 会话空闲多久后回收（毫秒）
     */
    private static final long IDLE_EVICT_MS = 300_000L;

    /**
     * 建立连接的最长等待（毫秒）
     */
    private static final long CONNECT_WAIT_MS = 8_000L;

    /**
     * 子节点返回上限，避免误连超级大的父节点时拖垮页面
     */
    private static final int CHILD_LIMIT = 1000;

    /**
     * 版本探测用的四字命令，需服务端在 {@code 4lw.commands.whitelist} 中放开
     */
    private static final String SRVR_COMMAND = "srvr";

    /**
     * 版本探测的读写超时（毫秒），内网服务给 2 秒足够
     */
    private static final int VERSION_PROBE_MS = 2_000;

    /**
     * 四字命令探测的读写超时（毫秒），集群指标采集时使用
     */
    private static final int MNTR_PROBE_MS = 3_000;

    /**
     * 监控数据源服务
     */
    private final DatasourceService datasourceService;

    /**
     * 长会话缓存，键为「连接串+命名空间+账号+配置更新时间」
     */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * 一次 ZooKeeper 操作。
     *
     * @param <T> 返回类型
     * @author CH
     * @since 4.0.0.42
     */
    @FunctionalInterface
    public interface Action<T> {

        /**
         * 执行。
         *
         * @param curator 已连上的客户端
         * @return 结果
         * @throws Exception 由 {@link ZookeeperClient#execute} 统一翻译
         */
        T run(CuratorFramework curator) throws Exception;
    }

    /**
     * 用登记的会话执行一次操作，失败一律还原成带原因的 {@link IllegalStateException}。
     *
     * @param datasourceId 数据源 ID，可空时取第一个 zookeeper 数据源
     * @param action       操作
     * @param <T>          返回类型
     * @return 操作结果
     */
    public <T> T execute(Long datasourceId, Action<T> action) {
        DataSourceVO ds = requireDatasource(datasourceId);
        return execute(ds, action);
    }

    /**
     * 用给定配置执行一次操作（即席测试用）。
     *
     * @param ds     数据源配置
     * @param action 操作
     * @param <T>    返回类型
     * @return 操作结果
     */
    public <T> T execute(DataSourceVO ds, Action<T> action) {
        String key = key(ds);
        try {
            Session session = acquire(key, ds);
            T result = action.run(session.curator());
            session.touch();
            return result;
        } catch (Exception e) {
            // 语义类失败（节点不存在等）不该拆掉好会话，只有连接类失败才重连
            if (isConnectionRelated(e)) {
                closeQuietly(key);
            }
            throw toIllegalState(ds, e);
        }
    }

    /**
     * 读取服务端版本，取自四字命令 {@code srvr}。
     *
     * <p>3.5 之后 {@code /zookeeper} 节点数据不再带版本号，只能走四字命令；
     * 服务端未把 {@code srvr} 加入 {@code 4lw.commands.whitelist} 时返回空串，
     * 版本仅作展示，不影响连通性判定。</p>
     *
     * @param curator 已连上的客户端
     * @return 版本号，如 {@code 3.9.4}，取不到时为空串
     */
    public static String serverVersion(CuratorFramework curator) {
        String connectString;
        try {
            connectString = curator.getZookeeperClient().getCurrentConnectionString();
        } catch (Exception e) {
            return "";
        }
        for (String address : connectString.split(",")) {
            String version = srvrVersion(address.trim());
            if (!version.isEmpty()) {
                return version;
            }
        }
        return "";
    }

    /**
     * 向单个地址发一次 {@code srvr} 并取版本号。
     *
     * @param address {@code host:port}
     * @return 版本号，未放开四字命令或解析失败时为空串
     */
    private static String srvrVersion(String address) {
        int split = address.lastIndexOf(':');
        if (split <= 0 || split == address.length() - 1) {
            return "";
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address.substring(0, split),
                    Integer.parseInt(address.substring(split + 1))), VERSION_PROBE_MS);
            socket.setSoTimeout(VERSION_PROBE_MS);
            socket.getOutputStream().write(SRVR_COMMAND.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            socket.shutdownOutput();
            return parseVersion(new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.debug("[zookeeper] {} 版本探测失败: {}", address, e.getMessage());
            return "";
        }
    }

    /**
     * 从 {@code srvr} 回包里取 {@code Zookeeper version: 3.9.4-xxxx} 的版本号。
     *
     * @param body 回包全文
     * @return 版本号，没有该行时为空串
     */
    private static String parseVersion(String body) {
        for (String line : body.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon < 0 || !line.substring(0, colon).trim().equalsIgnoreCase("zookeeper version")) {
                continue;
            }
            String value = line.substring(colon + 1).trim();
            int dash = value.indexOf('-');
            return (dash > 0 ? value.substring(0, dash) : value).trim();
        }
        return "";
    }

    /**
     * 取当前会话 ID。
     *
     * @param curator 已连上的客户端
     * @return 十六进制会话 ID，取不到时为空串
     */
    public static String sessionId(CuratorFramework curator) {
        try {
            return Long.toHexString(curator.getZookeeperClient().getZooKeeper().getSessionId());
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 校验路径合法性。
     *
     * @param path 节点路径
     * @return 规整后的绝对路径
     * @throws IllegalArgumentException 路径为空或不是绝对路径
     */
    public static String requirePath(String path) {
        String value = path == null ? "" : path.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("节点路径不能为空");
        }
        if (!value.startsWith("/")) {
            throw new IllegalArgumentException("节点路径必须以 / 开头: " + value);
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 父路径 + 子节点名拼绝对路径。
     *
     * @param parent 父路径
     * @param name   子节点名
     * @return 绝对路径
     */
    public static String childOf(String parent, String name) {
        return "/".equals(parent) ? "/" + name : parent + "/" + name;
    }

    /**
     * 列出子节点名，超过上限时截断。
     *
     * @param curator 客户端
     * @param path    父路径
     * @return 子节点名列表
     * @throws Exception 读失败
     */
    public static List<String> listChildren(CuratorFramework curator, String path) throws Exception {
        List<String> children = curator.getChildren().forPath(path);
        List<String> names = new ArrayList<>(children == null ? List.of() : children);
        if (names.size() > CHILD_LIMIT) {
            log.warn("[zookeeper] {} 下子节点 {} 个，仅返回前 {} 个", path, names.size(), CHILD_LIMIT);
            return names.subList(0, CHILD_LIMIT);
        }
        return names;
    }

    /**
     * 节点数据按 UTF-8 解码，二进制内容换成占位说明。
     *
     * @param bytes 原始数据
     * @return 可展示文本
     */
    public static String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0xFFFD || (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
                return "<二进制数据 " + bytes.length + " 字节>";
            }
        }
        return text;
    }

    /**
     * 创建模式。
     *
     * @param ephemeral 是否临时节点
     * @return 创建模式
     */
    public static CreateMode modeOf(Boolean ephemeral) {
        return Boolean.TRUE.equals(ephemeral) ? CreateMode.EPHEMERAL : CreateMode.PERSISTENT;
    }

    /**
     * 关闭全部缓存会话，容器销毁时调用。
     */
    @PreDestroy
    public void shutdown() {
        for (String key : new ArrayList<>(sessions.keySet())) {
            closeQuietly(key);
        }
    }

    /**
     * 取出（必要时新建）长会话。
     *
     * @param key 缓存键
     * @param ds  数据源配置
     * @return 已连上的会话
     * @throws IllegalStateException 连接超时或建连失败
     */
    private Session acquire(String key, DataSourceVO ds) {
        evictIdle();
        Session session = sessions.computeIfAbsent(key, k -> open(k, ds));
        if (!session.connected()) {
            closeQuietly(key);
            throw new IllegalStateException("连接超时或失败");
        }
        return session;
    }

    /**
     * 失败是否属于连接类，决定要不要丢弃缓存会话。
     *
     * @param e 原始异常
     * @return 连接类失败为真
     */
    private static boolean isConnectionRelated(Exception e) {
        if (e instanceof KeeperException) {
            return e instanceof KeeperException.ConnectionLossException
                    || e instanceof KeeperException.SessionExpiredException
                    || e instanceof KeeperException.SessionMovedException
                    || e instanceof KeeperException.OperationTimeoutException;
        }
        return true;
    }

    /**
     * 建立新会话。
     *
     * @param key 缓存键
     * @param ds  数据源配置
     * @return 会话
     */
    private Session open(String key, DataSourceVO ds) {
        String connectString = connectString(ds);
        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs((int) CONNECT_WAIT_MS * 5)
                .connectionTimeoutMs((int) CONNECT_WAIT_MS)
                .retryPolicy(noRetry());
        String namespace = namespace(ds);
        if (!namespace.isEmpty()) {
            builder.namespace(namespace);
        }
        String username = trim(ds.username());
        if (!username.isEmpty()) {
            builder.authorization("digest", (username + ":" + blankToEmpty(ds.password())).getBytes(StandardCharsets.UTF_8));
        }
        CuratorFramework curator = builder.build();
        curator.start();
        Session session = new Session(curator);
        boolean ok = false;
        try {
            long deadline = System.currentTimeMillis() + CONNECT_WAIT_MS;
            while (System.currentTimeMillis() < deadline) {
                if (curator.getZookeeperClient().isConnected()) {
                    ok = true;
                    break;
                }
                // [P3C A20 豁免]
                Thread.sleep(100L);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("[zookeeper] 建连异常 {}", connectString, e);
        }
        session.markConnected(ok);
        log.info("[zookeeper] {} 会话 {}: {}", connectString, ok ? "已建立" : "建立失败", key);
        return session;
    }

    /**
     * 不重试的重试策略：管理页要的是「一次请求一个明确结果」。
     *
     * @return 重试策略
     */
    private static RetryPolicy noRetry() {
        return new RetryNTimes(0, 0);
    }

    /**
     * 空闲会话回收。
     */
    private void evictIdle() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (now - entry.getValue().lastUsed() > IDLE_EVICT_MS) {
                closeQuietly(entry.getKey());
            }
        }
    }

    /**
     * 关闭并移除会话。
     *
     * @param key 缓存键
     */
    private void closeQuietly(String key) {
        Session session = sessions.remove(key);
        if (session != null) {
            try {
                if (session.curator().getState() != CuratorFrameworkState.STOPPED) {
                    session.curator().close();
                }
            } catch (Exception e) {
                log.debug("[zookeeper] 关闭会话 {} 失败: {}", key, e.getMessage());
            }
        }
    }

    /**
     * 连接串：host:port，端口缺省 2181。
     *
     * @param ds 数据源配置
     * @return 连接串
     */
    private static String connectString(DataSourceVO ds) {
        String host = trim(ds.host());
        if (host.isEmpty()) {
            throw new IllegalStateException("数据源 " + ds.name() + " 未填写主机地址");
        }
        // [P3C A20 豁免]
        int port = ds.port() == null || ds.port() <= 0 ? 2181 : ds.port();
        // 允许 host 字段直接写多节点（逗号分隔），端口对全部节点生效
        List<String> nodes = new ArrayList<>();
        for (String node : host.split(",")) {
            String value = trim(node);
            if (value.isEmpty()) {
                continue;
            }
            nodes.add(value.contains(":") ? value : value + ":" + port);
        }
        if (nodes.isEmpty()) {
            throw new IllegalStateException("数据源 " + ds.name() + " 的主机地址无法解析");
        }
        return String.join(",", nodes);
    }

    /**
     * 命名空间（chroot），取自库名字段；填写完整 URL 时不作为命名空间。
     *
     * @param ds 数据源配置
     * @return 归一后的命名空间，可为空串
     */
    private static String namespace(DataSourceVO ds) {
        String value = trim(ds.database());
        if (value.isEmpty() || value.contains("://")) {
            return "";
        }
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    /**
     * 缓存键：同一数据源改配置后必须换会话，故带上更新时间。
     *
     * @param ds 数据源配置
     * @return 键
     */
    private static String key(DataSourceVO ds) {
        return connectString(ds) + "|" + namespace(ds) + "|" + trim(ds.username()) + "|" + ds.updateTime();
    }

    /**
     * 把 Curator/ZooKeeper 异常翻译成界面可读的原因。
     *
     * @param ds 数据源配置
     * @param e  原始异常
     * @return 统一异常
     */
    private IllegalStateException toIllegalState(DataSourceVO ds, Exception e) {
        String reason = reasonOf(e);
        log.warn("[zookeeper] 数据源 {}({}) 操作失败: {}", ds.name(), connectStringQuietly(ds), reason);
        return new IllegalStateException(describe(ds, reason), e);
    }

    /**
     * 统一失败文案。
     *
     * @param ds     数据源配置
     * @param reason 原因
     * @return 文案
     */
    private static String describe(DataSourceVO ds, String reason) {
        return "ZooKeeper " + ds.name() + "(" + connectStringQuietly(ds) + ") " + reason;
    }

    /**
     * 连接串，用于文案；解析失败时退化成原始字段。
     *
     * @param ds 数据源配置
     * @return 连接串
     */
    private static String connectStringQuietly(DataSourceVO ds) {
        try {
            return connectString(ds);
        } catch (Exception e) {
            return trim(ds.host());
        }
    }

    /**
     * 逐层取异常原因，ZooKeeper 的语义化异常换成中文。
     *
     * @param e 原始异常
     * @return 可读原因
     */
    private static String reasonOf(Throwable e) {
        if (e instanceof KeeperException.NoNodeException ex) {
            return "节点不存在: " + ex.getPath();
        }
        if (e instanceof KeeperException.NodeExistsException ex) {
            return "节点已存在: " + ex.getPath();
        }
        if (e instanceof KeeperException.NotEmptyException ex) {
            return "节点仍有子节点，请先删除子节点: " + ex.getPath();
        }
        if (e instanceof KeeperException.SessionExpiredException) {
            return "会话已过期，请重试";
        }
        if (e instanceof KeeperException.ConnectionLossException) {
            return "连接已中断，请重试";
        }
        if (e instanceof KeeperException.NoAuthException) {
            return "无权限访问该节点（digest 账号不足）";
        }
        if (e instanceof KeeperException.InvalidACLException) {
            return "ACL 不合法";
        }
        if (e instanceof KeeperException.BadArgumentsException) {
            return "参数不合法（路径过长或数据过大）";
        }
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return "等待响应被中断";
        }
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    /**
     * 取数据源配置并校验类型。
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 数据源配置
     * @throws IllegalStateException 未登记或类型不符
     */
    public DataSourceVO requireDatasource(Long datasourceId) {
        return MiddlewareDatasources.require(datasourceService, datasourceId, DS_TYPE, "zk");
    }

    /**
     * 解析数据源配置里的全部集群节点地址（{@code host:port} 列表）。
     *
     * <p>host 字段支持逗号分隔多节点，端口对未显式写 {@code host:port} 的节点生效。</p>
     *
     * @param datasourceId 数据源 ID，可为空
     * @return 节点地址列表，至少一个
     */
    public List<String> clusterHosts(Long datasourceId) {
        DataSourceVO ds = requireDatasource(datasourceId);
        String host = trim(ds.host());
        if (host.isEmpty()) {
            throw new IllegalStateException("数据源 " + ds.name() + " 未填写主机地址");
        }
        // [P3C A20 豁免]
        int port = ds.port() == null || ds.port() <= 0 ? 2181 : ds.port();
        List<String> nodes = new ArrayList<>();
        for (String node : host.split(",")) {
            String value = trim(node);
            if (value.isEmpty()) {
                continue;
            }
            nodes.add(value.contains(":") ? value : value + ":" + port);
        }
        if (nodes.isEmpty()) {
            throw new IllegalStateException("数据源 " + ds.name() + " 的主机地址无法解析");
        }
        return nodes;
    }

    /**
     * 向单个 ZooKeeper 节点发送一次四字命令并返回原始响应。
     *
     * <p>服务端需在 {@code 4lw.commands.whitelist} 中放开对应命令，否则连接被拒或返回错误串。
     * 该方法不抛异常，失败返回空串，由调用方决定如何展示。</p>
     *
     * @param address   {@code host:port}
     * @param command   四字命令，如 {@code mntr}、{@code srvr}、{@code stat}
     * @param timeoutMs 连接与读取超时（毫秒）
     * @return 响应全文，失败为空串
     */
    public static String fourLetter(String address, String command, int timeoutMs) {
        int split = address.lastIndexOf(':');
        if (split <= 0 || split == address.length() - 1) {
            return "";
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address.substring(0, split),
                    Integer.parseInt(address.substring(split + 1))), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            socket.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            socket.shutdownOutput();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("[zookeeper] {} 四字命令 {} 失败: {}", address, command, e.getMessage());
            return "";
        }
    }

    /**
     * 默认超时的四字命令采集。
     *
     * @param address {@code host:port}
     * @param command 四字命令
     * @return 响应全文，失败为空串
     */
    public static String fourLetter(String address, String command) {
        return fourLetter(address, command, MNTR_PROBE_MS);
    }

    /**
     * 空值安全的去空白。
     *
     * @param value 原值
     * @return 去空白结果
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * null 转空串。
     *
     * @param value 原值
     * @return 非 null 值
     */
    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 缓存的长会话。
     *
     * @author CH
     * @since 4.0.0.42
     */
    private static final class Session {

        /**
         * Curator 客户端
         */
        private final CuratorFramework curator;

        /**
         * 是否已连上
         */
        private volatile boolean connected;

        /**
         * 最近使用时间（毫秒）
         */
        private volatile long lastUsed;

        /**
         * Session
         * @param curator 参数
         * @return 返回值
        */
        Session(CuratorFramework curator) {
            this.curator = curator;
            this.lastUsed = System.currentTimeMillis();
        }

        CuratorFramework curator() {
            return curator;
        }

        boolean connected() {
            return connected && curator.getState() == CuratorFrameworkState.STARTED;
        }

        void markConnected(boolean value) {
            this.connected = value;
            this.lastUsed = System.currentTimeMillis();
        }

        void touch() {
            this.lastUsed = System.currentTimeMillis();
        }

        long lastUsed() {
            return lastUsed;
        }
    }
}
