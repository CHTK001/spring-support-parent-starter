// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.sso.controller.callback;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.annotation.Ignore;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.entity.system.SysUserThirdParty;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.sso.service.SsoSettingService;
import com.chua.starter.sso.support.AuthRequestFactory;
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
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 第三方登录回调（通用路径变量控制器，覆盖 11 个无专用控制器的平台）
 *
 * <p>路径 {@code /v2/third/login/{platform}/callback|binder}。Spring 精确路径优先，
 * gitee/github/google/wechat/oidc 五个专用控制器不受影响，本控制器只服务白名单内平台：</p>
 *
 * <ul>
 *   <li>wechat_mp / wechat_enterprise / qq / alipay / dingtalk / weibo / baidu /
 *       microsoft / taobao / douyin / feishu</li>
 * </ul>
 *
 * <p>白名单之外的平台名直接拒绝（防止任意路径变量流入 AuthRequestFactory）。
 * 绑定与登录的差别由 state 缓存中的 {@code _thirdType}（"0"=绑定）区分，
 * 与 5 个专用控制器一致：写 {@code redirectParam} 一次性凭证、回调地址过白名单、
 * 错误提示双写（flash {@code msg} + 静态 {@code thirdMsg}）。</p>
 *
 * @since 2026/10/07
 * @author CH
 */
@Controller
@Slf4j
@Api(tags = "第三方登录回调(通用)")
@Tag(name = "第三方登录回调(通用)")
@RequestMapping("/v2/third/login/{platform}")
@RequiredArgsConstructor
public class ThirdPartyPlatformCallbackController {

    /**
     * 本控制器覆盖的 11 个平台（其余平台由专用控制器承接）
     */
    private static final Set<String> SUPPORTED_PLATFORMS = Set.of(
            "wechat_mp", "wechat_enterprise", "qq", "alipay", "dingtalk", "weibo",
            "baidu", "microsoft", "taobao", "douyin", "feishu");

    /**
     * 登录开关配置组编码
     */
    private static final String SSO_GROUP = "sso";

    /**
     * 平台展示名（错误提示用）
     */
    private static final Map<String, String> PLATFORM_LABELS = Map.ofEntries(
            Map.entry("wechat_mp", "微信公众号"),
            Map.entry("wechat_enterprise", "企业微信"),
            Map.entry("qq", "QQ"),
            Map.entry("alipay", "支付宝"),
            Map.entry("dingtalk", "钉钉"),
            Map.entry("weibo", "微博"),
            Map.entry("baidu", "百度"),
            Map.entry("microsoft", "微软"),
            Map.entry("taobao", "淘宝"),
            Map.entry("douyin", "抖音"),
            Map.entry("feishu", "飞书"));

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
     * 系统设置服务（sso 开关）
     */
    final SsoSettingService sysSettingService;

    /**
     * 认证客户端配置
     */
    final AuthClientProperties authProperties;

    /**
     * SSO 安全配置（回调地址白名单、换票密钥）
     */
    final SsoProperties securityProperties;

    /**
     * 三方登录回调（登录态）
     *
     * @param platform   平台标识（路径变量，小写白名单）
     * @param authCallback 第三方回调参数
     * @param attributes 重定向属性
     * @param request    请求
     * @param response   响应
     * @return 重定向视图
     */
    @Ignore
    @GetMapping("callback")
    public RedirectView callback(@PathVariable("platform") String platform,
                                 AuthCallback authCallback,
                                 RedirectAttributes attributes,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        String normalized = normalize(platform);
        String systemCallback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_system_callback");
        RedirectView errorView = new RedirectView();
        // 读取点兜底：缓存里的回调地址过白名单（开放重定向读取点防线）
        errorView.setUrl(ThirdPartyCallbackSupport.safeSystemCallback(systemCallback, securityProperties));

        if (normalized == null) {
            log.warn("不支持的三方平台回调: platform={}", platform);
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, "不支持的第三方登录平台");
            return errorView;
        }
        String label = PLATFORM_LABELS.get(normalized);

