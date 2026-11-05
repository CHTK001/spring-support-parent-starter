// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.common;

import lombok.Getter;

/**
 * 前端契约机器错误码。
 *
 * <p>错误码为字符串常量（机器可读），前端 {@code BackendHttpError} 会依据 {@code code} 做分支处理。
 * 每个错误码绑定一个 HTTP 状态码，由全局异常处理器转换为对应响应状态。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Getter
public enum ApiErrorCode {

    /**
     * 鉴权过期或缺失（对应前端登出/重定向）
     * @param "FORBIDDEN" "FORBIDDEN"
     * @param 403 403
     * @return FORBIDDEN的结果
     */
    FORBIDDEN("FORBIDDEN", 403),

    /**
     * 未授权（如供应商 API Key 无效，前端提示检查 Key）
     * @param "UNAUTHORIZED" "UNAUTHORIZED"
     * @param 401 401
     * @return UNAUTHORIZED的结果
     */
    UNAUTHORIZED("UNAUTHORIZED", 401),

    /**
     * 余额/额度不足（如 appKey 余额耗尽 且透支关闭，前端提示充值）
     * @param "INSUFFICIENT_BALANCE" "INSUFFICIENT_BALANCE"
     * @param 402 402
     * @return INSUFFICIENT_BALANCE的结果
     */
    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE", 402),

    /**
     * 上游请求超时（前端提示检查网络/代理）
     * @param "TIMEOUT" "超时"
     * @param 504 504
     * @return 超时的结果
     */
    TIMEOUT("TIMEOUT", 504),

    /**
     * 网关错误（前端提示无法连接供应商接口）
     * @param "BAD_GATEWAY" "BAD_GATEWAY"
     * @param 502 502
     * @return BAD_GATEWAY的结果
     */
    BAD_GATEWAY("BAD_GATEWAY", 502),

    /**
     * 资源不存在（前端 silent状态:[404] 场景静默处理）
     * @param "NOT_FOUND" "NOT_FOUND"
     * @param 404 404
     * @return NOT_FOUND的结果
     */
    NOT_FOUND("NOT_FOUND", 404),

    /**
     * 参数非法（含 JSON 字段校验失败、deny_unknown_字段 触发）
     * @param "INVALID_PARAM" "INVALID_参数"
     * @param 400 400
     * @return invalid参数的结果
     */
    INVALID_PARAM("INVALID_PARAM", 400),

    /**
     * 会话已归档（示例业务错误码，由契约守护线测试断言）
     * @param "CONVERSATION_ARCHIVED" "CONVERSATION_ARCHIVED"
     * @param 409 409
     * @return CONVERSATION_ARCHIVED的结果
     */
    CONVERSATION_ARCHIVED("CONVERSATION_ARCHIVED", 409),

    /**
     * 资源冲突（如非空闲状态尝试召唤、重复创建等）
     * @param "CONFLICT" "CONFLICT"
     * @param 409 409
     * @return CONFLICT的结果
     */
    CONFLICT("CONFLICT", 409),

    /**
     * 能力不足（如缺少 AI 提供者 时触发生成类接口）
     * @param "NO_COMPLETER" "NO_COMPLETER"
     * @param 409 409
     * @return NO_COMPLETER的结果
     */
    NO_COMPLETER("NO_COMPLETER", 409),

    NOT_IMPLEMENTED("NOT_IMPLEMENTED", 501),

    /**
     * 请求过于频繁（幂等键复用等）
     * @param "RATE_LIMITED" "RATE_限制"
     * @param 429 429
     * @return rate限制的结果
     */
    RATE_LIMITED("RATE_LIMITED", 429),

    /**
     * 内部服务错误
     * @param "INTERNAL_ERROR" "内部_错误"
     * @param 500 500
     * @return 内部错误的结果
     */
    INTERNAL_ERROR("INTERNAL_ERROR", 500),

    /**
     * 上游模型服务异常
     * @param "UPSTREAM_ERROR" "UPSTREAM_错误"
     * @param 502 502
     * @return upstream错误的结果
     */
    UPSTREAM_ERROR("UPSTREAM_ERROR", 502),

    /**
     * AI 调用失败（如编剧生成失败、没有可用的对话模型）
     * @param "AI_ERROR" "AI_ERROR"
     * @param 500 500
     * @return AI_ERROR
     */
    AI_ERROR("AI_ERROR", 500);

    /**
     * 机器可读错误码
     */
    private final String code;

    /**
     * 绑定的 HTTP 状态码
     */
    private final int httpStatus;

    /**
     * api错误编码。
     * @param code 编码
     * @param httpStatus http状态
     * @return 返回值
    */
    ApiErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }
}