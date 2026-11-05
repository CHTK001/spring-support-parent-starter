// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.manage.api;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;

import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

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
        String username = body.get("username");
        String password = body.get("password");

        log.info("[AuthController] 用户登录: {}", username);

        var clientExecute = AuthClientExecute.getInstance();
        LoginAuthResult result = clientExecute.getAccessToken(username, password, AuthType.WEB, null);

        if (result == null || result.getToken() == null
                || result.getUserResume() == null || result.getUserResume().getUserId() == null) {
            log.warn("[AuthController] 登录失败: {}", username);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("success", false);
            resp.put("message", "用户名或密码错误");
            return resp;
        }

        String token = result.getToken();
        UserResume userResume = result.getUserResume();

        Cookie cookie = new Cookie("x-oauth-cookie", token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(cookie);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("user_id", userResume.getUserId());
        user.put("username", userResume.getUsername());
        user.put("display_name", StringUtils.defaultString(userResume.getNickName(), userResume.getUsername()));
        user.put("role", userResume.getRoles() == null || userResume.getRoles().isEmpty()
                ? "ops" : userResume.getRoles().iterator().next());
        user.put("readonly", userResume.isReadonly());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("token", token);
        resp.put("refreshToken", result.getRefreshToken());
        resp.put("expires", userResume.getExpireTime());
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
}