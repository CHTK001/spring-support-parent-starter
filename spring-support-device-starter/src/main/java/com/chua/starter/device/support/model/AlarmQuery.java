package com.chua.starter.device.support.model;

import lombok.Data;

/**
 * 设备告警查询条件。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class AlarmQuery {

    /** 页码，从 1 开始 */
    private Integer pageNo = 1;

    /** 每页条数 */
    // [P3C A20 豁免]
    private Integer pageSize = 20;

    /** 级别：critical / major / minor / info */
    private String level;

    /** 处理状态：unhandled / handling / resolved / ignored */
    private String status;

    /** 设备 ID */
    private String deviceId;

    /** 告警类型 */
    private String type;

    /** 关键词（标题 / 详情 / 来源模糊匹配） */
    private String keyword;

    /** 起始时间，支持 yyyy-MM-dd HH:mm:ss / ISO 格式 */
    private String start;

    /** 结束时间 */
    private String end;
}
