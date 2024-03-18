package com.chua.starter.oauth.server.support.provider;

import com.chua.common.support.crypto.Codec;
import com.chua.common.support.json.Json;
import com.chua.common.support.json.JsonObject;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.MapUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.entity.AuthRequest;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.user.AccessSecret;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.server.support.advice.AdviceView;
import com.chua.starter.oauth.server.support.check.LoginCheck;
import com.chua.starter.oauth.server.support.condition.OnBeanCondition;
import com.chua.starter.oauth.server.support.properties.AuthServerProperties;
import com.chua.starter.oauth.server.support.resolver.LoggerResolver;
import com.chua.starter.oauth.server.support.token.TokenResolver;
import com.google.common.base.Strings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.chua.common.support.lang.code.ReturnCode.*;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.*;

/**
 * 登录
 *
 * @author CH
 */
@Controller
@Conditional(OnBeanCondition.class)
@SuppressWarnings("ALL")
@RequestMapping("${plugin.auth.server.context-path:}")
public class LoginProvider implements InitializingBean {
    @Resource
    private AuthServerProperties authServerProperties;

    @Value("${plugin.auth.server.context-path:}")
    private String contextPath;

    @Resource
    private LoginCheck loginCheck;
    @Resource
    private LoggerResolver loggerResolver;
    private String encryption;
    private String serviceKey;

    /**
     * 无权限页面
     *
     * @param url      原始地址
     * @param modelMap 参数
     * @return 登录页
     */
    @GetMapping("/oauth-page")
    public String oauthPage(@RequestParam(value = "redirect_url", required = false) String url, Model modelMap, HttpServletRequest request) {
        return "oauth/oauth-page";
    }

    /**
     * 登出
     *
     * @return 登出
     */
    public ReturnResult<UserResult> logout(
            HttpServletRequest request, HttpServletResponse response) {
        AuthRequest request1 = new AuthRequest();
        request1.setType(LogoutType.NONE.name());
        logoutWeb(request1, request, response);
        return ReturnResult.ok();
    }


    /**
     * 登出
     *
     * @return 登出
     */
    @PostMapping("/logout")
    @ResponseBody
    public AdviceView logoutWeb(@RequestBody AuthRequest request1,
                                HttpServletRequest request, HttpServletResponse response) {
        String data = request1.getData();
        LogoutType type = LogoutType.valueOf(request1.getType().toUpperCase());
        String address = RequestUtils.getIpAddress(request);
        String accept = request.getHeader("accept");

        TokenResolver tokenResolver = ServiceProvider.of(TokenResolver.class).getExtension(authServerProperties.getTokenManagement());
        Cookie cookie = CookieUtil.get(request, authServerProperties.getCookieName());
        if (null != cookie) {
            tokenResolver.logout(cookie.getValue());
            CookieUtil.remove(request, response, authServerProperties.getCookieName());
        }

        String headerValue = request.getHeader(authServerProperties.getTokenName());

        if (!Strings.isNullOrEmpty(headerValue)) {
            tokenResolver.logout(headerValue);
        }


        Object attributeValue = request.getAttribute(authServerProperties.getTokenName());

        if (null != attributeValue) {
            tokenResolver.logout(attributeValue.toString());
        }

        Object uid = request.getAttribute("uid");
        if (null != uid) {
            tokenResolver.logout(uid.toString(), (LogoutType) request.getAttribute("type"));
        }

        loggerResolver.register("logout", OK.getCode(), "登出成功", address);
        if (!Strings.isNullOrEmpty(accept) && !accept.contains("text/html")) {
            return new AdviceView(logoutApi(data, type, request, response), request);
        }
        return new AdviceView("redirect:/login");
    }

    /**
     * 登出api
     *
     * @param data     数据
     * @param type     类型
     * @param request  请求
     * @param response 响应
     * @return 结果
     */
    private Object logoutApi(String data, LogoutType type, HttpServletRequest request, HttpServletResponse response) {
        if (Strings.isNullOrEmpty(data)) {
            return ReturnResult.ok();
        }

        JsonObject jsonObject = Json.getJsonObject(Codec.build(encryption, serviceKey).decodeHex(data));
        String key = jsonObject.getString(OAUTH_KEY);
        JsonObject request1 = Json.getJsonObject(Codec.build(encryption, DigestUtils.md5Hex(key) ).decodeHex(jsonObject.getString(OAUTH_VALUE)));
        String uid = request1.getString("uid");
        request.setAttribute("uid", uid);
        request.setAttribute("type", type);
        return logoutWeb(new AuthRequest(), request, response);
    }

