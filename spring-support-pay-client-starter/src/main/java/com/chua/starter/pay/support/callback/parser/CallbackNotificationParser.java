package com.chua.starter.pay.support.callback.parser;


import com.chua.starter.pay.support.callback.WechatOrderCallbackResponse;

/**
 * 通知解析
 *
 * @author CH
 * @since 2024/12/31
 */
public interface CallbackNotificationParser {


    /**
     * 解析
     *
     * @return 响应
     */
    WechatOrderCallbackResponse parse();
}
