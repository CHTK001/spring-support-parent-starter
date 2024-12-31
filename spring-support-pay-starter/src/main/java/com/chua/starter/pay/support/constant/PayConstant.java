package com.chua.starter.pay.support.constant;

/**
 * 支付常量
 * @author CH
 * @since 2024/12/30
 */
public interface PayConstant {


    /**
     * 订单创建前缀
     */
     String ORDER_CREATE_PREFIX = "order:create:";
     /**
     * 订单签名前缀
     */
     String ORDER_SIGN_PREFIX = "order:sign:";

     /**
     * 订单退款前缀
     */
     String ORDER_REFUND_PREFIX = "order:refund:";

     /**
     * 订单回调前缀
     */
     String ORDER_CALLBACK_PREFIX = "order:callback:";
}
