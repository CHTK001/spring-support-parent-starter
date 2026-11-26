// [P3C A20 豁免] A20/A30/A31: 含 HTTP 状态码/请求头名/协议文案等非业务魔法值场景，豁免。
package com.chua.starter.common.support.api.sniff;

import com.chua.common.support.application.GlobalSettingFactory;
import com.chua.common.support.network.server.filter.security.BotSniffGuard;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.decode.ApiRequestDecodeSetting;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 嗅探（bot 挑战）Servlet 过滤器 —— 薄适配层。
 *
 * <p>判定规则全部在 utils 的 {@link BotSniffGuard}（与 utils 服务端过滤器共用同一引擎），
 * 本类只负责：读开关 → 取请求头 → 写响应 → 发事件。</p>
 *
 * <h2>两段式交互</h2>
 * <ol>
 *   <li>无 ticket、无解答：先做一次身份类不可伪造信号判定（缺 UA / bot UA / 无头 UA / bot TLS 指纹）。
 *       命中直接 403；否则返回 {@code 428} + 挑战 JS（{@code application/javascript}），
 *       并带 {@code X-Bot-Sniff-Challenge: 1}。</li>
 *   <li>前端执行 JS 得到指纹 JSON，以本次挑战的一次性随机数作 codec 密钥加密后，按
 *       {@code 随机数.密文hex} 放进 {@code X-Bot-Sniff-Solved} 头<b>重放同一请求</b>；
 *       解密后连同客户端探针二次判定，通过则签发 ticket（响应头回写），
 *       后续请求凭 ticket 直接放行。</li>
 * </ol>
 *
 * <p><b>开关</b>：与 {@code CodecRequestOpen} / {@code CodecTraceOpen} 同口径——过滤器缺省即装配，
 * 唯一开关是 {@code sys_setting}(group=config) 的 {@code CodecSniffOpen}（默认 false，关闭时全放行）；
 * yaml 只用于 {@code enabled=false} 硬关与 include/exclude/ttl/密钥调参。</p>
 *
 * @author CH
 * @since 4.0.0.60
 */
@Slf4j
public class BotSniffFilter implements Filter {

    /**
     * 挑战响应标记头（前端据此区分普通 428）
     */
    public static final String CHALLENGE_HEADER = "X-Bot-Sniff-Challenge";

    /**
     * 挑战状态码（Precondition Required）
     */
    private static final int CHALLENGE_STATUS = 428;

    /**
     * 拒绝状态码
     */
    private static final int BLOCK_STATUS = 403;

    /**
     * 运行时开关所在分组（sys_setting.sys_setting_group）
     */
    private static final String SETTING_GROUP = "config";

    /**
     * 判定引擎
     */
    private final BotSniffGuard guard;

    /**
     * 属性
     */
    private final BotSniffProperties properties;

    /**
     * 事件发布器（可为 null：无事件上下文时只拦截不通知）
     */
    private final ApplicationEventPublisher publisher;

    /**
     * 上一次生效的运行时开关（只在变化时重配引擎，避免每请求加锁）
     */
    private volatile Boolean appliedRuntime;

    /**
     * 构造过滤器。
     *
     * @param properties 嗅探属性
     * @param publisher  事件发布器，可为 null
     */
    public BotSniffFilter(BotSniffProperties properties, ApplicationEventPublisher publisher) {
        this.properties = properties;
        this.publisher = publisher;
        this.guard = new BotSniffGuard();
        List<String> exclude = null == properties.getExcludePaths() || properties.getExcludePaths().isEmpty()
                ? null : properties.getExcludePaths();
        this.guard.configure(properties.isEnabled(),
                properties.getSecretKey(),
                properties.getTicketTtlSeconds(),
                properties.getTicketHeader(),
                properties.getSolvedHeader(),
                properties.getIncludePaths(),
                exclude);
    }

