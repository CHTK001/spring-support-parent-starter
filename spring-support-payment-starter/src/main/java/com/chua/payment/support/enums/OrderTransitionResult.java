package com.chua.payment.support.enums;

/**
 * 订单状态转换结果
 */
public enum OrderTransitionResult {

    /**
     * 当前节点完成了状态转换
     */
    APPLIED,

    /**
     * 其他节点已经完成了等价转换
     */
    DUPLICATED,

    /**
     * 当前状态不允许执行该事件
     */
    REJECTED
}
