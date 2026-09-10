package com.chua.starter.account.controller;

import com.chua.starter.account.Account;
import com.chua.starter.account.CaptchaSolver;
import com.chua.starter.account.service.AccountPoolService;
import com.chua.starter.account.spi.PlatformRegister;
import com.chua.common.support.spi.ServiceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号管理 API。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountPoolService poolService;
    private final org.springframework.beans.factory.ObjectProvider<CaptchaSolver> captchaSolverProvider;
    private final com.chua.starter.account.config.AccountRegisterProperties properties;
    private final com.chua.starter.account.service.ProxyManager proxyManager;

    /** 简单内存存储的配置 */
    private final Map<String, Object> settingsStore = new ConcurrentHashMap<>();

    /**
     * 获取配置
     */
    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        if (settingsStore.isEmpty()) {
            settingsStore.put("enable", false);
            settingsStore.put("yescaptchaKey", "");
            settingsStore.put("emailProvider", "fce");
            settingsStore.put("proxy", "");
            settingsStore.put("autoRegisterCount", 0);
        }
        return Map.copyOf(settingsStore);
    }

    /**
     * 保存配置
     */
    @PostMapping("/settings")
    public void saveSettings(@RequestBody Map<String, Object> body) {
        log.info("[Account] 保存配置: {}", body);
        settingsStore.putAll(body);
        if (body.containsKey("proxy")) {
            Object proxy = body.get("proxy");
            proxyManager.setProxy(proxy != null ? proxy.toString() : null);
        }
    }

    /**
     * 获取当前代理配置
     */
    @GetMapping("/proxy")
    public Map<String, Object> getProxy() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("enabled", proxyManager.isEnabled());
        result.put("proxy", proxyManager.getRawProxy());
        result.put("host", proxyManager.getProxyHost());
        result.put("port", proxyManager.getProxyPort());
        return result;
    }

    /**
     * 设置代理
     */
    @PostMapping("/proxy")
    public Map<String, Object> setProxy(@RequestBody Map<String, Object> body) {
        Object proxy = body.get("proxy");
        proxyManager.setProxy(proxy != null ? proxy.toString() : null);
        settingsStore.put("proxy", proxyManager.getRawProxy());
        return getProxy();
    }

    /**
     * 测试代理连通性（默认 accounts.x.ai）
     */
    @PostMapping("/proxy/test")
    public Map<String, Object> testProxy(@RequestBody(required = false) Map<String, Object> body) {
        String target = body != null && body.get("url") != null ? body.get("url").toString() : null;
        int status = proxyManager.testProxy(target);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", status);
        result.put("reachable", status > 0 && status < 500);
        result.put("url", target != null ? target : "https://accounts.x.ai/sign-up");
        return result;
    }

    @PostMapping("/register/{platformType}")
    public Account register(
            @PathVariable Account.PlatformType platformType,
            @RequestParam(required = false) String emailProvider,
            @RequestParam(required = false) String proxy) {
        log.info("[Account] 注册账号: type={}, provider={}", platformType, emailProvider);
        PlatformRegister register = getPlatformRegister(platformType);
        if (register == null) {
            return Account.builder().active(false).build();
        }
        Account account = register.register(
                null,
                emailProvider != null ? emailProvider : properties.getEmailProvider(),
                proxy != null ? proxy : properties.getProxy());
        if (account != null) {
            poolService.addAccount(account);
        }
        return account;
    }

    /**
     * 批量注册
     */
    @PostMapping("/register-batch/{platformType}")
    public List<Account> registerBatch(
            @PathVariable Account.PlatformType platformType,
            @RequestParam int count,
            @RequestParam(required = false) String emailProvider) {
        log.info("[Account] 批量注册: type={}, count={}", platformType, count);
        PlatformRegister register = getPlatformRegister(platformType);
        if (register == null) {
            return List.of();
        }
        List<Account> accounts = register.registerBatch(
                count,
                emailProvider != null ? emailProvider : properties.getEmailProvider(),
                properties.getProxy());
        poolService.addAccounts(accounts);
        return accounts;
    }

    /**
     * 获取可用账号
     */
    @GetMapping("/acquire/{platformType}")
    public Optional<Account> acquire(@PathVariable Account.PlatformType platformType) {
        return poolService.getActiveAccount(platformType);
    }

    /**
     * 获取账号列表
     */
    @GetMapping("/list/{platformType}")
    public List<Account> list(@PathVariable Account.PlatformType platformType) {
        return poolService.getAllAccounts(platformType);
    }

    /**
     * 释放账号
     */
    @PostMapping("/release/{platformType}/{accountId}")
    public void release(@PathVariable Account.PlatformType platformType,
                        @PathVariable String accountId) {
        poolService.getAllAccounts(platformType).stream()
                .filter(a -> accountId.equals(a.getId()))
                .findFirst()
                .ifPresent(poolService::releaseAccount);
    }

    /**
     * 删除账号
     */
    @DeleteMapping("/{platformType}/{accountId}")
    public boolean delete(@PathVariable Account.PlatformType platformType,
                          @PathVariable String accountId) {
        return poolService.deleteAccount(accountId);
    }

    /**
     * 清空账号池
     */
    @DeleteMapping("/pool/{platformType}")
    public void clearPool(@PathVariable Account.PlatformType platformType) {
        poolService.clearPool(platformType);
    }

    /**
     * 获取统计
     */
    @GetMapping("/stats")
    public Map<Account.PlatformType, AccountPoolService.PoolStats> stats() {
        return poolService.getStats();
    }

    /**
     * 手动触发补号
     */
    @PostMapping("/replenish/{platformType}")
    public List<Account> replenish(
            @PathVariable Account.PlatformType platformType,
            @RequestParam(defaultValue = "1") int count) {
        PlatformRegister register = getPlatformRegister(platformType);
        if (register == null) return List.of();
        List<Account> accounts = register.registerBatch(count, properties.getEmailProvider(), properties.getProxy());
        poolService.addAccounts(accounts);
        return accounts;
    }

    private PlatformRegister getPlatformRegister(Account.PlatformType type) {
        try {
            String name = type.name().toLowerCase();
            CaptchaSolver solver = captchaSolverProvider.getIfAvailable();
            return solver != null
                    ? ServiceProvider.of(PlatformRegister.class).getNewExtension(name, solver, proxyManager)
                    : ServiceProvider.of(PlatformRegister.class).getNewExtension(name, proxyManager);
        } catch (Exception e) {
            log.warn("[Account] 获取平台注册器失败: {}", type);
            return null;
        }
    }
}
