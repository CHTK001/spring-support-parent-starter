package com.chua.starter.sync.data.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.sync.data.support.properties.SyncDataProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器 - embedded模式
 *
 * @author System
 * @since 2026/03/09
 */
@RestController
@RequestMapping("/v1/sync/auth")
@Tag(name = "认证管理")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.sync.web-auth", name = "mode", havingValue = "embedded", matchIfMissing = true)
public class AuthController {

    private static final String EMBEDDED_ADMIN_USER_ID = "1";
    private static final String SESSION_USER_INFO = "userInfo";
    private static final String SESSION_USERNAME = "username";
    private static final String SESSION_USERID = "userId";

    private final SyncDataProperties properties;

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public ReturnResult<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        SyncDataProperties.WebAuthConfig config = properties.getWebAuth();
        
        if (config.getUsername().equals(request.getUsername()) 
                && config.getPassword().equals(request.getPassword())) {
            
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("authenticated", true);
            session.setMaxInactiveInterval(config.getSessionTimeout());
            session.setAttribute(SESSION_USER_INFO, createSessionUserInfo(session.getId(), request.getUsername()));
            session.setAttribute(SESSION_USERNAME, request.getUsername());
            session.setAttribute(SESSION_USERID, EMBEDDED_ADMIN_USER_ID);
            
            Map<String, Object> data = new HashMap<>();
            data.put("token", session.getId());
            data.put("username", request.getUsername());
            
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
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ReturnResult.ok();
    }

    @GetMapping("/info")
    @Operation(summary = "获取用户信息")
    public ReturnResult<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("authenticated") != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("username", properties.getWebAuth().getUsername());
            return ReturnResult.ok(data);
        }
        return ReturnResult.error("未登录");
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
        private Boolean rememberMe;
    }
}
