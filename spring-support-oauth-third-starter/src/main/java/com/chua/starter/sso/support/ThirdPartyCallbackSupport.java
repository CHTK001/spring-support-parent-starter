package com.chua.starter.sso.support;

import com.chua.common.support.lang.algorithm.cipher.AesCipher;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.utils.DigestUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.sso.support.SystemCallbackPolicy;
import com.chua.starter.sso.pojo.LoginResult;
import com.chua.starter.sso.properties.SsoProperties;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.cache.AuthDefaultStateCache;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.nio.charset.StandardCharsets;

/**
 * 三方登录回调共享辅助
 *
 * <p>service 层与 6 个回调控制器（gitee/github/google/wechat/oidc + 通用路径变量控制器）
 * 共用，统一三件事：</p>
 *
 * <ol>
 *   <li><b>换票凭证签发与核销</b>：回调成功时把 {@link LoginResult} 用
 *       {@code plugin.system.security.third-party-callback-key} 加密成 {@code redirectParam}
 *       下发给前端，同时写入一次性 nonce（{@code xchg_ + sha256(密文)}）。前端无法本地解密
 *       （密钥不下发），只能把密文回传 {@code /v2/user/third/login/exchange} 兑换；
 *       兑换时<b>先核销 nonce 再解密</b>（fail-closed）——同一凭证不可重放，伪造密文过不了
 *       nonce 校验；</li>
 *   <li><b>回调地址读取消毒</b>：从 state 缓存读出的 {@code system_callback} 在
 *       {@link RedirectView#setUrl} 前必须过白名单（开放重定向读取点兜底）；</li>
 *   <li><b>错误提示双写</b>：{@code msg} 走 flash attribute（同域回跳可读），
 *       {@code thirdMsg} 走静态属性拼进重定向 query（跨域回跳 flash 必丢，query 活着）。</li>
 * </ol>
 *
 * @since 2026/10/07
 * @author CH
 */
@Slf4j
public final class ThirdPartyCallbackSupport {

    /**
     * 换票凭证一次性 nonce 的缓存键前缀
     */
    private static final String NONCE_PREFIX = "xchg_";

    /**
     * nonce 核销后的占位值（原值 "1"，核销即置 "0"，过期后 get 返回 null）
     */
    private static final String NONCE_CONSUMED = "0";

    /**
     * nonce 有效标记
     */
    private static final String NONCE_ACTIVE = "1";

    /**
     * 工具类
     */
    private ThirdPartyCallbackSupport() {
    }

    /**
     * 签发换票凭证：加密登录结果并登记一次性 nonce
     *
     * @param loginResult 回调签发的登录结果
     * @param props       系统安全配置（提供 {@code third-party-callback-key}）
     * @return AES 密文凭证，通过重定向属性 {@code redirectParam} 下发
     */
    public static String encryptRedirectParam(LoginResult loginResult, SsoProperties props) {
        String encrypt = AesCipher.create("jdk").encryptToString(
                props.getThirdPartyCallbackKey().getBytes(StandardCharsets.UTF_8),
                Json.toJson(loginResult));
        AuthDefaultStateCache.INSTANCE.cache(nonceKey(encrypt), NONCE_ACTIVE);
        return encrypt;
    }

    /**
     * 核销并解密换票凭证（一次性）
     *
     * <p>顺序刻意为「校验 → 核销 → 解密」：核销先于解密，解密失败（篡改、密钥不符）
     * 时凭证也已作废（fail-closed），不存在「失败后还能重试/重放」的窗口。</p>
     *
     * @param redirectParam 前端回传的密文凭证
     * @param props         系统安全配置
     * @return 登录结果；未登记、已使用、已过期、篡改或无 accessToken 时返回 {@code null}
     */
    public static LoginResult redeemRedirectParam(String redirectParam, SsoProperties props) {
        if (StringUtils.isBlank(redirectParam) || null == props) {
            return null;
        }
        String nonceKey = nonceKey(redirectParam);
        if (!NONCE_ACTIVE.equals(AuthDefaultStateCache.INSTANCE.get(nonceKey))) {
            log.warn("三方换票凭证核销失败: 未登记、已使用或已过期");
            return null;
        }
        AuthDefaultStateCache.INSTANCE.cache(nonceKey, NONCE_CONSUMED);
        try {
            String json = AesCipher.create("jdk").decryptToString(
                    props.getThirdPartyCallbackKey().getBytes(StandardCharsets.UTF_8), redirectParam);
            LoginResult loginResult = Json.fromJson(json, LoginResult.class);
            if (null == loginResult || StringUtils.isBlank(loginResult.getAccessToken())) {
                log.warn("三方换票凭证解密结果非法: 缺少 accessToken");
                return null;
            }
            return loginResult;
        } catch (Exception e) {
            log.warn("三方换票凭证解密失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 换票凭证的一次性 nonce 缓存键
     *
     * @param redirectParam 密文凭证
     * @return {@code xchg_ + sha256(密文)}（64 位小写十六进制）
     */
    public static String nonceKey(String redirectParam) {
        return NONCE_PREFIX + DigestUtils.sha256(redirectParam);
    }

    /**
     * 读取点兜底：state 缓存读出的回调地址过白名单后再交给 {@code RedirectView#setUrl}
     *
     * @param rawCallback 缓存中的原始回调地址
     * @param props       系统安全配置（提供 {@code allowed-system-callback-hosts}）
     * @return 校验通过的回调地址；不合法时返回 {@code "/"}
     */
    public static String safeSystemCallback(String rawCallback, SsoProperties props) {
        return SystemCallbackPolicy.sanitize(rawCallback, props == null ? null : props.getAllowedSystemCallbackHosts());
    }

    /**
     * 回调失败提示双写
     *
     * <p>{@code msg} 写 flash attribute（重定向前一跳的 flash，同域回跳可用），
     * {@code thirdMsg} 写静态属性（拼进重定向 query，跨域回跳不丢）。</p>
     *
     * @param attributes 重定向属性，可为 {@code null}
     * @param view       重定向视图，可为 {@code null}
     * @param message    错误提示
     */
    public static void markFailure(RedirectAttributes attributes, RedirectView view, String message) {
        if (null != attributes) {
            attributes.addFlashAttribute("msg", message);
        }
        if (null != view) {
            view.addStaticAttribute("thirdMsg", message);
        }
    }
}
