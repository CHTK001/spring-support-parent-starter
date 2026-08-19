package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态流转日志视图对象
 * @author CH
 */
@Data
public class OrderStateLogVO implements Serializable {

    /** ID */
    private Long id;

    /** 排序ID */
    private Long orderId;

    /** From状态 */
    private String fromState;

    /** TO状态 */
    private String toState;

    /** 事件 */
    private String event;

    /** Operator */
    private String operator;

    /** Remark */
    private String remark;

    /** 创建时间AT */
    private LocalDateTime createdAt;
}

