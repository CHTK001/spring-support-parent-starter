package com.chua.starter.account.spi.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.spi.AccountCapability;
import com.chua.starter.account.spi.AccountField;
import com.chua.starter.account.spi.AccountKindProvider;

import java.util.List;
import java.util.Set;

/**
 * 邮箱账号种类（POP3 / SMTP / IMAP）。
 *
 * <p>表单项与账号页「邮箱账号」一致：服务商 + 邮箱地址 + 授权码 + 发信 / 收信主机端口。</p>
 *
 * <p><b>能力位口径</b>：本模块自带 {@code EmailAccountVerifier}（IMAP 登录探活）与
 * {@code EmailInboxProvider}（收信箱），四项能力全部可用。</p>
 *
 * @author CH
 * @since 4.0.0.61
 */
@Spi("email")
public class EmailKindProvider implements AccountKindProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public String kind() {
        return AccountKind.EMAIL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String label() {
        return "邮箱账号";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String icon() {
        return "Message";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int order() {
        return 20;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String secretType() {
        return "mail";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AccountField> fields() {
        return List.of(
                AccountField.required("name", "账号名称", null),
                AccountField.required("code", "账号编码", null),
                AccountField.of("vendor", "邮件服务商", "如 qq / 163 / smtp"),
                AccountField.of("username", "邮箱地址", "如 ops@example.com"),
                AccountField.secret("secret", "邮箱密码 / 授权码", "服务商授权码"),
                AccountField.of("endpoint", "SMTP / POP 主机", "如 smtp.qq.com"),
                AccountField.of("from", "默认发件人", null),
                AccountField.of("port", "发信端口", "465 / 587"),
                AccountField.of("ssl", "SSL / TLS", null),
                AccountField.of("imapHost", "收信主机", "如 imap.qq.com"),
                AccountField.of("imapPort", "收信端口", "993"),
                AccountField.of("secretId", "选择密钥", null),
                AccountField.of("webUrl", "控制台地址", null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<AccountCapability> capabilities() {
        return Set.of(
                AccountCapability.VERIFY,
                AccountCapability.INBOX,
                AccountCapability.SECRET_LIBRARY,
                AccountCapability.DEFAULT_SWITCH);
    }
}
