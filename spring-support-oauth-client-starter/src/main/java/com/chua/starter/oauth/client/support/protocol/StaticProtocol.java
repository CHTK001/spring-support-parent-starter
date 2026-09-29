package com.chua.starter.oauth.client.support.protocol;

import com.chua.common.support.lang.json.Json;
import com.chua.common.support.spi.annotations.Spi;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.oauth.client.support.algorithm.TokenCipher;
import com.chua.starter.oauth.client.support.entity.AppKeySecret;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.enums.LogoutType;
import com.chua.starter.oauth.client.support.enums.UpgradeType;
import com.chua.starter.oauth.client.support.infomation.AuthenticationInformation;
import com.chua.starter.oauth.client.support.impersonation.TemporaryToken;
import com.chua.starter.oauth.client.support.infomation.Information;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.oauth.client.support.user.LoginResult;
import com.chua.starter.oauth.client.support.user.StaticTempLogin;
import com.chua.starter.oauth.client.support.user.UserResult;
import com.chua.starter.oauth.client.support.user.UserResume;
import com.chua.starter.oauth.client.support.session.StaticSessionRegistry;
import com.google.common.collect.Sets;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.chua.common.support.constant.NameConstant.NULL;
import static com.chua.starter.common.support.constant.Constant.ADMIN;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.SUPER_ADMIN;

