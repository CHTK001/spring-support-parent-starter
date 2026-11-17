package com.chua.starter.oauth.client.support.appkey;

import com.chua.starter.common.support.appkey.AppKeyAuthenticator;
import com.chua.starter.oauth.client.support.properties.AuthClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

/**
 * AI appKey 本地认证支持。
 *
 * <p>appKey 台账属于 AI 模块（{@code sys_ai_app_key}）。本类只负责「是否启用」与
 * 委派鉴权，真正的台账查询由 AI 模块通过 {@link AppKeyAuthenticator} 契约提供，
 * <b>不直连 AI 的持久层</b>。</p>
 *
 * <p>{@code plugin.oauth.ai-app-key.enabled=true} 且 AI 模块在类路径上时生效，
 * 用于单机部署或外部 o认证 服务不可达场景下的 AI appKey 认证。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class AiAppKeySupport {

    /**
     * 全局静态实例（由 认证客户端配置 注册，供每请求新建的 web请求 读取）
     */
    private static volatile AiAppKeySupport INSTANCE;

    /**
     * 认证配置
     */
    private final AuthClientProperties authProperties;

    /**
     * appKey 台账认证实现（由 AI 模块提供，可能不存在）
     */
    private final ObjectProvider<AppKeyAuthenticator> authenticatorProvider;

    /**
     * 构造 AI appKey 认证支持。
     *
     * @param authProperties        o认证 客户端配置
     * @param authenticatorProvider appKey 台账认证实现（AI 模块提供）
     */
    public AiAppKeySupport(AuthClientProperties authProperties,
                           ObjectProvider<AppKeyAuthenticator> authenticatorProvider) {
        this.authProperties = authProperties;
        this.authenticatorProvider = authenticatorProvider;
    }

    /**
     * 注册全局实例。
     *
     * @param support AI appKey 认证支持
     */
    public static void register(AiAppKeySupport support) {
        INSTANCE = support;
    }

    /**
     * 获取全局实例。
     *
     * @return AI appKey 认证支持，未注册返回 {@code null}
     */
    public static AiAppKeySupport get() {
        return INSTANCE;
    }

    /**
     * 是否已启用 AI appKey 本地认证。
     *
     * <p>配置开启但 AI 模块不在类路径上（拿不到台账实现）时视为未启用，
     * 由调用方继续走 o认证 协议，而不是把请求判成认证失败。</p>
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return authProperties.getAiAppKey() != null
                && authProperties.getAiAppKey().isEnabled()
                && authenticatorProvider.getIfAvailable() != null;
    }

    /**
     * 校验 appKey 并返回归属用户名。
     *
     * @param appKey appKey 值
     * @return 归属用户名；未启用或校验不通过返回 {@code null}
     */
    public String authenticate(String appKey) {
        if (appKey == null || appKey.isBlank()) {
            return null;
        }
        AppKeyAuthenticator authenticator = authenticatorProvider.getIfAvailable();
        if (authenticator == null || !authenticator.isAvailable()) {
            log.warn("[AiAppKey] appKey 台账不可用（AI 模块未装配），跳过 AppKey 认证");
            return null;
        }
        try {
            // 本地回退认证拿不到可信客户端 IP，交由实现方按「IP 未知不判定」处理
            return authenticator.authenticate(appKey, null);
        } catch (Exception e) {
            log.warn("[AiAppKey] AppKey 校验异常: {}", e.getMessage());
            return null;
        }
    }
}
