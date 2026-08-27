package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.mapper.SysAiAppKeyMapper;
import com.chua.starter.ai.support.service.SysAiAppKeyService;
import com.chua.starter.ai.support.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;
import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_MIN;

/**
 * AI 应用密钥服务实现。
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiAppKeyServiceImpl implements SysAiAppKeyService {

    /** Status_enabled */
    private static final int STATUS_ENABLED = 1;
    /** Millis_per_second */
    private static final long MILLIS_PER_SECOND = 1000L;
    /** Seconds_per_day */
    private static final long SECONDS_PER_DAY = 24L * 60L * 60L;

    /** SYSAIAPP密钥mapper */
    private final SysAiAppKeyMapper sysAiAppKeyMapper;

    @Override
    @Cacheable(
            cacheManager = REDIS_CACHE_MIN,
            cacheNames = REDIS_CACHE_MIN,
            key = "'sys:ai:app-key:' + #appKey",
            keyGenerator = "customTenantedKeyGenerator",
            unless = "#result == null"
    )
    /** 获取ByAppKey */
    public SysAiAppKey getByAppKey(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return null;
        }
        return sysAiAppKeyMapper.selectByAppKey(appKey);
    }

    @Override
    /** List */
    public List<SysAiAppKey> list() {
        return sysAiAppKeyMapper.selectList(
                Wrappers.lambdaQuery(SysAiAppKey.class)
                        .eq(SysAiAppKey::getDelete, 0)
                        .orderByDesc(SysAiAppKey::getId)
        );
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true)
    /** 保存 */
    public SysAiAppKey save(SysAiAppKey entity) {
        // 自动生成 sk- 格式密钥
        if (StringUtils.isBlank(entity.getAppKey())) {
            entity.setAppKey(KeyGenerator.generateKey());
        }
        if (StringUtils.isBlank(entity.getSecret())) {
            entity.setSecret(entity.getAppKey());
        }
        entity.setStatus(entity.getStatus() == null ? 1 : entity.getStatus());
        entity.setDelete(entity.getDelete() == null ? 0 : entity.getDelete());
        entity.setUseCount(entity.getUseCount() == null ? 0 : entity.getUseCount());
        sysAiAppKeyMapper.insert(entity);
        return entity;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true)
    /** 更新 */
    public boolean update(SysAiAppKey entity) {
        return sysAiAppKeyMapper.updateById(entity) > 0;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true)
    /** 删除ById */
    public boolean deleteById(Integer id) {
        if (id == null) {
            return false;
        }
        // 使用 MyBatis-Plus 内置 deleteById：@TableLogic 逻辑删除（SET delete=1）
        return sysAiAppKeyMapper.deleteById(id) > 0;
    }

    @Override
    /** 验证 */
    public boolean verify(String appKey, String secret, String ip) {
        SysAiAppKey dbAppKey = queryAppKeyUncached(appKey);
        if (!isValidAppKey(dbAppKey, ip)) {
            return false;
        }
        return StringUtils.equals(secret, dbAppKey.getSecret());
    }

    @Override
    /** 验证AppKeyOnly */
    public boolean verifyAppKeyOnly(String appKey, String ip) {
        return isValidAppKey(queryAppKeyUncached(appKey), ip);
    }

    /**
     * 绕过缓存实时查询 AppKey（限流/配额检查需要最新 DB 值）。
     *
     * @param appKey AppKey
     * @return AppKey 记录；不存在返回 null
     */
    private SysAiAppKey queryAppKeyUncached(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return null;
        }
        return sysAiAppKeyMapper.selectByAppKey(appKey);
    }

    /**
     * 校验 AppKey 记录自身有效性（存在、启用、未过期、次数上限、Token 配额、IP 白名单）。
     *
     * @param dbAppKey AppKey 记录
     * @param ip       客户端 IP
     * @return 是否有效
     */
    private boolean isValidAppKey(SysAiAppKey dbAppKey, String ip) {
        if (dbAppKey == null) {
            return false;
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(dbAppKey.getStatus())) {
            return false;
        }
        if (isExpired(dbAppKey)) {
            return false;
        }
        // 次数上限强制校验：达到 maxUse 后拒绝放行（配额限制）
        Long maxUse = dbAppKey.getMaxUse();
        if (maxUse != null && maxUse > 0) {
            Long used = dbAppKey.getUseCount() == null ? 0L : dbAppKey.getUseCount();
            if (used >= maxUse) {
                log.warn("[SysAiAppKey] AppKey {} 已达次数上限({}/{})，拒绝访问",
                        dbAppKey.getAppKey(), used, maxUse);
                return false;
            }
        }
        // Token 总量配额校验：达到 maxTokens 后拒绝放行
        Long maxTokens = dbAppKey.getMaxTokens();
        if (maxTokens != null && maxTokens > 0) {
            Long totalTokens = dbAppKey.getTotalTokens() == null ? 0L : dbAppKey.getTotalTokens();
            if (totalTokens >= maxTokens) {
                log.warn("[SysAiAppKey] AppKey {} 已达 Token 配额上限({}/{})，拒绝访问",
                        dbAppKey.getAppKey(), totalTokens, maxTokens);
                return false;
            }
        }
        String whitelist = dbAppKey.getIpWhitelist();
        if (StringUtils.isNotBlank(whitelist) && StringUtils.isNotBlank(ip)) {
            boolean allowed = Arrays.stream(whitelist.split(","))
                    .map(String::trim)
                    .anyMatch(it -> "*".equals(it) || it.equals(ip));
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Async("taskExecutor")
    /** 更新UseInfoByAppKey */
    public void updateUseInfoByAppKey(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return;
        }
        try {
            // SQL 原子自增，避免并发下丢失更新
            sysAiAppKeyMapper.updateUseCount(appKey);
            // 更新最后使用时间
            SysAiAppKey updateTime = new SysAiAppKey();
            updateTime.setAppKey(appKey);
            updateTime.setLastUsedTime(System.currentTimeMillis());
            sysAiAppKeyMapper.update(
                    null,
                    Wrappers.lambdaUpdate(SysAiAppKey.class)
                            .eq(SysAiAppKey::getAppKey, appKey)
                            .set(SysAiAppKey::getLastUsedTime, System.currentTimeMillis()));
        } catch (Exception e) {
            log.debug("更新 AppKey 使用信息失败: {}", e.getMessage());
        }
    }

    @Override
    @Async("taskExecutor")
    /** 更新TokenQuotaByAppKey */
    public void updateTokenQuotaByAppKey(String appKey, long tokens) {
        if (StringUtils.isBlank(appKey) || tokens <= 0) {
            return;
        }
        try {
            // SQL 原子自增，避免并发下丢失更新
            sysAiAppKeyMapper.addTokenQuota(appKey, tokens);
            log.debug("[SysAiAppKey] AppKey {} Token 累计更新: +{}", appKey, tokens);
        } catch (Exception e) {
            log.debug("更新 AppKey Token 配额失败: {}", e.getMessage());
        }
    }

    @Override
    @Cacheable(
            cacheManager = REDIS_CACHE_MIN,
            cacheNames = REDIS_CACHE_MIN,
            key = "'sys:ai:app-key-group:' + #appKey",
            unless = "#result == null"
    )
    /** 获取分组IdByAppKey */
    public Integer getGroupIdByAppKey(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return null;
        }
        var wrapper = Wrappers.lambdaQuery(SysAiAppKey.class)
                .select(SysAiAppKey::getGroupId)
                .eq(SysAiAppKey::getAppKey, appKey)
                .eq(SysAiAppKey::getDelete, 0)
                .last("LIMIT 1");
        SysAiAppKey result = sysAiAppKeyMapper.selectOne(wrapper);
        return result != null ? result.getGroupId() : null;
    }

    

    /** 是否Expired */
    private boolean isExpired(SysAiAppKey appKey) {
        Long expireTime = resolveExpireTime(appKey);
        if (expireTime == null) {
            return false;
        }
        return System.currentTimeMillis() / MILLIS_PER_SECOND > expireTime;
    }

    /** 解析ExpireTime */
    private Long resolveExpireTime(SysAiAppKey appKey) {
        if (appKey == null) {
            return null;
        }
        if (appKey.getExpireTime() != null) {
            return java.time.Instant.ofEpochMilli(appKey.getExpireTime())
                    .atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
        }
        return null;
    }
}
