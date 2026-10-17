package com.chua.payment.support.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态流转日志视图对象
 *
 * @param id        标识
 * @param orderId   订单标识
 * @param fromState 原状态
 * @param toState   目标状态
 * @param event     事件描述
 * @param operator  操作人
 * @param remark    备注
 * @param createdAt 创建时间
 * @author CH
 * @since 4.0.0
 */
public record OrderStateLogVO(
        Long id,
        Long orderId,
        String fromState,
        String toState,
        String event,
        String operator,
        String remark,
        LocalDateTime createdAt) implements Serializable {

    private static final long serialVersionUID = 1L; // 序列化版本uid
}
