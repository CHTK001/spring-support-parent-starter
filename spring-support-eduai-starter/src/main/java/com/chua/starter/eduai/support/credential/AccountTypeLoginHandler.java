package com.chua.starter.eduai.support.credential;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.utils.CookieUtil;
import com.chua.starter.oauth.client.support.credential.CredentialProvider;
import com.chua.starter.oauth.client.support.credential.CredentialRequest;
import com.chua.starter.oauth.client.support.enums.AuthType;
import com.chua.starter.oauth.client.support.provider.LoginData;
import com.chua.starter.oauth.client.support.user.LoginAuthResult;
import com.chua.starter.system.constant.SystemConstant;
import com.chua.starter.system.handler.LoginHandler;
import com.chua.starter.system.pojo.LoginResult;
import com.chua.starter.system.service.impl.SysLoginServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * 按「账号来源」登录的处理器基类：把 {@code POST /v2/user/login} 的分发口与 edu 域的账号来源接起来。
 *
 * <p><b>为什么需要它</b>：{@code SysLoginServiceImpl#login} 把 {@link LoginHandler} 的扩展名组合成
 * {@code loginType-accountType}（null 段被跳过），再用 {@code getNewExtension(...)} 取实现。
 * 请求里带 {@code accountType=teacher} / {@code student} 时组合出的名字分别是 {@code teacher} /
 * {@code student}，而框架只自带 {@code WEB}（默认实现）与 {@code WECHAT} 两个处理器：取不到实现
 * 返回 {@code null}，调用方随即在 {@code null#login(...)} 上抛空指针，对外表现为 500
 * （{@code S0512C0000 系统处理异常}）。因此「工号 / 学号能不能登录」不只取决于凭据怎么校验，
 * 还取决于**登录分发口认不认这个账号来源**。</p>
 *
 * <p><b>为什么是无参可构造、且凭据实现用静态绑定</b>：登录口走 {@code getNewExtension(...)}，
 * 它按 {@code implClass} 的构造器<b>反射新建</b>（不返回 {@code register(name, obj)} 预置的实例），
 * 所以实现必须无参可构造、依赖不能走构造器注入；而 {@code ServiceProvider.CACHE} 是
 * {@code ConcurrentReferenceHashMap}，默认引用类型为 <b>SOFT</b>，内存吃紧时其中的 provider
 * 实例会被 GC 回收并重建，{@code register(...)} 写下的注册随之丢失（实测会出现「启动日志显示注册成功、
 * 请求时却查不到」）。凭据实现是启动期一次性装配好的单例，绑定到本类的静态表最稳；查注册表只作兜底。</p>
 *
 * <p><b>大小写</b>：SPI 的注册侧与查找侧都会 {@code toUpperCase()}，名字匹配用 {@code equalsIgnoreCase}，
 * 所以实现注册成大写的 {@code TEACHER} / {@code STUDENT}，请求组合出的小写名字同样命中。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
public abstract class AccountTypeLoginHandler implements LoginHandler {

    /**
     * 账号来源 → 凭据实现。启动期由 {@link EduCredentialConfiguration} 绑定。
     */
    private static final Map<String, CredentialProvider> BOUND = new ConcurrentHashMap<>();

    /**
     * 绑定某个账号来源的凭据实现。
     *
     * @param accountType 账号来源（与学生 / 教师实现注册名一致）
     * @param provider    凭据实现；传 {@code null} 表示解绑
     */
    public static void bind(String accountType, CredentialProvider provider) {
        if (provider == null) {
            BOUND.remove(accountType);
        } else {
            BOUND.put(accountType, provider);
        }
    }

    /**
     * 本处理器负责的账号来源。
     *
     * @return 账号来源标识，如 {@code teacher} / {@code student}
     */
    protected abstract String accountType();

    /**
     * 凭据实现缺失时给用户的提示（面向该账号来源的措辞）。
     *
     * @return 提示文案
     */
    protected abstract String unavailableMessage();

    /**
     * 本次登录的认证类型，用于写入令牌的登录方式归因。
     *
     * @return 认证类型枚举
     */
    protected abstract AuthType authType();

    @Override
    public ReturnResult<LoginResult> login(SysLoginServiceImpl sysLoginService, LoginData loginData,
                                          HttpServletRequest request, HttpServletResponse response,
                                          String fingerprint) {
        long startTime = System.currentTimeMillis();
        String accountType = accountType();

        CredentialProvider provider = BOUND.get(accountType);
        if (provider == null) {
            // 兜底：注册表可用时仍能取到（例如 bind 尚未发生或缓存被回收重建）
            provider = ServiceProvider.of(CredentialProvider.class).getExtension(accountType);
        }
        if (provider == null) {
            log.error("[EduAi] 登录处理器找不到凭据实现 - 账号来源: {}", accountType);
            sysLoginService.publishLoginLog(loginData, request, fingerprint, null, "登录不可用",
                    System.currentTimeMillis() - startTime);
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), unavailableMessage());
        }

        var credential = new CredentialRequest(
                loginData.getUsername(),
                loginData.getPassword(),
                authType(),
                accountType,
                null,
                0);

        LoginAuthResult authResult;
        try {
            authResult = provider.authenticate(credential);
        } catch (Exception e) {
            // 凭据实现抛异常时不让登录口吐 500 堆栈：记日志、按业务失败返回，并保留原因便于排查
            log.error("[EduAi] 登录校验异常 - 账号来源: {}, 账号: {}", accountType, loginData.getUsername(), e);
            sysLoginService.publishLoginLog(loginData, request, fingerprint, null, "登录校验异常",
                    System.currentTimeMillis() - startTime);
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), "系统处理异常，请稍后重试");
        }

        if (authResult == null || authResult.getCode() != SystemConstant.HTTP_STATUS_OK) {
            String message = authResult == null || authResult.getMessage() == null
                    ? "账号或口令不正确"
                    : authResult.getMessage();
            sysLoginService.publishLoginLog(loginData, request, fingerprint, null, message,
                    System.currentTimeMillis() - startTime);
            return ReturnResult.error(REQUEST_PARAM_ERROR.getCode(), message);
        }

        LoginResult loginResult = LoginResult.builder()
                .userInfo(authResult.getUserResume() != null
                        ? sysLoginService.buildUserInfo(authResult.getUserResume())
                        : null)
                .tokenType("Bearer")
                .accessToken(authResult.getToken())
                .refreshToken(authResult.getRefreshToken())
                .expires(authResult.getUserResume() != null ? authResult.getUserResume().getExpireTime() : null)
                .build();
        CookieUtil.set(response, "x-oauth-cookie", authResult.getToken(), true);
        sysLoginService.publishLoginLog(loginData, request, fingerprint, authResult, null,
                System.currentTimeMillis() - startTime);
        return ReturnResult.ok(loginResult);
    }
}
