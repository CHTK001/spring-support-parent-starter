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
import com.chua.starter.sso.properties.SsoProperties;
import com.chua.starter.sso.service.SsoSettingService;
import com.chua.starter.sso.mapper.SysWechatUserMapper;
import com.chua.starter.sso.pojo.LoginResult;
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
import me.zhyd.oauth.request.AuthWeChatOpenRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 微信网页登录回调接口
 * <p>处理微信开放平台OAuth2.0授权回调</p>
 *
 * @since 2024/12/11
 * @author CH
 */
@Controller
@Slf4j
@Api(tags = "第三方登录回调(微信)")
@Tag(name = "第三方登录回调(微信)")
@RequestMapping("/v2/third/login/wechat")
@RequiredArgsConstructor
public class WechatCallbackController {

    /**
     * Third_party_类型
    */
    private static final String THIRD_PARTY_TYPE = "wechat";
    /**
     * 单点登录_群体
    */
    private static final String SSO_GROUP = "sso";
    /**
     * 单点登录_名称
    */
    private static final String SSO_NAME = "Wechat";

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
    final SysWechatUserMapper sysWechatUserMapper; // syswechat用户映射器

    /**
     * 微信账号绑定回调
     * <p>用户授权后回调此接口，将微信账号与系统用户绑定</p>
     *
     * @param authCallback 微信回调参数
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

        // 检查微信登录是否开启
        if (!isThirdPartyEnabled()) {
            log.warn("微信登录未开启，拒绝绑定请求");
            ThirdPartyCallbackSupport.markFailure(attributes, view, "微信登录未开启，无法绑定");
            return view;
        }

        if (null == userId) {
            ThirdPartyCallbackSupport.markFailure(attributes, view, "请重新登录重试");
            return view;
        }

        long count = sysUserThirdPartyService.count(Wrappers.<SysUserThirdParty>lambdaQuery()
                .eq(SysUserThirdParty::getSysUserId, userId)
                .eq(SysUserThirdParty::getSysUserThirdPartyType, THIRD_PARTY_TYPE));
        if (count > 0) {
            ThirdPartyCallbackSupport.markFailure(attributes, view, "已绑定微信，无需重复绑定");
            return view;
        }

        String clientSecret = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientSecret");
        String clientId = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientId");
        String callback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_callback");

        AuthConfig authConfig = AuthConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(callback)
                .httpConfig(ThirdPartyHttpSupport.httpConfig())
                .build();
        AuthWeChatOpenRequest authRequest = new AuthWeChatOpenRequest(authConfig, AuthDefaultStateCache.INSTANCE);
        AuthResponse login = authRequest.login(authCallback);
        if (!login.ok()) {
            log.error("微信授权失败: {}", login.getMsg());
            ThirdPartyCallbackSupport.markFailure(attributes, view, "微信授权失败: " + login.getMsg());
            return view;
        }

        AuthUser authUser = (AuthUser) login.getData();
        if (null == authUser) {
            log.error("微信授权失败: 未返回用户信息");
            ThirdPartyCallbackSupport.markFailure(attributes, view, "微信授权失败，请重试");
            return view;
        }
        SysUser userServiceById = ssoLoginBridge.getCallbackUser(userId);

        // 保存第三方绑定信息
        SysUserThirdParty sysUserThirdParty = new SysUserThirdParty();
        sysUserThirdParty.setSysUserId(Integer.valueOf(userId));
        sysUserThirdParty.setSysUserThirdPartyType(THIRD_PARTY_TYPE);
        sysUserThirdParty.setSysUserThirdPartyName(authUser.getNickname());
        sysUserThirdParty.setSysUserThirdPartyUid(authUser.getUuid());

        // 更新用户头像（如果为空）
        boolean updateUserInfo = false;
        if (StringUtils.isEmpty(userServiceById.getSysUserAvatar()) && StringUtils.isNotEmpty(authUser.getAvatar())) {
            userServiceById.setSysUserAvatar(authUser.getAvatar());
            updateUserInfo = true;
        }

        if (updateUserInfo) {
            ssoLoginBridge.updateCallbackUser(userServiceById);
        }
        sysUserThirdPartyService.save(sysUserThirdParty);

        // 保存或更新微信用户信息
        saveOrUpdateWechatUser(Integer.valueOf(userId), authUser);

        log.info("微信账号绑定成功: userId={}, openId={}", userId, authUser.getUuid());
        return view;
    }

    /**
     * 微信登录回调
     * <p>用户授权后回调此接口进行登录，需要先绑定账号</p>
     *
     * @param authCallback 微信回调参数
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

        // 检查微信登录是否开启
        if (!isThirdPartyEnabled()) {
            log.warn("微信登录未开启，拒绝登录请求");
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, "微信登录未开启");
            return errorView;
        }

        String thirdType = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_thirdType");
        if ("0".equals(thirdType)) {
            return binder(authCallback, attributes, request, response);
        }

        String clientSecret = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientSecret");
        String clientId = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientId");
        String callback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_callback");

        AuthConfig authConfig = AuthConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(callback)
                .httpConfig(ThirdPartyHttpSupport.httpConfig())
                .build();
        AuthWeChatOpenRequest authRequest = new AuthWeChatOpenRequest(authConfig, AuthDefaultStateCache.INSTANCE);
        AuthResponse login = authRequest.login(authCallback);
        if (!login.ok()) {
            log.error("微信授权失败: {}", login.getMsg());
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, "微信授权失败: " + login.getMsg());
            return errorView;
        }

        AuthUser authUser = (AuthUser) login.getData();
        if (null == authUser) {
            log.error("微信授权失败: 未返回用户信息");
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, "微信授权失败，请重试");
            return errorView;
        }
        SysUserThirdParty userThirdParty = sysUserThirdPartyService.getOne(
                Wrappers.<SysUserThirdParty>lambdaQuery()
                        .eq(SysUserThirdParty::getSysUserThirdPartyUid, authUser.getUuid())
                        .eq(SysUserThirdParty::getSysUserThirdPartyType, THIRD_PARTY_TYPE));

        if (null == userThirdParty) {
            log.warn("微信登录失败: 未绑定系统用户, openId={}", authUser.getUuid());
            // 未绑定：暂存三方身份为一次性待绑定凭证，引导前端绑定页完成账密核验后再签发令牌
            String bindToken = ThirdPartyPendingBindStore.create(THIRD_PARTY_TYPE, THIRD_PARTY_TYPE, authUser);
            errorView.addStaticAttribute("bindToken", bindToken);
            return errorView;
        }

        // 令牌签发统一走编排器：static 协议下本地用户不在 plugin.oauth.temp.user 静态账号名单内，
        // 直连 AuthClientExecute 必然换取不到令牌，必须回落到与密码登录同源的本地签发链路
        LoginAuthResult accessToken = thirdPartyLoginOrchestrator
                .issueThirdPartyToken(userThirdParty.getSysUserId(), THIRD_PARTY_TYPE, request);
        if (!ThirdPartyCallbackSupport.hasLoginOutcome(accessToken)) {
            log.warn("微信三方登录换取令牌失败");
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, "微信登录失败，请重试");
            return errorView;
        }

        // 保存或更新微信用户信息
        saveOrUpdateWechatUser(userThirdParty.getSysUserId(), authUser);

        LoginResult loginResult = thirdPartyLoginOrchestrator.toLoginResult(accessToken, response);

        RedirectView view = new RedirectView();
        String nanoTime = StringUtils.padAfter(System.nanoTime() + "", 16, '0');
        // 加密登录结果 + 登记一次性 nonce（供 /v2/user/third/login/exchange 兑换）
        String encrypt = ThirdPartyCallbackSupport.encryptRedirectParam(loginResult, securityProperties);
        response.setHeader("x-response-time", nanoTime);
        view.addStaticAttribute("redirectParam", encrypt);
        view.setUrl(ThirdPartyCallbackSupport.safeSystemCallback(systemCallback, securityProperties));

        log.info("微信登录成功: userId={}", userThirdParty.getSysUserId());
        return view;
    }

    /**
     * 保存或更新微信用户信息
     *
     * @param sysUserId 系统用户标识
     * @param authUser  微信返回的用户信息
     */
    private void saveOrUpdateWechatUser(Integer sysUserId, AuthUser authUser) {
        if (sysUserId == null || authUser == null) {
            return;
        }

        String openId = authUser.getUuid();
        if (StringUtils.isEmpty(openId)) {
            return;
        }

        var wechatUser = sysWechatUserMapper.selectOne(
                Wrappers.<com.chua.starter.entity.system.SysWechatUser>lambdaQuery()
                        .eq(com.chua.starter.entity.system.SysWechatUser::getSysWechatOpenId, openId)
        );

        if (wechatUser == null) {
            var newWechatUser = new com.chua.starter.entity.system.SysWechatUser();
            newWechatUser.setSysUserId(sysUserId);
            newWechatUser.setSysWechatOpenId(openId);
            newWechatUser.setSysWechatNickname(authUser.getNickname());
            newWechatUser.setSysWechatAvatar(authUser.getAvatar());
            sysWechatUserMapper.insert(newWechatUser);
            return;
        }

        wechatUser.setSysUserId(sysUserId);
        wechatUser.setSysWechatNickname(authUser.getNickname());
        wechatUser.setSysWechatAvatar(authUser.getAvatar());
        sysWechatUserMapper.updateById(wechatUser);
    }

    /**
     * 检查第三方登录是否开启
     * <p>从sys_setting表中查询sso组下Wechat配置是否为true</p>
     *
     * @return true-开启，false-未开启
     */
    private boolean isThirdPartyEnabled() {
        return sysSettingService.isThirdPartyEnabled(SSO_NAME);
    }
}
