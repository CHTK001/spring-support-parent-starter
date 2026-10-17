package com.chua.starter.device.spi.model;

import java.time.LocalDateTime;

/**
 * 录像文件信息
 *
 * @param fileId    录像文件唯一标识
 * @param channelId 所属通道标识
 * @param startTime 录像起始时间
 * @param endTime   录像结束时间
 * @param size      文件大小（字节）
 * @since 4.0.0.42
 * @author CH
 */
public record RecordFile(
        String fileId,
        String channelId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        long size) {
}
