// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.sso.controller.callback;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotation.Ignore;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.sso.properties.SsoProperties;
import com.chua.starter.entity.system.SysUser;
import com.chua.starter.entity.system.SysUserThirdParty;
import com.chua.starter.sso.service.ThirdPartyLoginOrchestrator;
import com.chua.starter.sso.service.SsoSettingService;
import com.chua.starter.sso.pojo.LoginResult;
import com.chua.starter.sso.service.SsoLoginBridge;
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
import me.zhyd.oauth.request.AuthGiteeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * gitee接口
 *
 * @since 2024/9/4
 * @author CH
 */
@Controller
@Slf4j
@Api(tags = "第三方登录回调(Gitee)")
@Tag(name = "第三方登录回调(Gitee)")
@RequestMapping("/v2/third/login/gitee")
@RequiredArgsConstructor
public class GiteeCallbackController {


    /**
     * Third_party_类型
    */
    private static final String THIRD_PARTY_TYPE = "gitee";
    /**
     * 单点登录_群体
    */
    private static final String SSO_GROUP = "sso";

    /**
     * Gitee 登录开关配置名
     */
    private static final String SSO_NAME = "Gitee";

    /**
     * Gitee 自动注册开关配置名
     */
    private static final String SSO_AUTO_REGISTER_NAME = "GiteeAutoRegister";

    @Autowired(required = false)
    /**
     * SSO 登录桥（宿主实现，允许缺省）
    */
    private SsoLoginBridge ssoLoginBridge;
    final SysUserThirdPartyService sysUserThirdPartyService; // sys用户thirdparty服务
    final ThirdPartyLoginOrchestrator thirdPartyLoginOrchestrator; // 第三方登录编排器
    final SsoSettingService sysSettingService; // syssetting服务
    final AuthClientProperties authProperties; // 认证属性
    final SsoProperties securityProperties; // 安全性属性

    /**
     * Gitee账号绑定回调
     * <p>用户授权后回调此接口，将Gitee账号与系统用户绑定</p>
     *
     * @param authCallback Gitee回调参数
     * @param attributes   重定向属性
     * @param request      请求
     * @param response     响应
     * @return 重定向视图
     */
    @Ignore
    @GetMapping("binder")
    public RedirectView binder(AuthCallback authCallback, RedirectAttributes attributes, HttpServletRequest request,
        HttpServletResponse response) {
        String userId = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_userId");
        RedirectView view = new RedirectView();
        String systemCallback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_system_callback");
        // 读取点兜底：缓存里的回调地址过白名单（开放重定向读取点防线）
        view.setUrl(ThirdPartyCallbackSupport.safeSystemCallback(systemCallback, securityProperties));
        
        // 检查 Gitee 登录是否开启
        if (!isThirdPartyEnabled()) {
            log.warn("Gitee登录未开启，拒绝绑定请求");
            ThirdPartyCallbackSupport.markFailure(attributes, view, "Gitee登录未开启，无法绑定");
            return view;
        }
        
        if(null == userId) {
            ThirdPartyCallbackSupport.markFailure(attributes, view, "请重新登录重试");
            return view;
        }
        long count = sysUserThirdPartyService.count(Wrappers.<SysUserThirdParty>lambdaQuery().eq(SysUserThirdParty::getSysUserId, userId));
        if(count > 0) {
            ThirdPartyCallbackSupport.markFailure(attributes, view, "已绑定，无需重复绑定");
            return view;
        }
        String info = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_info");
        String clientSecret = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientSecret");
        String thirdType = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_thirdType");
        String clientId = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientId");
        String callback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_callback");
        AuthConfig authConfig = AuthConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(callback)
                .httpConfig(ThirdPartyHttpSupport.httpConfig())
                .build();
        AuthGiteeRequest authGiteeRequest = new AuthGiteeRequest(authConfig, AuthDefaultStateCache.INSTANCE);
        AuthResponse login = authGiteeRequest.login(authCallback);
        AuthUser authUser = (AuthUser) login.getData();
        if (null == authUser) {
            log.warn("Gitee绑定授权失败: {}", login.getMsg());
            ThirdPartyCallbackSupport.markFailure(attributes, view, "Gitee授权失败，请重试");
            return view;
        }
        SysUser userServiceById = ssoLoginBridge.getCallbackUser(userId);
        SysUserThirdParty sysUserThirdParty = new SysUserThirdParty();
        sysUserThirdParty.setSysUserId(Integer.valueOf(userId));
        sysUserThirdParty.setSysUserThirdPartyType("gitee");
        sysUserThirdParty.setSysUserThirdPartyName(authUser.getUsername());
        sysUserThirdParty.setSysUserThirdPartyUid(authUser.getUuid());
        boolean updateUserInfo = false;
        if(StringUtils.isEmpty(userServiceById.getSysUserAvatar())) {
            userServiceById.setSysUserAvatar(authUser.getAvatar());
            updateUserInfo = true;
        }

        if(StringUtils.isEmpty(userServiceById.getSysUserEmail())) {
            userServiceById.setSysUserEmail(authUser.getEmail());
            updateUserInfo = true;
        }

        if(updateUserInfo) {
            ssoLoginBridge.updateCallbackUser(userServiceById);
        }
        sysUserThirdPartyService.save(sysUserThirdParty);
        return view;
    }
    /**
     * Gitee登录回调
     * <p>用户授权后回调此接口进行登录，需要先绑定账号</p>
     *
     * @param authCallback Gitee回调参数
     * @param attributes   重定向属性
     * @param request      请求
     * @param response     响应
     * @return 重定向视图
     */
    @Ignore
    @GetMapping("callback")
    public RedirectView callback(AuthCallback authCallback, RedirectAttributes attributes, HttpServletRequest request,
        HttpServletResponse response) {
        String systemCallback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_system_callback");
        RedirectView errorView = new RedirectView();
        // 读取点兜底：缓存里的回调地址过白名单（开放重定向读取点防线）
        errorView.setUrl(ThirdPartyCallbackSupport.safeSystemCallback(systemCallback, securityProperties));
        
        // 检查 Gitee 登录是否开启
        if (!isThirdPartyEnabled()) {
            log.warn("Gitee登录未开启，拒绝登录请求");
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, "Gitee登录未开启");
            return errorView;
        }
        
