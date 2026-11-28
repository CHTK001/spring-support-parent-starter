// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.sso.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.spring.support.utils.RequestUtils;
import com.chua.starter.entity.system.SysUser;
import com.chua.starter.entity.system.SysUserThirdParty;
import com.chua.starter.entity.system.SysWechatUser;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.execute.AuthClientExecute;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.provider.ThirdLoginData;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.sso.service.SsoSettingService;
import com.chua.starter.sso.support.SystemCallbackPolicy;
import com.chua.starter.sso.mapper.SysUserThirdPartyMapper;
import com.chua.starter.sso.mapper.SysWechatUserMapper;
import com.chua.starter.sso.pojo.LoginResult;
import com.chua.starter.sso.pojo.ThirdBindCompleteRequest;
import com.chua.starter.sso.pojo.ThirdBindPendingVO;
import com.chua.starter.sso.pojo.ThirdBindingVO;
import com.chua.starter.sso.pojo.ThirdPartyUnbindRequest;
import com.chua.starter.sso.pojo.UserInfoVO;
import com.chua.starter.sso.properties.SsoProperties;
import com.chua.starter.sso.support.ThirdPartyCallbackSupport;
import com.chua.starter.sso.support.ThirdPartyPendingBindStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 第三方登录编排器
 *
 * <p>从 {@code spring-api-support-common-system-starter} 登录服务中拆出的三方登录编排：
 * 授权码、绑定 / 解绑、换票、待绑定查询、绑定完成与令牌签发。本模块自带
 * {@code sys_user_third_party} / {@code sys_wechat_user} 的 Mapper，可直接落绑定关系；
 * 本地账号校验、令牌签发与用户信息组装经 {@link SsoLoginBridge} 委托给宿主
 * （system-starter 提供默认实现），monitor / ai / collect / oauth 等宿主
 * 无需引入整套系统业务即可复用三方登录。</p>
 *
 * @since 2026/11/24
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyLoginOrchestrator {

    /**
     * 微信第三方类型
     */
    private static final String THIRD_PARTY_TYPE_WECHAT = "wechat";

    /**
     * 管理端批量查询绑定的用户数上限
     */
    private static final int MAX_BINDINGS_BATCH_SIZE = 500;

    /**
     * SSO 登录桥
     */
    private final SsoLoginBridge bridge;
    /**
     * SSO 安全配置
     */
    private final SsoProperties ssoProperties;
    /**
     * SYS用户thirdpartymapper
     */
    private final SysUserThirdPartyMapper sysUserThirdPartyMapper;
    /**
     * syswechat用户映射器
     */
    private final SysWechatUserMapper sysWechatUserMapper;
    /**
     * SYS设置服务
     */
    private final SsoSettingService sysSettingService;
    /**
     * 认证属性
     */
    private final AuthClientProperties authProperties;

    /**
     * thirdlogin编码
     */
    public ReturnResult<String> thirdLoginCode(ThirdLoginData loginData, HttpServletRequest request, HttpServletResponse response) {
        log.debug("第三方登录请求: loginType={}, thirdType={}", loginData.loginType(), loginData.thirdType());

        // 开放重定向入口防御：callback 为绝对地址时必须命中白名单，否则直接拒绝，
        // 不进入 state 缓存（绑定 thirdBindCode 委托本方法，一并受保护）
        String rejectReason = SystemCallbackPolicy.rejectReason(loginData.callback(),
                ssoProperties.getAllowedSystemCallbackHosts());
        if (rejectReason != null) {
            log.warn("第三方登录回调地址被拒绝: loginType={}, callback={}, reason={}",
                    loginData.loginType(), loginData.callback(), rejectReason);
            return ReturnResult.error("回调地址不合法：" + rejectReason);
        }

        // 获取第三方登录配置
        var authRequest = sysSettingService.getAuthRequest(loginData);
        if (authRequest == null) {
            log.warn("第三方登录配置未找到或未启用: loginType={}", loginData.loginType());
            return ReturnResult.error("第三方登录未配置或未启用，请联系管理员");
        }

        // 获取授权 URL
        var request1 = authRequest.request();
        var authorizeUrl = request1.authorize(authRequest.state());
        log.info("第三方登录授权URL生成成功: loginType={}", loginData.loginType());

        return ReturnResult.ok(authorizeUrl);
    }

    /**
     * Third绑定编码
     */
    public ReturnResult<String> thirdBindCode(ThirdLoginData loginData, HttpServletRequest request, HttpServletResponse response) {
        return thirdLoginCode(loginData, request, response);
    }

    /**
     * Third绑定信息
     */
    public ReturnResult<List<String>> thirdBindInfo(String userId) {
        List<SysUserThirdParty> sysUserThirdParties = sysUserThirdPartyMapper.selectList(
                Wrappers.<SysUserThirdParty>lambdaQuery()
                        .eq(SysUserThirdParty::getSysUserId, userId));

        Set<String> result = new LinkedHashSet<>();
        sysUserThirdParties.stream()
                .map(SysUserThirdParty::getSysUserThirdPartyType)
                .filter(StringUtils::isNotBlank)
                .map(it -> it.toLowerCase(Locale.ROOT))
                .forEach(result::add);

        Long wechatCount = sysWechatUserMapper.selectCount(
                Wrappers.<SysWechatUser>lambdaQuery()
                        .eq(SysWechatUser::getSysUserId, userId));
        if (wechatCount != null && wechatCount > 0) {
            result.add(THIRD_PARTY_TYPE_WECHAT);
        }

        return ReturnResult.ok(List.copyOf(result));
    }

    /**
     * Third解绑
     */
    public ReturnResult<Boolean> thirdUnbind(String userId, ThirdPartyUnbindRequest thirdPartyUnbindRequest) {
        if (StringUtils.isBlank(thirdPartyUnbindRequest.loginType())) {
            return ReturnResult.error("登录类型不能为空");
        }

        // 大小写不敏感匹配：绑定写入端历史遗留既有小写（gitee/wechat）也有大写（DINGTALK/QQ），
        // 入参统一按忽略大小写过滤，避免「查得到却解不掉」
        String loginType = thirdPartyUnbindRequest.loginType();
        List<SysUserThirdParty> bindings = sysUserThirdPartyMapper.selectList(
                Wrappers.<SysUserThirdParty>lambdaQuery()
                        .eq(SysUserThirdParty::getSysUserId, userId));
        List<Integer> matchedIds = bindings.stream()
                .filter(it -> StringUtils.isNotBlank(it.getSysUserThirdPartyType())
                        && it.getSysUserThirdPartyType().equalsIgnoreCase(loginType))
                .map(SysUserThirdParty::getSysUserThirdPartyId)
                .filter(Objects::nonNull)
                .toList();

        int deleteCount = 0;
        if (!matchedIds.isEmpty()) {
            deleteCount = matchedIds.size();
            for (Integer id : matchedIds) {
                sysUserThirdPartyMapper.deleteById(id);
            }
        }

        if (THIRD_PARTY_TYPE_WECHAT.equalsIgnoreCase(thirdPartyUnbindRequest.loginType())) {
            sysWechatUserMapper.delete(
                    Wrappers.<SysWechatUser>lambdaQuery()
                            .eq(SysWechatUser::getSysUserId, userId));
        }

        if (deleteCount > 0) {
            return ReturnResult.ok();
        }
        return ReturnResult.error("解绑失败");
    }

    /**
     * 批量查询多个本地用户的第三方绑定（管理端展示用）。
     *
     * @param userIds 本地用户标识列表
     * @return 绑定记录列表
     */
    public ReturnResult<List<ThirdBindingVO>> listBindingsByUserIds(List<Integer> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return ReturnResult.error("用户ID列表不能为空");
        }
        if (userIds.size() > MAX_BINDINGS_BATCH_SIZE) {
            return ReturnResult.error("单次查询用户数不能超过 " + MAX_BINDINGS_BATCH_SIZE);
        }

        List<SysUserThirdParty> bindings = sysUserThirdPartyMapper.selectList(
                Wrappers.<SysUserThirdParty>lambdaQuery()
                        .in(SysUserThirdParty::getSysUserId, userIds));
        List<ThirdBindingVO> result = bindings.stream()
                .map(it -> new ThirdBindingVO(it.getSysUserId(), it.getSysUserThirdPartyType(),
                        it.getSysUserThirdPartyUid(), it.getSysUserThirdPartyName()))
                .toList();
        return ReturnResult.ok(result);
    }

    /**
     * 管理员解绑指定用户的第三方绑定。
     *
     * <p>与 {@link #thirdUnbind(String, ThirdPartyUnbindRequest)} 的差异：目标用户由请求参数指定，
     * 而非当前登录用户，供用户管理页代用户解绑。</p>
     *
     * @param userId    目标本地用户标识
     * @param loginType 第三方平台类型
     * @return 是否解绑成功
     */
    public ReturnResult<Boolean> adminUnbind(Integer userId, String loginType) {
        if (userId == null) {
            return ReturnResult.error("用户ID不能为空");
        }
        return thirdUnbind(String.valueOf(userId), new ThirdPartyUnbindRequest(loginType));
    }

    /**
     * 三方登录换票：用回调带回的 redirectParam（AES 密文 + 一次性 nonce）换取 LoginResult
     *
     * <p>密钥仅存在服务端，前端无法解密，只能原样回传兑换；nonce 在兑换时先置失效
     * 再解密（fail-closed），同一凭证不可重复使用、伪造密文无法通过 nonce 校验。</p>
     */
    public ReturnResult<LoginResult> thirdLoginExchange(String redirectParam) {
        if (StringUtils.isBlank(redirectParam)) {
            return ReturnResult.error("登录凭证不能为空");
        }
        LoginResult loginResult = ThirdPartyCallbackSupport.redeemRedirectParam(redirectParam, ssoProperties);
        if (null == loginResult || StringUtils.isBlank(loginResult.getAccessToken())) {
            log.warn("三方登录换票失败: 凭证缺失、已使用或已过期");
            return ReturnResult.error("登录凭证已失效，请重新发起第三方登录");
        }
        return ReturnResult.ok(loginResult);
    }

    /**
     * 查询三方待绑定身份（不核销凭证，绑定完成时才一次性消费）
     */
    public ReturnResult<ThirdBindPendingVO> thirdBindPending(String bindToken) {
        ThirdPartyPendingBindStore.PendingBind pending = ThirdPartyPendingBindStore.peek(bindToken);
        if (null == pending) {
            return ReturnResult.error("绑定凭证已失效，请重新发起第三方登录");
        }
        return ReturnResult.ok(new ThirdBindPendingVO(pending.extLoginType(), pending.username(),
                pending.nickname(), pending.avatar(), pending.email()));
    }

    /**
     * 三方账号绑定完成：核销 bindToken → 校验本地账号密码 → 落绑定关系 → 签发登录结果
     *
     * <p>绑定与签发在同一请求内完成：三方账号先绑定到本地账号，绑定成功即登录成功，
     * 不存在「先发 token 再补绑」的窗口。</p>
     */
    public ReturnResult<LoginResult> thirdBindComplete(ThirdBindCompleteRequest bindRequest,
                                                       HttpServletRequest request, HttpServletResponse response) {
        // 凭证一次性核销：缺失/已用/过期均失败
        ThirdPartyPendingBindStore.PendingBind pending = ThirdPartyPendingBindStore.consume(bindRequest.bindToken());
        if (null == pending) {
            log.warn("三方绑定完成失败: 绑定凭证缺失、已使用或已过期");
            return ReturnResult.error("绑定凭证已失效，请重新发起第三方登录");
        }
        // 本地账号校验（与密码登录同口径：账号状态检查 + 口令校验统一由宿主桥实现）
        SysUser user = bridge.findByUsername(bindRequest.username());
        if (null == user) {
            return ReturnResult.error("账号或密码错误");
        }
        if (!bridge.verifyPassword(user, bindRequest.password())) {
            return ReturnResult.error("账号或密码错误");
        }
        if ("phone".equals(pending.extLoginType())) {
            if (!bridge.bindVerifiedPhone(user, pending.uid())) {
                return ReturnResult.error("手机号已被使用或当前账号已绑定其他手机号");
            }
            return issueThirdBindToken(user, pending, request, response);
        }
        // 防重：三方身份已被绑定（绑到当前用户则直接视为登录成功；绑到他人则拒绝）
        SysUserThirdParty existed = sysUserThirdPartyMapper.selectOne(
                Wrappers.<SysUserThirdParty>lambdaQuery()
                        .eq(SysUserThirdParty::getSysUserThirdPartyUid, pending.uid())
                        .eq(SysUserThirdParty::getSysUserThirdPartyType, pending.bindType()));
        if (null != existed) {
            if (ObjectUtils.nullSafeEquals(existed.getSysUserId(), user.getSysUserId())) {
                return issueThirdBindToken(user, pending, request, response);
            }
            return ReturnResult.error("该第三方账号已绑定其他用户");
        }
        // 防重：当前用户同平台已有绑定（对齐 binder 口径：同用户同平台不可重复）
        long count = sysUserThirdPartyMapper.selectCount(
                Wrappers.<SysUserThirdParty>lambdaQuery()
                        .eq(SysUserThirdParty::getSysUserId, user.getSysUserId())
                        .eq(SysUserThirdParty::getSysUserThirdPartyType, pending.bindType()));
        if (count > 0) {
            return ReturnResult.error("当前账号已绑定该平台，请直接使用第三方登录");
        }
        // 落绑定关系（type 按各平台存量口径：gitee/wechat 小写，其余大写枚举名）+ 头像/邮箱空位回填
        SysUserThirdParty thirdParty = new SysUserThirdParty();
        thirdParty.setSysUserId(user.getSysUserId());
        thirdParty.setSysUserThirdPartyType(pending.bindType());
        thirdParty.setSysUserThirdPartyName(StringUtils.defaultString(
                StringUtils.isNotBlank(pending.username()) ? pending.username() : pending.nickname(),
                pending.bindType()));
        thirdParty.setSysUserThirdPartyUid(pending.uid());
        thirdParty.setSysUserThirdPartyValue(pending.email());
        sysUserThirdPartyMapper.insert(thirdParty);
        boolean updateUserInfo = false;
        if (StringUtils.isEmpty(user.getSysUserAvatar()) && StringUtils.isNotEmpty(pending.avatar())) {
            user.setSysUserAvatar(pending.avatar());
            updateUserInfo = true;
        }
        if (StringUtils.isEmpty(user.getSysUserEmail()) && StringUtils.isNotEmpty(pending.email())) {
            user.setSysUserEmail(pending.email());
            updateUserInfo = true;
        }
        if (updateUserInfo) {
            bridge.updateUser(user);
        }
        log.info("三方账号绑定完成: platform={}, uid={}, userId={}", pending.extLoginType(), pending.uid(), user.getSysUserId());
        return issueThirdBindToken(user, pending, request, response);
    }

    /**
     * 为已绑定三方身份的用户签发登录结果
     *
     * <p>与回调「已绑定」分支同构：{@code AuthType.THIRD_PARTY} 令牌 + Bearer 头 +
     * {@code x-oauth-cookie}，保证绑定登录与后续三方登录得到的令牌结构一致。</p>
     *
     * @param user     本地用户
     * @param pending  待绑定身份快照
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @return 登录结果
     */
    private ReturnResult<LoginResult> issueThirdBindToken(SysUser user, ThirdPartyPendingBindStore.PendingBind pending,
                                                          HttpServletRequest request, HttpServletResponse response) {
        LoginAuthResult accessToken = issueThirdPartyToken(user.getSysUserId(), pending.extLoginType(), request);
        if (null == accessToken || StringUtils.isBlank(accessToken.getToken())) {
            log.warn("三方绑定后签发令牌失败: userId={}", user.getSysUserId());
            return ReturnResult.error("绑定已完成，但登录令牌签发失败，请返回登录页重试");
        }
        LoginResult loginResult = LoginResult.builder()
                .userInfo(accessToken.getUserResume() != null
                        ? bridge.buildUserInfo(accessToken.getUserResume())
                        : bridge.getUserLoginInfo(accessToken.getToken()))
                .tokenType("Bearer")
                .accessToken(accessToken.getToken())
                .build();
        CookieUtil.set(response, "x-oauth-cookie", accessToken.getToken(), true);
        return ReturnResult.ok(loginResult);
    }

    /**
     * 为已绑定三方身份的本地用户签发访问令牌。
     *
     * <p>static 协议下 AuthClientExecute 的静态账号链路只认 {@code plugin.oauth.temp.user}
     * 配置的账号，三方登录对应的本地用户不在该名单里，必须复用与密码登录同源的
     * {@link SsoLoginBridge#issueLocalToken(SysUser)}；远程协议（http 等）仍经 OAuth 服务器按用户标识签发。</p>
     *
     * @param userId    本地用户 ID
     * @param thirdType 三方平台类型（gitee / wechat / ...），写入令牌便于用量归因
     * @param request   HTTP 请求
     * @return 登录认证结果，用户不存在或签发失败返回 {@code null}
     */
    public LoginAuthResult issueThirdPartyToken(Integer userId, String thirdType, HttpServletRequest request) {
        SysUser user = bridge.getById(userId);
        if (null == user) {
            log.warn("三方登录签发令牌失败: 本地用户不存在 - userId={}", userId);
            return null;
        }
        if (authProperties != null && "static".equalsIgnoreCase(authProperties.getProtocol())) {
            return bridge.issueLocalToken(user);
        }
        Map<String, Object> ext = new HashMap<>();
        ext.put("loginType", thirdType);
        ext.put("thirdPartyType", thirdType);
        ext.put("address", RequestUtils.getIpAddress(request));
        if ("phone".equals(thirdType)) {
            ext.put("loginType", "sms");
            ext.put("phone", user.getSysUserPhone());
        }
        return AuthClientExecute.getInstance()
                .getAccessToken(String.valueOf(user.getSysUserId()), null,
                        "phone".equals(thirdType) ? AuthType.SMS : AuthType.THIRD_PARTY, ext);
    }

    /**
     * 组装登录用户信息（委托宿主桥，保持原 SysLoginService#buildUserInfo 调用形态）
     *
     * @param userResume 认证用户快照
     * @return 用户信息
     */
    public UserInfoVO buildUserInfo(UserResume userResume) {
        return bridge.buildUserInfo(userResume);
    }

    /**
     * 按令牌读取登录用户信息（委托宿主桥，保持原 SysLoginService#getUserLoginInfo 调用形态）
     *
     * @param token 访问令牌
     * @return 用户信息
     */
    public UserInfoVO getUserLoginInfo(String token) {
        return bridge.getUserLoginInfo(token);
    }
}
