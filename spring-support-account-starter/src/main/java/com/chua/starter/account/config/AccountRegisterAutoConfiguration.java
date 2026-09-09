package com.chua.starter.account.config;

import com.chua.starter.account.Account;
import com.chua.starter.account.CaptchaSolver;
import com.chua.starter.account.spi.PlatformRegister;
import com.chua.common.support.spi.ServiceProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号注册自动配置。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "plugin.account.register", name = "enable", havingValue = "true")
public class AccountRegisterAutoConfiguration {

    private final AccountRegisterProperties properties;
    private final org.springframework.beans.factory.ObjectProvider<CaptchaSolver> captchaSolverProvider;
    private final Map<String, List<Account>> accountPools = new ConcurrentHashMap<>();

    public AccountRegisterAutoConfiguration(AccountRegisterProperties properties,
                                            org.springframework.beans.factory.ObjectProvider<CaptchaSolver> captchaSolverProvider) {
        this.properties = properties;
        this.captchaSolverProvider = captchaSolverProvider;
    }

    /**
     * 启动时预注册账号
     */
    @PostConstruct
    public void init() {
        if (properties.getAutoRegisterCount() > 0 && captchaSolverProvider.getIfAvailable() != null) {
            log.info("[AccountRegister] 启动时预注册 {} 个 Grok 账号", properties.getAutoRegisterCount());
            PlatformRegister grokRegister = getPlatformRegister("grok");
            if (grokRegister != null) {
                List<Account> accounts = grokRegister.registerBatch(
                    properties.getAutoRegisterCount(),
                    properties.getEmailProvider(),
                    properties.getProxy()
                );
                accountPools.put("grok", accounts);
                log.info("[AccountRegister] 预注册完成，成功 {} 个", accounts.size());
            }
        }
    }

    /**
     * 定期补号
     */
    @Scheduled(fixedDelayString = "${plugin.account.register.replenish-interval-seconds:3600}000")
    public void replenish() {
        if (captchaSolverProvider.getIfAvailable() == null) return;

        for (Map.Entry<String, List<Account>> entry : accountPools.entrySet()) {
            List<Account> pool = entry.getValue();
            long activeCount = pool.stream().filter(Account::isActive).count();
            if (activeCount < 2) {
                log.info("[AccountRegister] {} 账号池不足，开始补号 (当前: {})", entry.getKey(), activeCount);
                PlatformRegister register = getPlatformRegister(entry.getKey());
                if (register != null) {
                    List<Account> newAccounts = register.registerBatch(
                        2 - (int) activeCount,
                        properties.getEmailProvider(),
                        properties.getProxy()
                    );
                    pool.addAll(newAccounts);
                    log.info("[AccountRegister] 补号成功，当前 {} 账号数: {}", entry.getKey(), pool.size());
                }
            }
        }
    }

    /**
     * 手动注册账号
     */
    public Account register(String platform, String emailProvider, String proxyUrl) {
        PlatformRegister register = getPlatformRegister(platform);
        if (register == null) {
            log.warn("[AccountRegister] 未找到平台注册器: {}", platform);
            return null;
        }
        return register.register(null, emailProvider != null ? emailProvider : properties.getEmailProvider(), proxyUrl);
    }

    /**
     * 获取可用账号
     */
    public Account acquireAccount(String platform) {
        List<Account> pool = accountPools.get(platform);
        if (pool == null) {
            return null;
        }
        synchronized (pool) {
            for (Account account : pool) {
                if (account.isActive() && account.getToken() != null) {
                    account.setLastUsedAt(System.currentTimeMillis());
                    return account;
                }
            }
        }
        return null;
    }

    /**
     * 释放账号
     */
    public void releaseAccount(String platform, Account account) {
        if (account != null) {
            account.setActive(false);
        }
    }

    /**
     * 获取账号池统计
     */
    public Map<String, Integer> getPoolStats() {
        Map<String, Integer> stats = new ConcurrentHashMap<>();
        for (Map.Entry<String, List<Account>> entry : accountPools.entrySet()) {
            long active = entry.getValue().stream().filter(Account::isActive).count();
            stats.put(entry.getKey(), (int) active);
        }
        return stats;
    }

    private PlatformRegister getPlatformRegister(String platformName) {
        try {
            CaptchaSolver solver = captchaSolverProvider.getIfAvailable();
            return solver != null
                    ? ServiceProvider.of(PlatformRegister.class).getNewExtension(platformName, solver)
                    : ServiceProvider.of(PlatformRegister.class).getNewExtension(platformName);
        } catch (Exception e) {
            log.warn("[AccountRegister] 获取平台注册器失败: {}", platformName);
            return null;
        }
    }
}
