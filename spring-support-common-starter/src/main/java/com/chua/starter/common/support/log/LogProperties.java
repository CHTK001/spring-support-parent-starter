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
    private boolean enable = false;

    /**
     * 开启接口日志
     */
    private boolean openInterfaceLog = false;
}
    /**
     * 获取 enable
     *
     * @return enable
     */
    public boolean getEnable() {
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
     * 设置 openInterfaceLog
     *
     * @param openInterfaceLog openInterfaceLog
     */
    public void setOpenInterfaceLog(boolean openInterfaceLog) {
        this.openInterfaceLog = openInterfaceLog;
    }


