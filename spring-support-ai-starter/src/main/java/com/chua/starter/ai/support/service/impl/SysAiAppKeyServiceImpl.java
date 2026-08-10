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
import org.springframework.cache.annotation.Caching;
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

    private static final int STATUS_ENABLED = 1;
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final long SECONDS_PER_DAY = 24L * 60L * 60L;

    private final SysAiAppKeyMapper sysAiAppKeyMapper;

    @Override
    @Cacheable(
            cacheManager = REDIS_CACHE_MIN,
            cacheNames = REDIS_CACHE_MIN,
            key = "'sys:ai:app-key:' + #appKey",
            keyGenerator = "customTenantedKeyGenerator",
            unless = "#result == null"
    )
    public SysAiAppKey getByAppKey(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return null;
        }
        return sysAiAppKeyMapper.selectByAppKey(appKey);
    }

    @Override
    public List<SysAiAppKey> list() {
        return sysAiAppKeyMapper.selectList(
                Wrappers.lambdaQuery(SysAiAppKey.class)
                        .eq(SysAiAppKey::getSysAiAppKeyDelete, 0)
                        .orderByDesc(SysAiAppKey::getSysAiAppKeyId)
        );
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true),
            @CacheEvict(cacheManager = REDIS_CACHE_MIN, cacheNames = REDIS_CACHE_MIN, allEntries = true)
    })
    public SysAiAppKey save(SysAiAppKey entity) {
        // 自动生成 sk- 格式密钥
        if (StringUtils.isBlank(entity.getSysAiAppKeyAppKey())) {
            entity.setSysAiAppKeyAppKey(KeyGenerator.generateKey());
        }
        if (StringUtils.isBlank(entity.getSysAiAppKeySecret())) {
            entity.setSysAiAppKeySecret(entity.getSysAiAppKeyAppKey());
        }
        entity.setSysAiAppKeyStatus(entity.getSysAiAppKeyStatus() == null ? 1 : entity.getSysAiAppKeyStatus());
        entity.setSysAiAppKeyDelete(entity.getSysAiAppKeyDelete() == null ? 0 : entity.getSysAiAppKeyDelete());
        entity.setSysAiAppKeyUseCount(entity.getSysAiAppKeyUseCount() == null ? 0 : entity.getSysAiAppKeyUseCount());
        sysAiAppKeyMapper.insert(entity);
        return entity;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true),
            @CacheEvict(cacheManager = REDIS_CACHE_MIN, cacheNames = REDIS_CACHE_MIN, allEntries = true)
    })
    public boolean update(SysAiAppKey entity) {
        return sysAiAppKeyMapper.updateById(entity) > 0;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true),
            @CacheEvict(cacheManager = REDIS_CACHE_MIN, cacheNames = REDIS_CACHE_MIN, allEntries = true)
    })
    public boolean deleteById(Integer id) {
        if (id == null) {
            return false;
        }
        SysAiAppKey entity = new SysAiAppKey();
        entity.setSysAiAppKeyId(id);
        entity.setSysAiAppKeyDelete(1);
        return sysAiAppKeyMapper.updateById(entity) > 0;
    }

    @Override
    public boolean verify(String appKey, String secret, String ip) {
        SysAiAppKey dbAppKey = getByAppKey(appKey);
        if (!isValidAppKey(dbAppKey, ip)) {
            return false;
        }
        return StringUtils.equals(secret, dbAppKey.getSysAiAppKeySecret());
    }

    @Override
    public boolean verifyAppKeyOnly(String appKey, String ip) {
        return isValidAppKey(getByAppKey(appKey), ip);
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
        if (!Integer.valueOf(STATUS_ENABLED).equals(dbAppKey.getSysAiAppKeyStatus())) {
            return false;
        }
        if (isExpired(dbAppKey)) {
            return false;
        }
        // 次数上限强制校验：达到 maxUse 后拒绝放行（配额限制）
        Long maxUse = dbAppKey.getSysAiAppKeyMaxUse();
        if (maxUse != null && maxUse > 0) {
            Long used = dbAppKey.getSysAiAppKeyUseCount() == null ? 0L : dbAppKey.getSysAiAppKeyUseCount();
            if (used >= maxUse) {
                log.warn("[SysAiAppKey] AppKey {} 已达次数上限({}/{})，拒绝访问",
                        dbAppKey.getSysAiAppKeyAppKey(), used, maxUse);
                return false;
            }
        }
        // Token 总量配额校验：达到 maxTokens 后拒绝放行
        Long maxTokens = dbAppKey.getSysAiAppKeyMaxTokens();
        if (maxTokens != null && maxTokens > 0) {
            Long totalTokens = dbAppKey.getSysAiAppKeyTotalTokens() == null ? 0L : dbAppKey.getSysAiAppKeyTotalTokens();
            if (totalTokens >= maxTokens) {
                log.warn("[SysAiAppKey] AppKey {} 已达 Token 配额上限({}/{})，拒绝访问",
                        dbAppKey.getSysAiAppKeyAppKey(), totalTokens, maxTokens);
                return false;
            }
        }
        String whitelist = dbAppKey.getSysAiAppKeyIpWhitelist();
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
    public void updateUseInfoByAppKey(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return;
        }
        try {
            // SQL 原子自增，避免并发下丢失更新
            sysAiAppKeyMapper.updateUseCount(appKey);
            // 更新最后使用时间
            SysAiAppKey updateTime = new SysAiAppKey();
            updateTime.setSysAiAppKeyAppKey(appKey);
            updateTime.setSysAiAppKeyLastUsedTime(System.currentTimeMillis());
            sysAiAppKeyMapper.update(
                    null,
                    Wrappers.lambdaUpdate(SysAiAppKey.class)
                            .eq(SysAiAppKey::getSysAiAppKeyAppKey, appKey)
                            .set(SysAiAppKey::getSysAiAppKeyLastUsedTime, System.currentTimeMillis()));
        } catch (Exception e) {
            log.debug("更新 AppKey 使用信息失败: {}", e.getMessage());
        }
    }

    @Override
    @Async("taskExecutor")
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
    public Integer getGroupIdByAppKey(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return null;
        }
        var wrapper = Wrappers.lambdaQuery(SysAiAppKey.class)
                .select(SysAiAppKey::getSysAiAppKeyGroupId)
                .eq(SysAiAppKey::getSysAiAppKeyAppKey, appKey)
                .eq(SysAiAppKey::getSysAiAppKeyDelete, 0)
                .last("LIMIT 1");
        SysAiAppKey result = sysAiAppKeyMapper.selectOne(wrapper);
        return result != null ? result.getSysAiAppKeyGroupId() : null;
    }

    /**
     * 在同一行补写 OpenAI 兼容鉴权列。
     *
     * @param id      面板 AppKey 主键（兼容列按 id 定位）
     * @param key     兼容鉴权密钥
     * @param status  状态：1 启用，0 禁用
     * @param groupId 分组 ID（可空）
     */
    @Override
    @Caching(evict = {
            @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true),
            @CacheEvict(cacheManager = REDIS_CACHE_MIN, cacheNames = REDIS_CACHE_MIN, allEntries = true)
    })
    public void updateCompatibleColumns(Long id, String key, Integer status, Integer groupId) {
        if (id == null || StringUtils.isBlank(key)) {
            return;
        }
        sysAiAppKeyMapper.update(
                null,
                Wrappers.lambdaUpdate(SysAiAppKey.class)
                        .eq(SysAiAppKey::getSysAiAppKeyId, id.intValue())
                        .set(SysAiAppKey::getSysAiAppKeyAppKey, key)
                        .set(SysAiAppKey::getSysAiAppKeySecret, key)
                        .set(SysAiAppKey::getSysAiAppKeyStatus, status)
                        .set(SysAiAppKey::getSysAiAppKeyDelete, 0)
                        .set(SysAiAppKey::getSysAiAppKeyGroupId, groupId)
                        .set(SysAiAppKey::getUpdateTime, java.time.LocalDateTime.now()));
    }

    /**
     * 同步面板记录的过期时间与次数上限为协议鉴权列。
     *
     * @param id                面板 AppKey 主键
     * @param expireEpochSecond 过期时间（epoch 秒），null 表示永不
     * @param maxUse            次数上限，null 表示不限
     */
    @Override
    @Caching(evict = {
            @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true),
            @CacheEvict(cacheManager = REDIS_CACHE_MIN, cacheNames = REDIS_CACHE_MIN, allEntries = true)
    })
    public void updateExpireCompatible(Long id, Long expireEpochSecond, Long maxUse) {
        if (id == null) {
            return;
        }
        sysAiAppKeyMapper.update(
                null,
                Wrappers.lambdaUpdate(SysAiAppKey.class)
                        .eq(SysAiAppKey::getSysAiAppKeyId, id.intValue())
                        .set(SysAiAppKey::getSysAiAppKeyExpireTime, expireEpochSecond)
                        .set(SysAiAppKey::getSysAiAppKeyMaxUse, maxUse)
                        .set(SysAiAppKey::getUpdateTime, java.time.LocalDateTime.now()));
    }

    private boolean isExpired(SysAiAppKey appKey) {
        Long expireTime = resolveExpireTime(appKey);
        if (expireTime == null) {
            return false;
        }
        return System.currentTimeMillis() / MILLIS_PER_SECOND > expireTime;
    }

    /**
     * 解析 AppKey 过期时间（epoch 秒）。
     *
     * <p>{@code sys_ai_app_key_expire_time} 统一存储 epoch 秒（由面板同步与种子写入约定），
     * 直接返回，不再做毫秒转换（避免单位不一致导致 Key 被误判为已过期）。</p>
     *
     * @param appKey AppKey 记录
     * @return 过期时间（epoch 秒），未设置时返回 null
     */
    private Long resolveExpireTime(SysAiAppKey appKey) {
        if (appKey == null || appKey.getSysAiAppKeyExpireTime() == null) {
            return null;
        }
        return appKey.getSysAiAppKeyExpireTime();
    }
}
