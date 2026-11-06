// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.account.common;

import lombok.Getter;

/**
 * 账号与渠道模块的机器可读错误码。
 *
 * <p>本模块自带一套最小错误码词表，不复用也不依赖 AI 模块的同名类：
 * 账号、渠道、临时邮件短信是独立能力，模块之间只通过 SPI 交互。</p>
 *
 * <p>每个错误码绑定一个 HTTP 状态码，由全局异常处理器转换为对应响应状态。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
public enum ApiErrorCode {

    /**
     * 未登录或身份失效。
     */
    UNAUTHORIZED("UNAUTHORIZED", 401),

    /**
     * 无权操作该账号/渠道。
     */
    FORBIDDEN("FORBIDDEN", 403),

    /**
     * 账号或渠道不存在。
     */
    NOT_FOUND("NOT_FOUND", 404),

    /**
     * 参数非法。
     */
    INVALID_PARAM("INVALID_PARAM", 400),

    /**
     * 资源冲突（编码重复、默认账号重复等）。
     */
    CONFLICT("CONFLICT", 409),

    /**
     * 上游服务异常。
     */
    UPSTREAM_ERROR("UPSTREAM_ERROR", 502),

    /**
     * 模块内部错误。
     */
    INTERNAL_ERROR("INTERNAL_ERROR", 500);

    /**
     * 机器可读错误码。
     */
    private final String code;

    /**
     * 绑定的 HTTP 状态码。
     */
    private final int httpStatus;

    /**
     * 构造错误码。
     *
     * @param code       机器可读错误码
     * @param httpStatus HTTP 状态码
     */
    ApiErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
