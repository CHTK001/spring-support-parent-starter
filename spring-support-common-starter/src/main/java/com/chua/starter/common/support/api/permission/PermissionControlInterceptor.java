package com.chua.starter.common.support.api.permission;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.json.Json;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link Permission} 拦截器。
 *
 * <p>身份只认 {@link AuthService}（由认证体系签发，客户端不可伪造），角色与权限的比对规则见
 * {@link PermissionRules}。未注册任何受限出口的应用不受影响——只有打了注解的方法才会进入判定。</p>
 *
 * <p>注意：应用未接入认证体系（容器里没有 {@link AuthService}）时无法判定身份，此时本拦截器不拦；
 * 只在首次命中受限出口时告警一次，避免把「没有身份源」误当成「已授权」。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public class PermissionControlInterceptor implements HandlerInterceptor {

    /**
     * 日志
     */
    private static final Logger log = LoggerFactory.getLogger(PermissionControlInterceptor.class);

    /**
     * 无身份源的告警只打一次
     */
    private static final AtomicBoolean WARNED_NO_AUTH_SERVICE = new AtomicBoolean(false);

    /**
     * 当前身份提供者
     */
    private final ObjectProvider<AuthService> authServiceProvider;

    /**
     * 构造函数
     *
     * @param authServiceProvider 当前身份提供者
     */
    public PermissionControlInterceptor(ObjectProvider<AuthService> authServiceProvider) {
        this.authServiceProvider = authServiceProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Permission permission = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), Permission.class);
        if (permission == null) {
            permission = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), Permission.class);
        }
        if (permission == null) {
            return true;
        }

        AuthService authService = authServiceProvider.getIfAvailable();
        if (authService == null) {
            if (WARNED_NO_AUTH_SERVICE.compareAndSet(false, true)) {
                log.warn("[权限拦截]未注册 AuthService，@Permission 无法判定身份，已跳过校验：uri={}", request.getRequestURI());
            }
            return true;
        }

        CurrentUser currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            log.warn("[权限拦截]无登录身份访问受限出口：uri={}", request.getRequestURI());
            write(response, HttpServletResponse.SC_UNAUTHORIZED, ReturnResult.error("请先登录"));
            return false;
        }

        Set<String> roles = currentUser.getRoles() == null ? Collections.<String>emptySet() : currentUser.getRoles();
        Set<String> permissions = currentUser.getPermission() == null
                ? Collections.<String>emptySet() : currentUser.getPermission();
        String denied = PermissionRules.evaluate(permission, roles, permissions);
        if (denied != null) {
            log.warn("[权限拦截]拒绝访问：uri={}, roles={}, 原因={}", request.getRequestURI(), roles, denied);
            write(response, HttpServletResponse.SC_FORBIDDEN, ReturnResult.error(denied));
            return false;
        }
        return true;
    }

    /**
     * 写 JSON 响应
     *
     * @param response 响应
     * @param status   状态
     * @param body     主体
     * @throws IOException 写失败
     */
    private void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(Json.toJson(body));
    }
}