        String thirdType = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_thirdType");
        if("0".equals(thirdType)) {
            return binder(authCallback, attributes, request, response);
        }
        String info = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_info");
        String clientSecret = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientSecret");
        String clientId = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientId");
        String userId = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_userId");
        String callback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_callback");
        AuthConfig authConfig = AuthConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(callback)
                .httpConfig(ThirdPartyHttpSupport.httpConfig())
                .build();
        AuthGiteeRequest authGiteeRequest = new AuthGiteeRequest(authConfig, AuthDefaultStateCache.INSTANCE);
        AuthResponse login = authGiteeRequest.login(authCallback);
        AuthUser authUser = (AuthUser) login.getData();
        if (null == authUser) {
            log.warn("Gitee登录授权失败: {}", login.getMsg());
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, "Gitee授权失败，请重试");
            return errorView;
        }
        SysUserThirdParty userThirdParty = sysUserThirdPartyService.getOne(
            Wrappers.<SysUserThirdParty>lambdaQuery()
                .eq(SysUserThirdParty::getSysUserThirdPartyUid, authUser.getUuid()));
        if(null == userThirdParty) {
            // 自动注册开启时：首次 Gitee 登录直接创建本地账号并绑定（用户名=Gitee uid，不加前缀），
            // 注册失败回落到原有待绑定流程
            if (isAutoRegisterEnabled()) {
                ThirdPartyPendingBindStore.PendingBind pending = new ThirdPartyPendingBindStore.PendingBind(
                        THIRD_PARTY_TYPE, THIRD_PARTY_TYPE, authUser.getUuid(),
                        authUser.getUsername(), authUser.getNickname(), authUser.getAvatar(), authUser.getEmail());
                SysUser registered = ssoLoginBridge == null ? null
                        : ssoLoginBridge.registerThirdPartyUser(pending, request);
                if (registered != null && registered.getSysUserId() != null) {
                    return issueLoginRedirect(registered, request, response, attributes, systemCallback);
                }
                log.warn("Gitee自动注册未成功，回落到绑定流程: uid={}", authUser.getUuid());
            }
            // 未绑定：暂存三方身份为一次性待绑定凭证，引导前端绑定页完成账密核验后再签发令牌
            String bindToken = ThirdPartyPendingBindStore.create(THIRD_PARTY_TYPE, THIRD_PARTY_TYPE, authUser);
            errorView.addStaticAttribute("bindToken", bindToken);
            return errorView;
        }

        LoginAuthResult accessToken = thirdPartyLoginOrchestrator.issueThirdPartyToken(userThirdParty.getSysUserId(), THIRD_PARTY_TYPE, request);
        if (!ThirdPartyCallbackSupport.hasLoginOutcome(accessToken)) {
            log.warn("Gitee三方登录换取令牌失败");
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, "第三方登录失败，请重试");
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
     * 检查第三方登录是否开启
     * <p>从sys_setting表中查询sso组下Gitee配置是否为true</p>
     *
     * @return true-开启，false-未开启
     */
    private boolean isThirdPartyEnabled() {
        return sysSettingService.isThirdPartyEnabled("Gitee");
    }

    /**
     * Gitee 自动注册开关是否开启
     *
     * <p>读取 sys_setting {@code sso}/{@code GiteeAutoRegister}，行缺失、停用或异常均视为关闭（fail-safe）。</p>
     *
     * @return true-开启，false-关闭
     */
    private boolean isAutoRegisterEnabled() {
        return sysSettingService.isAutoRegisterEnabled("Gitee");
    }

    /**
     * 自动注册成功后签发令牌并生成换票凭证重定向
     *
     * @param user           自动注册出来的本地用户
     * @param request        请求
     * @param response       响应
     * @param attributes     重定向属性
     * @param systemCallback 前端回跳地址
     * @return 重定向视图
     */
    private RedirectView issueLoginRedirect(SysUser user, HttpServletRequest request, HttpServletResponse response,
                                            RedirectAttributes attributes, String systemCallback) {
        RedirectView view = new RedirectView();
        view.setUrl(ThirdPartyCallbackSupport.safeSystemCallback(systemCallback, securityProperties));
        LoginAuthResult accessToken = thirdPartyLoginOrchestrator.issueThirdPartyToken(user.getSysUserId(), THIRD_PARTY_TYPE, request);
        if (!ThirdPartyCallbackSupport.hasLoginOutcome(accessToken)) {
            log.warn("Gitee自动注册后登录签发令牌失败: userId={}", user.getSysUserId());
            ThirdPartyCallbackSupport.markFailure(attributes, view, "Gitee登录失败，请重试");
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
