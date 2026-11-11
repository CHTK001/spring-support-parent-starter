package com.chua.starter.device.spi.model;

import java.time.LocalDateTime;

/**
 * 车辆过车记录。
 *
 * @param passId      记录唯一标识
 * @param gateId      车闸标识
 * @param plateNo     车牌号
 * @param direction   方向：in 入场 / out 出场
 * @param time        过车时间
 * @param photoUrl    抓拍图片地址
 * @param vehicleType 车辆类型
 * @since 4.0.0.51
 * @author CH
 */
public record VehiclePassRecord(
        String passId,
        String gateId,
        String plateNo,
        String direction,
        LocalDateTime time,
        String photoUrl,
        String vehicleType) {
}
