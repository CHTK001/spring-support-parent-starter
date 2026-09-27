package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.oauth.client.support.annotation.TokenForIgnore;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一登录契约控制器（前端 Vue / React 均走该契约）。
 *
 * <p>提供 {@code /v2/user} 前缀下的登录、登出、当前用户信息、刷新令牌等接口，
 * 统一以 {@link ReturnResult} 作为响应信封，适配前端 {@code LoginResult} 结构。</p>
 *
 * <p>所有接口均标注 {@link TokenForIgnore}，避免被 oauth 客户端拦截。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/v2/user")
@Slf4j
public class UserAuthController {

    /**
     * Bearer 令牌 前缀
    */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Bearer 令牌 前缀长度
    */
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    /**
     * 静态协议登录 Cookie 名（登录时由服务端下发，登出时需一并吊销其中的令牌）
     */
    private static final String AUTH_COOKIE_NAME = "x-oauth-cookie";

    /**
     * 用户登录（对接 o认证 静态 协议）。
     *
     * <p>调用 oauth 客户端换取访问令牌，成功后组装前端契约所需的
     * {@code LoginResult} 结构并返回。</p>
     *
     * @param body 请求体（含 用户名 / 密码）
     * @return 登录结果（成功含 access令牌 / refresh令牌 / 用户信息 / 角色 / expires）
     */
    @TokenForIgnore
    @PostMapping("/login")
    public ReturnResult<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return ReturnResult.error("用户名和密码不能为空");
        }

        LoginAuthResult result = AuthClientExecute.getInstance()
                .getAccessToken(username, password, AuthType.WEB, Map.of());
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            // 区分「口令错误」与「静态账号未配置」等原因，否则配置缺失只会被当成密码错
            String message = result == null ? null : result.getMessage();
            log.warn("登录失败 username={} 原因={}", username, StringUtils.defaultString(message, "无返回"));
            return ReturnResult.error(StringUtils.isNotBlank(message) ? message : "用户名或密码错误");
        }

        String token = result.getToken();
 // refresh令牌 暂无独立来源，先复用 令牌 占位
        String refreshToken = StringUtils.isNotBlank(result.getRefreshToken())
                ? result.getRefreshToken() : (token != null ? token : "");

        UserResume userResume = result.getUserResume();
        if (userResume == null || StringUtils.isBlank(userResume.getUserId())) {
            return ReturnResult.error("登录未返回有效用户身份");
        }

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", userResume.getUserId());
        user.put("username", userResume.getUsername());
        user.put("nickname", StringUtils.defaultString(userResume.getNickName(), userResume.getUsername()));
        user.put("readonly", userResume.isReadonly());

        List<String> roles = new ArrayList<>();
        if (userResume.getRoles() != null) {
            roles.addAll(userResume.getRoles());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", token);
        data.put("refreshToken", refreshToken);
        data.put("user", user);
        data.put("roles", roles);
        data.put("expires", userResume.getExpireTime());
        return ReturnResult.ok(data);
    }

    /**
     * 用户登出（POST，保留给桌面端等既有调用方）。
     *
     * <p>与 {@link #logoutByDelete(HttpServletRequest)} 完全等价，共用同一套吊销逻辑。</p>
     *
     * @param request HTTP 请求，用于取回本次请求携带的令牌
     * @return 登出结果
     */
    @TokenForIgnore
    @PostMapping("/logout")
    public ReturnResult<Void> logout(HttpServletRequest request) {
        return doLogout(request);
    }

    /**
     * 用户登出（DELETE，前端 auth-core 契约使用的方法）。
     *
     * <p>前端 {@code /v2/user/logout} 走 {@code DELETE}，而本控制器原先只声明了
     * {@code POST}，导致请求方法不被支持、返回 405。</p>
     *
     * @param request HTTP 请求，用于取回本次请求携带的令牌
     * @return 登出结果
     */
    @TokenForIgnore
    @DeleteMapping("/logout")
    public ReturnResult<Void> logoutByDelete(HttpServletRequest request) {
        return doLogout(request);
    }

    /**
     * 执行登出：真正吊销本次请求携带的令牌。
     *
     * <p>登出接口在鉴权白名单内，鉴权链路不会解析令牌、会话里也没有用户信息，
     * 因此必须由业务侧自行取出令牌再吊销。若像原先那样传空令牌，
     * 服务端令牌不会被登记进吊销名单，登出后旧令牌仍能继续调用业务接口。</p>
     *
     * @param request HTTP 请求，用于取回本次请求携带的令牌
     * @return 登出结果
     */
    private ReturnResult<Void> doLogout(HttpServletRequest request) {
        String token = resolveLogoutToken(request);
        if (StringUtils.isBlank(token)) {
            // 本次请求没有携带任何凭据，无令牌可吊销，按幂等成功返回
            return ReturnResult.ok(null);
        }
        try {
            LoginAuthResult result = AuthClientExecute.getInstance().logoutByToken(token);
            if (null == result || null == result.getCode() || result.getCode() != 200) {
                // 吊销失败必须留痕，否则「登出成功」的表象会掩盖令牌仍然可用
                log.warn("用户登出未成功吊销令牌: {}", null == result ? "无返回" : result.getMessage());
            }
        } catch (Exception e) {
            // 登出异常不阻断响应，仅记录日志
            log.warn("用户登出调用 oauth 异常: {}", e.getMessage());
        }
        return ReturnResult.ok(null);
    }

    /**
     * 解析本次登出需要吊销的令牌。
     *
     * <p>覆盖三类凭据来源：{@code Authorization: Bearer} 头、{@code token} 查询参数，
     * 以及静态协议登录时由服务端下发的 {@code x-oauth-cookie} Cookie。</p>
     *
     * @param request HTTP 请求
     * @return 待吊销的令牌；请求未携带任何凭据时返回 {@code null}
     */
    private String resolveLogoutToken(HttpServletRequest request) {
        String token = resolveToken(request);
        if (StringUtils.isNotBlank(token)) {
            return token;
        }
        Cookie[] cookies = request.getCookies();
        if (null == cookies) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AUTH_COOKIE_NAME.equals(cookie.getName()) && StringUtils.isNotBlank(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 获取当前登录用户信息。
     *
     * <p>从 {@code Authorization: Bearer <token>} 请求头（或 query token）提取令牌，
     * 调用 OAuth 客户端校验并解析用户信息；无法解析时返回未登录。</p>
     *
     * @param request HTTP 请求
     * @return 当前用户信息（userId / 用户名 / nickname / 角色）
     */
    @TokenForIgnore
    @GetMapping("/me")
    public ReturnResult<Map<String, Object>> me(HttpServletRequest request) {
        String token = resolveToken(request);
        if (StringUtils.isBlank(token)) {
            return ReturnResult.error("未登录");
        }

        UserResult userResult = null;
        try {
            userResult = AuthClientExecute.getInstance().getUserResult(token);
        } catch (Exception e) {
            log.warn("解析用户信息异常: {}", e.getMessage());
        }

 // 解析不到真实身份即未登录，不得编造用户标识与角色
        if (userResult == null || StringUtils.isBlank(userResult.getUserId())) {
            return ReturnResult.error("未登录");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userResult.getUserId());
        data.put("username", userResult.getUsername());
        data.put("nickname", StringUtils.defaultString(userResult.getNickName(), userResult.getUsername()));
        data.put("readonly", userResult.isReadonly());
        data.put("roles", userResult.getRoles() == null
                ? new ArrayList<String>() : new ArrayList<>(userResult.getRoles()));
        return ReturnResult.ok(data);
    }

    /**
     * 刷新令牌。
     *
     * <p>用刷新令牌走 oauth 客户端 {@code upgrade(REFRESH)} 换发新令牌；
     * 换发失败说明刷新令牌已失效，需要重新登录。</p>
     *
     * @param body 请求体（含 refresh令牌）
     * @return 刷新后的令牌结果
     */
    @TokenForIgnore
    @PostMapping("/refresh-token")
    public ReturnResult<Map<String, Object>> refreshToken(@RequestBody(required = false) Map<String, String> body) {
        String refreshToken = body != null ? body.get("refreshToken") : null;
        if (StringUtils.isBlank(refreshToken)) {
            return ReturnResult.error("refreshToken 不能为空");
        }

        LoginResult upgraded;
        try {
            upgraded = AuthClientExecute.getInstance().upgrade(refreshToken, UpgradeType.REFRESH);
        } catch (Exception e) {
            log.warn("刷新令牌异常: {}", e.getMessage());
            return ReturnResult.error("刷新令牌失败");
        }
        if (upgraded == null || StringUtils.isBlank(upgraded.getToken())) {
            return ReturnResult.error("刷新令牌已失效，请重新登录");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", upgraded.getToken());
        data.put("refreshToken", StringUtils.defaultString(upgraded.getRefreshToken(), upgraded.getToken()));
        return ReturnResult.ok(data);
    }

    /**
     * 从请求中解析 令牌（优先 授权 头，其次 查询 令牌）。
     *
     * @param request HTTP 请求
     * @return Token 字符串，无法解析时返回 {@code null}
     */
    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (StringUtils.isNotBlank(auth) && auth.startsWith(BEARER_PREFIX)) {
            String token = auth.substring(BEARER_PREFIX_LENGTH).trim();
            if (StringUtils.isNotBlank(token)) {
                return token;
            }
        }
 // 查询 令牌 兜底
        return request.getParameter("token");
    }
}
