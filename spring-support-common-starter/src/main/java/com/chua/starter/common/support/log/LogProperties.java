package com.chua.starter.common.support.log;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志配置属性
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/01
 */
@ConfigurationProperties(prefix = "plugin.log", ignoreInvalidFields = true)
public class LogProperties {

    /**
     * 日志开启
     */
    private boolean enable = true;

    /**
     * 开启接口日志
     */
    private boolean openInterfaceLog = false;

    /**
     * 慢请求阈值（毫秒），超过此值将打印 WARN 日志，默认 3000ms
     */
    private long slowRequestThresholdMs = 3000;

    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
        return enable;
    }

    /**
     * 判断是否启用
     *
     * @return enable
     */
    public boolean isEnable() {
        return enable;
    }

    /**
     * 设置 enable
     *
     * @param enable enable
     */
    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    /**
     * 获取 openInterfaceLog
     *
     * @return openInterfaceLog
     */
    public boolean getOpenInterfaceLog() {
        return openInterfaceLog;
    }

    /**
     * 判断是否开启接口日志
     *
     * @return openInterfaceLog
     */
    public boolean isOpenInterfaceLog() {
        return openInterfaceLog;
    }

    /**
     * 设置 openInterfaceLog
     *
     * @param openInterfaceLog openInterfaceLog
     */
    public void setOpenInterfaceLog(boolean openInterfaceLog) {
        this.openInterfaceLog = openInterfaceLog;
    }

    /**
     * 获取慢请求阈值（毫秒）
     *
     * @return slowRequestThresholdMs
     */
    public long getSlowRequestThresholdMs() {
        return slowRequestThresholdMs;
    }

    /**
     * 设置慢请求阈值（毫秒）
     *
     * @param slowRequestThresholdMs 阈值
     */
    public void setSlowRequestThresholdMs(long slowRequestThresholdMs) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }
}