        if (!isThirdPartyEnabled(normalized)) {
            log.warn("{}登录未开启，拒绝回调", label);
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, label + "登录未开启");
            return errorView;
        }

        String thirdType = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_thirdType");
        if ("0".equals(thirdType)) {
            return doBinder(normalized, label, authCallback, attributes, systemCallback);
        }

        AuthUser authUser = doAuthLogin(normalized, label, authCallback);
        if (authUser == null) {
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, label + "授权失败，请重试");
            return errorView;
        }

        String type = typeOf(normalized);
        SysUserThirdParty userThirdParty = sysUserThirdPartyService.getOne(
                Wrappers.<SysUserThirdParty>lambdaQuery()
                        .eq(SysUserThirdParty::getSysUserThirdPartyUid, authUser.getUuid())
                        .eq(SysUserThirdParty::getSysUserThirdPartyType, type));
        if (userThirdParty == null) {
            log.warn("{}登录失败: 未绑定系统用户 uuid={}", label, authUser.getUuid());
            // 未绑定：暂存三方身份为一次性待绑定凭证，引导前端绑定页完成账密核验后再签发令牌
            String bindToken = ThirdPartyPendingBindStore.create(type, normalized, authUser);
            errorView.addStaticAttribute("bindToken", bindToken);
            return errorView;
        }

        // 令牌签发统一走编排器：static 协议下本地用户不在 plugin.oauth.temp.user 静态账号名单内，
        // 直连 AuthClientExecute 必然换取不到令牌，必须回落到与密码登录同源的本地签发链路
        LoginAuthResult accessToken = thirdPartyLoginOrchestrator
                .issueThirdPartyToken(userThirdParty.getSysUserId(), normalized, request);
        if (!ThirdPartyCallbackSupport.hasLoginOutcome(accessToken)) {
            log.warn("{}三方登录换取令牌失败: userId={}", label, userThirdParty.getSysUserId());
            ThirdPartyCallbackSupport.markFailure(attributes, errorView, label + "登录失败，请重试");
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
        log.info("{}登录成功: userId={}", label, userThirdParty.getSysUserId());
        return view;
    }

    /**
     * 三方登录绑定回调（绑定态）
     *
     * @param platform     平台标识（路径变量）
     * @param authCallback 第三方回调参数
     * @param attributes   重定向属性
     * @param request      请求
     * @param response     响应
     * @return 重定向视图
     */
    @Ignore
    @GetMapping("binder")
    public RedirectView binder(@PathVariable("platform") String platform,
                               AuthCallback authCallback,
                               RedirectAttributes attributes,
                               HttpServletRequest request,
                               HttpServletResponse response) {
        String normalized = normalize(platform);
        String systemCallback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_system_callback");
        RedirectView view = new RedirectView();
        // 读取点兜底：缓存里的回调地址过白名单（开放重定向读取点防线）
        view.setUrl(ThirdPartyCallbackSupport.safeSystemCallback(systemCallback, securityProperties));

        if (normalized == null) {
            log.warn("不支持的三方平台绑定回调: platform={}", platform);
            ThirdPartyCallbackSupport.markFailure(attributes, view, "不支持的第三方登录平台");
            return view;
        }
        String label = PLATFORM_LABELS.get(normalized);

        if (!isThirdPartyEnabled(normalized)) {
            log.warn("{}登录未开启，拒绝绑定请求", label);
            ThirdPartyCallbackSupport.markFailure(attributes, view, label + "登录未开启，无法绑定");
            return view;
        }

        return doBinder(normalized, label, authCallback, attributes, systemCallback);
    }

    /**
     * 绑定核心逻辑：校验登录态与重复绑定后换票落绑定关系
     *
     * @param normalized     归一化平台标识
     * @param label          展示名
     * @param authCallback   第三方回调参数
     * @param attributes     重定向属性
     * @param systemCallback 缓存中的系统回调地址
     * @return 重定向视图
     */
    private RedirectView doBinder(String normalized, String label, AuthCallback authCallback,
                                  RedirectAttributes attributes, String systemCallback) {
        RedirectView view = new RedirectView();
        view.setUrl(ThirdPartyCallbackSupport.safeSystemCallback(systemCallback, securityProperties));

        String userId = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_userId");
        if (userId == null) {
            ThirdPartyCallbackSupport.markFailure(attributes, view, "请重新登录重试");
            return view;
        }

        String type = typeOf(normalized);
        // 重复绑定检查按 userId + type（同一用户可绑多个平台，同平台不可重复）
        long count = sysUserThirdPartyService.count(Wrappers.<SysUserThirdParty>lambdaQuery()
                .eq(SysUserThirdParty::getSysUserId, userId)
                .eq(SysUserThirdParty::getSysUserThirdPartyType, type));
        if (count > 0) {
            ThirdPartyCallbackSupport.markFailure(attributes, view, "已绑定" + label + "，无需重复绑定");
            return view;
        }

        AuthUser authUser = doAuthLogin(normalized, label, authCallback);
        if (authUser == null) {
            ThirdPartyCallbackSupport.markFailure(attributes, view, label + "授权失败，请重试");
            return view;
        }

        SysUserThirdParty thirdParty = new SysUserThirdParty();
        thirdParty.setSysUserId(Integer.valueOf(userId));
        // 统一存大写枚举名（存量 gitee/wechat 小写不改，解绑侧已做大小写不敏感）
        thirdParty.setSysUserThirdPartyType(type);
        thirdParty.setSysUserThirdPartyName(StringUtils.defaultString(
                StringUtils.isNotBlank(authUser.getUsername()) ? authUser.getUsername() : authUser.getNickname(),
                label));
        thirdParty.setSysUserThirdPartyUid(authUser.getUuid());
        thirdParty.setSysUserThirdPartyValue(authUser.getEmail());
        sysUserThirdPartyService.save(thirdParty);
        log.info("{}绑定成功: userId={}, uuid={}", label, userId, authUser.getUuid());
        return view;
    }

    /**
     * 重建 AuthRequest 并执行三方换票，失败返回 {@code null}（不抛出）
     *
     * @param normalized   归一化平台标识
     * @param label        展示名
     * @param authCallback 第三方回调参数
     * @return 授权用户，失败为 {@code null}
     */
    private AuthUser doAuthLogin(String normalized, String label, AuthCallback authCallback) {
        String clientSecret = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientSecret");
        String clientId = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_clientId");
        String callback = AuthDefaultStateCache.INSTANCE.get(authCallback.getState() + "_callback");
        AuthConfig authConfig = AuthConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(callback)
                .httpConfig(ThirdPartyHttpSupport.httpConfig())
                .build();
        AuthRequest authRequest = AuthRequestFactory.createAuthRequest(normalized, authConfig, AuthDefaultStateCache.INSTANCE);
        if (authRequest == null) {
            log.warn("{}未接入 JustAuth 平台实现", label);
            return null;
        }
        try {
            AuthResponse login = authRequest.login(authCallback);
            if (login == null || !login.ok()) {
                log.warn("{}授权失败: {}", label, login == null ? "无响应" : login.getMsg());
                return null;
            }
            return (AuthUser) login.getData();
        } catch (Exception e) {
            log.warn("{}授权换票异常: {}", label, e.getMessage());
            return null;
        }
    }

    /**
     * sso 开关校验：group=sso + status=1 + name 忽略大小写匹配平台 + value=true
     *
     * @param normalized 归一化平台标识
     * @return true 表示已开启
     */
    private boolean isThirdPartyEnabled(String normalized) {
        return sysSettingService.isThirdPartyEnabled(normalized);
    }

    /**
     * 归一化平台标识：小写且在 11 平台白名单内，否则返回 {@code null}
     *
     * @param platform 路径变量
     * @return 归一化平台标识或 {@code null}
     */
    private static String normalize(String platform) {
        if (StringUtils.isBlank(platform)) {
            return null;
        }
        String lower = platform.toLowerCase(Locale.ROOT);
        return SUPPORTED_PLATFORMS.contains(lower) ? lower : null;
    }

    /**
     * 存储用第三方类型：{@code SysUserThirdParty.Type} 大写枚举名
     *
     * @param normalized 归一化平台标识
     * @return 大写枚举名
     */
    private static String typeOf(String normalized) {
        return SysUserThirdParty.Type.valueOf(normalized.toUpperCase(Locale.ROOT)).name();
    }
}
