// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.appender;

import com.chua.starter.logger.client.LoggerEventCollector;
import com.chua.starter.logger.event.LoggerEvent;
import com.chua.starter.logger.properties.LoggerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Appender 安装编排：按 {@code plugin.logger.frameworks} 选择安装器，
 * 在容器启动后把采集器接入 slf4j 绑定，停机时卸载。
 *
 * <p>SmartLifecycle 相位高于批量线程：启动时批量线程已就绪、停止时先摘 Appender 再停线程，
 * 避免把刚采集的事件丢进已停止的队列。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class LoggerAppenderLifecycle implements SmartLifecycle {

    private final LoggerProperties properties;
    private final LoggerEventCollector collector;

    private volatile boolean running;
    private LoggerAppenderInstaller installed;

    /**
     * @param properties 模块配置
     * @param collector  采集器
     */
    public LoggerAppenderLifecycle(LoggerProperties properties, LoggerEventCollector collector) {
        this.properties = properties;
        this.collector = collector;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        Consumer<LoggerEvent> sink = collector::offer;
        for (LoggerAppenderInstaller candidate : candidates()) {
            if (!candidate.isAvailable()) {
                log.debug("[logger] 绑定不可用，跳过: {}", candidate.framework());
                continue;
            }
            if (candidate.install(sink)) {
                installed = candidate;
                break;
            }
        }
        if (installed == null) {
            log.warn("[logger] 未找到可安装的 slf4j 绑定（frameworks={}），日志采集未生效", properties.getFrameworks());
        }
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        if (installed != null) {
            installed.uninstall();
            installed = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 高于 LoggerEventBatcher 的相位：启动在后、停止在前
        return Integer.MAX_VALUE;
    }

    private List<LoggerAppenderInstaller> candidates() {
        // 先探测绑定 classpath 再实例化：绑定缺席时连安装器类都不加载，杜绝 NCDFE
        List<LoggerAppenderInstaller> all = new ArrayList<>(2);
        if (present(LogbackAppenderInstaller.PROBE_CLASS)) {
            all.add(new LogbackAppenderInstaller());
        }
        if (present(Log4j2AppenderInstaller.PROBE_CLASS)) {
            all.add(new Log4j2AppenderInstaller());
        }
        String frameworks = properties.getFrameworks() == null ? "auto" : properties.getFrameworks().trim().toLowerCase();
        if ("auto".equals(frameworks)) {
            return all;
        }
        if ("none".equals(frameworks)) {
            return List.of();
        }
        List<LoggerAppenderInstaller> selected = new ArrayList<>(1);
        for (LoggerAppenderInstaller installer : all) {
            if (installer.framework().equals(frameworks)) {
                selected.add(installer);
            }
        }
        return selected;
    }

    /**
     * 绑定探测类是否在 classpath 上。
     *
     * @param className 探测类全限定名
     * @return 存在返回 true
     */
    private boolean present(String className) {
        try {
            Class.forName(className, false, LoggerAppenderLifecycle.class.getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