    /**
     * 登录
     *
     * @param username 账号
     * @param passwd   密码
     * @param authType 类型
     * @param data     请求数据
     * @param request  请求
     * @param response
     * @return 登录页
     */
    public ReturnResult<?> doLogin(String username, String passwd, String authType, String data, HttpServletRequest request, HttpServletResponse response) {
        String address = RequestUtils.getIpAddress(request);
        AccessSecret accessSecret = getAkSk(data);
        if (StringUtils.isEmpty(passwd)) {
            passwd = accessSecret.getPassword();
        }

        if (null != accessSecret.getExt()) {
            address = MapUtils.getString(accessSecret.getExt(), "address", address);
        }

        ReturnResult result = loginCheck.doLogin(address, username, passwd, authType, accessSecret.getExt());
        if (!result.getCode().equals(OK.getCode())) {
            loggerResolver.register("doLogin", RESOURCE_NOT_FOUND.getCode(), "认证服务器离线", address);
            Object data1 = result.getData();
            if (null != data1) {
                request.setAttribute(authServerProperties.getTokenName(), ((LoginResult) data1).getToken());
            }
            logout(request, response);
            return ReturnResult.newBuilder().code(result.getCode()).data(result.getData()).msg(result.getMsg()).build();
        }

        if (!checkAkSk(result, data, accessSecret)) {
            Object data1 = result.getData();
            if (null != data1) {
                request.setAttribute(authServerProperties.getTokenName(), ((LoginResult) data1).getToken());
            }
            logout(request, response);
            return ReturnResult.newBuilder().code(RESULT_ACCESS_UNAUTHORIZED.getCode()).msg("ak/sk无效").build();
        }
        if (authServerProperties.isOpenReturnCheckAkSk()) {
            doEncodeReturnValue(result, accessSecret);
        }

        loggerResolver.register("doLogin", OK.getCode(), "登录成功", address);
        return result;

    }

    /**
     * 加密返回结果
     *
     * @param resultReturnResult 结果
     * @param accessSecret       AK/SK
     */
    private void doEncodeReturnValue(ReturnResult resultReturnResult, AccessSecret accessSecret) {
        if (!authServerProperties.isOpenReturnCheckAkSk()) {
            return;
        }

        LoginResult loginResult = (LoginResult) resultReturnResult.getData();
        loginResult.getUserResult().setAccessSecret(new AccessSecret());

        String json = Json.toJson(loginResult);
        resultReturnResult.setData(Codec.build(encryption, accessSecret.getUKey()).encodeHex(json));
    }

    private AccessSecret getAkSk(String data) {
        return null != data ? createAccessSecret(data) : null;
    }

    /**
     * 检验ak/sk合法
     *
     * @param resultReturnResult 数据
     * @param data               请求数据
     * @param accessSecret1      AK/SK
     * @return 合法性
     */
    private boolean checkAkSk(ReturnResult<Object> resultReturnResult, String data, AccessSecret accessSecret1) {
        if (!authServerProperties.isOpenCheckAkSk()) {
            return doEncode(true, resultReturnResult, null);
        }

        LoginResult loginResult = (LoginResult) resultReturnResult.getData();

        AccessSecret accessSecret = loginResult.getUserResult().getAccessSecret();
        if (null == accessSecret) {
            return false;
        }

        try {
            AccessSecret requestAccessSecret = accessSecret1;
            return doEncode(accessSecret.getAccessKey().equals(requestAccessSecret.getAccessKey())
                    && accessSecret.getSecretKey().equals(requestAccessSecret.getSecretKey()), resultReturnResult, requestAccessSecret.getUKey());
        } catch (Exception e) {
            return false;
        }

    }

    /**
     * 数据加密
     *
     * @param status             状态
     * @param resultReturnResult 结果
     * @param uKey               ukey
     * @return 加密
     */
    private boolean doEncode(boolean status, ReturnResult<Object> resultReturnResult, String uKey) {
        if (!authServerProperties.isOpenCheckAkSk()) {
            return true;
        }

        return status;
    }

    /**
     * 解析请求
     *
     * @param data 数据
     * @return 请求
     */
    private AccessSecret createAccessSecret(String data) {
        String decode1 = Codec.build(encryption, serviceKey).decodeHex(data);
        JsonObject jsonObject = Json.getJsonObject(decode1);
        String key = jsonObject.getString(OAUTH_KEY);
        String authKey = jsonObject.getString("x-oauth-uid");
        String value = jsonObject.getString(OAUTH_VALUE);
        String request = Codec.build(encryption, DigestUtils.md5Hex(key)).decodeHex(value);
        JsonObject jsonObject2 = Json.getJsonObject(request);

        return new AccessSecret(jsonObject2.getString(ACCESS_KEY),
                jsonObject2.getString(SECRET_KEY),
                authKey,
                jsonObject.getString("password"),
                jsonObject.getObject("ext", Map.class)
        );
    }

