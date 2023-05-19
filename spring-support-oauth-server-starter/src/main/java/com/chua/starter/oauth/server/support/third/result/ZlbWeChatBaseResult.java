package com.chua.starter.oauth.server.support.third.result;

import lombok.Data;

/**
 * 浙里办微信小程序结果集
 *
 * @author CH
 */
@Data
public class ZlbWeChatBaseResult<T> {
    /**
     * 错误码
     */
    private String errorCode;
    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 请求是否成功
     */
    private Boolean success;

    /**
     * 响应体
     */
    private T data;
}
