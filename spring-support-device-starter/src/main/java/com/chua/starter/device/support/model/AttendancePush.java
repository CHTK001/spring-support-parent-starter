package com.chua.starter.device.support.model;

import lombok.Data;

/**
 * 考勤记录推送条目（webhook 入站）。
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
public class AttendancePush {

    /**
     * 厂商事件唯一标识（幂等键；为空时按包体摘要生成）
     */
    private String externalId;

    /**
     * 设备 ID
     */
    private String deviceId;

    /**
     * 人员姓名
     */
    private String personName;

    /**
     * 人员编号（工号）
     */
    private String personNo;

    /**
     * 卡号
     */
    private String cardNo;

    /**
     * 方向：in 上班 / out 下班
     */
    private String direction;

    /**
     * 考勤时间，yyyy-MM-dd HH:mm:ss
     */
    private String occurredAt;
}
