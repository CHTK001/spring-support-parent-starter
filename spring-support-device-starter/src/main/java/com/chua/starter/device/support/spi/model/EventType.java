package com.chua.starter.device.spi.model;

/**
 * 事件类型枚举
 *
 * @author CH
 * @since 4.0.0.42
 */
public enum EventType {

    /**
     * 移动侦测
     */
    MOTION,

    /**
     * IO 报警
     */
    IO_ALARM,

    /**
     * 门禁事件
     */
    ACCESS,

    /**
     * 温度报警
     */
    TEMP,

    /**
     * 网络异常
     */
    NETWORK,

    /**
     * 其他
     */
    OTHER
}