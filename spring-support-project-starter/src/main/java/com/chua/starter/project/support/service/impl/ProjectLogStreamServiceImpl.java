package com.chua.starter.project.support.service.impl;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.project.support.config.ProjectProperties;
import com.chua.starter.project.support.entity.MsProject;
import com.chua.starter.project.support.mapper.MsProjectMapper;
import com.chua.starter.project.support.service.ProjectLogStreamService;
import com.chua.starter.project.support.service.ProjectPushService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.model.SshCredential;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.spi.HostRemoteProvider;
import com.chua.starter.server.support.spi.HostRemoteProviderRegistry;
import com.chua.starter.server.support.spi.RemoteLogChunk;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 项目实时日志流服务实现（后端共享采集器 + socket 广播）
 * <p>
 * 设计要点与取舍：
 * </p>
 * <ol>
 *   <li><b>一条流服务 N 个观察者</b>：流键为 {@code projectId:hostId}，无论多少浏览器在看，
 *       同一时间窗只产生一次远程 tail。远程开销从 O(观察者) 降到 O(被观察项目)。</li>
 *   <li><b>游标由后端持有</b>：避免多观察者各自推进游标互相吃掉增量。</li>
 *   <li><b>失败指数退避</b>：远端不可达时按 2^n 退避到上限 {@value #MAX_BACKOFF_MILLIS}ms，
 *       并把错误原因作为一帧推给前端 —— 既不空转打爆远端，也不让用户面对「什么都没有」。</li>
 *   <li><b>inFlight 单飞</b>：同一流不允许并发采集，避免慢 SSH 把 tick 堆成雪崩。</li>
 *   <li><b>观察者空闲剪枝</b>：前端需周期续订；未续订的观察者被剔除，
 *       流空则停止 —— 兜住「浏览器被强杀 / 断网未退订」导致的通道泄漏。</li>
 * </ol>
 * <p>
 * <b>为什么不用 {@code tail -f}</b>：POSIX 能挂住一个长连接流式读，但 WinRM 没有原生流式能力，
 * 跨平台无法统一；且会长期独占 SSH 通道，与既有连接池（refCount + 空闲回收）模型冲突。
 * 保持「定时增量拉取」语义，由 SPI 层负责各系统的读取差异。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class ProjectLogStreamServiceImpl implements ProjectLogStreamService {

    /** 失败退避上限（毫秒） */
    private static final long MAX_BACKOFF_MILLIS = 30_000L;

    /** 单次首帧拉取行数的运行时上限 */
    private static final int MAX_TAIL_LINES = 2000;

    /** 调度器首轮延迟（毫秒），避免与容器启动竞争 */
    private static final long TICK_INITIAL_DELAY_MILLIS = 1_000L;

    /** 项目 映射器 */
    private final MsProjectMapper projectMapper;

    /** 服务器主机 映射器 */
    private final MsServerHostMapper serverHostMapper;

    /** 服务器主机服务（凭据解析） */
    private final ServerHostService serverHostService;

    /**
    * 远程操作 SPI 注册中心（按系统类型路由，本类不关心是 SSH 还是 WinRM）
    */
    private final HostRemoteProviderRegistry remoteRegistry;

    /** 实时推送（socket 广播；宿主未装配 socket 时内部降级为空操作） */
    private final ProjectPushService pushService;

    /** 模块配置 */
    private final ProjectProperties projectProperties;

    /** 活跃流：key = {@code projectId:hostId} */
    private final Map<String, LogStream> streams = new ConcurrentHashMap<>();

    /** 采集调度器（单线程，只负责挑选到期的流并提交，不做阻塞 IO） */
    private ScheduledExecutorService ticker;

    /**
    * 采集线程池（真正执行远程 tail；大小受 logStreamMaxStreams 约束）
    */
    private ExecutorService workers;

    /**
    * 构造器注入
    *
    * @param projectMapper    项目映射器
    * @param serverHostMapper 主机映射器
    * @param serverHostService 主机服务（凭据解析）
    * @param remoteRegistry   远程操作 SPI 注册中心
    * @param pushService      实时推送
    * @param projectProperties 模块配置
    */
    public ProjectLogStreamServiceImpl(MsProjectMapper projectMapper,
                                       MsServerHostMapper serverHostMapper,
                                       ServerHostService serverHostService,
                                       HostRemoteProviderRegistry remoteRegistry,
                                       ProjectPushService pushService,
                                       ProjectProperties projectProperties) {
        this.projectMapper = projectMapper;
        this.serverHostMapper = serverHostMapper;
        this.serverHostService = serverHostService;
        this.remoteRegistry = remoteRegistry;
        this.pushService = pushService;
        this.projectProperties = projectProperties;
    }

    /**
    * 启动采集调度
    */
    @PostConstruct
    public void start() {
        int maxStreams = Math.max(1, projectProperties.getLogStreamMaxStreams());
        // 线程池按流上限收敛：每条活跃流同一时刻最多一个在飞的采集任务
        workers = Executors.newFixedThreadPool(Math.min(maxStreams, 8), r -> daemonOf(r, "project-log-fetch"));
        ticker = Executors.newSingleThreadScheduledExecutor(r -> daemonOf(r, "project-log-tick"));
        long interval = intervalMillis();
        ticker.scheduleWithFixedDelay(this::tick, TICK_INITIAL_DELAY_MILLIS, interval, TimeUnit.MILLISECONDS);
        log.info("[project-log] 实时日志采集器已启动: 间隔={}ms, 续订超时={}ms, 流上限={}",
                interval, projectProperties.getLogStreamIdleMillis(), maxStreams);
    }

    /**
    * 停止采集调度并释放线程
    */
    @PreDestroy
    public void stop() {
        if (ticker != null) {
            ticker.shutdownNow();
        }
        if (workers != null) {
            workers.shutdownNow();
        }
        streams.clear();
    }

    @Override
    public ReturnResult<Map<String, Object>> readOnce(Long projectId, Long hostId, Long cursor, Integer lines) {
        if (projectId == null) {
            return ReturnResult.error("项目不存在: null");
        }
        if (hostId == null) {
            return ReturnResult.error("请指定目标主机");
        }
        ReturnResult<StreamContext> resolved = resolveContext(projectId, hostId);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        StreamContext context = resolved.getData();
        int tailLines = lines == null || lines <= 0
                ? tailLines() : Math.min(lines, MAX_TAIL_LINES);
        try {
            RemoteLogChunk chunk = context.provider().tail(context.host(), context.credential(),
                    context.logFile(), cursor, tailLines);
            Map<String, Object> data = new HashMap<>(4);
            data.put("content", chunk.content());
            data.put("cursor", chunk.nextCursor());
            data.put("eof", chunk.eof());
            return ReturnResult.ok(data);
        } catch (Exception e) {
            return ReturnResult.error("日志读取失败: " + e.getMessage());
        }
    }

    @Override
    public ReturnResult<Map<String, Object>> subscribe(Long projectId, Long hostId, String subscriberId) {
        if (projectId == null || hostId == null) {
            return ReturnResult.error("项目与主机不能为空");
        }
        // 先校验一遍配置与凭据，避免为「注定拉不到日志」的项目建流
        ReturnResult<StreamContext> resolved = resolveContext(projectId, hostId);
        if (!resolved.isOk()) {
            return ReturnResult.error(resolved.getMsg());
        }
        String subscriber = subscriberId == null || subscriberId.isBlank() ? "anonymous" : subscriberId.trim();
        String key = keyOf(projectId, hostId);
        LogStream stream = streams.get(key);
        if (stream == null) {
            synchronized (this) {
                stream = streams.get(key);
                if (stream == null) {
                    int maxStreams = Math.max(1, projectProperties.getLogStreamMaxStreams());
                    if (streams.size() >= maxStreams) {
                        return ReturnResult.error("实时日志流已达上限(" + maxStreams + ")，请关闭部分日志窗口后重试");
                    }
                    stream = new LogStream(projectId, hostId, key);
                    streams.put(key, stream);
                    log.info("[project-log] 新建日志流 {}（当前 {} 条）", key, streams.size());
                }
            }
        }
        boolean firstSubscriber = stream.subscribers.isEmpty();
        stream.subscribers.put(subscriber, System.currentTimeMillis());
        if (firstSubscriber) {
            // 首个观察者：立刻拉一帧首屏（取末尾 N 行），不让用户等一个采集周期
            scheduleFetch(stream, true);
        }
        Map<String, Object> data = new HashMap<>(8);
        data.put("projectId", projectId);
        data.put("hostId", hostId);
        data.put("subscriberId", subscriber);
        data.put("intervalMillis", intervalMillis());
        data.put("idleMillis", projectProperties.getLogStreamIdleMillis());
        data.put("subscribers", stream.subscribers.size());
        return ReturnResult.ok(data);
    }

    @Override
    public ReturnResult<Map<String, Object>> unsubscribe(Long projectId, Long hostId, String subscriberId) {
        String key = keyOf(projectId, hostId);
        LogStream stream = streams.get(key);
        Map<String, Object> data = new HashMap<>(4);
        data.put("projectId", projectId);
        data.put("hostId", hostId);
        if (stream == null) {
            // 幂等：流可能已因空闲被回收，不算错误
            data.put("stopped", true);
            return ReturnResult.ok(data);
        }
        String subscriber = subscriberId == null || subscriberId.isBlank() ? "anonymous" : subscriberId.trim();
        stream.subscribers.remove(subscriber);
        boolean stopped = stream.subscribers.isEmpty();
        if (stopped) {
            streams.remove(key, stream);
            log.info("[project-log] 日志流 {} 已无观察者，停止采集（剩余 {} 条）", key, streams.size());
        }
        data.put("stopped", stopped);
        data.put("subscribers", stream.subscribers.size());
        return ReturnResult.ok(data);
    }

    @Override
    public List<Map<String, Object>> activeStreams() {
        List<Map<String, Object>> out = new ArrayList<>(streams.size());
        for (LogStream stream : streams.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", stream.key);
            item.put("projectId", stream.projectId);
            item.put("hostId", stream.hostId);
            item.put("cursor", stream.cursor);
            item.put("subscribers", stream.subscribers.size());
            item.put("failCount", stream.failCount);
            item.put("lastFetchMs", stream.lastFetchMs);
            item.put("aliveMs", System.currentTimeMillis() - stream.createdMs);
            out.add(item);
        }
        return out;
    }

    /**
    * 调度节拍：剔除超时观察者 → 挑出到期的流提交采集
    */
    private void tick() {
        try {
            long now = System.currentTimeMillis();
            for (LogStream stream : streams.values()) {
                pruneSubscribers(stream, now);
                if (stream.subscribers.isEmpty()) {
                    streams.remove(stream.key, stream);
                    log.info("[project-log] 日志流 {} 观察者全部超时，停止采集", stream.key);
                    continue;
                }
                if (now < stream.nextEligibleMs) {
                    continue;
                }
                scheduleFetch(stream, false);
            }
        } catch (Exception e) {
            // 调度线程绝不能因单次异常退出，否则所有流永久停摆
            log.warn("[project-log] 采集调度异常: {}", e.getMessage());
        }
    }

    /**
    * 提交一次采集任务（inFlight 单飞，同一流不允许并发采集）
    *
    * @param stream    目标流
    * @param immediate 是否无视退避立即执行
    */
    private void scheduleFetch(LogStream stream, boolean immediate) {
        if (!stream.inFlight.compareAndSet(false, true)) {
            return;
        }
        if (immediate) {
            stream.nextEligibleMs = 0L;
        } else {
            stream.nextEligibleMs = System.currentTimeMillis() + intervalMillis();
        }
        try {
            workers.execute(() -> fetch(stream));
        } catch (RejectedExecutionException e) {
            // 池已关闭或已满：必须释放标记，否则该流永久不再采集
            stream.inFlight.set(false);
            log.warn("[project-log] 采集线程池不可用，本轮跳过: {}", stream.key);
        }
    }

    /**
    * 执行一次增量采集并推送
    *
    * @param stream 目标流
    */
    private void fetch(LogStream stream) {
        try {
            ReturnResult<StreamContext> resolved = resolveContext(stream.projectId, stream.hostId);
            if (!resolved.isOk()) {
                onFailure(stream, resolved.getMsg());
                return;
            }
            StreamContext context = resolved.getData();
            RemoteLogChunk chunk = context.provider().tail(context.host(), context.credential(),
                    context.logFile(), stream.cursor, tailLines());
            stream.cursor = chunk.nextCursor();
            stream.failCount = 0;
            stream.lastFetchMs = System.currentTimeMillis();
            String content = chunk.content();
            boolean truncated = false;
            int maxChars = projectProperties.getLogStreamMaxChunkChars();
            if (content != null && maxChars > 0 && content.length() > maxChars) {
                // 保留最新部分：截掉头部而不是尾部，否则用户永远看不到刚写进去的日志
                content = content.substring(content.length() - maxChars);
                truncated = true;
            }
            pushService.pushProjectLog(stream.projectId, stream.hostId, content,
                    chunk.nextCursor(), chunk.eof(), truncated, "");
        } catch (Exception e) {
            onFailure(stream, e.getMessage());
        } finally {
            stream.inFlight.set(false);
        }
    }

    /**
    * 采集失败：累计失败次数、按 2^n 退避，并把原因推给观察者
    * <p>
    * 推错误帧而不是静默：前端要能区分「确实没有日志」与「连不上/配置错」，
    * 否则用户只会看到一个永远空着的面板。
    * </p>
    *
    * @param stream  目标流
    * @param message 错误原因
    */
    private void onFailure(LogStream stream, String message) {
        stream.failCount++;
        long factor = 1L << Math.min(stream.failCount, 5);
        long delay = Math.min(intervalMillis() * factor, MAX_BACKOFF_MILLIS);
        stream.nextEligibleMs = System.currentTimeMillis() + delay;
        log.debug("[project-log] 拉取失败 {}（第 {} 次，退避 {}ms）: {}",
                stream.key, stream.failCount, delay, message);
        pushService.pushProjectLog(stream.projectId, stream.hostId, "",
                stream.cursor == null ? 0L : stream.cursor, true, false,
                message == null ? "日志读取失败" : message);
    }

    /**
    * 剔除长时间未续订的观察者
    *
    * @param stream 目标流
    * @param now    当前毫秒
    */
    private void pruneSubscribers(LogStream stream, long now) {
        // 下限取 3 个采集周期：避免用户把 idle 配得比 interval 还小导致刚订阅就被踢
        long idle = Math.max(projectProperties.getLogStreamIdleMillis(), intervalMillis() * 3);
        stream.subscribers.entrySet().removeIf(entry -> now - entry.getValue() > idle);
    }

    /**
    * 解析项目 / 主机 / 凭据 / 日志文件全路径
    *
    * @param projectId 项目标识
    * @param hostId    主机标识
    * @return 上下文；配置缺失或主机不可用时返回错误
    */
    private ReturnResult<StreamContext> resolveContext(Long projectId, Long hostId) {
        MsProject project = projectMapper.selectById(projectId);
        if (project == null) {
            return ReturnResult.error("项目不存在: " + projectId);
        }
        if (isBlank(project.getMsProjectLogDir()) || isBlank(project.getMsProjectLogFile())) {
            return ReturnResult.error("项目未配置日志目录/日志文件");
        }
        MsServerHost host = serverHostMapper.selectById(hostId);
        if (host == null) {
            return ReturnResult.error("目标服务器不存在: " + hostId);
        }
        ReturnResult<SshCredential> credential = serverHostService.resolveCredential(hostId, null);
        if (!credential.isOk()) {
            return ReturnResult.error(credential.getMsg());
        }
        HostRemoteProvider provider = remoteRegistry.require(host);
        // 路径拼接走 SPI：POSIX 用 /，Windows 用 \，本类不再关心分隔符
        String logFile = provider.join(project.getMsProjectLogDir(), project.getMsProjectLogFile());
        return ReturnResult.ok(new StreamContext(host, credential.getData(), provider, logFile));
    }

    /**
    * 采集间隔（毫秒），带下限保护
    *
    * @return 毫秒
    */
    private long intervalMillis() {
        return Math.max(projectProperties.getLogStreamIntervalMillis(), 200);
    }

    /**
    * 首帧拉取行数，带上限保护
    *
    * @return 行数
    */
    private int tailLines() {
        int lines = projectProperties.getLogStreamTailLines();
        if (lines <= 0) {
            lines = 500;
        }
        return Math.min(lines, MAX_TAIL_LINES);
    }

    /**
    * 流键
    *
    * @param projectId 项目标识
    * @param hostId    主机标识
    * @return {@code projectId:hostId}
    */
    private String keyOf(Long projectId, Long hostId) {
        return projectId + ":" + hostId;
    }

    /**
    * 空串判断
    *
    * @param value 字符串
    * @return 是否为空
    */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
    * 构造守护线程
    *
    * @param runnable 任务
    * @param name     线程名
    * @return 线程
    */
    private Thread daemonOf(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    /**
    * 一条共享采集流
    */
    private static final class LogStream {

        /** 项目标识 */
        final Long projectId;

        /** 主机标识 */
        final Long hostId;

        /** 流键 */
        final String key;

        /** 观察者标识 -> 最后续订毫秒 */
        final Map<String, Long> subscribers = new ConcurrentHashMap<>();

        /** 采集单飞标记 */
        final AtomicBoolean inFlight = new AtomicBoolean(false);

        /** 创建时间 */
        final long createdMs = System.currentTimeMillis();

        /** 当前读取的字节偏移；null 表示尚未读取过（首帧取末尾 N 行） */
        volatile Long cursor;

        /** 下次允许采集的时间（退避用） */
        volatile long nextEligibleMs;

        /** 最近一次成功采集时间 */
        volatile long lastFetchMs;

        /** 连续失败次数 */
        volatile int failCount;

        LogStream(Long projectId, Long hostId, String key) {
            this.projectId = projectId;
            this.hostId = hostId;
            this.key = key;
        }
    }

    /**
        * 解析后的采集上下文
        *
        * @param host       主机
        * @param credential 凭据
        * @param provider   该主机的远程操作实现
        * @param logFile    日志文件绝对路径
        */
    private record StreamContext(MsServerHost host, SshCredential credential,
                                 HostRemoteProvider provider, String logFile) {
    }
}
