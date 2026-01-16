package com.chua.starter.pay.support.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 支付分状态
 * @author CH
 * @since 2025/5/26 9:12
 */
@Getter
@RequiredArgsConstructor
public enum PaymentPointState {

    /**
     * 创建中
     */
    CREATED("CREATED", "商户已创建服务订单"),
    /**
     * 进行中
     */
    DOING("DOING", "服务订单进行中"),
    /**
     * 完成
     */
    DONE("DONE", "服务订单完成(终态)"),
    /**
     * 取消
     */
    REVOKED("REVOKED", "商户取消服务订单(终态)"),
    /**
     * 失效
     */
    EXPIRED("EXPIRED", "服务订单已失效，'商户已创建服务订单'状态超过30天未变动，则订单失效(终态)"),
    /**
     * 创建失败
     */
    FAILED("FAILED", "创建失败" );

    private final String code;
    private final String desc;


    public static PaymentPointState parse(String state) {
        for (PaymentPointState value : values()) {
            if (value.code.equals(state)) {
                return value;
            }
        }
        return null;
    }
}
