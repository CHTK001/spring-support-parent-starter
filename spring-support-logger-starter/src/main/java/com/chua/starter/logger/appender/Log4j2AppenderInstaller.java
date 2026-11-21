// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.logger.appender;

import com.chua.starter.logger.event.LoggerEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.util.function.Consumer;

/**
 * Log4j2 绑定安装器：把 {@link Log4j2BridgeAppender} 挂到 ROOT LoggerConfig。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class Log4j2AppenderInstaller implements LoggerAppenderInstaller {

    /**
     * classpath 探测类
     */
    public static final String PROBE_CLASS = "org.apache.logging.log4j.core.LoggerContext";

    /**
     * 桥接 Appender 名
     */
    public static final String APPENDER_NAME = "LoggerEventCollectorAppender";

    private Log4j2BridgeAppender appender;

    @Override
    public String framework() {
        return "log4j2";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName(PROBE_CLASS, false, Log4j2AppenderInstaller.class.getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public boolean install(Consumer<LoggerEvent> sink) {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            appender = new Log4j2BridgeAppender(APPENDER_NAME, sink);
            appender.start();
            LoggerConfig root = context.getConfiguration().getRootLogger();
            root.addAppender(appender, Level.ALL, null);
            context.updateLoggers();
            log.info("[logger] Log4j2 桥接 Appender 已挂载到 ROOT");
            return true;
        } catch (Throwable e) {
            log.warn("[logger] Log4j2 Appender 安装失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void uninstall() {
        try {
            if (appender != null) {
                LoggerContext context = (LoggerContext) LogManager.getContext(false);
                context.getConfiguration().getRootLogger().removeAppender(appender.getName());
                context.updateLoggers();
                appender.stop();
                appender = null;
            }
        } catch (Throwable e) {
            log.debug("[logger] Log4j2 Appender 卸载失败（忽略）: {}", e.getMessage());
        }
    }
}
