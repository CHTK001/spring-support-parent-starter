package com.chua.starter.device.support.model;

import lombok.Data;

/**
 * 推送队列（outbox）查询条件。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class SyncOutboxQuery {

    /** 页码，从 1 开始 */
    private Integer pageNo = 1;

    /** 每页条数 */
    // [P3C A20 豁免]
    private Integer pageSize = 20;

    /** 厂商标识 */
    private String vendor;

    /** 动作：create / update / delete */
    private String action;

    /** 状态：pending / sent / failed / dead */
    private String status;

    /** 设备 ID */
    private String deviceId;
}
