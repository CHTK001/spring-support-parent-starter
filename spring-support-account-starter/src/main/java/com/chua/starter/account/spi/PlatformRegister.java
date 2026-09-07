package com.chua.starter.account.spi;

import com.chua.starter.account.Account;
import java.util.List;

/**
 * 平台注册器 SPI 接口。
 *
 * <p>每个第三方平台（如Grok、Twitter、Discord等）实现此接口，
 * 定义该平台特有的注册流程。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface PlatformRegister {

    /**
     * 获取平台名称。
     */
    String platformName();

    /**
     * 执行注册流程。
     *
     * @param email         临时邮箱地址
     * @param emailProvider 邮箱提供商
     * @param proxyUrl      代理地址（可选）
     * @return 注册结果；失败返回 null
     */
    Account register(String email, String emailProvider, String proxyUrl);

    /**
     * 批量注册。
     */
    List<Account> registerBatch(int count, String emailProvider, String proxyUrl);

    /**
     * 验证账号有效性。
     */
    boolean validate(Account account);

    /**
     * 刷新token（如OAuth refresh）。
     */
    Account refreshToken(Account account);
}
