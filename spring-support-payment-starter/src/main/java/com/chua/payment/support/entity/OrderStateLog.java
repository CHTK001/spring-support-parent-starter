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
@TableName("order_state_log")
public class OrderStateLog {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单ID
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
     * 触发事件
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
