// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.sso.controller.callback;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotation.Ignore;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.CookieUtil;

import com.chua.starter.entity.system.SysUser;
import com.chua.starter.entity.system.SysUserThirdParty;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.sso.service.SsoSettingService;
import com.chua.starter.sso.pojo.LoginResult;
import com.chua.starter.sso.properties.SsoProperties;
import com.chua.starter.sso.service.SsoLoginBridge;
import com.chua.starter.sso.service.ThirdPartyLoginOrchestrator;
import com.chua.starter.sso.service.SysUserThirdPartyService;
import com.chua.starter.sso.support.ThirdPartyCallbackSupport;
import com.chua.starter.sso.support.ThirdPartyHttpSupport;
import com.chua.starter.sso.support.ThirdPartyPendingBindStore;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.cache.AuthDefaultStateCache;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthGoogleRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 第三方登录回调(Google)
 *
 * @since 2026/08/03
 * @author CH
 */
@Controller
@Slf4j
@Api(tags = "第三方登录回调(Google)")
@Tag(name = "第三方登录回调(Google)")
@RequestMapping("/v2/third/login/google")
@RequiredArgsConstructor
public class GoogleCallbackController {

    /**
     * 第三方登录类型标识
     */
    private static final String THIRD_PARTY_TYPE = "google";

    /**
     * 登录开关配置组编码
     */
    private static final String SSO_GROUP = "sso";

    /**
     * 登录开关配置名
     */
    private static final String SSO_NAME = "Google";

    /**
     * 自动注册开关配置名
     */
    private static final String SSO_AUTO_REGISTER_NAME = "GoogleAutoRegister";

    /**
     * 登录回调服务（允许缺省）
     */
    @Autowired(required = false)
    /**
     * SSO 登录桥（宿主实现，允许缺省）
    */
    private SsoLoginBridge ssoLoginBridge;

    /**
     * 三方用户绑定服务
     */
    final SysUserThirdPartyService sysUserThirdPartyService;

    /**
     * 第三方登录编排器
     */
    final ThirdPartyLoginOrchestrator thirdPartyLoginOrchestrator;

    /**
     * 系统设置服务
     */
    final SsoSettingService sysSettingService;

    /**
     * 认证客户端配置
     */
    final AuthClientProperties authProperties;

    /**
     * 系统安全配置
     */
    final SsoProperties securityProperties;

    @Ignore
    @GetMapping("binder")
    /**
     * Binder
     *
     * @param authCallback 认证callback
     * @param attributes attributes
     * @param request 请求
     * @param response 响应
     * @return binder的结果
     */
    public RedirectView binder(AuthCallback authCallback, RedirectAttributes attributes, HttpServletRequest request,
        HttpServletResponse response) {
        String userId = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_userId");
        RedirectView view = new RedirectView();
        String systemCallback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_system_callback");
        // 读取点兜底：缓存里的回调地址过白名单（开放重定向读取点防线）
        view.setUrl(ThirdPartyCallbackSupport.safeSystemCallback(systemCallback, securityProperties));

        if (!isThirdPartyEnabled()) {
            ThirdPartyCallbackSupport.markFailure(attributes, view, "Google登录未开启，无法绑定");
            return view;
        }

        if (userId == null) {
            ThirdPartyCallbackSupport.markFailure(attributes, view, "请重新登录重试");
            return view;
        }

        long count = sysUserThirdPartyService.count(Wrappers.<SysUserThirdParty>lambdaQuery()
                .eq(SysUserThirdParty::getSysUserId, userId)
                .eq(SysUserThirdParty::getSysUserThirdPartyType, SysUserThirdParty.Type.GOOGLE.name()));
        if (count > 0) {
            ThirdPartyCallbackSupport.markFailure(attributes, view, "已绑定Google，无需重复绑定");
            return view;
        }

        AuthUser authUser = doAuthLogin(authCallback);
        if (authUser == null) {
            ThirdPartyCallbackSupport.markFailure(attributes, view, "Google授权失败");
            return view;
        }

        SysUser sysUser = ssoLoginBridge.getCallbackUser(userId);
        SysUserThirdParty thirdParty = new SysUserThirdParty();
        thirdParty.setSysUserId(Integer.valueOf(userId));
        thirdParty.setSysUserThirdPartyType(SysUserThirdParty.Type.GOOGLE.name());
        thirdParty.setSysUserThirdPartyName(StringUtils.defaultString(authUser.getUsername(), authUser.getNickname()));
        thirdParty.setSysUserThirdPartyUid(authUser.getUuid());
        thirdParty.setSysUserThirdPartyValue(authUser.getEmail());

        boolean updateUserInfo = false;
        if (StringUtils.isEmpty(sysUser.getSysUserAvatar()) && StringUtils.isNotEmpty(authUser.getAvatar())) {
            sysUser.setSysUserAvatar(authUser.getAvatar());
            updateUserInfo = true;
        }
        if (StringUtils.isEmpty(sysUser.getSysUserEmail()) && StringUtils.isNotEmpty(authUser.getEmail())) {
            sysUser.setSysUserEmail(authUser.getEmail());
            updateUserInfo = true;
        }
        if (updateUserInfo) {
            ssoLoginBridge.updateCallbackUser(sysUser);
        }

        sysUserThirdPartyService.save(thirdParty);
        return view;
    }

