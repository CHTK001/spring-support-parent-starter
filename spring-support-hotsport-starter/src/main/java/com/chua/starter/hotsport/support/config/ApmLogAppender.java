package com.chua.starter.hotsport.support.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.chua.runtime.apm.storage.ApmStorage;
import com.chua.runtime.apm.storage.LogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import com.chua.common.support.utils.ArrayUtils;

/**
 * 应用日志采集器 — 把宿主进程**真实**产生的 logback 日志写入 APM 存储。
 *
 * <p>这是「日志」页面与「实时日志」页面唯一的真实数据来源：不接这个 appender，
 * 存储里的日志永远是 0 条。采集到的每条记录都来自宿主真实的一次日志输出，
 * 级别、logger、类名、方法名、消息、traceId 全部原样透传，不做任何补充或模拟。</p>
 *
 * <p>四条硬性约束（APM 是观测组件，绝不允许影响宿主）：</p>
 * <ol>
 *   <li>任何异常都在内部吞掉，只做有限自诊断，绝不外抛；</li>
 *   <li>用 ThreadLocal 防重入，避免「记录日志 → 触发采集 → 又记录日志」的递归；</li>
 *   <li>不采集 APM 自身与存储实现的日志，防止自我放大；</li>
 *   <li><b>绝不在任何全局锁内做格式化或存储 I/O</b>：日志调用线程只做「过滤 + 组装 +
 *       有界入队」，落库由单个后台守护线程异步完成。历史上 {@code append} 在
 *       父类 synchronized 锁内同步写 SQLite，晚间磁盘分页抖动时该锁被长时间持有，
 *       全进程所有写日志的线程（含 /actuator/health 链路）集体阻塞，酿成健康检查假死。
 *       队列有界，写不动时丢弃并计数，宁可少采也不拖垮宿主。</li>
 * </ol>
 *
 * @author CH
 * @since 4.1.0
 */
public class ApmLogAppender extends AppenderBase<ILoggingEvent> {

    /**
     * 与请求链路关联的 MDC 键：请求过滤器写入，采集器读取。
     */
    public static final String MDC_TRACE_ID = "hotsportTraceId";

    /**
     * 默认 appender 名称。
     */
    public static final String APPENDER_NAME = "HOTSPORT_APM_LOG";

    /**
     * 单条消息最大长度（超出截断），防止一条巨型异常栈把存储打爆。
     */
    private static final int DEFAULT_MAX_MESSAGE = 4096;

    /**
     * 采集队列容量（超出即丢弃，保护宿主）。2048 条 × 4KB 消息上限 ≈ 8MB，可控。
     */
    private static final int QUEUE_CAPACITY = 2048;

    /**
     * 后台写线程单轮批量落库上限。
     */
    private static final int DRAIN_BATCH = 64;

    /**
     * 队列等待超时（毫秒），保证停止信号能及时被感知。
     */
    private static final long POLL_TIMEOUT_MS = 500;

    /**
     * 每丢弃多少条输出一次自诊断。
     */
    private static final long DROP_LOG_INTERVAL = 4096;

    /**
     * stop 时等待后台线程退出/清空队列的上限（毫秒）。
     */
    private static final long STOP_TIMEOUT_MS = 2000;

    /**
     * 不采集的 logger 前缀（APM 自身 / 存储实现），避免自我放大。
     */
    private static final String[] EXCLUDED_LOGGER_PREFIXES = {
            "com.chua.starter.hotsport.support.config.ApmLog",
            "com.chua.starter.hotsport.support.service.ApmQueryService",
            "com.chua.runtime.apm.storage"
    };

    /**
     * 传输层自噪声：APM 自己的 SSE 端点在客户端正常关闭连接时，
     * 容器会回调一次「响应已中断 / 客户端已断开」，被全局异常通知器记成 WARN。
     * 这类记录与被观测业务无关，属于观测组件自身的传输噪声，不入库。
     */
    private static final String[] SELF_NOISE_MARKERS = {
            "流式响应已中断",
            "disconnected client",
            "Response not usable after response errors"
    };

    /**
     * 自噪声必须同时命中该 URI 片段才丢弃，避免误伤真实业务异常。
     */
    private static final String SELF_NOISE_URI = "/api/apm/stream";

