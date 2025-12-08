package com.chua.starter.common.support.api.interceptor;

import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.annotations.ApiDeprecated;
import com.chua.starter.common.support.api.annotations.ApiFeature;
import com.chua.starter.common.support.api.annotations.ApiInternal;
import com.chua.starter.common.support.api.annotations.ApiMock;
import com.chua.starter.common.support.api.feature.ApiFeatureManager;
import com.chua.starter.common.support.api.properties.ApiProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * API 控制拦截器
 * <p>
 * 处理 @ApiMock、@ApiDeprecated、@ApiFeature 注解的拦截逻辑。
 * </p>
 *
 * @author CH
 * @since 2024/12/08
 * @version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ApiControlInterceptor implements HandlerInterceptor {

    private final ApiProperties apiProperties;
    private final Environment environment;
    private final ApiFeatureManager featureManager;

    /**
     * 内网IP正则表达式
     */
    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
            "^(127\\.)|(10\\.)|(172\\.(1[6-9]|2[0-9]|3[0-1])\\.)|(192\\.168\\.)|(::1)|(0:0:0:0:0:0:0:1)$"
    );

    /**
     * 内部接口标识属性名
     */
    public static final String ATTR_SKIP_AUTH = "API_INTERNAL_SKIP_AUTH";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 处理 @ApiInternal（优先处理，用于设置跳过鉴权标识）
        if (!handleApiInternal(handlerMethod, request, response)) {
            return false;
        }

        // 处理 @ApiFeature
        if (!handleApiFeature(handlerMethod, request, response)) {
            return false;
        }

        // 处理 @ApiMock
        if (!handleApiMock(handlerMethod, request, response)) {
            return false;
        }

        // 处理 @ApiDeprecated
        if (!handleApiDeprecated(handlerMethod, request, response)) {
            return false;
        }

        return true;
    }

    /**
     * 处理 @ApiInternal 注解
     * <p>
     * 校验请求是否来自内网IP或白名单
     * </p>
     */
    private boolean handleApiInternal(HandlerMethod handlerMethod, HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        ApiInternal apiInternal = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), ApiInternal.class);
        if (apiInternal == null) {
            apiInternal = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), ApiInternal.class);
        }

        if (apiInternal == null) {
            return true;
        }

        // 设置跳过鉴权标识
        if (apiInternal.skipAuth()) {
            request.setAttribute(ATTR_SKIP_AUTH, true);
        }

        String clientIp = getClientIp(request);
        log.debug("内部接口访问检查: uri={}, clientIp={}", request.getRequestURI(), clientIp);

        // 检查是否允许内网IP
        if (apiInternal.allowPrivateNetwork() && isPrivateIp(clientIp)) {
            log.debug("内网IP访问内部接口: {}", clientIp);
            return true;
        }

        // 检查IP白名单
        String[] allowedIps = apiInternal.allowedIps();
        if (allowedIps.length > 0) {
            for (String allowedIp : allowedIps) {
                if (matchIp(clientIp, allowedIp)) {
                    log.debug("白名单IP访问内部接口: {}", clientIp);
                    return true;
                }
            }
        }

        // 检查服务名白名单
        String[] allowedServices = apiInternal.allowedServices();
        if (allowedServices.length > 0) {
            String serviceName = request.getHeader("X-Service-Name");
            if (StringUtils.isNotBlank(serviceName)) {
                for (String allowedService : allowedServices) {
                    if (allowedService.equalsIgnoreCase(serviceName)) {
                        log.debug("白名单服务访问内部接口: {}", serviceName);
                        return true;
                    }
                }
            }
        }

        // 如果配置了白名单但未匹配，拒绝访问
        if (allowedIps.length > 0 || allowedServices.length > 0) {
            log.warn("非授权访问内部接口: uri={}, clientIp={}", request.getRequestURI(), clientIp);
            writeResponse(response, apiInternal.status(), ReturnResult.error(apiInternal.message()));
            return false;
        }

        // 默认情况：未开启内网访问且无白名单配置，拒绝访问
        if (!apiInternal.allowPrivateNetwork()) {
            log.warn("内部接口未配置访问规则: uri={}", request.getRequestURI());
            writeResponse(response, apiInternal.status(), ReturnResult.error(apiInternal.message()));
            return false;
        }

        // 非内网IP访问
        log.warn("非内网IP访问内部接口被拒绝: uri={}, clientIp={}", request.getRequestURI(), clientIp);
        writeResponse(response, apiInternal.status(), ReturnResult.error(apiInternal.message()));
        return false;
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 判断是否为内网IP
     */
    private boolean isPrivateIp(String ip) {
        if (StringUtils.isBlank(ip)) {
            return false;
        }
        // localhost
        if ("localhost".equalsIgnoreCase(ip)) {
            return true;
        }
        return PRIVATE_IP_PATTERN.matcher(ip).find();
    }

    /**
     * 匹配IP（支持CIDR格式）
     */
    private boolean matchIp(String clientIp, String pattern) {
        if (StringUtils.isBlank(clientIp) || StringUtils.isBlank(pattern)) {
            return false;
        }
        // 精确匹配
        if (clientIp.equals(pattern)) {
            return true;
        }
        // 简单通配符匹配（如 192.168.1.*）
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return clientIp.matches(regex);
        }
        // CIDR格式匹配（如 192.168.1.0/24）
        if (pattern.contains("/")) {
            return matchCidr(clientIp, pattern);
        }
        return false;
    }

    /**
     * CIDR格式IP匹配
     */
    private boolean matchCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            String baseIp = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            long ipLong = ipToLong(ip);
            long baseIpLong = ipToLong(baseIp);
            long mask = -1L << (32 - prefixLength);

            return (ipLong & mask) == (baseIpLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * IP转长整型
     */
    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return 0;
        }
        long result = 0;
        for (String part : parts) {
            result = result * 256 + Integer.parseInt(part);
        }
        return result;
    }

    /**
     * 处理 @ApiFeature 注解
     */
    private boolean handleApiFeature(HandlerMethod handlerMethod, HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        var apiFeature = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), ApiFeature.class);
        if (apiFeature == null) {
            apiFeature = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), ApiFeature.class);
        }

        if (apiFeature == null) {
            return true;
        }

        String featureId = apiFeature.value();
        if (!featureManager.isEnabled(featureId)) {
            log.debug("功能开关已关闭: {}", featureId);
            writeResponse(response, apiFeature.disabledStatus(),
                    ReturnResult.error(apiFeature.disabledMessage()));
            return false;
        }

        return true;
    }

    /**
     * 处理 @ApiMock 注解
     */
    private boolean handleApiMock(HandlerMethod handlerMethod, HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        var apiMock = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), ApiMock.class);
        if (apiMock == null) {
            return true;
        }

        // 检查是否在允许的环境中
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = new String[]{"default"};
        }

        boolean matched = false;
        for (String profile : apiMock.profile()) {
            if (ArrayUtils.containsIgnoreCase(profiles, profile)) {
                matched = true;
                break;
            }
        }

        if (!matched) {
            // 不在 Mock 环境中，正常执行
            return true;
        }

        // 检查全局 Mock 开关
        if (!isMockEnabled()) {
            return true;
        }

        // 模拟延迟
        if (apiMock.delay() > 0) {
            try {
                Thread.sleep(apiMock.delay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 获取 Mock 响应
        String mockResponse = getMockResponse(apiMock);
        if (StringUtils.isBlank(mockResponse)) {
            return true;
        }

        log.debug("返回 Mock 数据: {} -> {}", request.getRequestURI(), apiMock.description());
        response.setStatus(apiMock.status());
        response.setContentType(apiMock.contentType());
        response.getWriter().write(mockResponse);
        return false;
    }

    /**
     * 处理 @ApiDeprecated 注解
     */
    private boolean handleApiDeprecated(HandlerMethod handlerMethod, HttpServletRequest request,
                                        HttpServletResponse response) throws IOException {
        var apiDeprecated = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), ApiDeprecated.class);
        if (apiDeprecated == null) {
            apiDeprecated = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), ApiDeprecated.class);
        }

        if (apiDeprecated == null) {
            return true;
        }

        // 获取请求版本
        double requestVersion = getRequestVersion(request);
        double sinceVersion = parseVersion(apiDeprecated.since());
        double removedVersion = StringUtils.isBlank(apiDeprecated.removedIn())
                ? Double.MAX_VALUE
                : parseVersion(apiDeprecated.removedIn());

        // 添加废弃警告头
        if (apiDeprecated.addWarningHeader()) {
            response.setHeader("X-API-Deprecated", "true");
            response.setHeader("X-API-Deprecated-Since", apiDeprecated.since());
            response.setHeader("X-API-Deprecated-Message", apiDeprecated.message());
            if (StringUtils.isNotBlank(apiDeprecated.replacement())) {
                response.setHeader("X-API-Deprecated-Replacement", apiDeprecated.replacement());
            }
        }

        // 如果请求版本 >= 移除版本，返回 410 Gone
        if (requestVersion >= removedVersion) {
            log.warn("接口已移除: {} (removed in {})", request.getRequestURI(), apiDeprecated.removedIn());
            writeResponse(response, 410, ReturnResult.error("此接口已被移除"));
            return false;
        }

        // 如果请求版本 >= 废弃版本
        if (requestVersion >= sinceVersion) {
            // 有替代接口，返回提示
            if (StringUtils.isNotBlank(apiDeprecated.replacement())) {
                log.debug("接口已废弃，建议使用: {}", apiDeprecated.replacement());
                // 继续执行，但在响应头中提示
                return true;
            } else {
                // 没有替代接口，返回空结果
                log.debug("接口已废弃，无替代接口，返回空: {}", request.getRequestURI());
                writeResponse(response, 200, ReturnResult.ok(null));
                return false;
            }
        }

        return true;
    }

    /**
     * 检查 Mock 功能是否开启
     */
    private boolean isMockEnabled() {
        return environment.getProperty("plugin.api.mock.enable", Boolean.class, true);
    }

    /**
     * 获取 Mock 响应内容
     */
    private String getMockResponse(ApiMock apiMock) {
        // 优先使用 response 属性
        if (StringUtils.isNotBlank(apiMock.response())) {
            return apiMock.response();
        }

        // 从文件读取
        if (StringUtils.isNotBlank(apiMock.responseFile())) {
            try {
                var resource = new ClassPathResource(apiMock.responseFile());
                if (resource.exists()) {
                    return IoUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
                } else {
                    log.warn("Mock 文件不存在: {}", apiMock.responseFile());
                }
            } catch (IOException e) {
                log.error("读取 Mock 文件失败: {}", apiMock.responseFile(), e);
            }
        }

        return null;
    }

    /**
     * 获取请求中的 API 版本
     */
    private double getRequestVersion(HttpServletRequest request) {
        // 从请求头获取
        String versionHeader = request.getHeader("X-API-Version");
        if (StringUtils.isNotBlank(versionHeader)) {
            return parseVersion(versionHeader);
        }

        // 从 URL 路径解析 (如 /api/v2/users)
        String uri = request.getRequestURI();
        var matcher = java.util.regex.Pattern.compile("/v(\\d+(?:\\.\\d+)?)/").matcher(uri);
        if (matcher.find()) {
            return parseVersion(matcher.group(1));
        }

        // 从查询参数获取
        String versionParam = request.getParameter("version");
        if (StringUtils.isNotBlank(versionParam)) {
            return parseVersion(versionParam);
        }

        // 默认版本 1.0
        return 1.0;
    }

    /**
     * 解析版本号
     */
    private double parseVersion(String version) {
        try {
            return Double.parseDouble(version);
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    /**
     * 写入 JSON 响应
     */
    private void writeResponse(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Json.toJson(body));
    }
}
