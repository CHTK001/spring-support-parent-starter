package com.chua.starter.ai.support.security;

import com.chua.common.support.utils.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 网关安全过滤器基类。
 *
 * <p>统一的拦截开关与路径判定：<b>所有开关读 {@code sys_ai_global_setting} 的
 * group=security 字段（全局设置页 / YAML 覆盖层 {@code plugin.ai.settings.security.*}）。</b>
 * 读取统一走 {@link SecuritySettings} 的 1 秒本地快照，避免每条请求十几次 Redis 往返。</p>
 *
 * <h2>生效边界</h2>
 * <pre>
 * 只有请求路径命中 security_gateway_path_prefixes 时，才把资源花在拦截上：
 *   /v2/v1/**  OpenAI 兼容聊天网关
 *   /v2/ai/**  AI 管理出口
 * </pre>
 *
 * <h2>模板方法</h2>
 * <p>{@link #doFilterInternal} 固定为「判定 → 要么拒答、要么放行一次」，子类只实现
 * {@link #reject}。这带来三个强制保证：</p>
 * <ol>
 *   <li><b>过滤器链只会被调用一次</b>。把 {@code chain.doFilter} 放进 {@code try}、
 *       catch 兜底再放行一次，等于一个请求走了两遍链（Controller 执行两次、SSE 起两条流）；</li>
 *   <li><b>拦截器自身异常一律放行</b>（fail-open）。设置表未初始化、Redis 抖动都不能让
 *       网关整片 500；</li>
 *   <li><b>下游异常不被吞</b>。放行只有 {@code chain.doFilter} 一条路径，异常原样交给
 *       Spring 的 {@code HandlerExceptionResolver} / {@code ApiException} 处理器。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public abstract class AbstractGatewaySecurityFilter extends OncePerRequestFilter {

    /**
     * 未配置生效路径时的内置前缀：OpenAI 兼容网关 + AI 管理出口
     */
    private static final String DEFAULT_PATH_PREFIXES = "/v2/v1/,/v2/v1beta/,/v2/ai/";

    /**
     * 未配置健康检查路径时的内置清单
     */
    private static final String DEFAULT_HEALTH_PATHS = "/healthz /ping /readyz";

    /**
     * 安全分组设置快照
     */
    private final SecuritySettings settings;

    /**
     * 构造器注入：安全过滤器的唯一配置来源。
     *
     * @param settings 安全设置快照
     */
    protected AbstractGatewaySecurityFilter(SecuritySettings settings) {
        this.settings = settings;
    }

    /**
     * 获取安全设置快照（子类扩展用）。
     *
     * @return 安全设置快照
     */
    protected SecuritySettings settings() {
        return settings;
    }

    /**
     * 统一拦截骨架：判定命中后只做一次「拒答或放行」。
     *
     * <p>子类若需要包装请求/响应（包装后仍要把包装对象传下去），可以覆盖本方法，
     * 但必须遵守同样的三条约定：链只走一次、判定异常放行、下游异常不吞。</p>
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean intercepted;
        try {
            intercepted = applies(request) && reject(request, response);
        } catch (Exception e) {
            // 拦截器自身异常：放行。设置表未初始化、Redis 抖动都不该让网关整片 500
            log.warn("[{}] 拦截判定异常，已放行: {}", getClass().getSimpleName(), e.getMessage());
            intercepted = false;
        }
        if (!intercepted) {
            chain.doFilter(request, response);
        }
    }

    /**
     * 该请求是否进入本过滤器的判定范围。
     *
     * <p>默认是「总开关开启 + 路径命中网关前缀」。健康检查过滤器需要覆盖本方法：
     * 探针路径（{@code /healthz}）根本不在 {@code /v2/v1/} 前缀下。</p>
     *
     * @param request 请求
     * @return 需要判定返回 true
     */
    protected boolean applies(HttpServletRequest request) {
        return settings().getBool("security_enabled", true) && matches(request);
    }

    /**
     * 拦截判定：返回 true 表示已经写完拒答响应，过滤器链不再继续。
     *
     * <p>实现里只做判定与写响应，<b>绝对不能调用 {@code chain.doFilter}</b>。</p>
     *
     * @param request  请求
     * @param response 响应
     * @return 已拒答返回 true
     * @throws Exception 判定异常（基类捕获后放行）
     */
    protected abstract boolean reject(HttpServletRequest request, HttpServletResponse response) throws Exception;

    /**
     * 该请求是否命中本过滤器的生效路径。
     *
     * <p>前缀列表为逗号分隔的 URL 前缀（如 {@code /v2/v1/,/v2/v1beta/,/v2/ai/}）：命中的是
     * <strong>请求路径</strong>以任一前缀开头，而非完整 URL。</p>
     *
     * @param request HTTP 请求
     * @return 命中返回 true
     */
    protected boolean matches(HttpServletRequest request) {
        String prefixString = settings().getStr("security_gateway_path_prefixes");
        String path = request.getRequestURI();
        if (!StringUtils.hasText(prefixString)) {
            // 未配置时守护 OpenAI 兼容网关 + AI 管理出口两个前缀
            prefixString = DEFAULT_PATH_PREFIXES;
        }
        for (String prefix : prefixString.split(",")) {
            String p = prefix.trim();
            if (StringUtils.hasText(p) && path.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否命中健康检查路径（零开销路径，不进入任何拦截器）。
     *
     * @param request HTTP 请求
     * @return 健康路径命中返回 true
     */
    protected boolean isHealthPath(HttpServletRequest request) {
        String paths = settings().getStr("security_health_paths");
        String path = request.getRequestURI();
        if (!StringUtils.hasText(paths)) {
            paths = DEFAULT_HEALTH_PATHS;
        }
        for (String p : paths.split(" +")) {
            if (StringUtils.hasText(p) && StringUtils.equals(path, p.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取 Bearer 令牌（即 AppKey）：{@code Authorization: Bearer <appKey>}。
     *
     * <p>空令牌（未认证、无授权）返回 {@code null}。</p>
     *
     * @param request HTTP 请求
     * @return AppKey 或 null
     */
    protected String resolveAppKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String token = authorization.trim();
        if (token.startsWith("Bearer ")) {
            token = token.substring("Bearer ".length()).trim();
        } else {
            // 不认的非 Bearer 格式（Basic/Token）交回下游认证守卫
            token = null;
        }
        return StringUtils.hasText(token) ? token : null;
    }

    /**
     * 限流拒答：写出 429，不能进入下游 Handler。
     *
     * @param response 响应
     * @param limit    配额上限值
     */
    protected void rejectTooMany(HttpServletResponse response, int limit) {
        try {
            // 429 在该 jakarta.servlet-api 版本的常量中缺失（verify: javap HttpServletResponse）
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Too Many Requests\",\"limit\":" + limit + "}");
        } catch (IOException e) {
            log.error("[AbstractGatewaySecurityFilter] 429 拒答写失败: {}", e.getMessage());
        }
    }

    /**
     * 请求体过大拒答：写出 413，不能进入下游 Handler。
     *
     * @param response 响应
     * @param limit    限制大小
     */
    protected void rejectBodySize(HttpServletResponse response, long limit) {
        try {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Request Body Too Large\",\"limit\":" + limit + "}");
        } catch (IOException e) {
            log.error("[AbstractGatewaySecurityFilter] 413 拒答写失败: {}", e.getMessage());
        }
    }

    /**
     * 请求超时拒答：写出 504。
     *
     * <p>与 {@link #rejectTooMany} 一致按 JSON 写出，不走 {@code sendError}
     * （那个会交给容器的错误页，拿不到本项目统一的错误结构）。</p>
     *
     * @param response      响应
     * @param timeoutMillis 超时阈值（毫秒）
     */
    protected void rejectTimeout(HttpServletResponse response, long timeoutMillis) {
        try {
            response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Gateway Timeout\",\"timeout\":" + timeoutMillis + "}");
        } catch (IOException e) {
            log.error("[AbstractGatewaySecurityFilter] 504 拒答写失败: {}", e.getMessage());
        }
    }

    /**
     * IP ACL 拒答。
     *
     * @param response 响应
     * @param reason   拒绝原因
     */
    protected void rejectIp(HttpServletResponse response, String reason) {
        try {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Access Denied\",\"reason\":\"" + reason + "\"}");
        } catch (IOException e) {
            log.error("[AbstractGatewaySecurityFilter] IP ACL 拒答写失败: {}", e.getMessage());
        }
    }
}
