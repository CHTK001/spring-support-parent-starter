package com.chua.starter.device.support.model;

import lombok.Data;

/**
 * 过车记录推送条目（webhook 入站）。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class VehiclePassPush {

    /**
     * 厂商事件唯一标识（幂等键；为空时按包体摘要生成）
     */
    private String externalId;

    /**
     * 设备 ID
     */
    private String deviceId;

    /**
     * 车闸标识
     */
    private String gateId;

    /**
     * 车牌号
     */
    private String plateNo;

    /**
     * 方向：in 入场 / out 出场
     */
    private String direction;

    /**
     * 过车时间，yyyy-MM-dd HH:mm:ss
     */
    private String passTime;

    /**
     * 抓拍图片地址
     */
    private String photoUrl;

    /**
     * 车辆类型
     */
    private String vehicleType;
}
