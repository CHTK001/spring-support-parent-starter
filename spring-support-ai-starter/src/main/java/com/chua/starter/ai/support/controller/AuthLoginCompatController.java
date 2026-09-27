package com.chua.starter.ai.support.controller;

import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.common.support.api.annotations.ApiReturnFormatIgnore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /v2/auth/login 兼容端点：前端调用 /v2/auth/login，后端实际登录逻辑在 /v2/user/login。
 * 返回前端期望的扁平结构 {code, msg, data:{accessToken,...}}。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@ApiReturnFormatIgnore
@RestController
@RequestMapping("/v2/auth")
public class AuthLoginCompatController {

    /**
     * 执行兼容登录。
     *
     * @param body 登录请求，包含用户名和密码
     * @return 兼容前端格式的登录结果
     */
    @TokenForIgnore
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        Map<String, Object> resp = new LinkedHashMap<>();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            resp.put("code", "400");
            resp.put("msg", "用户名和密码不能为空");
            resp.put("success", false);
            return resp;
        }

        LoginAuthResult result = AuthClientExecute.getInstance()
                .getAccessToken(username, password, AuthType.WEB, Map.of());
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            String message = result == null ? null : result.getMessage();
            log.warn("[AuthCompat] 登录失败 username={} 原因={}", username, StringUtils.defaultString(message, "无返回"));
            resp.put("code", "401");
            resp.put("msg", StringUtils.isNotBlank(message) ? message : "用户名或密码错误");
            resp.put("success", false);
            return resp;
        }

        String token = result.getToken();
        String refreshToken = StringUtils.isNotBlank(result.getRefreshToken())
                ? result.getRefreshToken() : (token != null ? token : "");
        UserResume userResume = result.getUserResume();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", token);
        data.put("refreshToken", refreshToken);
        data.put("userId", userResume != null ? userResume.getUserId() : username);
        data.put("username", username);
        data.put("nickname", username);
        data.put("roles", java.util.List.of("ADMIN"));
        data.put("expires", System.currentTimeMillis() + 86400000L);

        resp.put("code", "00000");
        resp.put("msg", "操作成功");
        resp.put("ok", true);
        resp.put("success", true);
        resp.put("data", data);
        return resp;
    }
}
