// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.spi.impl;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.common.support.http.ChannelHttpException;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 账号渠道 SPI 实现共享的 HTTP / JSON 辅助（AI 模块适配壳）。
 *
 * <p>实现已下沉到 common 的 {@link com.chua.starter.common.support.http.ChannelHttp}：
 * 账号模块与 AI 模块都要发同样的上游请求，放在任一业务模块都会造成反向依赖，
 * 放在 common 才能两边共用。</p>
 *
 * <p>本类只做两件事：</p>
 * <ul>
 *   <li>把 common 的静态成员暴露给本包内的 20+ 渠道实现，方法签名保持不变；</li>
 *   <li>把 {@link ChannelHttpException} 翻译成本模块的 {@link ApiException}，
 *       让既有的 {@code catch (ApiException)} 与前端错误码分支零改动。</li>
 * </ul>
 *
 * <p>本类是迁移过渡壳：渠道实现整体搬到账号模块后即可删除。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
final class ChannelHttp {

    /** 共享的静态 HttpClient（委托 common 的同名常量，保证全进程复用同一连接池）。 */
    static final HttpClient CLIENT = com.chua.starter.common.support.http.ChannelHttp.CLIENT;

    /** 不跟随重定向的静态 HttpClient（委托 common）。 */
    static final HttpClient NO_REDIRECT = com.chua.starter.common.support.http.ChannelHttp.NO_REDIRECT;

    /** 单次请求默认超时（委托 common）。 */
    static final Duration DEFAULT_TIMEOUT = com.chua.starter.common.support.http.ChannelHttp.DEFAULT_TIMEOUT;

    /** 登录轮询单次请求超时（委托 common）。 */
    static final Duration POLL_TIMEOUT = com.chua.starter.common.support.http.ChannelHttp.POLL_TIMEOUT;

    /**
     * 限流错误信息前缀：{@link #isRateLimited(Throwable)} 据此识别。
     *
     * <p>用前缀而不是自定义异常类型，是因为 {@link ApiException} 的契约被整个
     * 渠道模块复用（前端按 {@code ApiErrorCode} 分支），加子类会牵动面过大。</p>
     */
    static final String RATE_LIMITED_MARKER = com.chua.starter.common.support.http.ChannelHttp.RATE_LIMITED_MARKER;

    private ChannelHttp() {
    }

    /**
     * 执行一次 POST JSON 请求，返回响应体字符串。
     *
     * @param url 目标地址
     * @param body 请求体 JSON（可为 null，表示无 body）
     * @param headers 附加请求头（可为 null）
     * @return 响应体；HTTP 非 2xx 时抛 {@link ApiException}
     */
    static String postJson(String url, String body, Map<String, String> headers) {
        try {
            return com.chua.starter.common.support.http.ChannelHttp.postJson(url, body, headers);
        } catch (ChannelHttpException e) {
            throw translate(e);
        }
    }

    /**
     * 执行一次 GET 请求，返回响应体字符串。
     *
     * @param url 目标地址
     * @param headers 附加请求头（可为 null）
     * @return 响应体；HTTP 非 2xx 时抛 {@link ApiException}
     */
    static String getJson(String url, Map<String, String> headers) {
        try {
            return com.chua.starter.common.support.http.ChannelHttp.getJson(url, headers);
        } catch (ChannelHttpException e) {
            throw translate(e);
        }
    }

    /**
     * 执行一次 GET 请求并原样返回响应（不跟随重定向、非 2xx 不抛异常）。
     *
     * @param url     目标地址
     * @param headers 附加请求头（可为 null）
     * @return 原始响应（状态码、响应头、响应体）
     */
    static HttpResponse<String> getRaw(String url, Map<String, String> headers) {
        try {
            return com.chua.starter.common.support.http.ChannelHttp.getRaw(url, headers);
        } catch (ChannelHttpException e) {
            throw translate(e);
        }
    }

    /**
     * 执行一次 POST 表单请求（OAuth 授权码/刷新码等）。
     *
     * @param url 目标地址
     * @param form 表单体，形如 {@code client_id=...&code=...}
     * @param headers 附加请求头（可为 null）
     * @return 响应体；HTTP 非 2xx 时抛 {@link ApiException}
     */
    static String postForm(String url, String form, Map<String, String> headers) {
        try {
            return com.chua.starter.common.support.http.ChannelHttp.postForm(url, form, headers);
        } catch (ChannelHttpException e) {
            throw translate(e);
        }
    }

    /**
     * 判断异常是否为「上游限流」。
     *
     * <p>登录轮询遇到限流不应判失败：用户还在授权页操作，等下一轮即可。</p>
     *
     * @param e 异常
     * @return 是否为限流
     */
    static boolean isRateLimited(Throwable e) {
        if (e instanceof ApiException && e.getMessage() != null
                && e.getMessage().startsWith(RATE_LIMITED_MARKER)) {
            return true;
        }
        return com.chua.starter.common.support.http.ChannelHttp.isRateLimited(e);
    }

    /**
     * 摘要化响应体（最多 300 字符），避免异常信息带出超大 body。
     *
     * @param body 原始响应体，可为 null
     * @return 压平空白并截断后的摘要
     */
    static String truncate(String body) {
        return com.chua.starter.common.support.http.ChannelHttp.truncate(body);
    }

    /**
     * 把 common 的渠道 HTTP 异常翻成本模块的契约异常。
     *
     * <p>消息与根因原样保留：前端展示的文案、限流前缀识别、排障日志都依赖它。</p>
     *
     * @param e common 抛出的渠道 HTTP 异常
     * @return 本模块的契约异常
     */
    private static ApiException translate(ChannelHttpException e) {
        ApiErrorCode code = com.chua.starter.common.support.http.ChannelHttp.CODE_INTERNAL_ERROR.equals(e.getCode())
                ? ApiErrorCode.INTERNAL_ERROR
                : ApiErrorCode.UPSTREAM_ERROR;
        return new ApiException(code, e.getMessage(), e);
    }
}
