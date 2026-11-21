package com.chua.starter.logger.appender;

import com.chua.starter.logger.event.LoggerEvent;

/**
 * slf4j 绑定探测与 Appender 安装契约。
 *
 * <p>本模块不做日志配置文件改造，而是在运行期把桥接 Appender 挂到当前
 * slf4j 绑定（Logback / Log4j2）的根 Logger 上。每种绑定一个安装器实现。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface LoggerAppenderInstaller {

    /**
     * 绑定标识
     *
     * @return logback | log4j2
     */
    String framework();

    /**
     * 绑定是否在 classpath 上（粗探；安装时还会做绑定一致性校验）
     *
     * @return 可用返回 true
     */
    boolean isAvailable();

    /**
     * 安装桥接 Appender。
     *
     * @param sink 事件出口
     * @return 安装成功返回 false 表示绑定不匹配或安装失败
     */
    boolean install(java.util.function.Consumer<LoggerEvent> sink);

    /**
     * 卸载桥接 Appender。
     */
    void uninstall();
}
