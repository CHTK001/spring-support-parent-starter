package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流转日志实体类
 *
 * @author CH
 * @since 2026-03-18
 */
@Data
@TableName("pay_order_state_log")
public class OrderStateLog {

    @TableId(value = "pay_order_state_log_id", type = IdType.AUTO)
    private Long id;

    @TableField("pay_order_state_log_order_id")
    private Long orderId;

    @TableField("pay_order_state_log_from_state")
    private String fromState;

    @TableField("pay_order_state_log_to_state")
    private String toState;

    @TableField("pay_order_state_log_event")
    private String event;

    @TableField("pay_order_state_log_operator")
    private String operator;

    @TableField("pay_order_state_log_remark")
    private String remark;

    @TableField(value = "pay_order_state_log_created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
