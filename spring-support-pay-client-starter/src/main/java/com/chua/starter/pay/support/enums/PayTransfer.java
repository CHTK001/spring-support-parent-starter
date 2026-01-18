package com.chua.starter.pay.support.enums;

import com.chua.starter.swagger.support.SwaggerEnum;
import lombok.Getter;

/**
 * 支付类型
 * @author CH
 * @since 2025/10/14 13:41
 */
@Getter
public enum PayTransfer implements SwaggerEnum {

    PAY_WECHAT_BILLS("PAY_WECHAT_BILLS", "微信转账"),

    ;
    private final String code;
    private final String name;

    PayTransfer(String code, String name) {
        this.code = code;
        this.name = name;
    }


    /**
     * 解析
     * @param code code
     * @return PayTradeType
     */
    public static PayTransfer parse(String code) {
        for (PayTransfer value : values()) {
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
