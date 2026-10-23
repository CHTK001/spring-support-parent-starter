package com.chua.starter.ai.support.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 编程式事务支持。
 *
 * <p>渠道鉴权的 SSE 登录流程跑在独立守护线程池里（{@code ApiChannelAuthController}
 * 的 {@code SSE_EXECUTOR}），那里没有请求线程的事务上下文，
 * {@code @Transactional} 完全不起作用；而落库要连写
 * {@code sys_ai_provider_connection} 与 {@code sys_ai_provider} 两行，
 * 中途失败会留下「连接已写入但渠道未启用」的半成品记录。</p>
 *
 * <p>因此提供一个共享的 {@link TransactionTemplate}，让跨线程的写入走编程式事务。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Configuration
public class AiTransactionConfiguration {

    /**
     * 共享的编程式事务模板。
     *
     * <p>不设只读、不设超时：由调用方按业务语义决定。容器里只应有一个
     * {@link PlatformTransactionManager}（MyBatis-Plus 的 DataSourceTransactionManager）；
     * 若宿主引入多数据源，请在此显式指定 {@code @Qualifier}。</p>
     *
     * @param transactionManager 事务管理器
     * @return 事务模板
     */
    @Bean
    public TransactionTemplate aiTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
