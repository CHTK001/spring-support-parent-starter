package com.chua.starter.device.support.model;

import lombok.Data;

/**
 * 告警事件推送项（webhook 入站）。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class AlarmPush {

    /** 外部事件 ID；留空时取包体摘要做幂等键 */
    private String externalId;

    /** 所属设备 ID */
    private String deviceId;

    /** 通道号 */
    private Integer channelNo;

    /** 告警类型：MOTION / IO_ALARM / OFFLINE / ACCESS / OTHER */
    private String type;

    /** 级别：critical / major / minor / info；留空默认 info */
    private String level;

    /** 告警标题 */
    private String title;

    /** 告警详情 */
    private String detail;

    /** 发生时间，支持 yyyy-MM-dd HH:mm:ss / ISO 格式；留空取当前时间 */
    private String occurredAt;
}
