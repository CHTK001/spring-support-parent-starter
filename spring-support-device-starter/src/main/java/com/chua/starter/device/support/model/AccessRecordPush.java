package com.chua.starter.device.support.model;

import lombok.Data;

/**
 * 门禁通行记录推送项（webhook 入站）。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class AccessRecordPush {

    /** 外部事件 ID；留空时取包体摘要做幂等键 */
    private String externalId;

    /** 所属设备 ID */
    private String deviceId;

    /** 门标识 */
    private String doorId;

    /** 卡号 */
    private String cardNo;

    /** 人员姓名 */
    private String userName;

    /** 事件类型 */
    private String eventType;

    /** 进出方向：in / out */
    private String direction;

    /** 抓拍照片地址 */
    private String photoUrl;

    /** 刷卡时间，支持 yyyy-MM-dd HH:mm:ss / ISO 格式；留空取当前时间 */
    private String occurredAt;
}
