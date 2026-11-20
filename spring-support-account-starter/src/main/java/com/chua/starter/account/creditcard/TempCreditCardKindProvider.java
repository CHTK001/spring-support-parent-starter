package com.chua.starter.account.creditcard;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.account.manage.store.AccountKind;
import com.chua.starter.account.spi.AccountCapability;
import com.chua.starter.account.spi.AccountField;
import com.chua.starter.account.spi.AccountKindProvider;

import java.util.List;
import java.util.Set;

/**
 * 信用卡种类（临时虚拟卡号，仅测试用途）。
 *
 * <p>与「临时手机号」同思路的最小种类实现：只声明种类元数据与表单字段，
 * 不提供领取 / 生成服务——卡号由使用方自行填入或借助 mock 体系的
 * {@code creditcard} 生成器产生（按卡组织前缀 + Luhn 校验位，不代表真实卡片）。
 * 卡号存 {@code username}，发卡组织存 {@code vendor}，CVV 走敏感凭据
 * {@code secret}（打码、不回显），对账 / 网关地址存可选 {@code endpoint}。</p>
 *
 * <p><b>能力位口径</b>：本种类没有 {@code AccountVerifier} 实现，也没有收信箱
 * 实现，不声明 {@link AccountCapability#VERIFY} / {@link AccountCapability#INBOX}；
 * 凭据可引用密钥库（密钥类型 {@code payment}），并参与默认账号切换。</p>
 *
 * @author CH
 * @since 4.0.0.62
 */
@Spi("credit-card")
public class TempCreditCardKindProvider implements AccountKindProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public String kind() {
        return AccountKind.CREDIT_CARD;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String label() {
        return "信用卡";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String icon() {
        return "CreditCard";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int order() {
        return 50;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String secretType() {
        return "payment";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AccountField> fields() {
        return List.of(
                AccountField.required("name", "账号名称", null),
                AccountField.required("code", "账号编码", null),
                AccountField.of("username", "卡号", "虚拟卡号，仅测试用途"),
                AccountField.of("vendor", "发卡组织", "如 Visa / Mastercard / 银联"),
                AccountField.secret("secret", "CVV / 安全码", "卡片背面 3 位安全码"),
                AccountField.of("endpoint", "对账地址", "可选，如 https://gateway.example.com"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<AccountCapability> capabilities() {
        return Set.of(AccountCapability.SECRET_LIBRARY, AccountCapability.DEFAULT_SWITCH);
    }
}
