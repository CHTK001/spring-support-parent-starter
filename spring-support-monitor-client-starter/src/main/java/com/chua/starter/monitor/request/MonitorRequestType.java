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

    HEARTBEAT("heartbeat"),
    ;

    private final String name;
}
