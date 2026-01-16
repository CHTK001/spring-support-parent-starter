package com.chua.starter.oauth.client.support.infomation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 状态码
 *
 * @author CH
 */
@Getter
@AllArgsConstructor
public enum Information {
    /**
     * 200
     */
    OK(200, ""),
    /**
     * 300
     */
    AUTHENTICATION_FAILURE(403, "用户信息不存在/请重新登录!"),
    /**
     * 400
     */
    KEY_NO_EXIST(400, "令牌不存在"),
    /**
     * 500
     */
    AUTHENTICATION_SERVER_EXCEPTION(500, "鉴权服务器异常"),
    /**
     * 501
     */
    AUTHENTICATION_SERVER_NO_EXIST(501, "鉴权服务器异常"),
    /**
     * 40301 - 签名不匹配
     */
    FINGERPRINT_MISMATCH(40301, "签名不匹配，请重新登录"),
    /**
     * 40302 - 签名不存在或已过期
     */
    FINGERPRINT_MISSING(40302, "签名不存在或已过期"),
    /**
     * -1
     */
    OTHER(-1, "其它错误");

    private final int code;
    private final String message;
}
