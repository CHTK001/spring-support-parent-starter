// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.exception;

/**
 * o认证异常
 * <p>
 * 携带 http 状态码，令全局异常处理能区分「未登录」（401，前端应跳登录页）与
 * 「无权限 / 只读拦截」（403，前端仅提示），避免统一退化为 500 系统繁忙。
 *
 * @author CH
 * @since 4.0.0
 */
public class OauthException extends RuntimeException {

    /**
     * http 状态码
     */
    private final int httpStatus;

    /**
     * 创建 o认证异常 实例，默认按 403 禁止访问处理
     * @param message 消息
     */
    public OauthException(String message) {
        this(message, 403);
    }

    /**
     * 创建 o认证异常 实例
     * @param message 消息
     * @param httpStatus http 状态码
     */
    public OauthException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * 获取 http 状态码
     *
     * @return 获取http状态码的结果
     */
    public int getHttpStatus() {
        return httpStatus;
    }
}