/**
 * 静态协议实现类，用于处理静态认证逻辑。
 * 该类通过 令牌cipher（SM4加密）解析令牌或Cookie中的用户信息，并构建认证信息。
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Spi({"Static"})
public class StaticProtocol extends AbstractProtocol {

    /**
     * 在线会话登记表
    */
    private static final StaticSessionRegistry SESSIONS = StaticSessionRegistry.getInstance();

    /**
     * 构造函数
     *
     * @param authClientProperties 认证客户端配置属性
     */
    public StaticProtocol(AuthClientProperties authClientProperties) {
        /**
         * 父。
         * @param authClientProperties 认证客户端属性
         */
        super(authClientProperties);
    }

    /**
     * 鉴权入口，先拦截已吊销令牌
     * <p>父类实现优先读取认证缓存，吊销后必须在此处拦断，否则缓存命中仍会放行已登出的令牌</p>
     *
     * @param cookies     Cookie数组
     * @param token       认证令牌
     * @param subProtocol 子协议
     * @return 认证信息对象
     */
    @Override
    public AuthenticationInformation approve(Cookie[] cookies, String token, String subProtocol) {
        if (isTokenRevoked(token)) {
            clearAuthenticationInformation(getCacheKey(cookies, token));
            return AuthenticationInformation.noAuth();
        }
        // 临时令牌必须在读缓存之前先判有效期与路径：
        // 父类的 approve 会先命中认证缓存直接返回，缓存里存的是签发那一刻的放行结果，
        // 若校验放在其后，管理员关掉页面后旧令牌仍会被缓存兜住而继续放行。
        if (!rejectTemporaryToken(token, cookies)) {
            return AuthenticationInformation.noAuth();
        }
        AuthenticationInformation information = super.approve(cookies, token, subProtocol);
        recordSession(token, cookies, information);
        return information;
    }

    /**
     * 临时令牌的额外准入校验：有效期与可访问路径。
     *
     * <p>之所以要独立于认证缓存再判一次：静态协议的鉴权链路只判断「有没有携带令牌」，
     * 并不比对 {@code expireTime}，认证缓存又会缓存放行结果。
     * 两层叠加意味着管理员关掉页面后，临时令牌仍能继续用，
     * 「5 分钟临时令牌」就成了永久有效的学生账号。两处都拦不住才算真正拦下。</p>
     *
     * @param token   请求携带的令牌，允许为 {@code null}（此时回落到 Cookie 里的令牌）
     * @param cookies 请求 Cookie，允许为 {@code null}
     * @return 允许通过返回 {@code true}；不通过返回 {@code false}
     */
    private boolean rejectTemporaryToken(String token, Cookie[] cookies) {
        String credential = resolveCredential(token, cookies);
        if (StringUtils.isBlank(credential)) {
            return true;
        }
        UserResult userResult = TokenCipher.decryptToUser(credential);
        if (!TemporaryToken.isTemporary(userResult)) {
            return true;
        }
        if (!TemporaryToken.isUsable(userResult)) {
            log.warn("[StaticProtocol]临时令牌已过期，拒绝认证");
            return false;
        }
        String path = currentRequestPath();
        if (!TemporaryToken.isPathAllowed(userResult, path)) {
            log.warn("[StaticProtocol]临时令牌无权访问该路径 - path: {}", path);
            return false;
        }
        return true;
    }

    /**
     * 取本次请求实际使用的令牌：优先 Authorization 头，其次 {@code x-oauth-cookie} Cookie。
     *
     * @param token   请求携带的令牌
     * @param cookies 请求 Cookie
     * @return 令牌字符串；两者都取不到时返回 {@code null}
     */
    private String resolveCredential(String token, Cookie[] cookies) {
        if (StringUtils.isNotBlank(token) && !NULL.equals(token)) {
            return token;
        }
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie != null && "x-oauth-cookie".equals(cookie.getName())
                    && StringUtils.isNotBlank(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 登记在线会话
     * <p>鉴权命中缓存时不会进入 newAuthenticationInformation，因此统一在鉴权入口登记，
     * 保证活跃用户的最后访问时间持续刷新</p>
     *
     * @param token       请求携带的令牌
     * @param cookies     请求 Cookie
     * @param information 鉴权结果
     */
    private void recordSession(String token, Cookie[] cookies, AuthenticationInformation information) {
        if (null == information || null == information.getReturnResult()) {
            return;
        }
        String credential = token;
        if (StringUtils.isBlank(credential) || NULL.equals(credential)) {
            Cookie cookie = CookieUtil.get(cookies, "x-oauth-cookie");
            credential = null != cookie ? cookie.getValue() : null;
        }
        if (StringUtils.isBlank(credential) || NULL.equals(credential)) {
            return;
        }
        SESSIONS.record(DigestUtils.sha256(credential), information.getReturnResult(), RequestUtils.getRequest());
    }

    /**
     * 认证方法，根据Cookie和令牌进行用户认证
     *
     * @param cookie      Cookie对象
     * @param token       认证令牌
     * @param subProtocol 子协议
     * @return 认证信息对象
     */
    @Override
    protected AuthenticationInformation approve(Cookie cookie, String token, String subProtocol) {
        try {
            return newAuthenticationInformation(token, new Cookie[]{cookie});
        } catch (Exception e) {
            log.error("[StaticProtocol]approve异常 - 异常: {}", e.toString(), e);
            return AuthenticationInformation.noAuth();
        }
    }

    @Override
    /**
     * 认证用户编码
    */
    protected AuthenticationInformation authenticationUserCode(AppKeySecret appKeySecret) {
        log.warn("[StaticProtocol]静态协议不支持AppKey认证 - appId: {}",
                appKeySecret != null ? appKeySecret.getAppId() : null);
        return AuthenticationInformation.noAuth();
    }

    /**
     * 升级认证信息方法，根据Cookie、令牌和升级类型进行认证信息升级
     *
     * @param cookie       Cookie对象
     * @param token        认证令牌
     * @param upgradeType  升级类型
     * @param refreshToken 刷新令牌
     * @return 认证信息对象
     */
    @Override
    protected AuthenticationInformation upgradeInformation(Cookie cookie, String token, UpgradeType upgradeType, String refreshToken) {
        return newAuthenticationInformation(token, new Cookie[]{cookie});
    }

    /**
     * 创建新的认证信息对象
     *
     * @param token  认证令牌
     * @param cookie Cookie数组
     * @return 认证信息对象
     */
    private AuthenticationInformation newAuthenticationInformation(String token, Cookie[] cookie) {
        UserResume userResume = new UserResume();
        UserResult userResult = null;
 // 如果令牌不为空且不为"空"，则尝试解析令牌
        if (StringUtils.isNotBlank(token) && !NULL.equals(token)) {
            if (isTokenRevoked(token)) {
                log.warn("[StaticProtocol]令牌已吊销，拒绝认证 - token长度: {}", token.length());
                return AuthenticationInformation.noAuth();
            }
            userResult = TokenCipher.decryptToUser(token);
            if (null == userResult) {
                // 显式携带的令牌解析失败即拒绝，不得回退 Cookie：否则该令牌的任意变体（如加引号）
                // 都会以变体字符串为缓存键写入鉴权结果，形成吊销名单覆盖不到的别名，登出后仍能放行
                log.warn("[StaticProtocol]token解密或解析失败，拒绝认证 - token长度: {}", token.length());
                return AuthenticationInformation.noAuth();
            }
            // 临时令牌额外校验。静态协议的 hasValid 只判断「有没有携带令牌」，
            // 并不比对 expireTime，因此管理员关掉页面后临时令牌不会自动作废；
            // 不在这里补校验，「5 分钟临时令牌」就等于一个永久有效的学生账号。
            if (!TemporaryToken.isUsable(userResult)) {
                log.warn("[StaticProtocol]临时令牌已过期，拒绝认证 - token长度: {}", token.length());
                return AuthenticationInformation.noAuth();
            }
            if (!TemporaryToken.isPathAllowed(userResult, currentRequestPath())) {
                log.warn("[StaticProtocol]临时令牌无权访问该路径 - path: {}", currentRequestPath());
                return AuthenticationInformation.noAuth();
            }
        }

 // 如果通过令牌未能获取到用户信息，则尝试从Cookie中解析
        if (null == userResult && null != cookie) {
            for (Cookie cookie1 : cookie) {
                if (null == cookie1) {
                    continue;
                }
                if (!"JSESSIONID".equals(cookie1.getName())) {
                    if (isTokenRevoked(cookie1.getValue())) {
                        continue;
                    }
                    userResult = TokenCipher.decryptToUser(cookie1.getValue());
                    if (null != userResult) {
                        break;
                    }
                }
            }
        }

        if (null == userResult || StringUtils.isBlank(userResult.getUsername())) {
            return AuthenticationInformation.noAuth();
        }

        // 如果成功解析到用户信息，则设置用户名并保存到请求上下文
        userResume.setUsername(userResult.getUsername());
        userResume.setLoginType(userResult.getLoginType());
        userResume.setNickName(userResult.getNickName());
        userResume.setPhone(userResult.getPhone());
        userResume.setTenantId(userResult.getTenantId());
        userResume.setDeptId(userResult.getDeptId());
        userResume.setAddress(userResult.getAddress());
        userResume.setExt(userResult.getExt());
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.setUsername(userResume.getUsername());

        // 根据用户名设置角色信息
        if (userResult.getRoles() != null && !userResult.getRoles().isEmpty()) {
            userResume.setRoles(userResult.getRoles());
        } else if (ADMIN.equalsIgnoreCase(userResume.getUsername())) {
            userResume.setRoles(Sets.newHashSet(SUPER_ADMIN, ADMIN));
        } else {
            // 角色为空必须保持为空：兜底给 OPS 会让"未绑定任何角色"变成升权路径
            userResume.setRoles(Collections.emptySet());
        }
        userResume.setUserId(StringUtils.defaultString(userResult.getUserId(), "1"));
        userResume.setUid(StringUtils.defaultString(userResult.getUid(), DigestUtils.sha1(userResume.getUserId())));
        userResume.setOpenId(StringUtils.defaultString(userResult.getOpenId(), userResume.getUid()));
        userResume.setUnionId(StringUtils.defaultString(userResult.getUnionId(), userResume.getUid()));
        userResume.setPermission(userResult.getPermission() == null ? Collections.emptySet() : userResult.getPermission());
        userResume.setFingerprint(userResult.getFingerprint());
        userResume.setExpireTime(userResult.getExpireTime());
        userResume.setDataPermission(userResult.getDataPermission());
        userResume.setDataPermissionRule(userResult.getDataPermissionRule());
        // 只读标记随令牌携带，必须拷贝，否则权限拦截器读到的永远是默认 false
        userResume.setReadonly(userResult.isReadonly());
        // 将用户信息保存到请求上下文
        com.chua.starter.oauth.client.support.execute.AuthSessionUtils.setUserInfo(userResume);
        // 返回认证成功的信息
        /**
         * 认证信息。
         * @param Information.OK 信息.OK
         * @param userResume 用户resume
         * @return 认证信息的结果
         */
        return new AuthenticationInformation(Information.OK, userResume);
    }

    /**
     * 取当前请求路径，供临时令牌做可访问范围校验。
     *
     * <p>调度线程等无请求上下文的场景取不到路径，返回 {@code null}，
     * 由 {@link TemporaryToken#isPathAllowed} 按「路径未知即拒绝受限令牌」处理。</p>
     *
     * @return 请求路径；无请求上下文时返回 {@code null}
     */
    private String currentRequestPath() {
        var request = RequestUtils.getRequest();
        return request == null ? null : request.getRequestURI();
    }

    /**
     * 升级认证信息
     *
     * @param cookie        Cookie数组
     * @param token         认证令牌
     * @param upgradeType   升级类型
     * @param refreshToken  刷新令牌
     * @return 登录结果对象
     */
    @Override
    public LoginResult upgrade(Cookie[] cookie, String token, UpgradeType upgradeType, String refreshToken) {
        String sourceToken = StringUtils.isNotBlank(refreshToken) ? refreshToken : token;
        if (StringUtils.isBlank(sourceToken)) {
            return null;
        }

        // 临时令牌不得续期。本方法会把 expireTime 重置成完整有效期，
        // 因此一次续期就能把「5 分钟的代入令牌」变成长期令牌，
        // 「临时」二字当场失效。令牌上的临时标记随 ext 保留（路径限制不会失效），
        // 所以这里必须按标记主动拒绝，而不是指望前端不去续期。
        UserResult sourceUser = TokenCipher.decryptToUser(sourceToken);
        if (TemporaryToken.isTemporary(sourceUser)) {
            log.warn("[StaticProtocol]临时令牌不允许续期，拒绝 - token长度: {}", sourceToken.length());
            return null;
        }

        AuthenticationInformation authenticationInformation = newAuthenticationInformation(sourceToken, cookie);
        if (authenticationInformation == null
                || authenticationInformation.getInformation() == null
                || authenticationInformation.getInformation().getCode() != Information.OK.getCode()
                || authenticationInformation.getReturnResult() == null) {
            return null;
        }

        UserResume userResume = authenticationInformation.getReturnResult();
        UserResult userResult = new UserResult();
        userResult.setUid(userResume.getUid());
        userResult.setUserId(userResume.getUserId());
        userResult.setUsername(userResume.getUsername());
        userResult.setNickName(userResume.getNickName());
        userResult.setPhone(userResume.getPhone());
        userResult.setAddress(userResume.getAddress());
        userResult.setLoginType(userResume.getLoginType());
        userResult.setTenantId(userResume.getTenantId());
        userResult.setDeptId(userResume.getDeptId());
        userResult.setOpenId(userResume.getOpenId());
        userResult.setUnionId(userResume.getUnionId());
        userResult.setFingerprint(userResume.getFingerprint());
        userResult.setPermission(userResume.getPermission() == null ? Collections.emptySet() : userResume.getPermission());
        userResult.setRoles(userResume.getRoles());
        userResult.setExt(userResume.getExt());
        userResult.setDataPermission(userResume.getDataPermission());
        userResult.setDataPermissionRule(userResume.getDataPermissionRule());
        userResult.setExpireTime(System.currentTimeMillis() / 1000
                + authClientProperties.getClampedStaticTokenExpireSeconds());

        String refreshedToken = TokenCipher.encrypt(userResult);
        if (StringUtils.isBlank(refreshedToken)) {
            return null;
        }

        userResult.setToken(refreshedToken);
        userResult.setRefreshToken(refreshedToken);
        /**
         * login结果。
         * @param refreshedToken refreshed令牌
         * @param refreshedToken refreshed令牌
         * @param userResume.getFingerprint( 用户resume.getfingerprint(
         * @return login结果的结果
         */
        return new LoginResult(refreshedToken, refreshedToken, userResume.getFingerprint(), userResult);
    }

    /**
     * 获取访问令牌
     *
     * <p>静态协议下基于 {@code plugin.oauth.temp.user} 验证账号密码，
     * 命中后通过 {@link TokenCipher} 签发 令牌，角色/用户标识 与
     * {@link #newAuthenticationInformation} 保持一致（admin → SUPER_ADMIN + ADMIN）。</p>
     *
     * @param username  用户名
     * @param password  密码
     * @param authType  认证类型
     * @param ext       扩展信息
     * @return 登录认证结果对象
     */
    @Override
    public LoginAuthResult getAccessToken(String username, String password, AuthType authType, Map<String, Object> ext) {
        String tempUser = authClientProperties.getTemp() != null
                ? authClientProperties.getTemp().getUser() : null;
        if (StringUtils.isBlank(tempUser)) {
            log.warn("[StaticProtocol] 临时账号未配置 (plugin.oauth.temp.user)");
            return null;
        }
        LoginAuthResult result = StaticTempLogin.login(tempUser, username, password,
                authType != null ? authType.getCode() : "web",
                authClientProperties.getClampedStaticTokenExpireSeconds());
        if (result == null) {
            log.warn("[StaticProtocol] 临时账号不匹配 - 输入: {}", username);
        }
        return result;
    }

    /**
     * 用户登出
     *
     * @param uid          用户标识
     * @param logoutType   登出类型
     * @param userResult   用户结果对象
     * @return 登录认证结果对象
     */
    @Override
    public LoginAuthResult logout(String uid, LogoutType logoutType, UserResult userResult) {
        if (logoutType == LogoutType.KICK) {
            return kick(uid);
        }
        String token = null != userResult && StringUtils.isNotBlank(userResult.getToken())
                ? userResult.getToken() : uid;
        if (StringUtils.isBlank(token) || NULL.equals(token)) {
            return new LoginAuthResult(400, "未登录");
        }
        revokeToken(token, null != userResult ? userResult.getExpireTime() : null);
        return new LoginAuthResult(200, "注销成功");
    }

    /**
     * 强制下线指定用户的全部会话
     *
     * @param userKey 用户标识或用户名
     * @return 登录认证结果
     */
    public static LoginAuthResult kick(String userKey) {
        List<String> hashes = SESSIONS.tokenHashesOf(userKey);
        if (hashes.isEmpty()) {
            return new LoginAuthResult(404, "用户不在线");
        }
        for (String hash : hashes) {
            AbstractProtocol.revokeTokenHash(hash, null);
        }
        log.info("[StaticProtocol]强制下线 - 用户: {}, 会话数: {}", userKey, hashes.size());
        return new LoginAuthResult(200, "已下线");
    }

    /**
     * 获取在线用户列表
     * <p>静态协议没有中心会话存储，数据来自鉴权过程中登记的进程内会话表</p>
     *
     * @param query 查询参数
     * @return 在线用户结果
     */
    @Override
    public OnlineUserResult getOnlineUsers(OnlineUserQuery query) {
        String username = null == query ? null : query.getUsername();
        String ip = null == query ? null : query.getIp();
        List<StaticSessionRegistry.OnlineSession> online = SESSIONS.online(username, ip);
        List<OnlineUserInfo> users = new ArrayList<>();
        for (StaticSessionRegistry.OnlineSession session : online) {
            OnlineUserInfo info = new OnlineUserInfo();
            info.setUserId(session.getUserId());
            info.setUsername(session.getUsername());
            info.setNickname(session.getNickname());
            info.setLoginIp(session.getLoginIp());
            info.setBrowser(session.getBrowser());
            info.setOs(session.getOs());
            info.setLoginType(session.getLoginType());
            info.setLoginTime(session.getLoginTime());
            info.setLastAccessTime(session.getLastAccessTime());
            users.add(info);
        }
        int page = null == query || query.getPage() < 1 ? 1 : query.getPage();
        int size = null == query || query.getSize() < 1 ? users.size() : query.getSize();
        int from = Math.min((page - 1) * size, users.size());
        int to = Math.min(from + size, users.size());
        return new OnlineUserResult(new ArrayList<>(users.subList(from, to)), users.size(), page, size);
    }

    /**
     * 获取在线状态
     *
     * @param uid 用户标识
     * @return 在线状态
     */
    @Override
    public OnlineStatus getOnlineStatus(String uid) {
        OnlineStatus status = new OnlineStatus();
        status.setOnlineCount(SESSIONS.size());
        return status;
    }
}

