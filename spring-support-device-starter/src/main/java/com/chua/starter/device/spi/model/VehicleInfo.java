package com.chua.starter.device.spi.model;

import java.time.LocalDateTime;

/**
 * 车辆档案。
 *
 * @param vehicleId   车辆唯一标识（厂商侧或本地主键）
 * @param plateNo     车牌号
 * @param plateColor  车牌颜色
 * @param vehicleType 车辆类型（小型车/大型车/新能源等）
 * @param ownerName   车主姓名
 * @param ownerPhone  车主电话
 * @param listType    名单类型：white 白名单 / black 黑名单 / normal 普通
 * @param validFrom   有效期起
 * @param validTo     有效期止
 * @param vendor      厂商标识
 * @since 4.0.0.51
 * @author CH
 */
public record VehicleInfo(
        String vehicleId,
        String plateNo,
        String plateColor,
        String vehicleType,
        String ownerName,
        String ownerPhone,
        String listType,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String vendor) {
}