    /**
     * 执行嗅探。
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤器链
     * @throws IOException      IO 异常
     * @throws ServletException Servlet 异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }
        if (!runtimeEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        String path = resolvePath(httpRequest);
        String method = httpRequest.getMethod();
        BotSniffGuard.SniffResult result = guard.evaluate(method, path, collectHeaders(httpRequest));

        if (result.passed()) {
            if (StringUtils.isNotBlank(result.ticket())) {
                httpResponse.setHeader(guard.getTicketHeader(), result.ticket());
            }
            chain.doFilter(request, response);
            return;
        }
        if (result.challenged()) {
            writeChallenge(httpResponse, result.challenge());
            publish(httpRequest, method, path, result);
            return;
        }
        writeBlocked(httpResponse, result.reason());
        publish(httpRequest, method, path, result);
    }

    /**
     * 写挑战响应。
     *
     * @param response  响应
     * @param challenge 挑战 JS
     * @throws IOException IO 异常
     */
    private void writeChallenge(HttpServletResponse response, String challenge) throws IOException {
        response.setStatus(CHALLENGE_STATUS);
        response.setContentType("application/javascript; charset=utf-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(CHALLENGE_HEADER, "1");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader(guard.getSolvedHeader() + "-Ttl", String.valueOf(guard.getChallengeTtlSeconds()));
        response.getWriter().write(challenge);
        response.flushBuffer();
    }

    /**
     * 写拒绝响应。
     *
     * @param response 响应
     * @param reason   拒绝原因
     * @throws IOException IO 异常
     */
    private void writeBlocked(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(BLOCK_STATUS);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(CHALLENGE_HEADER, "blocked");
        response.getWriter().write("{\"code\":403,\"success\":false,\"message\":\""
                + escape(reason) + "\"}");
        response.flushBuffer();
    }

    /**
     * 发布判定事件（供业务模块记录审计日志）。
     *
     * @param request  请求
     * @param method   方法
     * @param path     路径
     * @param result   判定结果
     */
    private void publish(HttpServletRequest request, String method, String path,
                         BotSniffGuard.SniffResult result) {
        if (null == publisher) {
            return;
        }
        try {
            publisher.publishEvent(new BotSniffEvent(this, result.decision(), result.verdict(),
                    result.reason(), result.score(), method, path,
                    resolveClientIp(request), request.getHeader("User-Agent"),
                    StringUtils.isNotBlank(result.ticket())));
        } catch (Exception e) {
            log.warn("[sniff] 事件发布失败 path={} reason={}", path, e.getMessage());
        }
    }

    /**
     * 运行时开关（sys_setting CodecSniffOpen）。
     *
     * <p>只在值变化时重配引擎：{@code GlobalSettingFactory} 的读取是内存查表，
     * 但引擎的 {@code configure} 是同步方法，逐请求加锁没有必要。</p>
     *
     * @return 是否生效
     */
    private boolean runtimeEnabled() {
        boolean current;
        try {
            ApiRequestDecodeSetting setting = GlobalSettingFactory.getInstance()
                    .get(SETTING_GROUP, ApiRequestDecodeSetting.class);
            current = null != setting && setting.isCodecSniffOpen();
        } catch (Exception e) {
            current = false;
        }
        Boolean applied = appliedRuntime;
        if (null == applied || applied.booleanValue() != current) {
            guard.configure(current, null, null, null, null, null, null);
            appliedRuntime = current;
        }
        return current;
    }

    /**
     * 解析请求路径（去掉 context path）。
     *
     * @param request 请求
     * @return 路径
     */
    private static String resolvePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (null != contextPath && !contextPath.isEmpty() && null != uri && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return null == uri ? "" : uri;
    }

    /**
     * 收集请求头（同名多值按逗号合并）。
     *
     * @param request 请求
     * @return 请求头 Map
     */
    private static Map<String, String> collectHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            List<String> values = Collections.list(request.getHeaders(name));
            headers.put(name, String.join(",", values));
        }
        return headers;
    }

    /**
     * 解析客户端地址。
     *
     * @param request 请求
     * @return 客户端地址
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustForwardedHeader()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.isNotBlank(forwarded)) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 转义 JSON 字符串中的引号与反斜杠。
     *
     * @param text 原始文本
     * @return 转义结果
     */
    private static String escape(String text) {
        if (null == text) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
