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
    private static final long serialVersionUID = 1L; // 串行版本uid

    /** 标识 */
    private Long id;

    /** 排序标识 */
    private Long orderId;

    /** 从状态 */
    private String fromState;

    /** 转为状态 */
    private String toState;

    /** 事件 */
    private String event;

    /** 操作符 */
    private String operator;

    /** 评论 */
    private String remark;

    /** 创建时间AT */
    private LocalDateTime createdAt;
}
