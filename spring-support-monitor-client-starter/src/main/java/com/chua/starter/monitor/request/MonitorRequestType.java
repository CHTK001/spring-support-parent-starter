package com.chua.starter.monitor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 监视器请求类型
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@AllArgsConstructor
@Getter
public enum MonitorRequestType {

    /**
     * 心跳事件
     */
    HEARTBEAT("heartbeat"),

    /**
     * 报告事件
     */
    REPORT("report"),

    ;

    private final String name;
}
