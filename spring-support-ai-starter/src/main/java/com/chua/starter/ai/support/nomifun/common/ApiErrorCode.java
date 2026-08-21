package com.chua.starter.ai.support.nomifun.common;

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
     */
    FORBIDDEN("FORBIDDEN", 403),

    /**
     * 资源不存在（前端 silentStatuses:[404] 场景静默处理）
     */
    NOT_FOUND("NOT_FOUND", 404),

    /**
     * 参数非法（含 JSON 字段校验失败、deny_unknown_fields 触发）
     */
    INVALID_PARAM("INVALID_PARAM", 400),

    /**
     * 会话已归档（示例业务错误码，由契约守护线测试断言）
     */
    CONVERSATION_ARCHIVED("CONVERSATION_ARCHIVED", 409),

    /**
     * 资源冲突（如非空闲状态尝试召唤、重复创建等）
     */
    CONFLICT("CONFLICT", 409),

    /**
     * 能力不足（如缺少 AI provider 时触发生成类接口）
     */
    NO_COMPLETER("NO_COMPLETER", 409),

    /**
     * 请求过于频繁（幂等键复用等）
     */
    RATE_LIMITED("RATE_LIMITED", 429),

    /**
     * 内部服务错误
     */
    INTERNAL_ERROR("INTERNAL_ERROR", 500),

    /**
     * 上游模型服务异常
     */
    UPSTREAM_ERROR("UPSTREAM_ERROR", 502);

    /**
     * 机器可读错误码
     */
    private final String code;

    /**
     * 绑定的 HTTP 状态码
     */
    private final int httpStatus;

    ApiErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
