package com.chua.starter.oauth.client.support.infomation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 状态码
 *
 * @author CH
 * @since 4.0.0
 */
@Getter
@AllArgsConstructor
public enum Information {
    /**
     * 200
     * @param 200 200
     * @param "" ""
     * @return OK的结果
     */
    OK(200, ""),
    /**
     * 300
     * @param 403 403
     * @param "用户信息不存在/请重新登录!" "用户信息不存在/请重新登录!"
     * @return 认证失败的结果
     */
    AUTHENTICATION_FAILURE(403, "用户信息不存在/请重新登录!"),
    /**
     * 400
     * @param 400 400
     * @param "令牌不存在" "令牌不存在"
     * @return Keynoexist的结果
     */
    KEY_NO_EXIST(400, "令牌不存在"),
    /**
     * 500
     * @param 500 500
     * @param "鉴权服务器异常" "鉴权服务器异常"
     * @return 认证服务端异常的结果
     */
    AUTHENTICATION_SERVER_EXCEPTION(500, "鉴权服务器异常"),
    /**
     * 501
     * @param 501 501
     * @param "鉴权服务器异常" "鉴权服务器异常"
     * @return 认证服务端noexist的结果
     */
    AUTHENTICATION_SERVER_NO_EXIST(501, "鉴权服务器异常"),
    /**
     * 40301 - 签名不匹配
     * @param 40301 40301
     * @param "签名不匹配，请重新登录" "签名不匹配，请重新登录"
     * @return FINGERPRINT_MISMATCH的结果
     */
    FINGERPRINT_MISMATCH(40301, "签名不匹配，请重新登录"),
    /**
     * 40302 - 签名不存在或已过期
     * @param 40302 40302
     * @param "签名不存在或已过期" "签名不存在或已过期"
     * @return FINGERPRINT_MISSING的结果
     */
    FINGERPRINT_MISSING(40302, "签名不存在或已过期"),
    /**
     * -1
     * @param -1 -1
     * @param "其它错误" "其它错误"
     * @return OTHER的结果
     * @param 1 方法入参 1
     */
    OTHER(-1, "其它错误");

    /** 代码 */
    private final int code;
    /** 消息 */
    private final String message;
}
