package com.chua.starter.device.support.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 告警批量处理请求
 *
 * @since 4.0.0.52
 * @author CH
 */
@Data
public class AlarmBatchStatusRequest implements Serializable {

    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 告警 ID 集合
     */
    private List<String> ids;

    /**
     * 目标状态：unhandled / handling / resolved / ignored
     */
    private String status;

    /**
     * 处理人，可空
     */
    private String handler;
}
