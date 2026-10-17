package com.chua.payment.support.service.impl;

import com.chua.payment.support.dto.PaymentSchedulerTaskUpdateDTO;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.service.PaymentSchedulerTaskOpsService;
import com.chua.payment.support.vo.PaymentSchedulerTaskVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于“调度器平台”的支付调度任务运维服务。
 * <p>
 * 当 {@code engine=job} 模式下不再直接管理本地调度器，而是将运行端的
 * 查询、修改和手工触发请求转发至调度器平台对应 namespace 的任务接口。
 * </p>
 *
 * @author CH
 * @since 4.0.0.50
 */
@Service
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "engine", havingValue = "job")
public class PaymentPlatformSchedulerTaskOpsService implements PaymentSchedulerTaskOpsService {

    /**
     * 默认连接超时
     */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /**
     * 默认读取超时
     */
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);
    /**
     * 默认控制台用户名
     */
    private static final String DEFAULT_CONSOLE_USERNAME = "admin";
    /**
     * 默认控制台密码
     */
    private static final String DEFAULT_CONSOLE_PASSWORD = "${payment.console.password:}";
    /**
     * 标准日期时间格式
     */
    private static final DateTimeFormatter STANDARD_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 运行环境
     */
    private final Environment environment;
    /**
     * JSON 序列化器
     */
    private final ObjectMapper objectMapper;
    /**
     * HTTP 客户端
     */
    private final RestTemplate restTemplate;
    private final Object consoleSessionMonitor = new Object(); // 控制台会话监视器

    /**
     * 轻量控制台模式（“调度器返回的 Cookie”）下缓存的登录态。
     * <p>
     * 这是给 Spring 内置简单页面使用的固定账号密码会话，不承载多用户语义；
     * 真正的开放接口仍然优先由 OAuth 授权转发触发。
     * </p>
     */
    private volatile String schedulerConsoleCookie;

    /**
     * 构造支付平台调度任务运维服务。
     * @param environment 运行环境
     * @param objectMapper 对象映射器
     * @param restTemplate HTTP 客户端
     */
    public PaymentPlatformSchedulerTaskOpsService(Environment environment,
                                                  ObjectMapper objectMapper,
                                                  RestTemplate restTemplate) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    /**
     * 列出调度任务
     */
    public List<PaymentSchedulerTaskVO> listTasks() {
        AccessMode accessMode = resolveAccessMode();
        JsonNode data = request(HttpMethod.GET, taskListUrl(accessMode), null, accessMode);
        List<PaymentSchedulerTaskVO> result = new ArrayList<>();
        if (data != null && data.isArray()) {
            data.forEach(item -> result.add(toTaskVO(item)));
        }
        return result;
    }

    @Override
    /**
     * 更新任务配置
     */
    public PaymentSchedulerTaskVO updateTask(String taskKey, PaymentSchedulerTaskUpdateDTO dto) {
        AccessMode accessMode = resolveAccessMode();
        return toTaskVO(request(HttpMethod.PUT, taskUrl(accessMode, taskKey), dto, accessMode));
    }

    @Override
    /**
     * 手动触发任务
     */
    public PaymentSchedulerTaskVO triggerTask(String taskKey) {
        AccessMode accessMode = resolveAccessMode();
        return toTaskVO(request(HttpMethod.POST, taskTriggerUrl(accessMode, taskKey), null, accessMode));
    }

    private JsonNode request(HttpMethod method, URI uri, Object body, AccessMode accessMode) {
        try {
            ResponseEntity<String> response = exchange(method, uri, body, accessMode, true);
            String responseBody = response.getBody();
            if (!StringUtils.hasText(responseBody)) {
                throw new PaymentException("调度平台返回空响应: " + uri);
            }
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode code = root.get("code");
            if (code != null && !"00000".equals(code.asText()) && !"200".equals(code.asText())) {
                throw new PaymentException("调度平台调用失败: " + root.path("msg").asText(root.path("message").asText()));
            }
            return root.path("data");
        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentException("调用调度平台失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发起 HTTP 调用
     * @param method 请求方法
     * @param uri 请求地址
     * @param body 请求主体
     * @param accessMode 访问模式
     * @param allowConsoleRelogin 是否允许控制台重新登录
     */
    private ResponseEntity<String> exchange(HttpMethod method,
                                            URI uri,
                                            Object body,
                                            AccessMode accessMode,
                                            boolean allowConsoleRelogin) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (accessMode == AccessMode.PLATFORM) {
                /**
                 * 透传当前请求认证头
                 * @param headers 请求头
                 */
                copyAuthHeaders(headers);
            } else {
                /**
                 * 确保控制台会话有效
                 * @param false 不强制刷新
                 */
                ensureConsoleSession(false);
                /**
                 * 附加控制台 Cookie
                 * @param headers 请求头
                 */
                applyConsoleCookie(headers);
            }
            HttpEntity<?> entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
            return restTemplate.exchange(uri, method, entity, String.class);
        } catch (HttpStatusCodeException e) {
            if (accessMode == AccessMode.CONSOLE
                    && allowConsoleRelogin
                    && (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN)) {
                /**
                 * 清除控制台会话
                 */
                clearConsoleSession();
                /**
                 * 重新登录控制台
                 * @param true 强制刷新
                 */
                ensureConsoleSession(true);
                return exchange(method, uri, body, accessMode, false);
            }
            throw e;
        }
    }

    /**
     * 确保控制台会话有效
     *
     * @param forceRefresh 是否强制重新登录
     */
    private void ensureConsoleSession(boolean forceRefresh) {
        synchronized (consoleSessionMonitor) {
            if (!forceRefresh && StringUtils.hasText(schedulerConsoleCookie)) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("username", consoleUsername());
            payload.put("password", consolePassword());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.exchange(
                    /**
                     * 控制台登录地址
                     */
                    consoleLoginUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class);
            String responseBody = response.getBody();
            if (!StringUtils.hasText(responseBody)) {
                throw new PaymentException("调度平台控制台登录返回空响应");
            }
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode code = root.get("code");
                if (code != null && !"00000".equals(code.asText()) && !"200".equals(code.asText())) {
                    throw new PaymentException("调度平台控制台登录失败: "
                            + root.path("msg").asText(root.path("message").asText()));
                }
            } catch (PaymentException e) {
                throw e;
            } catch (Exception e) {
                throw new PaymentException("解析调度平台控制台登录响应失败: " + e.getMessage(), e);
            }
            schedulerConsoleCookie = extractCookieHeader(response.getHeaders());
        }
    }

    /**
     * 清除控制台会话
     */
    private void clearConsoleSession() {
        synchronized (consoleSessionMonitor) {
            schedulerConsoleCookie = null;
        }
    }

    /**
     * 应用控制台 Cookie
     *
     * @param headers 请求头
     */
    private void applyConsoleCookie(HttpHeaders headers) {
        if (StringUtils.hasText(schedulerConsoleCookie)) {
            headers.set(HttpHeaders.COOKIE, schedulerConsoleCookie);
        }
    }

    /**
     * 从响应头中提取 Cookie
     *
     * @param headers 响应头
     * @return 提取出的 Cookie 请求头值
     */
    private String extractCookieHeader(HttpHeaders headers) {
        List<String> setCookieValues = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookieValues == null || setCookieValues.isEmpty()) {
            return null;
        }
        String cookieHeader = setCookieValues.stream()
                .map(this::toRequestCookie)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("; "));
        return StringUtils.hasText(cookieHeader) ? cookieHeader : null;
    }

    /**
     * 将 Set-Cookie 值转换为请求 Cookie
     *
     * @param rawCookie 原始 Set-Cookie 值
     * @return 可用于请求的 Cookie 值
     */
    private String toRequestCookie(String rawCookie) {
        if (!StringUtils.hasText(rawCookie)) {
            return null;
        }
        int delimiterIndex = rawCookie.indexOf(';');
        return delimiterIndex >= 0 ? rawCookie.substring(0, delimiterIndex).trim() : rawCookie.trim();
    }

    /**
     * 运营中心通过支付服务代理访问调度器平台时，需要把当前登录态继续向下游透传。
     * 调度器同时兼容 `x-oauth-token` 与 `Authorization: Bearer ...` 两种写法，这里保持原样转发。
     * @param headers 请求头
     */
    private void copyAuthHeaders(HttpHeaders headers) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        /**
         * 请求头存在时复制
         * @param request 当前请求
         * @param headers 目标请求头
         */
        copyHeaderIfPresent(request, headers, "x-oauth-token");
        /**
         * 请求头存在时复制
         * @param request 当前请求
         * @param headers 目标请求头
         * @param HttpHeaders.AUTHORIZATION 授权请求头
         */
        copyHeaderIfPresent(request, headers, HttpHeaders.AUTHORIZATION);
    }

    /**
     * 请求头存在时复制到目标请求头
     *
     * @param request 当前请求
     * @param headers 目标请求头
     * @param headerName 请求头名称
     */
    private void copyHeaderIfPresent(HttpServletRequest request, HttpHeaders headers, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (StringUtils.hasText(headerValue)) {
            headers.set(headerName, headerValue);
        }
    }

    /**
     * 解析访问模式：显式配置优先，其次按是否存在透传认证头判断
     *
     */
    private AccessMode resolveAccessMode() {
        String configuredMode = environment.getProperty("plugin.payment.scheduler.platform-access-mode", "auto");
        if ("platform".equalsIgnoreCase(configuredMode)) {
            return AccessMode.PLATFORM;
        }
        if ("console".equalsIgnoreCase(configuredMode)) {
            return AccessMode.CONSOLE;
        }
        return hasForwardedAuthHeaders() ? AccessMode.PLATFORM : AccessMode.CONSOLE;
    }

    /**
     * 是否携带透传认证头
     *
     * @return 是否包含透传认证头
     */
    private boolean hasForwardedAuthHeaders() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        return hasHeader(request, "x-oauth-token") || hasHeader(request, HttpHeaders.AUTHORIZATION);
    }

    /**
     * 是否携带指定请求头
     *
     * @param request 当前请求
     * @param headerName 请求头名称
     * @return 是否包含该请求头
     */
    private boolean hasHeader(HttpServletRequest request, String headerName) {
        return request != null && StringUtils.hasText(request.getHeader(headerName));
    }

    /**
     * 转换为任务视图对象
     *
     * @param node 任务节点
     * @return 任务视图对象
     */
    private PaymentSchedulerTaskVO toTaskVO(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw new PaymentException("调度平台返回空任务数据");
        }
        PaymentSchedulerTaskVO vo = new PaymentSchedulerTaskVO(
                node.path("taskKey").asText(),
                node.path("taskName").asText(),
                node.path("cronExpression").asText(),
                node.path("enabled").asBoolean(),
                node.path("description").asText(),
                node.path("scheduled").asBoolean(),
                parseTime(node.path("nextExecutionTime").asText(null)),
                parseTime(node.path("lastStartedAt").asText(null)),
                parseTime(node.path("lastFinishedAt").asText(null)),
                node.path("lastRunStatus").asText(null),
                node.path("lastRunMessage").asText(null));
        return vo;
    }

    /**
     * 解析时间字符串，兼容多种格式
     *
     * @param value 时间字符串
     * @return 解析出的时间，无法解析时抛出异常
     */
    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value) || "null".equalsIgnoreCase(value)) {
            return null;
        }
        String candidate = value.trim();
        try {
            return LocalDateTime.parse(candidate);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(candidate, STANDARD_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(candidate).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(candidate), ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
        }
        if (candidate.chars().allMatch(Character::isDigit)) {
            long epochValue = Long.parseLong(candidate);
            Instant instant = candidate.length() <= 10
                    ? Instant.ofEpochSecond(epochValue)
                    : Instant.ofEpochMilli(epochValue);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        throw new PaymentException("无法解析调度平台返回的时间字段: " + value);
    }

    /**
     * 构建任务列表地址
     *
     * @param accessMode 访问模式
     * @return 任务列表地址
     */
    private URI taskListUrl(AccessMode accessMode) {
        return buildUri(accessMode, namespace(), "task", "list");
    }

    /**
     * 构建任务详情地址
     *
     * @param accessMode 访问模式
     * @param taskKey 任务标识
     * @return 任务地址
     */
    private URI taskUrl(AccessMode accessMode, String taskKey) {
        return buildUri(accessMode, namespace(), "task", requireText(taskKey, "任务标识不能为空"));
    }

    /**
     * 构建任务触发地址
     *
     * @param accessMode 访问模式
     * @param taskKey 任务标识
     * @return 任务触发地址
     */
    private URI taskTriggerUrl(AccessMode accessMode, String taskKey) {
        return buildUri(accessMode, namespace(), "task", requireText(taskKey, "任务标识不能为空"), "trigger");
    }

    /**
     * 构建控制台登录地址
     *
     * @return 控制台登录地址
     */
    private URI consoleLoginUrl() {
        return buildRawUri("job-console", "auth", "login");
    }

    /**
     * 按访问模式拼接接口地址
     *
     * @param accessMode 访问模式
     * @param namespace 命名空间
     * @param pathSegments 路径片段
     * @return 拼接后的地址
     */
    private URI buildUri(AccessMode accessMode, String namespace, String... pathSegments) {
        if (accessMode == AccessMode.CONSOLE) {
            String[] segments = new String[pathSegments.length + 3];
            segments[0] = "job-console";
            segments[1] = "api";
            segments[2] = namespace;
            System.arraycopy(pathSegments, 0, segments, 3, pathSegments.length);
            return buildRawUri(segments);
        }
        String[] segments = new String[pathSegments.length + 3];
        segments[0] = "v1";
        segments[1] = "job-platform";
        segments[2] = namespace;
        System.arraycopy(pathSegments, 0, segments, 3, pathSegments.length);
        return buildRawUri(segments);
    }

    /**
     * 拼接原始地址
     *
     * @param pathSegments 路径片段
     * @return 拼接后的地址
     */
    private URI buildRawUri(String... pathSegments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl());
        for (String pathSegment : pathSegments) {
            builder.pathSegment(pathSegment);
        }
        return builder.build().encode().toUri();
    }

    /**
     * 调度平台基础地址
     *
     */
    private String baseUrl() {
        String value = environment.getProperty("plugin.payment.scheduler.platform-base-url",
                "http://127.0.0.1:18083/scheduler/api");
        String normalized = requireText(value, "调度平台地址不能为空");
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    /**
     * 调度平台命名空间
     *
     */
    private String namespace() {
        return requireText(environment.getProperty("plugin.payment.scheduler.platform-namespace", "payment"),
                "调度平台命名空间不能为空");
    }

    /**
     * 控制台账号
     *
     * @return 控制台账号
     */
    private String consoleUsername() {
        return requireText(environment.getProperty("plugin.payment.scheduler.platform-console-username",
                DEFAULT_CONSOLE_USERNAME), "调度平台控制台账号不能为空");
    }

    /**
     * 控制台密码
     *
     * @return 控制台密码
     */
    private String consolePassword() {
        return requireText(environment.getProperty("plugin.payment.scheduler.platform-console-password",
                DEFAULT_CONSOLE_PASSWORD), "调度平台控制台密码不能为空");
    }

    /**
     * 校验必填文本
     *
     * @param value 值
     * @param message 错误消息
     * @return 去除首尾空白后的文本
     */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new PaymentException(message);
        }
        return value.trim();
    }

    /**
     * 访问模式枚举
     *
     * @author CH
     * @since 4.0.0
     */
    private enum AccessMode {
        PLATFORM,
        CONSOLE
    }
}