    @Ignore
    @GetMapping("callback")
    /**
     * Callback
     *
     * @param authCallback 认证callback
     * @param attributes attributes
     * @param request 请求
     * @param response 响应
     * @return callback的结果
     */
    public RedirectView callback(AuthCallback authCallback, RedirectAttributes attributes, HttpServletRequest request,
        HttpServletResponse response) {
        String systemCallback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_system_callback");
        RedirectView errorView = new RedirectView();
        // 读取点兜底：缓存里的回调地址过白名单（开放重定向读取点防线）
        errorView.setUrl(ThirdPartyCallbackSupport.safeSystemCallback(systemCallback, securityProperties));

        if (!isThirdPartyEnabled()) {
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, "Google登录未开启");
            return errorView;
        }

        String thirdType = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_thirdType");
        if ("0".equals(thirdType)) {
            return binder(authCallback, attributes, request, response);
        }

        AuthUser authUser = doAuthLogin(authCallback);
        if (authUser == null) {
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, "Google授权失败");
            return errorView;
        }

        SysUserThirdParty userThirdParty = sysUserThirdPartyService.getOne(
                Wrappers.<SysUserThirdParty>lambdaQuery()
                        .eq(SysUserThirdParty::getSysUserThirdPartyUid, authUser.getUuid())
                        .eq(SysUserThirdParty::getSysUserThirdPartyType, SysUserThirdParty.Type.GOOGLE.name())
        );
        if (userThirdParty == null) {
            // 自动注册开启时，首次 Google 登录直接创建本地账号并绑定，用户名=Google 账户标识（重复值加前缀区分）；
            // 注册失败回落原有待绑定流程
            if (isAutoRegisterEnabled()) {
                ThirdPartyPendingBindStore.PendingBind pending = new ThirdPartyPendingBindStore.PendingBind(
                        SysUserThirdParty.Type.GOOGLE.name(), THIRD_PARTY_TYPE, authUser.getUuid(),
                        authUser.getUsername(), authUser.getNickname(), authUser.getAvatar(), authUser.getEmail());
                SysUser registered = ssoLoginBridge == null ? null
                        : ssoLoginBridge.registerThirdPartyUser(pending, request);
                if (registered != null && registered.getSysUserId() != null) {
                    return issueLoginRedirect(registered, request, response, attributes, systemCallback);
                }
                log.warn("Google自动注册未成功，回落到待绑定流程: uid={}", authUser.getUuid());
            }
            // 未绑定：暂存三方身份为一次性待绑定凭证，引导前端绑定页完成账密核验后再签发令牌
            String bindToken = ThirdPartyPendingBindStore.create(SysUserThirdParty.Type.GOOGLE.name(), THIRD_PARTY_TYPE, authUser);
            errorView.addStaticAttribute("bindToken", bindToken);
            return errorView;
        }

        // 令牌签发统一走编排器：static 协议下本地用户不在 plugin.oauth.temp.user 静态账号名单内，
        // 直连 AuthClientExecute 必然换取不到令牌，必须回落到与密码登录同源的本地签发链路
        LoginAuthResult accessToken = thirdPartyLoginOrchestrator
                .issueThirdPartyToken(userThirdParty.getSysUserId(), THIRD_PARTY_TYPE, request);
        if (!ThirdPartyCallbackSupport.hasLoginOutcome(accessToken)) {
            log.warn("Google三方登录换取令牌失败");
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, "Google登录失败，请重试");
            return errorView;
        }
        LoginResult loginResult = thirdPartyLoginOrchestrator.toLoginResult(accessToken, response);

        RedirectView view = new RedirectView();
        String nanoTime = StringUtils.padAfter(System.nanoTime() + "", 16, '0');
        // 加密登录结果 + 登记一次性 nonce（供 /v2/user/third/login/exchange 兑换）
        String encrypt = ThirdPartyCallbackSupport.encryptRedirectParam(loginResult, securityProperties);
        response.setHeader("x-response-time", nanoTime);
        view.addStaticAttribute("redirectParam", encrypt);
        view.setUrl(ThirdPartyCallbackSupport.safeSystemCallback(systemCallback, securityProperties));
        return view;
    }

    /**
     * 执行认证login
     *
     * @param authCallback 认证callback
     * @return 执行认证login的结果
     */
    private AuthUser doAuthLogin(AuthCallback authCallback) {
        String clientSecret = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientSecret");
        String clientId = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientId");
        String callback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_callback");
        AuthConfig authConfig = AuthConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(callback)
                .httpConfig(ThirdPartyHttpSupport.httpConfig())
                .build();
        AuthGoogleRequest authRequest = new AuthGoogleRequest(authConfig, AuthDefaultStateCache.INSTANCE);
        AuthResponse<?> login = authRequest.login(authCallback);
        if (!login.ok()) {
            log.warn("Google授权失败: {}", login.getMsg());
            return null;
        }
        return (AuthUser) login.getData();
    }

    /**
     * 是否thirdparty已启用
     *
     * @return 是否thirdparty已启用的结果
     */
    private boolean isThirdPartyEnabled() {
        return sysSettingService.isThirdPartyEnabled(SSO_NAME);
    }

    /**
     * Google 自动注册开关是否开启
     *
     * <p>读取 sys_setting {@code sso}/{@code GoogleAutoRegister}，行缺失、停用或异常一律视为关闭（fail-safe）。</p>
     *
     * @return 自动注册开关开启返回 {@code true}
     */
    private boolean isAutoRegisterEnabled() {
        return sysSettingService.isAutoRegisterEnabled(SSO_NAME);
    }

    /**
     * 为新注册用户签发登录结果并重定向
     *
     * <p>与「已绑定」分支同源：{@code AuthType.THIRD_PARTY} 换票 + Bearer 头 +
     * {@code x-oauth-cookie} + 加密 {@code redirectParam}，由 /v2/user/third/login/exchange 兑换，
     * 避免在 static 协议下自动注册账号落不到密码登录同源的换票链路。</p>
     *
     * @param user            新注册用户
     * @param request         HTTP 请求
     * @param response        HTTP 响应
     * @param attributes      重定向 attributes
     * @param systemCallback  前端回调地址
     * @return 携带登录凭证的重定向视图
     */
    private RedirectView issueLoginRedirect(SysUser user, HttpServletRequest request, HttpServletResponse response,
                                            RedirectAttributes attributes, String systemCallback) {
        RedirectView view = new RedirectView();
        view.setUrl(ThirdPartyCallbackSupport.safeSystemCallback(systemCallback, securityProperties));
        LoginAuthResult accessToken = thirdPartyLoginOrchestrator.issueThirdPartyToken(user.getSysUserId(), THIRD_PARTY_TYPE, request);
        if (!ThirdPartyCallbackSupport.hasLoginOutcome(accessToken)) {
            log.warn("Google自动注册后登录签发失败: userId={}", user.getSysUserId());
            ThirdPartyCallbackSupport.markFailure(attributes, view, "Google登录失败，请重试");
            return view;
        }
        LoginResult loginResult = thirdPartyLoginOrchestrator.toLoginResult(accessToken, response);
        String nanoTime = StringUtils.padAfter(System.nanoTime() + "", 16, '0');
        // 加密登录结果 + 登记一次性 nonce（供 /v2/user/third/login/exchange 兑换）
        String encrypt = ThirdPartyCallbackSupport.encryptRedirectParam(loginResult, securityProperties);
        response.setHeader("x-response-time", nanoTime);
        view.addStaticAttribute("redirectParam", encrypt);
        return view;
    }
}
