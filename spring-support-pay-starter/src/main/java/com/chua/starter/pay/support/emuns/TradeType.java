package com.chua.starter.pay.support.emuns;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易类型
 * @author CH
 * @since 2024/12/30
 */
@Getter
@AllArgsConstructor
public enum TradeType {

    /**
     * 微信扫码
     */
    WECHAT_NATIVE("wechat_native"),
    /**
     * 微信H5
     */
    WECHAT_H5("wechat_h5"),

    /**
     * 微信jsapi
     */
    WECHAT_JS_API("wechat_js_api"),


    ;

    private final String name;
}