    /**
     * 登录
     *
     * @return 登录页
     */
    @PostMapping("/doLogin")
    public AdviceView doWebLogin(
            @RequestBody AuthRequest authRequest,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            RedirectAttributes modelMap
    ) {
        String username = authRequest.getUsername(),
                passwd = authRequest.getPasswd(),
                code = authRequest.getCode(),
                type = authRequest.getType(),
                data = authRequest.getData(),
                url = authRequest.getRedirect_url(),
                mode = authRequest.getMode(),
                ifRemember = authRequest.getIfRemember();
        String address = RequestUtils.getIpAddress(request);
        String accept = request.getHeader("accept");
        if (null != accept && !accept.contains("text/html")) {
            ReturnResult<?> result = doLogin(username, passwd, type, data, request, response);
            return new AdviceView(result, request);
        }

        if (!DigestUtils.sha512Hex(username + passwd + code).equals(data)) {
            modelMap.addFlashAttribute("msg", "登录失败");
            try {
                return new AdviceView("redirect:" + contextPath + "/login?redirect_url=" + URLEncoder.encode(url, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                return new AdviceView("redirect:" + contextPath + "/login?redirect_url=" + url);
            }
        }
        String sessionKey = Optional.ofNullable(request.getSession().getAttribute("KAPTCHA_SESSION_KEY")).orElse("").toString();
        if (Strings.isNullOrEmpty(code) || !code.equalsIgnoreCase(sessionKey)) {
            loggerResolver.register("doWebLogin", REQUEST_PARAM_ERROR.getCode(), "[" + sessionKey + "][" + code + "]校验码错误", address);
            modelMap.addFlashAttribute("msg", "校验码错误");
            try {
                return new AdviceView("redirect:" + contextPath + "/login?redirect_url=" + URLEncoder.encode(url, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                return new AdviceView("redirect:" + contextPath + "/login?redirect_url=" + url);
            }
        }

        boolean ifRem = "on".equals(ifRemember);
        ReturnResult<LoginResult> result = loginCheck.doLogin(address, username, passwd, type, null);
        loggerResolver.register("doWebLogin", result.getCode(), "认证服务器离线", address);

        if (result.getCode().equals(500)) {
            redirectAttributes.addFlashAttribute("redirect_url", url);
            return new AdviceView("redirect:" + contextPath + "/login");
        }

        if (result.getCode().equals(403)) {
            modelMap.addFlashAttribute("msg", Optional.ofNullable(result.getMsg()).orElse("当前登陆端限制登录, 需要注册密钥"));
            try {
                return new AdviceView("redirect:" + contextPath + "/login?redirect_url=" + URLEncoder.encode(url, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                return new AdviceView("redirect:" + contextPath + "/login?redirect_url=" + url);
            }
        }

        LoginResult loginResult = result.getData();
        loggerResolver.register("doWebLogin", OK.getCode(), "登录成功", address);
        CookieUtil.set(response, authServerProperties.getCookieName(), loginResult.getToken(), ifRem);
        return new AdviceView("redirect:" + url);
    }


    /**
     * 登录
     *
     * @param url      原始地址
     * @param modelMap 参数
     * @return 登录页
     */
    @GetMapping("/login")
    public String login(@RequestParam(value = "redirect_url", required = false) String url, Model modelMap, HttpServletRequest request) {
        String address = RequestUtils.getIpAddress(request);

        if (null == url) {
            url = request.getParameter("redirect_url");
        }

        if (null == url) {
            loggerResolver.register("login", RESULT_ACCESS_UNAUTHORIZED.getCode(), "redirect_url不能为空", address);
            return "oauth/login";
        }

        try {
            modelMap.addAttribute("redirect_url", URLDecoder.decode(URLDecoder.decode(url, "UTF-8"), "UTF-8"));
        } catch (UnsupportedEncodingException ignored) {
        }
        Cookie[] cookies = request.getCookies();
        String token = request.getHeader(authServerProperties.getTokenName());
        String key = UUID.randomUUID().toString();
        if (null != cookies || Strings.isNullOrEmpty(token)) {
            ReturnResult<String> returnResult = oauthResult(cookies, token, key, address);
            if (returnResult.getCode().equals(200)) {
                return "redirect:" + url;
            }
        }
        loggerResolver.register("login", OK.getCode(), "登录成功", address);
        return "oauth/login";
    }


    /**
     * 认证
     *
     * @param cookies cookie
     * @param token   token
     * @param key     key
     * @param address 地址
     * @return ReturnResult
     */
    private ReturnResult<String> oauthResult(Cookie[] cookies, String token, String key, String address) {

        TokenResolver tokenResolver = ServiceProvider.of(TokenResolver.class).getExtension(authServerProperties.getTokenManagement());
        ReturnResult<UserResult> oauth = tokenResolver.resolve(cookies, token);
        UserResult data1 = oauth.getData();

        String rs = null;
        if (null != data1) {
            data1.setAddress(null);
            Codec codec = Codec.build(encryption, key);
            rs = codec.encodeHex(Json.toJson(data1));
        }

        loggerResolver.register("oauth", oauth.getCode(), oauth.getMsg(), address);
        return ReturnResult.of(oauth.getCode(), rs, oauth.getMsg());
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        this.encryption = authServerProperties.getEncryption();
        this.serviceKey = authServerProperties.getServiceKey();
    }
}
