package com.chua.starter.common.support.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 链路日志汇聚 Appender（logback 适配层，薄）。
 *
 * <p>把 logback 的 {@link ILoggingEvent} 转成 {@link TraceLogPortal.Event} 交给纯 JDK
 * 内核 {@link TraceLogPortal} 按 traceId 归组；汇聚块写到独立文件，MDC 只在块头
 * 打印一次，正文干净。现有 CONSOLE/FILE/ASYNC 逐行 appender 完全不受影响（实时性与
 * 崩溃兜底仍在），portal 只是「按链路阅读」的附加视图。</p>
 *
 * <p>默认不注册：只在 {@code plugin.log.portal.enabled=true} 时由
 * {@link TracePortalConfiguration} 挂到 root。任何异常内部吞掉，绝不阻断宿主、绝不
 * 再打日志（防递归）。写文件用单一 writer + 重入锁串行，避免多线程交错。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public class TracePortalAppender extends AppenderBase<ILoggingEvent> {

    /**
     * 默认 appender 名。
     */
    public static final String APPENDER_NAME = "TRACE_PORTAL";

    /**
     * 防重入：append 内部不得再触发任何日志。
     */
    private final ThreadLocal<Boolean> inAppend = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 写文件串行锁。
     */
    private final ReentrantLock writeLock = new ReentrantLock();

    /**
     * 汇聚块输出文件 writer（延迟创建）。
     */
    private Writer writer;

    /**
     * 输出文件路径。
     */
    private Path outputPath;

    /**
     * 内核配置（可空，空则取默认）。
     */
    private TraceLogPortal.Config portalConfig;

    /**
     * 构造。
     */
    public TracePortalAppender() {
        setName(APPENDER_NAME);
    }

    /**
     * 设置输出文件路径（由配置层调用）。
     *
     * @param path 路径
     */
    public void setOutputPath(String path) {
        this.outputPath = Paths.get(path);
    }

    /**
     * 设置内核配置（由配置层调用）。
     *
     * @param config 配置
     */
    public void setPortalConfig(TraceLogPortal.Config config) {
        this.portalConfig = config;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null || Boolean.TRUE.equals(inAppend.get())) {
            return;
        }
        inAppend.set(Boolean.TRUE);
        try {
            TraceLogPortal.append(toEvent(event));
        } catch (Throwable ignore) {
            // 观测组件绝不阻断宿主，且不能再打日志（会递归）
        } finally {
            inAppend.set(Boolean.FALSE);
        }
    }

    /**
     * logback 事件 → 纯数据快照。
     *
     * @param event logback 事件
     * @return 事件快照
     */
    private TraceLogPortal.Event toEvent(ILoggingEvent event) {
        String throwableText = null;
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            throwableText = ThrowableProxyUtil.asString(throwable);
        }
        Map<String, String> mdc = event.getMDCPropertyMap();
        Map<String, String> snapshot = (mdc == null || mdc.isEmpty())
                ? Map.of() : new HashMap<>(mdc);
        return new TraceLogPortal.Event(
                event.getTimeStamp(),
                event.getLevel() == null ? "" : event.getLevel().toString(),
                event.getLoggerName(),
                event.getThreadName(),
                event.getFormattedMessage(),
                throwableText,
                snapshot);
    }

    /**
     * 汇聚块写出（由内核 sink 回调）。
     *
     * @param block 汇聚块文本
     */
    private void writeBlock(String block) {
        writeLock.lock();
        try {
            ensureWriter();
            if (writer == null) {
                return;
            }
            writer.write(block);
            writer.flush();
        } catch (Throwable ignore) {
            // 写失败也不能影响宿主；下次 writeBlock 会重建 writer
            closeWriterQuietly();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 延迟创建 UTF-8 文件 writer。
     *
     * @throws IOException 创建失败
     */
    private void ensureWriter() throws IOException {
        if (writer != null) {
            return;
        }
        Path path = outputPath;
        if (path == null) {
            path = Paths.get(System.getProperty("logging.file.path", "./logs"), "trace-portal.log");
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        writer = new OutputStreamWriter(
                Files.newOutputStream(path,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                StandardCharsets.UTF_8);
    }

    /**
     * 安静关闭 writer。
     */
    private void closeWriterQuietly() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (Throwable ignore) {
            // 忽略
        } finally {
            writer = null;
        }
    }

    @Override
    public void start() {
        try {
            if (getContext() == null && LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx) {
                setContext(ctx);
            }
            // 启用内核：sink 指向本 appender 的写文件方法
            TraceLogPortal.configure(portalConfig, this::writeBlock);
            super.start();
            attachToRoot();
        } catch (Throwable ignore) {
            // 挂不上也不影响宿主
        }
    }

    @Override
    public void stop() {
        try {
            detachFromRoot();
            TraceLogPortal.shutdown();
        } catch (Throwable ignore) {
            // 忽略
        }
        closeWriterQuietly();
        super.stop();
    }

    /**
     * 挂到 root logger。
     */
    private void attachToRoot() {
        Logger root = rootLogger();
        if (root != null && !root.isAttached(this)) {
            root.addAppender(this);
        }
    }

    /**
     * 从 root logger 摘除。
     */
    private void detachFromRoot() {
        Logger root = rootLogger();
        if (root != null) {
            root.detachAppender(this);
        }
    }

    /**
     * 取 root logger。
     *
     * @return root logger，非 logback 环境返回 null
     */
    private Logger rootLogger() {
        if (getContext() instanceof LoggerContext ctx) {
            return ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        }
        return null;
    }
}
