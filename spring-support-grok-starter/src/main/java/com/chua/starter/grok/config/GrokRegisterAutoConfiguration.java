package com.chua.starter.grok;

import com.chua.starter.grok.config.GrokRegisterProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;

/**
 * Grok 注册自动配置。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "grok.register", name = "enable", havingValue = "true")
public class GrokRegisterAutoConfiguration {

    private final GrokRegisterProperties properties;
    private final CaptchaSolver captchaSolver;
    private final List<GrokAccount> accountPool = new ArrayList<>();

    public GrokRegisterAutoConfiguration(GrokRegisterProperties properties, CaptchaSolver captchaSolver) {
        this.properties = properties;
        this.captchaSolver = captchaSolver;
    }

    @Bean
    public GrokRegisterService grokRegisterService() {
        String providerName = properties.getEmailProvider();
        // EmailProvider will be injected via ServiceProvider at runtime
        return new GrokRegisterService(null, captchaSolver);
    }

    /**
     * 启动时预注册账号
     */
    @PostConstruct
    public void init() {
        if (properties.getAutoRegisterCount() > 0 && captchaSolver != null) {
            log.info("[GrokRegister] 启动时预注册 {} 个账号", properties.getAutoRegisterCount());
            GrokRegisterService service = grokRegisterService();
            List<GrokAccount> accounts = service.registerBatch(properties.getAutoRegisterCount(), properties.getEmailProvider());
            accountPool.addAll(accounts);
            log.info("[GrokRegister] 预注册完成，成功 {} 个", accounts.size());
        }
    }

    /**
     * 定期补号
     */
    @Scheduled(fixedDelayString = "${grok.register.replenish-interval-seconds:3600}000")
    public void replenish() {
        if (accountPool.size() < 2 && captchaSolver != null) {
            log.info("[GrokRegister] 账号池不足，开始补号");
            GrokRegisterService service = grokRegisterService();
            GrokAccount account = service.register(properties.getEmailProvider(), properties.getProxy());
            if (account != null) {
                accountPool.add(account);
                log.info("[GrokRegister] 补号成功，当前账号数: {}", accountPool.size());
            }
        }
    }

    /**
     * 获取可用账号
     */
    public GrokAccount acquireAccount() {
        synchronized (accountPool) {
            for (int i = 0; i < accountPool.size(); i++) {
                GrokAccount account = accountPool.get(i);
                if (account.isActive() && account.getSsoToken() != null) {
                    return account;
                }
            }
        }
        return null;
    }

    /**
     * 释放账号（标记为不活跃）
     */
    public void releaseAccount(GrokAccount account) {
        if (account != null) {
            account.setActive(false);
        }
    }
}
