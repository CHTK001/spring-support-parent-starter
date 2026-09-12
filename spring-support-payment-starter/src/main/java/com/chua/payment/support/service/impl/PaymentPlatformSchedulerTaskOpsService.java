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
* 基于 调度器 平台的支付调度任务运维服务。
* <p>
* 薪酬 在 {@code engine=job} 模式下不再直接管理本地调度器，而是将运营端的
* 查询、修改和手工触发请求转发到 调度器 平台对应 namespace 的任务接口。
* </p>
*
* @author CH
* @since 4.0.0.50
 */
@Service
@ConditionalOnProperty(prefix = "plugin.payment.scheduler", name = "engine", havingValue = "job")
public class PaymentPlatformSchedulerTaskOpsService implements PaymentSchedulerTaskOpsService {

    /** 默认_连接_超时 */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 默认_读取_超时 */
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);
    /** 默认_控制台_用户名 */
    private static final String DEFAULT_CONSOLE_USERNAME = "admin";
    /** 默认_控制台_密码 */
    private static final String DEFAULT_CONSOLE_PASSWORD = "${payment.console.password:}";
    /** 标准_日期_时间 */
    private static final DateTimeFormatter STANDARD_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 环境 */
    private final Environment environment;
    /** Objectmapper */
    private final ObjectMapper objectMapper;
    /** Rest模板 */
    private final RestTemplate restTemplate;
    private final Object consoleSessionMonitor = new Object(); // 控制台会话监控

    /**
    * 轻量控制台模式下缓存 调度器 返回的 Cookie。
    * <p>
    * 这是给 Spring 内置简单页使用的固定账号密码会话，不承载多用户语义；
    * 真正的开放接口仍然优先走 o认证 头部 转发。
    * </p>
     */
    private volatile String schedulerConsoleCookie;

    /**
    * 创建 薪酬platform调度器任务ops服务 实例
    * @param environment 环境
    * @param objectMapper 对象映射器
    * @param restTemplate RestTemplate
     */
    public PaymentPlatformSchedulerTaskOpsService(Environment environment,
                                                  ObjectMapper objectMapper,
                                                  RestTemplate restTemplate) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    /** 列表任务 */
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
    /** 更新任务 */
    public PaymentSchedulerTaskVO updateTask(String taskKey, PaymentSchedulerTaskUpdateDTO dto) {
        AccessMode accessMode = resolveAccessMode();
        return toTaskVO(request(HttpMethod.PUT, taskUrl(accessMode, taskKey), dto, accessMode));
    }

    @Override
    /** trigger任务 */
    public PaymentSchedulerTaskVO triggerTask(String taskKey) {
        AccessMode accessMode = resolveAccessMode();
        return toTaskVO(request(HttpMethod.POST, taskTriggerUrl(accessMode, taskKey), null, accessMode));
    }

    /**
    * 使用统一的 JSON 协议访问 调度器 平台。
    * <p>
    * 这里对空响应、业务码失败和反序列化异常都显式抛出 薪酬异常，
    * 避免运营台收到模糊的 NPE 或日期解析异常。
    * </p>
    * @param method 方法
    * @param uri uri
    * @param body 主体
    * @param accessMode accessmode
    * @return 请求的结果
     */
    private JsonNode request(HttpMethod method, URI uri, Object body, AccessMode accessMode) {
        try {
            ResponseEntity<String> response = exchange(method, uri, body, accessMode, true);
            String responseBody = response.getBody();
            if (!StringUtils.hasText(responseBody)) {
                /**
                * 薪酬异常。
                * @param uri uri
                * @return 薪酬异常的结果
                 */
                throw new PaymentException("调度平台返回空响应: " + uri);
            }
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode code = root.get("code");
            if (code != null && !"00000".equals(code.asText()) && !"200".equals(code.asText())) {
                /**
                * 薪酬异常。
                * @param root.path("msg" 根.路径("msg"
                * @return 薪酬异常的结果
                 */
                throw new PaymentException("调度平台调用失败: " + root.path("msg").asText(root.path("message").asText()));
            }
            return root.path("data");
        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            /**
            * 薪酬异常。
            * @param e.getMessage( e.获取消息(
            * @return 薪酬异常的结果
             */
            throw new PaymentException("调用调度平台失败: " + e.getMessage(), e);
        }
    }

    /**
    * Exchange
    * @param method 方法
    * @param uri uri
    * @param body 主体
    * @param accessMode accessmode
    * @param allowConsoleRelogin allow控制台relogin
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
                * 副本认证头部。
                * @param headers 头部
                 */
                copyAuthHeaders(headers);
            } else {
                /**
                * ensure控制台会话。
                * @param false false
                 */
                ensureConsoleSession(false);
                /**
                * apply控制台Cookie。
                * @param headers 头部
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
                * clear控制台会话。
                 */
                clearConsoleSession();
                /**
                * ensure控制台会话。
                * @param true true
                 */
                ensureConsoleSession(true);
                return exchange(method, uri, body, accessMode, false);
            }
            throw e;
        }
    }

    /**
    * ensure控制台会话
    *
    * @param forceRefresh forcerefresh
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
                    * 控制台loginurl。
                     */
                    consoleLoginUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    String.class);
            String responseBody = response.getBody();
            if (!StringUtils.hasText(responseBody)) {
                /**
                * 薪酬异常。
                * @param "调度平台控制台登录返回空响应" "调度平台控制台登录返回空响应"
                * @return 薪酬异常的结果
                 */
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
                /**
                * 薪酬异常。
                * @param e.getMessage( e.获取消息(
                * @return 薪酬异常的结果
                 */
                throw new PaymentException("解析调度平台控制台登录响应失败: " + e.getMessage(), e);
            }
            schedulerConsoleCookie = extractCookieHeader(response.getHeaders());
        }
    }

    /** clear控制台会话 */
    private void clearConsoleSession() {
        synchronized (consoleSessionMonitor) {
            schedulerConsoleCookie = null;
        }
    }

    /**
    * 应用控制台Cookie
    *
    * @param headers 头部
     */
    private void applyConsoleCookie(HttpHeaders headers) {
        if (StringUtils.hasText(schedulerConsoleCookie)) {
            headers.set(HttpHeaders.COOKIE, schedulerConsoleCookie);
        }
    }

    /**
    * extractCookie头部
    *
    * @param headers 头部
    * @return extractCookie头部的结果
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
    * 转为请求Cookie
    *
    * @param rawCookie rawCookie
    * @return 转为请求Cookie的结果
     */
    private String toRequestCookie(String rawCookie) {
        if (!StringUtils.hasText(rawCookie)) {
            return null;
        }
        int delimiterIndex = rawCookie.indexOf(';');
        return delimiterIndex >= 0 ? rawCookie.substring(0, delimiterIndex).trim() : rawCookie.trim();
    }

    /**
    * 运营中心通过 薪酬 服务代理访问 调度器 平台时，需要把当前登录态继续向下游透传。
    * 调度器 同时兼容 `x-oauth-token` 与 `Authorization: Bearer ...` 两种写法，这里保持原样复制。
    * @param headers 头部
     */
    private void copyAuthHeaders(HttpHeaders headers) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        /**
        * 副本头部ifpresent。
        * @param request 请求
        * @param headers 头部
        * @param "x-oauth-token" "x-OAuth-令牌"
         */
        copyHeaderIfPresent(request, headers, "x-oauth-token");
        /**
        * 副本头部ifpresent。
        * @param request 请求
        * @param headers 头部
        * @param HttpHeaders.AUTHORIZATION http头部.授权
         */
        copyHeaderIfPresent(request, headers, HttpHeaders.AUTHORIZATION);
    }

    /**
    * 复制头部ifpresent
    *
    * @param request 请求
    * @param headers 头部
    * @param headerName 头部名称
     */
    private void copyHeaderIfPresent(HttpServletRequest request, HttpHeaders headers, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (StringUtils.hasText(headerValue)) {
            headers.set(headerName, headerValue);
        }
    }

    /**
    * 解析accessmode
    *
    * @return resolveAccessMode的结果
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
    * 是否拥有远期认证头部
    *
    * @return 是否包含远期认证头部的结果
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
    * 是否拥有头部
    *
    * @param request 请求
    * @param headerName 头部名称
    * @return 是否包含头部的结果
     */
    private boolean hasHeader(HttpServletRequest request, String headerName) {
        return request != null && StringUtils.hasText(request.getHeader(headerName));
    }

    /**
    * 转为任务vo
    *
    * @param node 节点
    * @return 转为任务vo的结果
     */
    private PaymentSchedulerTaskVO toTaskVO(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            /**
            * 薪酬异常。
            * @param "调度平台返回空任务数据" "调度平台返回空任务数据"
            * @return 薪酬异常的结果
             */
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

    /**
    * 解析时间
    *
    * @param value 价值观
    * @return 解析时间的结果
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
        /**
        * 薪酬异常。
        * @param value 价值观
        * @return 薪酬异常的结果
         */
        throw new PaymentException("无法解析调度平台返回的时间字段: " + value);
    }

    /**
    * 任务列表url
    *
    * @param accessMode accessmode
    * @return 任务列表url的结果
     */
    private URI taskListUrl(AccessMode accessMode) {
        return buildUri(accessMode, namespace(), "task", "list");
    }

    /**
    * 任务url
    *
    * @param accessMode accessmode
    * @param taskKey 任务钥匙
    * @return 任务url的结果
     */
    private URI taskUrl(AccessMode accessMode, String taskKey) {
        return buildUri(accessMode, namespace(), "task", requireText(taskKey, "任务标识不能为空"));
    }

    /**
    * 任务triggerurl
    *
    * @param accessMode accessmode
    * @param taskKey 任务钥匙
    * @return 任务triggerurl的结果
     */
    private URI taskTriggerUrl(AccessMode accessMode, String taskKey) {
        return buildUri(accessMode, namespace(), "task", requireText(taskKey, "任务标识不能为空"), "trigger");
    }

    /**
    * 控制台loginurl
    *
    * @return 控制台loginurl的结果
     */
    private URI consoleLoginUrl() {
        return buildRawUri("job-console", "auth", "login");
    }

    /**
    * 构建Uri
    *
    * @param accessMode accessmode
    * @param namespace namespace
    * @param pathSegments 路径segments
    * @return 构建uri的结果
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
    * 构建rawuri
    *
    * @param pathSegments 路径segments
    * @return 构建rawuri的结果
     */
    private URI buildRawUri(String... pathSegments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl());
        for (String pathSegment : pathSegments) {
            builder.pathSegment(pathSegment);
        }
        return builder.build().encode().toUri();
    }

    /**
    * baseurl
    *
    * @return baseUrl的结果
     */
    private String baseUrl() {
        String value = environment.getProperty("plugin.payment.scheduler.platform-base-url",
                "http://127.0.0.1:18083/scheduler/api");
        String normalized = requireText(value, "调度平台地址不能为空");
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    /**
    * Namespace
    *
    * @return namespace的结果
     */
    private String namespace() {
        return requireText(environment.getProperty("plugin.payment.scheduler.platform-namespace", "payment"),
                "调度平台命名空间不能为空");
    }

    /**
    * 控制台用户名
    *
    * @return 控制台用户名的结果
     */
    private String consoleUsername() {
        return requireText(environment.getProperty("plugin.payment.scheduler.platform-console-username",
                DEFAULT_CONSOLE_USERNAME), "调度平台控制台账号不能为空");
    }

    /**
    * 控制台密码
    *
    * @return 控制台密码的结果
     */
    private String consolePassword() {
        return requireText(environment.getProperty("plugin.payment.scheduler.platform-console-password",
                DEFAULT_CONSOLE_PASSWORD), "调度平台控制台密码不能为空");
    }

    /**
    * require文本
    *
    * @param value 价值观
    * @param message 消息
    * @return require文本的结果
     */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            /**
            * 薪酬异常。
            * @param message 消息
            * @return 薪酬异常的结果
             */
            throw new PaymentException(message);
        }
        return value.trim();
    }

    /**
    * accessmode枚举。
    *
    * @author CH
    * @since 4.0.0
     */
    private enum AccessMode {
        PLATFORM,
        CONSOLE
    }
}

