package com.chua.payment.support.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态流转日志视图对象
 */
@Data
public class OrderStateLogVO implements Serializable {

    private Long id;

    private Long orderId;

    private String fromState;

    private String toState;

    private String event;

    private String operator;

    private String remark;

    private LocalDateTime createdAt;
}
