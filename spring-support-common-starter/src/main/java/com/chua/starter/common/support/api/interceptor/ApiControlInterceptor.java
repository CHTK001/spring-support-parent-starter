package com.chua.starter.common.support.api.interceptor;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.lang.version.Version;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.annotations.ApiDeprecated;
import com.chua.starter.common.support.api.annotations.ApiFeature;
import com.chua.starter.common.support.api.annotations.ApiGray;
import com.chua.starter.common.support.api.annotations.ApiInternal;
import com.chua.starter.common.support.api.annotations.ApiMock;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.api.feature.ApiFeatureManager;
import com.chua.starter.common.support.api.feature.ApiSwitchOverrideProvider;
import com.chua.starter.common.support.api.gray.ApiGrayEvaluator;
import com.chua.starter.common.support.api.permission.PermissionRules;
import com.chua.starter.common.support.api.properties.ApiProperties;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import com.chua.starter.common.support.utils.IpUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * API 控制拦截器
 * <p>
 * 处理 @apimock、@api已弃用、@api特征 注解的拦截逻辑。
 * </p>
 *
 * @since 2024/12/08
 * @author CH
 */
public class ApiControlInterceptor implements HandlerInterceptor  {
    /**
     * 日志
    */
    private static final Logger log = LoggerFactory.getLogger(ApiControlInterceptor.class);
    /**
     * 构造函数
     *
     * @param apiProperties api属性
     * @param environment 环境
     * @param featureManager api特征管理器
     * @param overrideProvider 运行时覆盖提供者（可空：未注册实现时走默认判定）
     * @param authServiceProvider 当前身份提供者（可空：未接入认证体系时视为匿名）
     */
    public ApiControlInterceptor(ApiProperties apiProperties, Environment environment, ApiFeatureManager featureManager,
                                 ObjectProvider<ApiSwitchOverrideProvider> overrideProvider,
                                 ObjectProvider<AuthService> authServiceProvider) {
        this.apiProperties = apiProperties;
        this.environment = environment;
        this.featureManager = featureManager;
        this.overrideProvider = overrideProvider.getIfAvailable();
        this.authService = authServiceProvider.getIfAvailable();
    }

        /**
         * API属性
        */
        private final ApiProperties apiProperties;
    /**
     * 环境
    */
    private final Environment environment;
    /**
     * 特征管理器
    */
    private final ApiFeatureManager featureManager;
    /**
     * 运行时覆盖提供者（可空）
    */
    private final ApiSwitchOverrideProvider overrideProvider;
    /**
     * 当前身份服务（可空），角色与权限的唯一可信来源
    */
    private final AuthService authService;
    /**
     * Grayevaluator
    */
    private final ApiGrayEvaluator grayEvaluator = new ApiGrayEvaluator();

