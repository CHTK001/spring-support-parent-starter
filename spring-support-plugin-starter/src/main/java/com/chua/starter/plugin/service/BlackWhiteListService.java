package com.chua.starter.plugin.service;

import com.chua.starter.plugin.entity.BlackWhiteList;
import com.chua.starter.plugin.store.PersistenceStore;
import com.chua.starter.plugin.store.QueryCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 黑白名单服务
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlackWhiteListService implements ApplicationRunner {

    private final PersistenceStore<BlackWhiteList, Long> store;

    /**
     * 黑名单缓存
     */
    private final ConcurrentMap<String, BlackWhiteList> blacklistCache = new ConcurrentHashMap<>();

    /**
     * 白名单缓存
     */
    private final ConcurrentMap<String, BlackWhiteList> whitelistCache = new ConcurrentHashMap<>();

    /**
     * 应用启动时加载所有名单到内存
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        loadAllListsToCache();
    }

    /**
     * 从数据库加载所有名单到内存缓存
     */
    public void loadAllListsToCache() {
        try {
            // 加载黑名单
            QueryCondition blacklistCondition = QueryCondition.empty().eq("listType", BlackWhiteList.ListType.BLACKLIST)
                    .eq("enabled", true).orderByAsc("priority");
            List<BlackWhiteList> blacklists = store.findByCondition(blacklistCondition);
            blacklistCache.clear();
            for (BlackWhiteList item : blacklists) {
                blacklistCache.put(item.getListValue(), item);
            }

            // 加载白名单
            QueryCondition whitelistCondition = QueryCondition.empty().eq("listType", BlackWhiteList.ListType.WHITELIST)
                    .eq("enabled", true).orderByAsc("priority");
            List<BlackWhiteList> whitelists = store.findByCondition(whitelistCondition);
            whitelistCache.clear();
            for (BlackWhiteList item : whitelists) {
                whitelistCache.put(item.getListValue(), item);
            }

            log.info("Loaded {} blacklist and {} whitelist items to cache", blacklists.size(), whitelists.size());
        } catch (Exception e) {
            log.error("Failed to load black/white lists to cache", e);
        }
    }

    /**
     * 检查是否在黑名单中
     * 
     * @param value 要检查的值
     * @return 是否在黑名单中
     */
    public boolean isBlacklisted(String value) {
        if (value == null) {
            return false;
        }

        // 先检查精确匹配
        if (blacklistCache.containsKey(value)) {
            BlackWhiteList item = blacklistCache.get(value);
            return item.getEnabled() && !item.isExpired();
        }

        // 检查通配符和正则匹配
        for (BlackWhiteList item : blacklistCache.values()) {
            if (item.getEnabled() && !item.isExpired() && item.matches(value)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否在白名单中
     * 
     * @param value 要检查的值
     * @return 是否在白名单中
     */
    public boolean isWhitelisted(String value) {
        if (value == null) {
            return false;
        }

        // 先检查精确匹配
        if (whitelistCache.containsKey(value)) {
            BlackWhiteList item = whitelistCache.get(value);
            return item.getEnabled() && !item.isExpired();
        }

        // 检查通配符和正则匹配
        for (BlackWhiteList item : whitelistCache.values()) {
            if (item.getEnabled() && !item.isExpired() && item.matches(value)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查访问是否被允许 白名单优先级高于黑名单
     * 
     * @param value 要检查的值
     * @return 是否允许访问
     */
    public boolean isAccessAllowed(String value) {
        // 白名单优先
        if (isWhitelisted(value)) {
            return true;
        }

        // 检查黑名单
        return !isBlacklisted(value);
    }

    /**
     * 添加黑名单条目
     * 
     * @param value       值
     * @param matchType   匹配类型
     * @param description 描述
     * @return 保存的条目
     */
    @Transactional
    public BlackWhiteList addToBlacklist(String value, BlackWhiteList.MatchType matchType, String description) {
        BlackWhiteList item = BlackWhiteList.createBlacklist(value, matchType);
        item.setDescription(description);
        item.setCreatedBy("SYSTEM");
        item.setUpdatedBy("SYSTEM");

        BlackWhiteList saved = store.save(item);

        // 更新缓存
        blacklistCache.put(value, saved);

        log.info("Added to blacklist: {} ({})", value, matchType);
        return saved;
    }

    /**
     * 添加白名单条目
     * 
     * @param value       值
     * @param matchType   匹配类型
     * @param description 描述
     * @return 保存的条目
     */
    @Transactional
    public BlackWhiteList addToWhitelist(String value, BlackWhiteList.MatchType matchType, String description) {
        BlackWhiteList item = BlackWhiteList.createWhitelist(value, matchType);
        item.setDescription(description);
        item.setCreatedBy("SYSTEM");
        item.setUpdatedBy("SYSTEM");

        BlackWhiteList saved = store.save(item);

        // 更新缓存
        whitelistCache.put(value, saved);

        log.info("Added to whitelist: {} ({})", value, matchType);
        return saved;
    }

    /**
     * 从黑名单移除
     * 
     * @param value 值
     * @return 是否移除成功
     */
    @Transactional
    public boolean removeFromBlacklist(String value) {
        QueryCondition condition = QueryCondition.empty().eq("listType", BlackWhiteList.ListType.BLACKLIST)
                .eq("listValue", value);
        List<BlackWhiteList> items = store.findByCondition(condition);

        if (!items.isEmpty()) {
            store.delete(items.get(0));
            blacklistCache.remove(value);
            log.info("Removed from blacklist: {}", value);
            return true;
        }

        return false;
    }

    /**
     * 从白名单移除
     * 
     * @param value 值
     * @return 是否移除成功
     */
    @Transactional
    public boolean removeFromWhitelist(String value) {
        QueryCondition condition = QueryCondition.empty().eq("listType", BlackWhiteList.ListType.WHITELIST)
                .eq("listValue", value);
        List<BlackWhiteList> items = store.findByCondition(condition);

        if (!items.isEmpty()) {
            store.delete(items.get(0));
            whitelistCache.remove(value);
            log.info("Removed from whitelist: {}", value);
            return true;
        }

        return false;
    }

    /**
     * 获取所有黑名单
     * 
     * @return 黑名单列表
     */
    public List<BlackWhiteList> getAllBlacklist() {
        QueryCondition condition = QueryCondition.empty().eq("listType", BlackWhiteList.ListType.BLACKLIST)
                .eq("enabled", true).orderByAsc("priority");
        return store.findByCondition(condition);
    }

    /**
     * 获取所有白名单
     * 
     * @return 白名单列表
     */
    public List<BlackWhiteList> getAllWhitelist() {
        QueryCondition condition = QueryCondition.empty().eq("listType", BlackWhiteList.ListType.WHITELIST)
                .eq("enabled", true).orderByAsc("priority");
        return store.findByCondition(condition);
    }

    /**
     * 启用或禁用条目
     * 
     * @param id      条目ID
     * @param enabled 是否启用
     * @return 是否操作成功
     */
    @Transactional
    public boolean setEnabled(Long id, boolean enabled) {
        Optional<BlackWhiteList> itemOpt = store.findById(id);
        if (itemOpt.isPresent()) {
            BlackWhiteList item = itemOpt.get();
            item.setEnabled(enabled);
            store.save(item);

            // 更新缓存
            if (enabled) {
                if (item.getListType() == BlackWhiteList.ListType.BLACKLIST) {
                    blacklistCache.put(item.getListValue(), item);
                } else {
                    whitelistCache.put(item.getListValue(), item);
                }
            } else {
                if (item.getListType() == BlackWhiteList.ListType.BLACKLIST) {
                    blacklistCache.remove(item.getListValue());
                } else {
                    whitelistCache.remove(item.getListValue());
                }
            }

            log.info("{} list item: {} -> {}", enabled ? "Enabled" : "Disabled", item.getListValue(),
                    item.getListType());
            return true;
        }

        return false;
    }

    /**
     * 清理过期条目
     * 
     * @return 清理的数量
     */
    @Transactional
    public int cleanupExpired() {
        QueryCondition condition = QueryCondition.empty().lt("expireTime", LocalDateTime.now()).isNotNull("expireTime");
        int deleted = store.deleteByCondition(condition);
        if (deleted > 0) {
            // 重新加载缓存
            loadAllListsToCache();
            log.info("Cleaned up {} expired black/white list items", deleted);
        }
        return deleted;
    }

    /**
     * 重新加载缓存
     */
    public void reloadCache() {
        log.info("Reloading black/white list cache");
        loadAllListsToCache();
    }

    /**
     * 获取缓存统计信息
     * 
     * @return 统计信息
     */
    public String getCacheStats() {
        return String.format("Blacklist cache: %d, Whitelist cache: %d", blacklistCache.size(), whitelistCache.size());
    }
}
