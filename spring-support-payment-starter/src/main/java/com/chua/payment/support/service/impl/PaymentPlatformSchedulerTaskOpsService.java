package com.chua.payment.support.service.impl;

import com.chua.payment.support.dto.PaymentSchedulerTaskUpdateDTO;
import com.chua.payment.support.exception.PaymentException;
import com.chua.payment.support.service.PaymentSchedulerTaskOpsService;
import com.chua.payment.support.vo.PaymentSchedulerTaskVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.ObjectProvider;
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
 * 基于 scheduler 平台的支付调度任务运维服务。
 * <p>
 * payment 在 {@code engine=job} 模式下不再直接管理本地调度器，而是将运营端的
 * 查询、修改和手工触发请求转发到 scheduler 平台对应 namespace 的任务接口。
 * </p>
 */
@Service
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "engine", havingValue = "job")
public class PaymentPlatformSchedulerTaskOpsService implements PaymentSchedulerTaskOpsService {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_CONSOLE_USERNAME = "admin";
    private static final String DEFAULT_CONSOLE_PASSWORD = "admin123456";
    private static final DateTimeFormatter STANDARD_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final Object consoleSessionMonitor = new Object();

    /**
     * 轻量控制台模式下缓存 scheduler 返回的 Cookie。
     * <p>
     * 这是给 Spring 内置简单页使用的固定账号密码会话，不承载多用户语义；
     * 真正的开放接口仍然优先走 OAuth Header 转发。
     * </p>
     */
    private volatile String schedulerConsoleCookie;

