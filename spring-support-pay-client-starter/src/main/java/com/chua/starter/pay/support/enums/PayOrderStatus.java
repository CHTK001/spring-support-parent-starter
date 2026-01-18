package com.chua.starter.pay.support.enums;

import com.chua.starter.swagger.support.SwaggerEnum;
import lombok.Getter;

import java.io.Serializable;

/**
 * 订单状态
 * @author CH
 * @since 2025/10/14 13:11
 */
@Getter
public enum PayOrderStatus implements SwaggerEnum, Serializable {

    /**
     * 订单不存在
     */
    PAY_NOT_EXIST("9999", "订单不存在"),
    /**
     * 创建
     */
    PAY_CREATE("0000", "创建"),

    /**
     * 创建失败
     */
    PAY_CREATE_FAILED("0100", "订单创建失败"),
    /**
     * 待支付
     */
    PAY_WAITING("1000", "待支付"),

    /**
     * 支付中
     */
    PAY_PAYING("1100", "支付中"),
    /**
     * 支付成功
     */
    PAY_SUCCESS("2000", "支付成功"),
    /**
     * 订单超时
     */
    PAY_TIMEOUT("3000", "订单超时"),

    /**
     * 订单取消
     */
    PAY_CANCEL_SUCCESS("4000", "订单取消"),

    /**
     * 订单关闭
     */
    PAY_CLOSE_SUCCESS("5000", "订单关闭"),

    /**
     * 订单退款
     */
    PAY_REFUND_SUCCESS("6000", "退款成功"),

    /**
     * 部分退款
     */
    PAY_REFUND_PART_SUCCESS("6001", "订单部分退款"),
    /**
     * 订单退款失败
     */
    PAY_REFUND_WAITING("6002", "正在退款"),
    ;

    private final String code;
    private final String name;

    /**
     * 构造函数
     *
     * @param code 状态码
     * @param name 状态名称
     */
    PayOrderStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }



    /**
     * 获取枚举
     * @param code code
     * @return 枚举
     */
    public static PayOrderStatus parse(String code) {
        for (PayOrderStatus value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    @Override
    public Object getCode() {
        return code;
    }

    @Override
    public String getName() {
        return name;
    }

}