    /**
     * 自诊断 logger：命名落在 {@link #EXCLUDED_LOGGER_PREFIXES} 内，绝不二次采集。
     */
    private static final Logger SELF_LOG = LoggerFactory.getLogger(ApmLogAppender.class);

    /**
     * 最低采集级别。
     */
    private final Level minLevel;

    /**
     * 是否采集调用方类名/方法名（{@code getCallerData()} 有一定开销）。
     */
    private final boolean captureCaller;

    /**
     * 单条消息最大长度。
     */
    private final int maxMessageLength;

    /**
     * 防重入标记。
     */
    private final ThreadLocal<Boolean> inAppend = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 有界采集队列：日志调用线程只入队（offer，永不阻塞），落库由后台线程完成。
     */
    private final LinkedBlockingQueue<LogRecord> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    /**
     * 队列溢出丢弃计数。
     */
    private final AtomicLong droppedCount = new AtomicLong();

    /**
     * 后台写线程存活标记。
     */
    private final AtomicBoolean writerRunning = new AtomicBoolean(false);

    /**
     * stop 幂等标记。
     */
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * 后台写线程引用（用于停止时中断与 join）。
     */
    private volatile Thread writerThread;

    /**
     * 构造采集器。
     *
     * @param minLevel         最低采集级别（如 {@code INFO}）
     * @param captureCaller    是否采集调用方类名/方法名
     * @param maxMessageLength 单条消息最大长度
     */
    public ApmLogAppender(String minLevel, boolean captureCaller, int maxMessageLength) {
        this.minLevel = Level.toLevel(minLevel, Level.INFO);
        this.captureCaller = captureCaller;
        this.maxMessageLength = maxMessageLength > 0 ? maxMessageLength : DEFAULT_MAX_MESSAGE;
        setName(APPENDER_NAME);
    }

    /**
     * 日志入口 — 覆写父类的 synchronized doAppend。
     *
     * <p>父类 {@code AppenderBase#doAppend} 是 synchronized 的，历史上
     * {@link #append} 在这把全局锁内同步写 SQLite，晚间磁盘分页抖动时
     * 全进程写日志的线程都在等这把锁。这里绕开同步块：调用线程只做
     * 过滤、组装与有界入队，绝不在锁内做任何 I/O。</p>
     *
     * @param event 日志事件
     */
    @Override
    public void doAppend(ILoggingEvent event) {
        if (event == null || !isStarted() || Boolean.TRUE.equals(inAppend.get())) {
            return;
        }
        inAppend.set(Boolean.TRUE);
        try {
            enqueue(event);
        } catch (Throwable ignore) {
            // 观测组件永不阻断宿主，且此处绝不能再打日志（会造成递归）
        } finally {
            inAppend.set(Boolean.FALSE);
        }
    }

    /**
     * 保留父类抽象方法的实现；真实入口是 {@link #doAppend(ILoggingEvent)}。
     *
     * @param event 日志事件
     */
    @Override
    protected void append(ILoggingEvent event) {
        enqueue(event);
    }

    /**
     * 过滤并组装记录后入队（永不阻塞、绝不持锁做 I/O）。
     *
     * @param event 日志事件
     */
    private void enqueue(ILoggingEvent event) {
        if (!accept(event)) {
            return;
        }
        if (HotsportProperties.storage() == null) {
            return;
        }
        if (!queue.offer(toRecord(event))) {
            onQueueOverflow();
        }
    }

    /**
     * 队列满：丢弃并计数，每 {@link #DROP_LOG_INTERVAL} 条自诊断一次。
     * SELF_LOG 的名字在排除前缀内，不会二次采集，也不会递归。
     */
    private void onQueueOverflow() {
        long dropped = droppedCount.incrementAndGet();
        if (dropped % DROP_LOG_INTERVAL == 0) {
            SELF_LOG.warn("[Hotsport] APM 日志队列已满，累计丢弃 {} 条（宿主压力保护，不影响业务）", dropped);
        }
    }

    /**
     * 判断这条日志是否采集。
     *
     * @param event 日志事件
     * @return 是否采集
     */
    private boolean accept(ILoggingEvent event) {
        if (event.getLevel() == null || !event.getLevel().isGreaterOrEqual(minLevel)) {
            return false;
        }
        String logger = event.getLoggerName();
        if (logger != null) {
            for (String prefix : EXCLUDED_LOGGER_PREFIXES) {
                if (logger.startsWith(prefix)) {
                    return false;
                }
            }
        }
        return !isSelfTransportNoise(event);
    }

