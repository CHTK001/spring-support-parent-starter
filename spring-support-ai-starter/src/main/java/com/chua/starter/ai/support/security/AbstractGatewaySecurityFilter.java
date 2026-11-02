package com.chua.starter.ai.support.security;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
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
 * group=security 字段（全局设置页 / YAML 覆盖层 {@code plugin.ai.settings.security.*}）。</b></p>
 *
 * <h2>生效边界</h2>
 * <pre>
 * 只有请求路径命中 security_gateway_path_prefixes 时，才把资源花在拦截上：
 *   /v2/v1/**  OpenAI 兼容聊天网关
 *   /v2/ai/**  AI 管理出口
 * </pre>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
public abstract class AbstractGatewaySecurityFilter extends OncePerRequestFilter {

    /**
     * 公钥：安全总开关
     */
    private static final String FIELD_SECURITY_ENABLED = "security_enabled";
    /**
     * 公钥：生效路径前缀（逗号分隔）
     */
    private static final String FIELD_SECURITY_PATH_PREFIXES = "security_gateway_path_prefixes";
    /**
     * 公钥：健康检查路径（空格分隔）
     */
    private static final String FIELD_SECURITY_HEALTH_PATHS = "security_health_paths";

    /**
     * 目录前缀：全局设置读取器，安全分组的开关与配额全部走这里
     */
    private final GlobalSettingReader globalSettingReader;

    /**
     * 构造器注入：安全过滤器的唯一配置来源。
     *
     * @param globalSettingReader 全局设置读取器
     */
    protected AbstractGatewaySecurityFilter(GlobalSettingReader globalSettingReader) {
        this.globalSettingReader = globalSettingReader;
    }

    /**
     * 获取全局设置读取器（子类扩展用）。
     *
     * @return 全局设置读取器
     */
    protected GlobalSettingReader globalSettingReader() {
        return globalSettingReader;
    }

    /**
     * 开关（全局）是否开启。
     *
     * <p>关闭时本级所有拦截器放行，防护降级回 {@code AiAccessGuardService} 的
     * Bearer 鉴权与计费守卫。</p>
     *
     * @return 开关开启返回 true
     */
    protected boolean enabled() {
        return globalSettingReader().getBool("security", FIELD_SECURITY_ENABLED, true);
    }

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
        String prefixString = globalSettingReader().getStr("security", FIELD_SECURITY_PATH_PREFIXES);
        String path = request.getRequestURI();
        if (!StringUtils.hasText(prefixString)) {
            // 未配置时守护 OpenAI 兼容网关 + AI 管理出口两个前缀
            prefixString = "/v2/v1/,/v2/v1beta/,/v2/ai/";
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
        String paths = globalSettingReader().getStr("security", FIELD_SECURITY_HEALTH_PATHS);
        String path = request.getRequestURI();
        if (!StringUtils.hasText(paths)) {
            paths = "/healthz /ping /readyz";
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
            token = null; // 不认的非 Bearer 格式（Basic/Token）直接放行给认证过滤器
        }
        return StringUtils.hasText(token) ? token : null;
    }

    /**
     * 限流拒答：记录日志并写出 429，不能进入下游 Handler。
     *
     * @param request  请求
     * @param response 响应
     * @param limit    配额上限值
     */
    protected void rejectTooMany(HttpServletRequest request, HttpServletResponse response, String limit) {
        try {
            response.setStatus(429);  // 429 在该 jakarta.servlet-api 版本的常量中缺失（verify: javap HttpServletResponse）
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Too Many Requests\",\"limit\":" + limit + "}");
        } catch (IOException e) {
            log.error("[AbstractGatewaySecurityFilter] 429 拒答写失败: {}", e.getMessage());
        }
    }

    /**
     * 请求体过大拒答：记录日志并写出 413，不能进入下游 Handler。
     *
     * @param request  请求
     * @param response 响应
     * @param limit    限制大小
     */
    protected void rejectBodySize(HttpServletRequest request, HttpServletResponse response, String limit) {
        try {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Request Body Too Large\",\"limit\":" + limit + "}");
        } catch (IOException e) {
            log.error("[AbstractGatewaySecurityFilter] 413 拒答写失败: {}", e.getMessage());
        }
    }

    /**
     * 请求超时拒答：看门狗抢在控制器之前把 504 写出去。
     *
     * <p>控制器仍在跑，随后写出会被丢弃 —— 调用方看到的是及时的 504，而不是等到
     * 连接超时。与 {@link #rejectTooMany} 一致，按 JSON 写出，不走 {@code sendError}
     * （那个会交给容器的错误页，拿不到本项目统一的错误结构）。</p>
     *
     * @param request       请求
     * @param response      响应
     * @param timeoutMillis 超时阈值（毫秒）
     */
    protected void rejectTimeout(HttpServletRequest request, HttpServletResponse response, long timeoutMillis) {
        try {
            response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Gateway Timeout\",\"timeout\":" + timeoutMillis + "}");
        } catch (IOException e) {
            log.error("[AbstractGatewaySecurityFilter] 504 拒答写失败: {}", e.getMessage());
        }
    }

    /**
     * IP ACL 拒答。
     *
     * @param request  请求
     * @param response 响应
     * @param reason   拒绝原因
     */
    protected void rejectIp(HttpServletRequest request, HttpServletResponse response, String reason) {
        try {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Access Denied\",\"reason\":\"" + reason + "\"}");
        } catch (IOException e) {
            log.error("[AbstractGatewaySecurityFilter] IP ACL 拒答写失败: {}", e.getMessage());
        }
    }

    /**
     * 兜底放行：当 {@code GlobalSettingReader} 不可用（设置表未初始化等）或读写异常时
     * 直接放行，避免拦截器本身停机。
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤器链
     */
    protected void safePass(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException ex) {
            log.error("[AbstractGatewaySecurityFilter] 过滤器链执行失败，放行: {}", ex.getMessage());
        }
    }

    /**
     * 按前缀列表做路径判断的工具（方便子类用 Akka 风格扩展）。
     *
     * @param prefixString 前缀列表
     * @param path         请求路径
     * @return 命中的前缀
     */
    protected static String matchPrefix(String prefixString, String path) {
        if (!StringUtils.hasText(prefixString)) {
            prefixString = "/v2/v1/,/v2/v1beta/,/v2/ai/";
        }
        for (String prefix : prefixString.split(",")) {
            String p = prefix.trim();
            if (StringUtils.hasText(p) && path.startsWith(p)) {
                return p;
            }
        }
        return null;
    }
}
