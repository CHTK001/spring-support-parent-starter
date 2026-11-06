package com.chua.starter.account.spi;

import com.chua.starter.account.manage.store.SysAccount;

import java.util.Map;

/**
 * 账号可用性校验 SPI（可选能力）。
 *
 * <p>账号管理只负责保存凭据，具体「这份凭据还能不能用」由对应能力模块实现：
 * 邮箱模块校验 POP/SMTP 登录，短信模块校验服务商签名，设备模块校验 AccessKey。
 * 引入对应 starter 即自动被 {@code ServiceProvider} 发现，账号模块不反向依赖它们。</p>
 *
 * <p>没有任何实现时，校验结果保持 {@code UNKNOWN}，不影响账号的增删改查。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
public interface AccountVerifier {

    /**
     * 是否处理该账号种类。
     *
     * @param kind 账号种类（{@code API_KEY} / {@code EMAIL} / {@code SMS}）
     * @return true 表示本实现负责该种类
     */
    boolean supports(String kind);

    /**
     * 校验账号凭据。
     *
     * @param account     账号（不含明文 Secret）
     * @param plainSecret 解密后的明文凭据；未配置时为 {@code null}
     * @return 校验结果：至少含 {@code valid}（布尔），可选 {@code message}
     * @throws Exception 上游校验失败
     */
    Map<String, Object> verify(SysAccount account, String plainSecret) throws Exception;
}