    public PaymentPlatformSchedulerTaskOpsService(Environment environment,
                                                  ObjectMapper objectMapper,
                                                  ObjectProvider<RestTemplateBuilder> restTemplateBuilderProvider) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        RestTemplateBuilder builder = restTemplateBuilderProvider.getIfAvailable(RestTemplateBuilder::new);
        this.restTemplate = builder
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .readTimeout(DEFAULT_READ_TIMEOUT)
                .build();
    }

    @Override
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
    public PaymentSchedulerTaskVO updateTask(String taskKey, PaymentSchedulerTaskUpdateDTO dto) {
        AccessMode accessMode = resolveAccessMode();
        return toTaskVO(request(HttpMethod.PUT, taskUrl(accessMode, taskKey), dto, accessMode));
    }

    @Override
    public PaymentSchedulerTaskVO triggerTask(String taskKey) {
        AccessMode accessMode = resolveAccessMode();
        return toTaskVO(request(HttpMethod.POST, taskTriggerUrl(accessMode, taskKey), null, accessMode));
    }

    /**
     * 使用统一的 JSON 协议访问 scheduler 平台。
     * <p>
     * 这里对空响应、业务码失败和反序列化异常都显式抛出 PaymentException，
     * 避免运营台收到模糊的 NPE 或日期解析异常。
     * </p>
     */
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

    private ResponseEntity<String> exchange(HttpMethod method,
                                            URI uri,
                                            Object body,
                                            AccessMode accessMode,
                                            boolean allowConsoleRelogin) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (accessMode == AccessMode.PLATFORM) {
                copyAuthHeaders(headers);
            } else {
                ensureConsoleSession(false);
                applyConsoleCookie(headers);
            }
            HttpEntity<?> entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
            return restTemplate.exchange(uri, method, entity, String.class);
        } catch (HttpStatusCodeException e) {
            if (accessMode == AccessMode.CONSOLE
                    && allowConsoleRelogin
                    && (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN)) {
                clearConsoleSession();
                ensureConsoleSession(true);
                return exchange(method, uri, body, accessMode, false);
            }
            throw e;
        }
    }

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

    private void clearConsoleSession() {
        synchronized (consoleSessionMonitor) {
            schedulerConsoleCookie = null;
        }
    }

    private void applyConsoleCookie(HttpHeaders headers) {
        if (StringUtils.hasText(schedulerConsoleCookie)) {
            headers.set(HttpHeaders.COOKIE, schedulerConsoleCookie);
        }
    }

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

    private String toRequestCookie(String rawCookie) {
        if (!StringUtils.hasText(rawCookie)) {
            return null;
        }
        int delimiterIndex = rawCookie.indexOf(';');
        return delimiterIndex >= 0 ? rawCookie.substring(0, delimiterIndex).trim() : rawCookie.trim();
    }

    /**
     * 运营中心通过 payment 服务代理访问 scheduler 平台时，需要把当前登录态继续向下游透传。
     * scheduler 同时兼容 `x-oauth-token` 与 `Authorization: Bearer ...` 两种写法，这里保持原样复制。
     */
    private void copyAuthHeaders(HttpHeaders headers) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        copyHeaderIfPresent(request, headers, "x-oauth-token");
        copyHeaderIfPresent(request, headers, HttpHeaders.AUTHORIZATION);
    }

    private void copyHeaderIfPresent(HttpServletRequest request, HttpHeaders headers, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (StringUtils.hasText(headerValue)) {
            headers.set(headerName, headerValue);
        }
    }

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

    private boolean hasForwardedAuthHeaders() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        return hasHeader(request, "x-oauth-token") || hasHeader(request, HttpHeaders.AUTHORIZATION);
    }

    private boolean hasHeader(HttpServletRequest request, String headerName) {
        return request != null && StringUtils.hasText(request.getHeader(headerName));
    }

    private PaymentSchedulerTaskVO toTaskVO(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw new PaymentException("调度平台返回空任务数据");
        }
        PaymentSchedulerTaskVO vo = new PaymentSchedulerTaskVO();
        vo.setTaskKey(node.path("taskKey").asText());
        vo.setTaskName(node.path("taskName").asText());
        vo.setCronExpression(node.path("cronExpression").asText());
        vo.setEnabled(node.path("enabled").asBoolean());
        vo.setDescription(node.path("description").asText());
        vo.setScheduled(node.path("scheduled").asBoolean());
        vo.setNextExecutionTime(parseTime(node.path("nextExecutionTime").asText(null)));
        vo.setLastStartedAt(parseTime(node.path("lastStartedAt").asText(null)));
        vo.setLastFinishedAt(parseTime(node.path("lastFinishedAt").asText(null)));
        vo.setLastRunStatus(node.path("lastRunStatus").asText(null));
        vo.setLastRunMessage(node.path("lastRunMessage").asText(null));
        return vo;
    }

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

    private URI taskListUrl(AccessMode accessMode) {
        return buildUri(accessMode, namespace(), "task", "list");
    }

    private URI taskUrl(AccessMode accessMode, String taskKey) {
        return buildUri(accessMode, namespace(), "task", requireText(taskKey, "任务标识不能为空"));
    }

    private URI taskTriggerUrl(AccessMode accessMode, String taskKey) {
        return buildUri(accessMode, namespace(), "task", requireText(taskKey, "任务标识不能为空"), "trigger");
    }

    private URI consoleLoginUrl() {
        return buildRawUri("job-console", "auth", "login");
    }

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

    private URI buildRawUri(String... pathSegments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl());
        for (String pathSegment : pathSegments) {
            builder.pathSegment(pathSegment);
        }
        return builder.build().encode().toUri();
    }

    private String baseUrl() {
        String value = environment.getProperty("plugin.payment.scheduler.platform-base-url",
                "http://127.0.0.1:18083/scheduler/api");
        String normalized = requireText(value, "调度平台地址不能为空");
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String namespace() {
        return requireText(environment.getProperty("plugin.payment.scheduler.platform-namespace", "payment"),
                "调度平台命名空间不能为空");
    }

    private String consoleUsername() {
        return requireText(environment.getProperty("plugin.payment.scheduler.platform-console-username",
                DEFAULT_CONSOLE_USERNAME), "调度平台控制台账号不能为空");
    }

    private String consolePassword() {
        return requireText(environment.getProperty("plugin.payment.scheduler.platform-console-password",
                DEFAULT_CONSOLE_PASSWORD), "调度平台控制台密码不能为空");
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new PaymentException(message);
        }
        return value.trim();
    }

    private enum AccessMode {
        PLATFORM,
        CONSOLE
    }
}
