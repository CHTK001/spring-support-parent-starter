package com.chua.starter.account.service;

import com.chua.starter.account.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 账号池管理服务。
 *
 * <p>管理各平台的账号池，支持增删查改和统计。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class AccountPoolService {

    /** 账号池：platformType -> List<Account> */
    private final Map<Account.PlatformType, List<Account>> pools = new ConcurrentHashMap<>();

    /**
     * 添加账号到池
     */
    public Account addAccount(Account account) {
        if (account == null) return null;
        account.setId(UUID.randomUUID().toString());
        account.setCreatedAt(System.currentTimeMillis());
        account.setActive(true);
        pools.computeIfAbsent(account.getPlatformType(), k -> new ArrayList<>()).add(account);
        log.info("[AccountPool] 添加账号: type={}, email={}", account.getPlatformType(), account.getEmail());
        return account;
    }

    /**
     * 批量添加账号
     */
    public List<Account> addAccounts(List<Account> accounts) {
        if (accounts == null || accounts.isEmpty()) return List.of();
        List<Account> result = new ArrayList<>();
        for (Account acc : accounts) {
            Account added = addAccount(acc);
            if (added != null) result.add(added);
        }
        return result;
    }

    /**
     * 获取活跃账号
     */
    public Optional<Account> getActiveAccount(Account.PlatformType type) {
        List<Account> pool = pools.get(type);
        if (pool == null || pool.isEmpty()) return Optional.empty();
        return pool.stream()
                .filter(Account::isActive)
                .findFirst();
    }

    /**
     * 获取所有活跃账号
     */
    public List<Account> getActiveAccounts(Account.PlatformType type) {
        List<Account> pool = pools.get(type);
        if (pool == null) return List.of();
        return pool.stream().filter(Account::isActive).collect(Collectors.toList());
    }

    /**
     * 获取所有账号（含非活跃）
     */
    public List<Account> getAllAccounts(Account.PlatformType type) {
        List<Account> pool = pools.get(type);
        return pool != null ? new ArrayList<>(pool) : List.of();
    }

    /**
     * 释放账号（标记为非活跃）
     */
    public void releaseAccount(Account account) {
        if (account == null) return;
        account.setActive(false);
        log.info("[AccountPool] 释放账号: type={}, email={}", account.getPlatformType(), account.getEmail());
    }

    /**
     * 删除账号
     */
    public boolean deleteAccount(String accountId) {
        return pools.values().stream()
                .flatMap(List::stream)
                .filter(a -> accountId.equals(a.getId()))
                .map(a -> { a.setActive(false); return true; })
                .findFirst()
                .orElse(false);
    }

    /**
     * 清空指定类型的账号池
     */
    public void clearPool(Account.PlatformType type) {
        pools.remove(type);
        log.info("[AccountPool] 清空账号池: type={}", type);
    }

    /**
     * 获取账号池统计
     */
    public Map<Account.PlatformType, PoolStats> getStats() {
        Map<Account.PlatformType, PoolStats> stats = new ConcurrentHashMap<>();
        for (Map.Entry<Account.PlatformType, List<Account>> entry : pools.entrySet()) {
            List<Account> pool = entry.getValue();
            long active = pool.stream().filter(Account::isActive).count();
            stats.put(entry.getKey(), new PoolStats(pool.size(), (int) active, pool.size() - (int) active));
        }
        return stats;
    }

    /**
     * 账号池统计
     */
    public record PoolStats(int total, int active, int inactive) {}
}
