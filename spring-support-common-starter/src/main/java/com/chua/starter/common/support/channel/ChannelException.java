// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.common.support.channel;

import lombok.Getter;

/**
 * 渠道能力（绑定 / 签到 / 积分 / 上游管理）的跨模块业务异常。
 *
 * <p><b>为什么需要它</b>：渠道实现已迁到 {@code spring-support-account-starter}，
 * 而消费方是 {@code spring-support-ai-starter}。两边各自维护一套 {@code ApiException}，
 * 若实现直接抛自己模块的异常，消费方的兜底分支只会把它当成
 * {@code UPSTREAM_ERROR}（502），{@code INVALID_PARAM} / {@code NOT_FOUND} /
 * {@code NOT_IMPLEMENTED} 全部丢失，前端也就无法给出可操作的提示。</p>
 *
 * <p>因此契约异常下沉到 common：错误码用与前端一致的字符串词表（{@link #getCode()}），
 * 附带建议的 HTTP 状态码（{@link #getHttpStatus()}）。消费方在自己的边界上把它翻译成
 * 模块内的契约异常即可，common 不反向依赖任何业务模块。</p>
 *
 * <p>{@code com.chua.starter.common.support.http.ChannelHttpException} 是本类的子类：
 * 上游 HTTP 失败天然也是一种渠道失败。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Getter
public class ChannelException extends RuntimeException {

    /**
     * 未登录 / 凭据失效。
     */
    public static final String CODE_UNAUTHORIZED = "UNAUTHORIZED";

    /**
     * 无权限。
     */
    public static final String CODE_FORBIDDEN = "FORBIDDEN";

    /**
     * 资源不存在（渠道未绑定、上游对象被删等）。
     */
    public static final String CODE_NOT_FOUND = "NOT_FOUND";

    /**
     * 入参不合法。
     */
    public static final String CODE_INVALID_PARAM = "INVALID_PARAM";

    /**
     * 当前渠道不支持该能力（如不可续期）。
     */
    public static final String CODE_NOT_IMPLEMENTED = "NOT_IMPLEMENTED";

    /**
     * 上游返回错误 / 连接失败。
     */
    public static final String CODE_UPSTREAM_ERROR = "UPSTREAM_ERROR";

    /**
     * 本模块内部错误。
     */
    public static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";

    /**
     * 机器可读错误码，取值见本类的 {@code CODE_*} 常量。
     */
    private final String code;

    /**
     * 建议的 HTTP 状态码；{@code 0} 表示由调用方决定。
     */
    private final int httpStatus;

    /**
     * 构造渠道业务异常（状态码按错误码推导）。
     *
     * @param code    机器可读错误码
     * @param message 人类可读信息
     */
    public ChannelException(String code, String message) {
        this(code, statusOf(code), message, null);
    }

    /**
     * 构造渠道业务异常（状态码按错误码推导，带根因）。
     *
     * @param code    机器可读错误码
     * @param message 人类可读信息
     * @param cause   根因，可为 null
     */
    public ChannelException(String code, String message, Throwable cause) {
        this(code, statusOf(code), message, cause);
    }

    /**
     * 构造渠道业务异常（显式状态码）。
     *
     * @param code       机器可读错误码
     * @param httpStatus 建议的 HTTP 状态码
     * @param message    人类可读信息
     * @param cause      根因，可为 null
     */
    public ChannelException(String code, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /**
     * 错误码到 HTTP 状态码的默认映射。
     *
     * @param code 错误码
     * @return 状态码；未知错误码按 500 处理
     */
    public static int statusOf(String code) {
        if (code == null) {
            return 500;
        }
        return switch (code) {
            case CODE_UNAUTHORIZED -> 401;
            case CODE_FORBIDDEN -> 403;
            case CODE_NOT_FOUND -> 404;
            case CODE_INVALID_PARAM -> 400;
            case CODE_NOT_IMPLEMENTED -> 501;
            case CODE_UPSTREAM_ERROR -> 502;
            default -> 500;
        };
    }
}
