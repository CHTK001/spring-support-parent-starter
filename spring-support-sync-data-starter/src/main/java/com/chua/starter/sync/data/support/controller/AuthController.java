package com.chua.starter.sync.data.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.data.support.properties.SyncDataProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 认证控制器
 * <p>
 * 控制器本身始终注册，便于共享 Spring 轻控制台在无认证模式下也能通过
 * {@code /status} 判断是否需要登录。真正的接口拦截仍由 embedded 模式下的过滤器控制。
 * </p>
 *
 * @author System
 * @since 2026/03/09
 */
@RestController
@RequestMapping("/v1/sync/auth")
@Tag(name = "认证管理")
@RequiredArgsConstructor
public class AuthController {

    private static final String EMBEDDED_ADMIN_USER_ID = "1";
    private static final String SESSION_USER_INFO = "userInfo";
    private static final String SESSION_USERNAME = "username";
    private static final String SESSION_USERID = "userId";
    private static final String DEFAULT_ANONYMOUS_USER = "anonymous";

    private final SyncDataProperties properties;

    @GetMapping("/status")
    @Operation(summary = "获取登录状态")
    public ReturnResult<Map<String, Object>> status(HttpServletRequest request) {
        return ReturnResult.ok(buildStatusData(request));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public ReturnResult<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        if (!isEmbeddedAuthEnabled()) {
            return ReturnResult.ok(buildStatusData(false, true, DEFAULT_ANONYMOUS_USER));
        }

        SyncDataProperties.WebAuthConfig config = getWebAuthConfig();
        if (Objects.equals(config.getUsername(), request.getUsername())
                && Objects.equals(config.getPassword(), request.getPassword())) {
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("authenticated", true);
            session.setMaxInactiveInterval(resolveSessionTimeout(config));
            session.setAttribute(SESSION_USER_INFO, createSessionUserInfo(session.getId(), request.getUsername()));
            session.setAttribute(SESSION_USERNAME, request.getUsername());
            session.setAttribute(SESSION_USERID, EMBEDDED_ADMIN_USER_ID);

            Map<String, Object> data = buildStatusData(true, true, request.getUsername());
            data.put("token", session.getId());
            return ReturnResult.ok(data);
        }

        return ReturnResult.error("用户名或密码错误");
    }

    private Object createSessionUserInfo(String sessionId, String username) {
        Map<String, Object> ext = new HashMap<>();
        ext.put("sysUserId", EMBEDDED_ADMIN_USER_ID);
        ext.put("userRealName", username);

        try {
            Class<?> userResultClass = Class.forName("com.chua.starter.oauth.client.support.user.UserResult");
            Object userResult = userResultClass.getDeclaredConstructor().newInstance();
            invokeSetter(userResultClass, userResult, "setUserId", String.class, EMBEDDED_ADMIN_USER_ID);
            invokeSetter(userResultClass, userResult, "setUsername", String.class, username);
            invokeSetter(userResultClass, userResult, "setNickName", String.class, username);
            invokeSetter(userResultClass, userResult, "setLoginType", String.class, "EMBEDDED");
            invokeSetter(userResultClass, userResult, "setToken", String.class, sessionId);
            invokeSetter(userResultClass, userResult, "setExt", Map.class, ext);
            return userResult;
        } catch (Exception e) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", EMBEDDED_ADMIN_USER_ID);
            userInfo.put("username", username);
            userInfo.put("nickName", username);
            userInfo.put("loginType", "EMBEDDED");
            userInfo.put("token", sessionId);
            userInfo.put("ext", ext);
            return userInfo;
        }
    }

    private void invokeSetter(Class<?> type, Object target, String methodName, Class<?> parameterType, Object value)
            throws Exception {
        Method method = type.getMethod(methodName, parameterType);
        method.invoke(target, value);
    }

    @PostMapping("/logout")
    @Operation(summary = "用户登出")
    public ReturnResult<Void> logout(HttpServletRequest request) {
        if (!isEmbeddedAuthEnabled()) {
            return ReturnResult.ok();
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ReturnResult.ok();
    }

    @GetMapping("/info")
    @Operation(summary = "获取用户信息")
    public ReturnResult<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        if (!isEmbeddedAuthEnabled()) {
            return ReturnResult.ok(buildStatusData(false, true, DEFAULT_ANONYMOUS_USER));
        }
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute("authenticated"))) {
            Map<String, Object> data = buildStatusData(true, true, resolveSessionUsername(session));
            return ReturnResult.ok(data);
        }
        return ReturnResult.error("未登录");
    }

    private Map<String, Object> buildStatusData(HttpServletRequest request) {
        if (!isEmbeddedAuthEnabled()) {
            return buildStatusData(false, true, DEFAULT_ANONYMOUS_USER);
        }

        HttpSession session = request.getSession(false);
        boolean authenticated = session != null && Boolean.TRUE.equals(session.getAttribute("authenticated"));
        return buildStatusData(true, authenticated, authenticated ? resolveSessionUsername(session) : null);
    }

    private Map<String, Object> buildStatusData(boolean authEnabled, boolean authenticated, String username) {
        Map<String, Object> data = new HashMap<>();
        data.put("authEnabled", authEnabled);
        data.put("authenticated", authenticated);
        if (username != null && !username.isEmpty()) {
            data.put("username", username);
        }
        return data;
    }

    private boolean isEmbeddedAuthEnabled() {
        return !"none".equalsIgnoreCase(getWebAuthConfig().getMode());
    }

    private SyncDataProperties.WebAuthConfig getWebAuthConfig() {
        return properties.getWebAuth() == null ? new SyncDataProperties.WebAuthConfig() : properties.getWebAuth();
    }

    private int resolveSessionTimeout(SyncDataProperties.WebAuthConfig config) {
        Integer timeout = config.getSessionTimeout();
        return timeout == null || timeout <= 0 ? 3600 : timeout;
    }

    private String resolveSessionUsername(HttpSession session) {
        Object username = session.getAttribute(SESSION_USERNAME);
        if (username != null) {
            return String.valueOf(username);
        }
        return getWebAuthConfig().getUsername();
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
        private Boolean rememberMe;
    }
}
