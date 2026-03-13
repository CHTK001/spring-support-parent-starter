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
            
            Map<String, Object> data = new HashMap<>();
            data.put("token", session.getId());
            data.put("username", request.getUsername());
            
            return ReturnResult.ok(data);
        }
        
        return ReturnResult.error("用户名或密码错误");
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