    /**
     * 判断是否为 APM 自身 SSE 端点产生的传输噪声。
     *
     * @param event 日志事件
     * @return true 表示应丢弃
     */
    private boolean isSelfTransportNoise(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null || !message.contains(SELF_NOISE_URI)) {
            return false;
        }
        for (String marker : SELF_NOISE_MARKERS) {
            if (message.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 日志事件 → 存储记录（字段全部来自真实日志事件）。
     *
     * @param event 日志事件
     * @return 存储记录
     */
    private LogRecord toRecord(ILoggingEvent event) {
        String className = null;
        String methodName = null;
        if (captureCaller) {
            try {
                StackTraceElement[] caller = event.getCallerData();
                if (ArrayUtils.isNotEmpty(caller)) {
                    className = caller[0].getClassName();
                    methodName = caller[0].getMethodName();
                }
            } catch (Throwable ignore) {
                // 取不到调用方信息就留空，不影响主流程
            }
        }
        String message = event.getFormattedMessage();
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            message = message + " | " + throwable.getClassName() + ": " + throwable.getMessage()
                    + "\n" + ThrowableProxyUtil.asString(throwable);
        }
        if (message != null && message.length() > maxMessageLength) {
            message = message.substring(0, maxMessageLength) + "...(truncated)";
        }
        return LogRecord.builder()
                .timestamp(event.getTimeStamp())
                .level(event.getLevel().toString())
                .logger(event.getLoggerName())
                .className(className)
                .methodName(methodName)
                .message(message)
                .traceId(MDC.get(MDC_TRACE_ID))
                .build();
    }

    @Override
    public void start() {
        try {
            if (getContext() == null && LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx) {
                setContext(ctx);
            }
            super.start();
            attachToRoot();
            startWriter();
        } catch (Throwable ignore) {
            // 采集器挂不上也不影响宿主
        }
    }

    /**
     * 启动后台落库线程（幂等，守护线程不阻止宿主退出）。
     */
    private void startWriter() {
        if (!writerRunning.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(this::drainLoop, "hotsport-apm-log-writer");
        thread.setDaemon(true);
        writerThread = thread;
        thread.start();
    }

    /**
     * 后台落库主循环：单线程串行消费队列，单条失败不影响后续，绝不外抛。
     */
    private void drainLoop() {
        while (writerRunning.get() || !queue.isEmpty()) {
            try {
                LogRecord record = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (record == null) {
                    continue;
                }
                ApmStorage storage = HotsportProperties.storage();
                if (storage == null) {
                    continue;
                }
                storage.appendLog(record);
                int drained = 1;
                while (drained < DRAIN_BATCH && (record = queue.poll()) != null) {
                    storage.appendLog(record);
                    drained++;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable ignore) {
                // 落库失败不影响采集主流程
            }
        }
    }

    @Override
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        try {
            detachFromRoot();
        } catch (Throwable ignore) {
            // 忽略
        }
        writerRunning.set(false);
        Thread thread = writerThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(STOP_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        drainRemaining();
        super.stop();
    }

    /**
     * 停止时尽力清空队列中剩余记录（有限时间内，不拖慢停机）。
     */
    private void drainRemaining() {
        ApmStorage storage = HotsportProperties.storage();
        if (storage == null) {
            return;
        }
        LogRecord record;
        while ((record = queue.poll()) != null) {
            try {
                storage.appendLog(record);
            } catch (Throwable ignore) {
                // 忽略
            }
        }
    }

    /**
     * 挂到 root logger。
     */
    private void attachToRoot() {
        ch.qos.logback.classic.Logger root = rootLogger();
        if (root != null && !root.isAttached(this)) {
            root.addAppender(this);
        }
    }

    /**
     * 从 root logger 摘除。
     */
    private void detachFromRoot() {
        ch.qos.logback.classic.Logger root = rootLogger();
        if (root != null) {
            root.detachAppender(this);
        }
    }

    /**
     * 取 root logger。
     *
     * @return root logger，非 logback 环境返回 null
     */
    private ch.qos.logback.classic.Logger rootLogger() {
        if (getContext() instanceof LoggerContext ctx) {
            return ctx.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        }
        return null;
    }
}
