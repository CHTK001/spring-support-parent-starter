package com.chua.starter.account.controller;

import com.chua.starter.account.Account;
import com.chua.starter.account.config.AccountRegisterAutoConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 账号注册管理 API。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRegisterAutoConfiguration config;

    /**
     * 手动注册账号
     */
    @PostMapping("/register/{platform}")
    public Account register(@PathVariable String platform,
                           @RequestParam(required = false) String emailProvider,
                           @RequestParam(required = false) String proxy) {
        log.info("[Account] 手动注册: platform={}, emailProvider={}", platform, emailProvider);
        return config.register(platform, emailProvider, proxy);
    }

    /**
     * 批量注册
     */
    @PostMapping("/register-batch/{platform}")
    public java.util.List<Account> registerBatch(@PathVariable String platform,
                                                  @RequestParam int count,
                                                  @RequestParam(required = false) String emailProvider) {
        log.info("[Account] 批量注册: platform={}, count={}", platform, count);
        // 简化实现：逐个注册
        java.util.List<Account> result = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            Account account = config.register(platform, emailProvider, null);
            if (account != null) {
                result.add(account);
            }
        }
        return result;
    }

    /**
     * 获取可用账号
     */
    @GetMapping("/acquire/{platform}")
    public Account acquire(@PathVariable String platform) {
        return config.acquireAccount(platform);
    }

    /**
     * 释放账号
     */
    @PostMapping("/release/{platform}")
    public void release(@PathVariable String platform, @RequestBody Account account) {
        config.releaseAccount(platform, account);
    }

    /**
     * 获取账号池统计
     */
    @GetMapping("/stats")
    public Map<String, Integer> stats() {
        return config.getPoolStats();
    }
}
