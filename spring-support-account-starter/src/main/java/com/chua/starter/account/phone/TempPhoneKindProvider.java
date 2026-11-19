package com.chua.starter.account.phone;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.spi.AccountCapability;
import com.chua.starter.account.spi.AccountField;
import com.chua.starter.account.spi.AccountKindProvider;

import java.util.List;
import java.util.Set;

/**
 * 临时手机号种类（本地目录轮询收短信，只收不发）。
 *
 * <p>号码由「领取」接口服务端生成（{@code username} 存号码），不提供手工
 * 填写号码的表单项；收信走统一账号的 {@code /api/accounts/{accountId}/mailbox}，
 * 由 {@link TempPhoneInboxProvider} 提供只读收信箱。</p>
 *
 * <p><b>能力位口径</b>：本种类没有 {@code AccountVerifier} 实现，不声明
 * {@link AccountCapability#VERIFY}；收信箱实现随本 jar 注册，声明
 * {@link AccountCapability#INBOX}。临时号只收不发，也不收录密钥库、
 * 不参与默认账号切换。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Spi("temp-phone")
public class TempPhoneKindProvider implements AccountKindProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public String kind() {
        return AccountKind.TEMP_PHONE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String label() {
        return "临时手机号";
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
        return 40;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AccountField> fields() {
        return List.of(
                AccountField.required("name", "账号名称", null),
                AccountField.required("code", "账号编码", null),
                AccountField.of("username", "手机号", "领取后自动生成"),
                AccountField.of("vendor", "厂商", "temp-phone"),
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
