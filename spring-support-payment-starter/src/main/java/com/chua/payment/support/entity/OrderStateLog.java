package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流转日志实体类
 *
 * @since 2026-03-18
 * @author CH
 */
@Data
@TableName("pay_order_state_log")
public class OrderStateLog {

    @TableId(value = "pay_order_state_log_id", type = IdType.AUTO)
    /** ID */
    private Long id;

    @TableField("pay_order_state_log_order_id")
    /** 排序ID */
    private Long orderId;

    @TableField("pay_order_state_log_from_state")
    /** From状态 */
    private String fromState;

    @TableField("pay_order_state_log_to_state")
    /** TO状态 */
    private String toState;

    @TableField("pay_order_state_log_event")
    /** 事件 */
    private String event;

    @TableField("pay_order_state_log_operator")
    /** Operator */
    private String operator;

    @TableField("pay_order_state_log_remark")
    /** Remark */
    private String remark;

    @TableField(value = "pay_order_state_log_created_at", fill = FieldFill.INSERT)
    /** 创建时间AT */
    private LocalDateTime createdAt;
}
