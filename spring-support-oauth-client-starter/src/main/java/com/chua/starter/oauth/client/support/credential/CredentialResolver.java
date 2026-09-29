package com.chua.starter.oauth.client.support.credential;

import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * 凭据校验扩展点的解析器：按「自定义优先、内置其次、都没有则不接管」的顺序取实现。
 *
 * <p>前缀约定：内置实现一律以 {@code builtin-} 开头注册（如 {@code builtin-web}），
 * 这样同名自定义实现（如 {@code web}）总是先被命中，从而不需要覆盖或重写内置类。</p>
 *
 * @since 4.0.0.42
 * @author CH
 * @see CredentialProvider
 */
@Slf4j
public final class CredentialResolver {

    /**
     * 内置实现注册名的统一前缀
     */
    private static final String BUILTIN_PREFIX = "builtin-";

    private CredentialResolver() {
    }

    /**
     * 按请求解析凭据校验实现。
     *
     * @param request 登录请求上下文
     * @return 命中的实现；没有任何实现接管时返回 {@code null}，调用方应执行既有逻辑
     */
    public static CredentialProvider resolve(CredentialRequest request) {
        return request == null ? null : resolve(request.effectiveAccountType());
    }

    /**
     * 按账号来源解析凭据校验实现。
     *
     * <p>解析顺序：自定义同名实现 → {@code builtin-} 前缀的内置实现 → {@code null}。
     * 解析过程不抛异常：SPI 容器尚未初始化或扫描失败时视为「无人接管」，
     * 由调用方回落到既有逻辑，宁可保持旧行为也不能让登录整体不可用。</p>
     *
     * @param accountType 账号来源，大小写与首尾空白不敏感
     * @return 命中的实现；无人接管时返回 {@code null}
     */
    public static CredentialProvider resolve(String accountType) {
        if (StringUtils.isBlank(accountType)) {
            return null;
        }
        var key = accountType.trim().toLowerCase(Locale.ROOT);
        try {
            var provider = ServiceProvider.of(CredentialProvider.class);
            var custom = provider.getExtension(key);
            if (custom != null) {
                return custom;
            }
            return provider.getExtension(BUILTIN_PREFIX + key);
        } catch (Exception e) {
            log.warn("[CredentialResolver] 解析凭据实现失败，回落既有逻辑 - 账号来源: {} - 异常: {}",
                    key, e.toString());
            return null;
        }
    }
}
