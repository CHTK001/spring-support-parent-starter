package com.chua.starter.device.support.model;

import lombok.Data;

/**
 * 同步执行记录查询条件。
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
public class SyncRecordQuery {

    /** 页码，从 1 开始 */
    private Integer pageNo = 1;

    /** 每页条数 */
    // [P3C A20 豁免]
    private Integer pageSize = 20;

    /** 厂商标识 */
    private String vendor;

    /** 方向：pull / push / preview */
    private String mode;

    /** 阶段：devices / organizations / persons / device / device-delete */
    private String phase;

    /** 状态：running / success / partial / failed */
    private String status;

    /** 起始时间，支持 yyyy-MM-dd HH:mm:ss / ISO 格式 */
    private String start;

    /** 结束时间 */
    private String end;
}
