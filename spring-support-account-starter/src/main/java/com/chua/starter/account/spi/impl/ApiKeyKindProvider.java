package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.spi.AccountCapability;
import com.chua.starter.account.spi.AccountField;
import com.chua.starter.account.spi.AccountKindProvider;

import java.util.List;
import java.util.Set;

/**
 * API 密钥 / Token 种类（设备厂商、第三方服务）。
 *
 * <p>表单项与账号页「API 密钥」一致：厂商 + AccessKey + 敏感凭据 + API 地址 + 区域。</p>
 *
 * <p><b>能力位口径</b>：本种类没有 {@code AccountVerifier} 实现，也没有收信箱实现，
 * 因此不声明 {@link AccountCapability#VERIFY} / {@link AccountCapability#INBOX}，
 * 界面不会出现点了只会返回「未知」的按钮。</p>
 *
 * @author CH
 * @since 4.0.0.61
 */
@Spi("api-key")
public class ApiKeyKindProvider implements AccountKindProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public String kind() {
        return AccountKind.API_KEY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String label() {
        return "API 密钥";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String icon() {
        return "Key";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int order() {
        return 10;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String secretType() {
        return "api";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AccountField> fields() {
        return List.of(
                AccountField.required("name", "账号名称", null),
                AccountField.required("code", "账号编码", null),
                AccountField.of("vendor", "厂商 / 平台", "如 hikvision / aliyun / openai"),
                AccountField.of("username", "AccessKey / 账号", "如 AccessKeyId"),
                AccountField.secret("secret", "敏感凭据", "Secret / Token"),
                AccountField.of("endpoint", "API 地址", "如 https://api.example.com"),
                AccountField.of("region", "区域", "如 cn-shanghai"),
                AccountField.of("secretId", "选择密钥", null),
                AccountField.of("webUrl", "控制台地址", null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<AccountCapability> capabilities() {
        return Set.of(AccountCapability.SECRET_LIBRARY, AccountCapability.DEFAULT_SWITCH);
    }
}
