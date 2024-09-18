package com.chua.report.client.starter.report.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * JVM信息类，用于封装JVM的相关监控数据
 * 包括运行时间、线程数量、内存使用情况以及类加载情况等
 * @author CH
 * @since 2024/9/18
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class JvmEvent extends TimestampEvent{

    /**
     * JVM运行时间（毫秒）
     */
    private long elapsedTime;
    /**
     * 当前线程数量
     */
    private long threadCount;
    /**
     * 最大内存容量（字节）
     */
    private long maxMemory;
    /**
     * 可用内存容量（字节）
     */
    private long freeMemory;
    /**
     * 当前加载到JVM的类的数量
     */
    private long classLoadedCount;
}
