package com.chua.starter.ai.support.manage.api;

import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
* 认证控制器（根路径 /login、/logout）。
* 对接 o认证 静态协议，桌面模式单用户认证。
* @author CH
* @since 4.0.0
 */
@Slf4j
@ApiReturnFormatIgnore
@RestController
public class AuthController {

    /**
    * 用户登录。
    *
    * @param body     请求体 {用户名, 密码}
    * @param response HTTP 响应（用于设置 Cookie）
    * @return 登录结果 {成功, 令牌, 用户}
     */
    @TokenForIgnore
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body,
                                     HttpServletResponse response) {
        String username = body.getOrDefault("username", "admin");
        String password = body.getOrDefault("password", "admin");

        log.info("[AuthController] 用户登录: {}", username);

        var clientExecute = AuthClientExecute.getInstance();
        LoginAuthResult result = clientExecute.getAccessToken(username, password, AuthType.WEB, null);

        if (result == null || result.getToken() == null) {
            log.warn("[AuthController] 登录失败: {}", username);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("message", "用户名或密码错误");
            return resp;
        }

        String token = result.getToken();

        Cookie cookie = new Cookie("x-oauth-cookie", token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(cookie);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("user_id", generateUuidV7());
        user.put("username", result.getUserResume() != null ? result.getUserResume().getUsername() : username);
        user.put("display_name", result.getUserResume() != null ? result.getUserResume().getNickName() : username);
        user.put("role", "admin");

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("token", token);
        resp.put("user", user);
        return resp;
    }

    @TokenForIgnore
    @PostMapping("/logout")
    /**
    * logout。
    * @param response 响应
    * @return logout的结果
     */
    public Map<String, Object> logout(HttpServletResponse response) {
        var clientExecute = AuthClientExecute.getInstance();
        clientExecute.logout(null, null, LogoutType.LOGOUT);

        Cookie cookie = new Cookie("x-oauth-cookie", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        return resp;
    }

    /**
    * generateuuidv7。
    * @return generateUuidV7的结果
     */
    private static String generateUuidV7() {
        UUID uuid = UUID.randomUUID();
        long ms = System.currentTimeMillis();
        byte[] bytes = new byte[16];
        bytes[0] = (byte) (ms >> 40);
        bytes[1] = (byte) (ms >> 32);
        bytes[2] = (byte) (ms >> 24);
        bytes[3] = (byte) (ms >> 16);
        bytes[4] = (byte) (ms >> 8);
        bytes[5] = (byte) ms;
        byte[] random = uuid.toString().replace("-", "").substring(12).getBytes();
        System.arraycopy(random, 0, bytes, 6, 10);
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        return String.format("%08x-%04x-%04x-%04x-%012x",
                ((bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF)),
                ((bytes[4] & 0xFF) << 8 | (bytes[5] & 0xFF)),
                ((bytes[6] & 0xFF) << 8 | (bytes[7] & 0xFF)),
                ((bytes[8] & 0xFF) << 8 | (bytes[9] & 0xFF)),
                (((long) (bytes[10] & 0xFF) << 40) | ((long) (bytes[11] & 0xFF) << 32) | ((long) (bytes[12] & 0xFF) << 24) | ((long) (bytes[13] & 0xFF) << 16) | ((long) (bytes[14] & 0xFF) << 8) | (bytes[15] & 0xFF))
        );
    }
}
