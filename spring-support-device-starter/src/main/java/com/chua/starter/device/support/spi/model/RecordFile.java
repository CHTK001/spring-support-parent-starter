package com.chua.starter.device.spi.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 录像文件信息
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
public class RecordFile {

    /**
     * 录像文件唯一 ID
     */
    private String fileId;

    /**
     * 所属通道 ID
     */
    private String channelId;

    /**
     * 录像起始时间
     */
    private LocalDateTime startTime;

    /**
     * 录像结束时间
     */
    private LocalDateTime endTime;

    /**
     * 文件大小（字节）
     */
    private long size;
}