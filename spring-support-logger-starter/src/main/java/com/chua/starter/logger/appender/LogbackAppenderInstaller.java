// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.appender;

import ch.qos.logback.classic.LoggerContext;
import com.chua.starter.logger.event.LoggerEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Logback 绑定安装器：把 {@link LogbackBridgeAppender} 挂到根 Logger。
 *
 * <p>仅当当前 slf4j 绑定确实是 Logback（{@code LoggerFactory.getILoggerFactory()}
 * 返回 {@link LoggerContext}）时才安装，避免 classpath 上同时存在多个绑定时挂错。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class LogbackAppenderInstaller implements LoggerAppenderInstaller {

    /**
     * classpath 探测类
     */
    public static final String PROBE_CLASS = "ch.qos.logback.classic.LoggerContext";

    /**
     * 桥接 Appender 名
     */
    public static final String APPENDER_NAME = "LoggerEventCollectorAppender";

    private LogbackBridgeAppender appender;

    @Override
    public String framework() {
        return "logback";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName(PROBE_CLASS, false, LogbackAppenderInstaller.class.getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean install(Consumer<LoggerEvent> sink) {
        try {
            org.slf4j.ILoggerFactory factory = org.slf4j.LoggerFactory.getILoggerFactory();
            if (!(factory instanceof LoggerContext)) {
                log.debug("[logger] slf4j 绑定不是 Logback（{}），跳过 Logback 安装", factory.getClass().getName());
                return false;
            }
            LoggerContext context = (LoggerContext) factory;
            appender = new LogbackBridgeAppender(APPENDER_NAME, sink);
            appender.setContext(context);
            appender.start();
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
            log.info("[logger] Logback 桥接 Appender 已挂载到 ROOT");
            return true;
        } catch (Throwable e) {
            log.warn("[logger] Logback Appender 安装失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void uninstall() {
        try {
            if (appender != null) {
                org.slf4j.ILoggerFactory factory = org.slf4j.LoggerFactory.getILoggerFactory();
                if (factory instanceof LoggerContext) {
                    ((LoggerContext) factory).getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                            .detachAppender(appender.getName());
                }
                appender.stop();
                appender = null;
            }
        } catch (Throwable e) {
            log.debug("[logger] Logback Appender 卸载失败（忽略）: {}", e.getMessage());
        }
    }
}
