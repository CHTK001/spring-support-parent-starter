// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.provider;

import com.chua.common.support.lang.json.Json5;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.oauth.client.support.annotation.UserValue;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.runtime.OauthClientRuntimeContext;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.google.common.base.Strings;
import com.google.common.base.Splitter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;
import static com.chua.starter.common.support.constant.Constant.ADMIN;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.CAPTCHA_SESSION_KEY;
import static com.chua.starter.common.support.utils.RequestUtils.getIpAddress;

/**
 * @author CH
 * @since 4.0.0
 */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "plugin.oauth.temp.open", havingValue = "true", matchIfMissing = false)
public class UserStatisticProvider {


    @Autowired
    /**
     * 认证属性
    */
    private AuthClientProperties authProperties;
    /**
     * 登录
     *
     * @param loginData     登录信息
     * @param request       请求
     * @param response      响应
     * @return 结果
     */
    @PostMapping("/login")    public ReturnResult<LoginResult> login(@Valid @RequestBody LoginData loginData,
                                     HttpServletRequest request,
                                     HttpServletResponse response,
                                     BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }

        ReturnResult<LoginResult> result = doLogin(loginData, request);
        if (!result.isOk() || null == result.getData() || null == result.getData().getAccessToken()) {
            return result;
        }
        // 非分离模式的会话闭环：浏览器表单拿不到请求头，后续导航只能靠 Cookie 带凭据。
        // 这里把刚换到的 accessToken 写进 HttpOnly 会话 Cookie，各协议本就「头优先、cookie 其次」
        // 地读 x-oauth-cookie —— 读的那一半早已实现，写的那一半到此才补上。
        writeSessionCookie(response, result.getData().getAccessToken());
        return result;
    }

    /**
     * 自渲染表单登录端点（{@code POST /login/form}，表单编码）。
     *
     * <p>浏览器原生表单只能发 {@code application/x-www-form-urlencoded}，而
     * {@link #login} 只收 JSON（发 JSON 会 415），所以表单投递到这里。
     * 成功：写会话 Cookie 后 302 到 {@code redirect_url}（只允许站内相对路径）；
     * 失败：302 回登录页并带上错误原因（经 HTML 转义后展示）。</p>
     *
     * @param username 账号
     * @param password 密码
     * @param verifyCode 验证码
     * @param redirectUrl 回跳地址
     * @param request 当前请求
     * @param response 当前响应
     * @throws IOException 重定向写失败时抛出
     */
    @PostMapping(value = "${plugin.oauth.login-form-action:/login/form}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void loginForm(@RequestParam(name = "username", required = false) String username,
                          @RequestParam(name = "password", required = false) String password,
                          @RequestParam(name = "verifyCode", required = false) String verifyCode,
                          @RequestParam(name = "redirect_url", required = false) String redirectUrl,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        LoginData loginData = new LoginData();
        loginData.setUsername(username);
        loginData.setPassword(password);
        loginData.setVerifyCodeKey(verifyCode);

        ReturnResult<LoginResult> result = doLogin(loginData, request);
        String contextPath = request.getContextPath();
        String loginPage = contextPath + authProperties.getLoginPage();
        if (!result.isOk() || null == result.getData() || null == result.getData().getAccessToken()) {
            String error = "登录失败";
            try {
                String msg = result.getMsg();
                if (null != msg && !msg.isBlank()) {
                    error = msg;
                }
            } catch (Exception ignored) {
                // 取不到就用默认文案
            }
            response.sendRedirect(loginPage
                    + "?redirect_url=" + urlEncode(LocalLoginPageProvider.resolveRedirect(redirectUrl))
                    + "&error=" + urlEncode(error));
            return;
        }
        writeSessionCookie(response, result.getData().getAccessToken());
        response.sendRedirect(LocalLoginPageProvider.resolveRedirect(redirectUrl));
    }

    /**
     * 写会话 Cookie（非分离模式凭据闭环的「写」一半）。
     *
     * <p>Cookie 名取 {@code plugin.oauth.cookie-name}（默认 {@code x-oauth-cookie}），
     * 与各协议读的一侧同名；{@code HttpOnly} 防 JS 窃取；{@code Path=/} 全站携带；
     * 会话级（maxAge=-1）关闭浏览器即失效；{@code SameSite=Lax} 挡跨站 POST 携带
     * （顶层 GET 回跳仍可携带，保证从登录页 302 回来时已登录）。</p>
     *
     * @param response 当前响应
     * @param accessToken 刚换到的访问令牌
     */
    public void writeSessionCookie(HttpServletResponse response, String accessToken) {
        // Cookie 值按 RFC 6265 只能是 token68；accessToken 若含非法字符会导致整条 Set-Cookie 被浏览器丢弃。
        // 先做白名单校验，不合规就拒绝写（调用方已返回 200 则此分支理论不可达，属于纵深防御）。
        if (null == accessToken || !accessToken.matches("[A-Za-z0-9!#$%&'*+\\-.^_`|~:/=?@\\[\\]]+")) {
            log.error("[Login]令牌含 Cookie 非法字符，拒绝写会话 Cookie");
            return;
        }
        String name = authProperties.getCookieName();
        String encoded = java.net.URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        StringBuilder header = new StringBuilder(name).append('=').append(encoded)
                .append("; Path=/; HttpOnly; SameSite=Lax");
        // Secure 由业务在反向代理层按需补：本机 HTTP 调试若加上，浏览器直接拒收 Cookie。
        response.addHeader("Set-Cookie", header.toString());
    }

    /**
     * url Encode
     * @param value 字符串
     * @return 返回值
    */
    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    /**
     * 登录协议调用（JSON 登录与表单登录共用）。
     *
     * @param loginData 登录信息
     * @param request 当前请求（取验证码 session 与客户端 IP）
     * @return 登录结果
     */
    private ReturnResult<LoginResult> doLogin(LoginData loginData, HttpServletRequest request) {
        var code = loginData.getVerifyCodeKey();
        var sessionKey = Optional.ofNullable(request.getSession().getAttribute(CAPTCHA_SESSION_KEY)).orElse("").toString();
        if (Strings.isNullOrEmpty(code) || !code.equalsIgnoreCase(sessionKey)) {
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), "校验码错误");
        }

        var address = getIpAddress(request);
        var clientExecute = AuthClientExecute.getInstance();
        var extBuilder = new java.util.LinkedHashMap<String, Object>();
        extBuilder.put("address", address);
        if (StringUtils.isNotBlank(loginData.getTenantId())) {
            extBuilder.put("tenantId", loginData.getTenantId());
        }
        if (StringUtils.isNotBlank(loginData.getTenantCode())) {
            extBuilder.put("tenantCode", loginData.getTenantCode());
        }
        if (StringUtils.isNotBlank(loginData.getAccountType())) {
            extBuilder.put("accountType", loginData.getAccountType());
        }
        var accessToken = clientExecute.getAccessToken(
                loginData.getUsername(),
                loginData.getPassword(),
                AuthType.WEB,
                Collections.unmodifiableMap(new java.util.LinkedHashMap<>(extBuilder))
        );
        if (null == accessToken) {
            return ReturnResult.error("账号或者密码不正确");
        }

        if (null == accessToken.getCode() || accessToken.getCode() != 200) {
            return ReturnResult.error(String.valueOf(accessToken.getMessage()));
        }

        LoginResult loginResult = LoginResult.builder()
                .userInfo(getUserLoginInfo(accessToken.getToken()))
                .tokenType("Bearer")
                .accessToken(accessToken.getToken())
                .build();
        return ReturnResult.ok(loginResult);
    }

    /**
     * 获取用户登录信息
     *
     * @param token 代币
     * @return {@link 用户信息vo}
     */
    public UserInfoVO getUserLoginInfo(String token) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null || StringUtils.isBlank(token)) {
            /**
             * 用户信息vo。
             * @return 用户信息vo的结果
             */
            return new UserInfoVO();
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) requestAttributes;
        attributes.getRequest().setAttribute(authProperties.getTokenName(), token);
        return getUserLoginInfo();
    }

    /**
     * 获取用户登录信息
     *
     * @return {@link 用户信息vo}
     */
    public UserInfoVO getUserLoginInfo() {
        UserResult userLoginInfo = AuthClientExecute.getInstance().getUserResult();
        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setUserName(userLoginInfo.getUsername());
        userInfoVO.setUserRealName(String.valueOf(userLoginInfo.getOption("userRealName")));
        userInfoVO.setNickname(userLoginInfo.getNickName());
        userInfoVO.setUserId(MapUtils.getString(userLoginInfo.getExt(), "userId"));
        userInfoVO.setUserMobile(MapUtils.getString(userLoginInfo.getExt(), "userMobile"));
        userInfoVO.setUserGender(MapUtils.getString(userLoginInfo.getExt(), "userGender"));
        userInfoVO.setUserEmail(MapUtils.getString(userLoginInfo.getExt(), "userEmail"));
        userInfoVO.setUserAddress(MapUtils.getString(userLoginInfo.getExt(), "userAddress"));
        userInfoVO.setUserSex(MapUtils.getInteger(userLoginInfo.getExt(), "userGender"));
        userInfoVO.setUserMarker(MapUtils.getString(userLoginInfo.getExt(), "userMarker"));
        userInfoVO.setAvatar(MapUtils.getString(userLoginInfo.getExt(), "userAvatar"));
        userInfoVO.setRoles(Optional.ofNullable(userLoginInfo.getRoles()).orElse(Collections.emptySet()));
        userInfoVO.setPerms(Optional.ofNullable(userLoginInfo.getPermission()).orElse(Collections.emptySet()));
        return userInfoVO;
    }

    /**
     * 注销
     *
     * @param uid      uid
     * @param request  要求
     * @param response 回答
     * @return {@link 结果}
     * @param @UserValue("token" @用户值("令牌"
     * @param @UserValue("token" @用户值("令牌"
     * @param @UserValue("token" @UserValue("token"
     */
    @DeleteMapping("/logout")
    public ReturnResult logout(@UserValue("token") String uid, HttpServletRequest request, HttpServletResponse response) {
        if (StringUtils.isEmpty(uid)) {
            return ReturnResult.ok("注销成功");
        }
        AuthClientExecute clientExecute = AuthClientExecute.getInstance();
        LoginAuthResult accessToken = clientExecute.logout(uid, "WEB", LogoutType.LOGOUT);
        if (null == accessToken) {
            return ReturnResult.error("退出失败请稍后重试");
        }

        if (accessToken.getCode() != 200) {
            return ReturnResult.error(accessToken.getMessage());
        }

        CookieUtil.remove(request, response, "x-oauth-cookie");
        return ReturnResult.ok("注销成功");
    }
    /**
     * 保存用户首页布局
     *
     * @param sysUserSetting 保存用户首页布局
     * @return 保存用户首页布局
     */
    @PostMapping("/grid")
    public ReturnResult<Boolean> grid(@RequestBody JsonObject sysUserSetting,
                                      @UserValue("userDashboardGrid") String userDashboardGrid,
                                      @UserValue("userId") Long userId,
                                      @UserValue("username") String username,
                                      @UserValue("roles") Set<String> roles
    ) {
        log.warn("[OAuth] 用户 {} 网格布局保存请求，暂不支持持久化，userId={}, grid={}", username, userId, userDashboardGrid);
        return ReturnResult.ok(true);
    }
    /**
     * 我菜单
     *
     * @param userId        用户标识
     * @param userGrid      用户网格
     * @param userDashboard 用户面板
     * @param username      用户名
     * @param roles         角色
     * @return {@link 结果}<0>
     * @param @UserValue("userId" @用户值("用户标识"
     */
    @GetMapping("/menus/my/**")
    public ReturnResult<UserMenuResult> myMenu(@UserValue("userId") String userId,
                                         @UserValue("userDashboardGrid") String userGrid,
                                         @UserValue("userDashboard") String userDashboard,
                                         @UserValue("username") String username,
                                         @UserValue("roles") Set<String> roles) {
        if(roles.isEmpty() && !ADMIN.equalsIgnoreCase(username)) {
            return ReturnResult.error("联系管理员分配权限");
        }
        Set<String> permissions = AuthClientExecute.getInstance().getUserResult().getPermission();
        Environment environment = OauthClientRuntimeContext.getEnvironment();
        /**
         * 尝试。
         * @param UserStatisticProvider.class.getResourceAsStream(StringUtils.defaultString(authProperties.getTemp( 用户statisticprovider.类.getresourceasstream(stringutils.defaultstring(authproperties.gettemp(
         */
        List<RouteVO> menu;
        try (InputStream resourceAsStream = UserStatisticProvider.class.getResourceAsStream(StringUtils.defaultString(authProperties.getTemp().getMenuPath(), "/menu.json5"));){
            List<RouteVO> routeVOS =
                    Json5.fromJsonList(IoUtils.asString(resourceAsStream, StandardCharsets.UTF_8), RouteVO.class);
            List<RouteVO> result = new ArrayList<>(routeVOS.size());
            for (RouteVO routeVO : routeVOS) {
                String condition = routeVO.getCondition();
                if(StringUtils.isEmpty(condition)) {
                    result.add(routeVO);
                    continue;
                }

                Boolean aBoolean = false;
                if(condition.startsWith("#login.role.")) {
                    String substring = condition.substring(11);
                    aBoolean = ArrayUtils.containsAny(roles, substring.split(","), true);
                } else if (environment != null) {
                    aBoolean = environment.getProperty(condition, Boolean.class);
                }
                if(null == aBoolean || aBoolean) {
                    result.add(routeVO);
                }

            }
            menu = result;
        } catch (Exception ignored) {
            menu = Collections.emptyList();
        }
        Set<String> dashboardGrid = new HashSet<>();
        try {
            dashboardGrid = new HashSet<>(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(userGrid));
        } catch (Exception e) {
            log.debug("[oauth] 解析用户面板网格失败，回退空集合: {}", e.getMessage());
        }
        return ReturnResult.ok(new UserMenuResult(menu, permissions, dashboardGrid, userDashboard));
    }
}