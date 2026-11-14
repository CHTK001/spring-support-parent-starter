package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.spi.AccountCapability;
import com.chua.starter.account.spi.AccountField;
import com.chua.starter.account.spi.AccountKindProvider;

import java.util.List;
import java.util.Set;

/**
 * 短信账号种类（服务商密钥 + 签名 / 模板）。
 *
 * <p>表单项与账号页「短信账号」一致：服务商 + 手机号 + 密钥 + 服务地址 + 区域 / 签名 / 应用 ID / 模板编码。</p>
 *
 * <p><b>能力位口径</b>：本种类没有 {@code AccountVerifier} 实现（校验会返回「未安装该
 * 账号种类的校验能力」），因此不声明 {@link AccountCapability#VERIFY}；
 * 收信箱同理不声明。</p>
 *
 * @author CH
 * @since 4.0.0.61
 */
@Spi("sms")
public class SmsKindProvider implements AccountKindProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public String kind() {
        return AccountKind.SMS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String label() {
        return "短信账号";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String icon() {
        return "ChatDotRound";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int order() {
        return 30;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String secretType() {
        return "sms";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AccountField> fields() {
        return List.of(
                AccountField.required("name", "账号名称", null),
                AccountField.required("code", "账号编码", null),
                AccountField.of("vendor", "短信服务商", "如 aliyun / tencent / huawei"),
                AccountField.of("username", "手机号 / 账号", "如 13800000000"),
                AccountField.secret("secret", "敏感凭据", "AccessKey / Secret"),
                AccountField.of("endpoint", "服务地址", "如 dysms.aliyun.com"),
                AccountField.of("region", "区域", "如 cn-hangzhou"),
                AccountField.of("signName", "短信签名", null),
                AccountField.of("appId", "应用 ID", null),
                AccountField.of("templateCode", "模板编码", "如 SMS_123456789"),
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
