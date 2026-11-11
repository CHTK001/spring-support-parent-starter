package com.chua.starter.device.spi.model;

/**
 * 车闸（道闸）点位。
 *
 * @param gateId    车闸唯一标识
 * @param name      车闸名称
 * @param deviceId  所属设备
 * @param channelNo 通道号
 * @param direction 方向：in 入口 / out 出口 / both 双向
 * @param status    状态：open 开 / closed 关 / offline 离线 / unknown
 * @since 4.0.0.51
 * @author CH
 */
public record BarrierGateInfo(
        String gateId,
        String name,
        String deviceId,
        Integer channelNo,
        String direction,
        String status) {
}
