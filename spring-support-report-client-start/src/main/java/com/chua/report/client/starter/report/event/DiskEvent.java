package com.chua.report.client.starter.report.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 磁盘信息
 * 该类用于封装磁盘的相关信息，包括磁盘名称、型号、读取字节数、写入字节数以及时间戳
 * @author CH
 * @since 2024/9/18
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DiskEvent extends TimestampEvent{
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
    private double total;
    /**
     * 磁盘的可用空间
     */
    private double free;
    /**
     * 磁盘的已用空间
     */
    private double used;
    /**
     * 磁盘的总空间，以文本形式表示
     */
    private String totalText;

    /**
     * 磁盘已用空间，以文本形式表示
     */
    private String usedText;
    /**
     * 磁盘可用空间，以文本形式表示
     */
    private String freeText;
    /**
     * 磁盘使用率，表示已用空间占总空间的比例
     */
    private BigDecimal usedPercent;

}
