package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态流转日志视图对象
 * @author CH
 * @since 4.0.0
 */
@Data
public class OrderStateLogVO implements Serializable {
    private static final long serialVersionUID = 1L; // 序列化版本uid

    /**
     * 标识
     */
    private Long id;

    /**
     * 订单标识
     */
    private Long orderId;

    /**
     * 原状态
     */
    private String fromState;

    /**
     * 目标状态
     */
    private String toState;

    /**
     * 事件描述
     */
    private String event;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
