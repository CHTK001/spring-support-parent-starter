package com.chua.starter.account.phone;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.spi.AccountCapability;
import com.chua.starter.account.spi.AccountField;
import com.chua.starter.account.spi.AccountKindProvider;

import java.util.List;
import java.util.Set;

/**
 * 手机号种类（手工录入号码，本地目录轮询收短信，只收不发）。
 *
 * <p>与 {@link TempPhoneKindProvider}（临时手机号）同链路不同生命周期：
 * 号码由用户在表单里手工填写（{@code username} 存号码），长期有效、不过期、
 * 不参与「领取 / 销毁」；收信走统一账号的 {@code /api/accounts/{accountId}/mailbox}，
 * 由 {@link PhoneInboxProvider} 提供只读收信箱。</p>
 *
 * <p><b>能力位口径</b>：本种类没有 {@code AccountVerifier} 实现，不声明
 * {@link AccountCapability#VERIFY}；收信箱实现随本 jar 注册，声明
 * {@link AccountCapability#INBOX}。手机号只收不发，也没有凭据：
 * 不收录密钥库、不参与默认账号切换。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Spi("phone")
public class PhoneKindProvider implements AccountKindProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public String kind() {
        return AccountKind.PHONE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String label() {
        return "手机号";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String icon() {
        return "Iphone";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int order() {
        return 41;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AccountField> fields() {
        return List.of(
                AccountField.required("name", "账号名称", null),
                AccountField.required("code", "账号编码", null),
                AccountField.required("username", "手机号", "如 13800000000"),
                AccountField.of("vendor", "运营商", "移动 / 联通 / 电信（可留空）"),
                AccountField.of("webUrl", "控制台地址", null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<AccountCapability> capabilities() {
        return Set.of(AccountCapability.INBOX);
    }
}
