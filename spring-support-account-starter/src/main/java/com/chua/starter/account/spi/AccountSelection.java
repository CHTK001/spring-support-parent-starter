package com.chua.starter.account.spi;

import com.chua.starter.account.manage.store.AccountKind;

/**
 * 业务配置选定的账号（可选能力）。
 *
 * <p>账号模块按种类解析「默认账号」时，先问本接口：配置管理里为邮件 / 短信
 * 指定了具体账号（{@code smtp.accountId} / {@code sms.accountId}）就该用它，
 * 而不是猜一个默认值。宿主实现本接口把配置值翻译成账号标识即可，账号模块
 * 不反向依赖宿主的配置存储。</p>
 *
 * <p>没有任何实现、或实现返回空时，退回
 * {@code ManageAccountService.resolveDefault(kind)} 的默认解析，不影响既有部署。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
public interface AccountSelection {

    /**
     * 取业务配置为指定种类选定的账号。
     *
     * @param kind 账号种类（{@link AccountKind#EMAIL} / {@link AccountKind#SMS}）
     * @return 账号标识；未配置或配置已失效返回 {@code null}
     */
    String selectAccountId(String kind);
}