    /**
     * 应用灰度运行时覆盖：无覆盖时原样返回注解；有覆盖时经动态代理合并——
     * percentage/角色/用户/头部/rule 取覆盖值（未覆盖字段沿用注解），其余属性委托原注解。
     *
     * @param apiGray 原注解
     * @return 生效的灰度规则
     */
    private ApiGray applyGrayOverride(ApiGray apiGray) {
        if (overrideProvider == null) {
            return apiGray;
        }
        ApiSwitchOverrideProvider.GrayOverride override = overrideProvider.grayOverride(apiGray.value());
        if (override == null) {
            return apiGray;
        }
        return ReflectUtils.newProxy(
                apiGray.getClass().getClassLoader(), new Class<?>[]{ApiGray.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "percentage":
                            return override.percentage() != null ? override.percentage() : apiGray.percentage();
                        case "roles":
                            return override.roles() != null ? override.roles() : apiGray.roles();
                        case "users":
                            return override.users() != null ? override.users() : apiGray.users();
                        case "headers":
                            return override.headers() != null ? override.headers() : apiGray.headers();
                        case "rule":
                            return StringUtils.hasText(override.rule()) ? override.rule() : apiGray.rule();
                        default:
                            return ReflectUtils.invoke(apiGray, method.getName(), Object.class, args);
                    }
                });
    }

    /**
     * 语义化版本正则（预编译）
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("/v([0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z.-]+)?)/");

    /**
     * 内部接口标识属性名
     */
    public static final String ATTR_SKIP_AUTH = "API_INTERNAL_SKIP_AUTH";

    @Override
    /**
     * Pre处理
    */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

 // 处理 @api内部（优先处理，用于设置跳过鉴权标识）
        if (!handleApiInternal(handlerMethod, request, response)) {
            return false;
        }

 // 处理 @api特征
        if (!handleApiFeature(handlerMethod, request, response)) {
            return false;
        }

 // 处理 @apimock
        if (!handleApiMock(handlerMethod, request, response)) {
            return false;
        }

 // 处理 @api已弃用
        if (!handleApiDeprecated(handlerMethod, request, response)) {
            return false;
        }

 // 处理 @apigray
        if (!handleApiGray(handlerMethod, request, response)) {
            return false;
        }

 // 处理 @权限（角色 / 权限标识 校验）
        if (!handlePermission(handlerMethod, request, response)) {
            return false;
        }

        return true;
    }

    /**
     * 处理 @api内部 注解
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

        String clientIp = IpUtils.getClientIp(request);
        log.debug("内部接口访问检查: uri={}, clientIp={}", request.getRequestURI(), clientIp);

        // 检查是否允许内网IP
        if (apiInternal.allowPrivateNetwork() && IpUtils.isPrivateIp(clientIp)) {
            log.debug("内网IP访问内部接口: {}", clientIp);
            return true;
        }

        // 检查IP白名单
        String[] allowedIps = apiInternal.allowedIps();
        if (allowedIps.length > 0) {
            for (String allowedIp : allowedIps) {
                if (IpUtils.matchIp(clientIp, allowedIp)) {
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
            /**
             * 写入响应。
             * @param response 响应
             * @param apiInternal.status( api内部.状态(
             */
            writeResponse(response, apiInternal.status(), ReturnResult.error(apiInternal.message()));
            return false;
        }

        // 默认情况：未开启内网访问且无白名单配置，拒绝访问
        if (!apiInternal.allowPrivateNetwork()) {
            log.warn("内部接口未配置访问规则: uri={}", request.getRequestURI());
            /**
             * 写入响应。
             * @param response 响应
             * @param apiInternal.status( api内部.状态(
             */
            writeResponse(response, apiInternal.status(), ReturnResult.error(apiInternal.message()));
            return false;
        }

        // 非内网IP访问
        log.warn("非内网IP访问内部接口被拒绝: uri={}, clientIp={}", request.getRequestURI(), clientIp);
        /**
         * 写入响应。
         * @param response 响应
         * @param apiInternal.status( api内部.状态(
         */
        writeResponse(response, apiInternal.status(), ReturnResult.error(apiInternal.message()));
        return false;
    }

    /**
     * 处理 @api特征 注解
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
        ApiSwitchOverrideProvider.FeatureOverride override =
                overrideProvider != null ? overrideProvider.featureOverride(featureId) : null;
        boolean enabled = override != null ? override.isEnabled() : featureManager.isEnabled(featureId);
        if (!enabled) {
            log.debug("功能开关已关闭: {}", featureId);
            int status = override != null && override.disabledStatus() > 0
                    ? override.disabledStatus() : apiFeature.disabledStatus();
            String message = override != null && StringUtils.hasText(override.disabledMessage())
                    ? override.disabledMessage() : apiFeature.disabledMessage();
            /**
             * 写入响应。
             * @param response 响应
             * @param status 状态
             * @param ReturnResult.error(message 申报结果.错误(消息
             */
            writeResponse(response, status, ReturnResult.error(message));
            return false;
        }

        return true;
    }

    /**
     * 处理 @apimock 注解
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

        // 模拟延迟（使用虚拟线程避免阻塞主线程）
        if (apiMock.delay() > 0) {
            try {
                Thread.ofVirtual().start(() -> {}).join(apiMock.delay());
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
     * 处理 @api已弃用 注解
     * <p>
     * 支持语义化版本号（如 1.0.0, 1.0.0-release, 2.0.0-rc.1）进行比较
     * </p>
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

        // 获取请求版本（使用语义化版本）
        Version requestVersion = getRequestVersion(request);
        Version sinceVersion = Version.of(apiDeprecated.since());
 // 移除入 为空时使用 "latest" 表示永远不会移除
        Version removedVersion = StringUtils.isBlank(apiDeprecated.removedIn())
                ? Version.of("latest")
                : Version.of(apiDeprecated.removedIn());

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
        if (requestVersion.compareTo(removedVersion) >= 0) {
            log.warn("接口已移除: {} (removed in {})", request.getRequestURI(), apiDeprecated.removedIn());
            /**
             * 写入响应。
             * @param response 响应
             * @param 410 410
             * @param ReturnResult.error("此接口已被移除" 申报结果.错误("此接口已被移除"
             */
            writeResponse(response, 410, ReturnResult.error("此接口已被移除"));
            return false;
        }

        // 如果请求版本 >= 废弃版本
        if (requestVersion.compareTo(sinceVersion) >= 0) {
            // 有替代接口，返回提示
            if (StringUtils.isNotBlank(apiDeprecated.replacement())) {
                log.debug("接口已废弃，建议使用: {}", apiDeprecated.replacement());
                // 继续执行，但在响应头中提示
                return true;
            } else {
                // 没有替代接口，返回空结果
                log.debug("接口已废弃，无替代接口，返回空: {}", request.getRequestURI());
                /**
                 * 写入响应。
                 * @param response 响应
                 * @param 200 200
                 * @param ReturnResult.ok(null 申报结果.ok(空
                 */
                writeResponse(response, 200, ReturnResult.ok(null));
                return false;
            }
        }

        return true;
    }

    /**
     * 处理 @apigray 注解
     */
    private boolean handleApiGray(HandlerMethod handlerMethod, HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        var apiGray = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), ApiGray.class);
        if (apiGray == null) {
            return true;
        }

        // 检查全局灰度开关
        if (!isGrayEnabled()) {
            return true;
        }

 // 获取用户信息（避免创建新会话）
        Object userId = request.getAttribute("userId");
        String username = null;
        var session = request.getSession(false);
        if (session != null) {
            if (userId == null) {
                userId = session.getAttribute("userId");
            }
            username = (String) session.getAttribute("username");
        }

        // 获取角色信息
        java.util.Collection<String> roles = resolveRoles(request, session);

        // 评估灰度规则（应用运行时覆盖）
        boolean hitGray = grayEvaluator.evaluate(applyGrayOverride(apiGray), request, userId, username, roles);

        if (hitGray) {
            // 命中灰度，添加响应头并继续执行
            String headerName = apiProperties.getGray().getHeaderName();
            response.setHeader(headerName, "true");
            response.setHeader(headerName + "-Version", StringUtils.defaultString(apiGray.value(), "default"));
            log.debug("灰度命中: uri={}, version={}", request.getRequestURI(), apiGray.value());
            return true;
        }

        // 未命中灰度
        if (apiGray.forceGray()) {
            // 强制灰度模式，未命中则拒绝访问
            log.debug("灰度未命中(强制模式): uri={}", request.getRequestURI());
            
            // 检查是否有降级接口
            if (StringUtils.isNotBlank(apiGray.fallback())) {
                // 转发到降级接口
                log.debug("灰度降级: {} -> {}", request.getRequestURI(), apiGray.fallback());
                try {
                    request.getRequestDispatcher(apiGray.fallback()).forward(request, response);
                } catch (ServletException e) {
                    /**
                     * runtime异常。
                     * @param e e
                     * @return runtime异常的结果
                     */
                    throw new RuntimeException(e);
                }
                return false;
            }
            
            // 返回未命中灰度的响应
            /**
             * 写入响应。
             * @param response 响应
             * @param apiGray.notInGrayStatus( apigray.not入graystatus(
             */
            writeResponse(response, apiGray.notInGrayStatus(), 
                    ReturnResult.error(apiGray.notInGrayMessage()));
            return false;
        }

        // 非强制灰度模式，未命中则正常执行
        log.debug("灰度未命中(非强制模式)，继续执行: uri={}", request.getRequestURI());
        return true;
    }

    /**
     * 检查灰度功能是否开启
     * @return 是否gray已启用的结果
     */
    private boolean isGrayEnabled() {
        return apiProperties.getGray() != null && apiProperties.getGray().isEnable();
    }

    /**
     * 解析当前用户角色
     * <p>
     * 已注册 {@link AuthService} 时只认它（身份由认证体系签发，客户端不可伪造）；
     * 未接入认证体系的应用才回退到 请求 attribute / 会话 的 "roles"（集合 或逗号分隔字符串）。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private Collection<String> resolveRoles(HttpServletRequest request,
                                            jakarta.servlet.http.HttpSession session) {
        if (authService != null) {
            CurrentUser currentUser = authService.getCurrentUser();
            Set<String> roles = currentUser == null ? null : currentUser.getRoles();
            return roles == null ? Collections.emptyList() : roles;
        }
        Object raw = request.getAttribute("roles");
        if (raw == null && session != null) {
            raw = session.getAttribute("roles");
        }
        if (raw instanceof Collection) {
            return (Collection<String>) raw;
        }
        if (raw instanceof String str && !str.isBlank()) {
            return java.util.Arrays.asList(str.split(","));
        }
        return Collections.emptyList();
    }

    /**
     * 检查 Mock 功能是否开启
     * @return 是否mock已启用的结果
     */
    private boolean isMockEnabled() {
        return environment.getProperty("plugin.api.mock.enable", Boolean.class, true);
    }

    /**
     * 获取 Mock 响应内容
     * @param apiMock apimock
     * @return 获取mock响应的结果
     */
    private String getMockResponse(ApiMock apiMock) {
 // 优先使用 响应 属性
        if (StringUtils.isNotBlank(apiMock.response())) {
            return apiMock.response();
        }

        // 从文件读取
        if (StringUtils.isNotBlank(apiMock.responseFile())) {
            try {
                var resource = new ClassPathResource(apiMock.responseFile());
                if (resource.exists()) {
                    return IoUtils.asString(resource.getInputStream(), StandardCharsets.UTF_8);
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
     * <p>
     * 支持从请求头、URL路径、查询参数中获取语义化版本号
     * </p>
     * @return 解析后的 版本 对象
     * @param request 请求
     */
    private Version getRequestVersion(HttpServletRequest request) {
        // 从请求头获取
        String versionHeader = request.getHeader("X-API-Version");
        if (StringUtils.isNotBlank(versionHeader)) {
            return Version.of(versionHeader);
        }

 // 从 URL 路径解析 (如 /api/v2/用户, /api/v1.0.0/用户, /api/v2.1.0-beta/用户)
        String uri = request.getRequestURI();
        // 支持语义化版本格式: v1, v1.0, v1.0.0, v1.0.0-rc.1
        Matcher matcher = VERSION_PATTERN.matcher(uri);
        if (matcher.find()) {
            return Version.of(matcher.group(1));
        }

        // 从查询参数获取
        String versionParam = request.getParameter("version");
        if (StringUtils.isNotBlank(versionParam)) {
            return Version.of(versionParam);
        }

        // 默认版本 1.0.0
        return Version.of("1.0.0");
    }

    /**
     * 处理 @权限 注解
     * <p>
     * {@code role()} 与用户角色比对，{@code value()/permission()} 与用户角色 ∪ 权限比对
     * （注解值即可写角色名也可写权限标识）；超级管理员放行全部受限出口。
     * 解析不出身份即拒绝——不信任任何客户端可写的请求头。
     * </p>
     */
    private boolean handlePermission(HandlerMethod handlerMethod, HttpServletRequest request,
                                     HttpServletResponse response) throws IOException {
        Permission permission = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), Permission.class);
        if (permission == null) {
            permission = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), Permission.class);
        }
        if (permission == null) {
            return true;
        }

        Collection<String> owned = resolveRoles(request, request.getSession(false));
        CurrentUser currentUser = authService == null ? null : authService.getCurrentUser();
        Collection<String> permissions = currentUser == null || currentUser.getPermission() == null
                ? Collections.<String>emptyList() : currentUser.getPermission();
        String denied = PermissionRules.evaluate(permission, owned, permissions);
        if (denied != null) {
            log.warn("权限拒绝：uri={}, owned={}, 原因={}", request.getRequestURI(), owned, denied);
            /**
             * 写入响应。
             * @param response 响应
             * @param HttpServletResponse.SC_FORBIDDEN httpservlet响应.SC_FORBIDDEN
             */
            writeResponse(response, HttpServletResponse.SC_FORBIDDEN, ReturnResult.error(denied));
            return false;
        }

        return true;
    }

    /**
     * 写 JSON 响应
     * @param response 响应
     * @param status 状态
     * @param body 主体
     */
    private void writeResponse(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Json.toJson(body));
    }
}
