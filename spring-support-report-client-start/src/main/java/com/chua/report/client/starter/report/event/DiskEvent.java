package com.chua.report.client.starter.report.event;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 磁盘信息
 * 该类用于封装磁盘的相关信息，包括磁盘名称、型号、读取字节数、写入字节数以及时间戳
 * @author CH
 * @since 2024/9/18
 */
@Data
public class DiskEvent {
    /**
     * 磁盘目录名称
     */
    private String dirName;
    /**
     * 磁盘系统的类型名称
     */
    private String sysTypeName;
    /**
     * 磁盘的类型名称
     */
    private String typeName;
    /**
     * 磁盘的总空间
     */
    private String total;
    /**
     * 磁盘的可用空间
     */
    private String free;
    /**
     * 磁盘的已用空间
     */
    private String used;
    /**
     * 磁盘使用率，表示已用空间占总空间的比例
     */
    private double usage;
    /**
     * 时间戳，记录磁盘读写状态的时间
     */
    private long timeStamp;

}
