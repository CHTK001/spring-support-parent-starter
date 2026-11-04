package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.mapper.SysAiAppKeyMapper;
import com.chua.starter.ai.support.service.SysAiAppKeyService;
import com.chua.starter.ai.support.util.KeyGenerator;
import com.chua.starter.ai.support.entity.SysAiAppKeyTopupLog;
import com.chua.starter.ai.support.mapper.SysAiAppKeyTopupLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;
import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_MIN;

/**
 * AI 应用密钥服务实现。
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiAppKeyServiceImpl implements SysAiAppKeyService {

    /**
     * 状态_已启用
    */
    private static final int STATUS_ENABLED = 1;
    /**
     * Millis_per_second
    */
    private static final long MILLIS_PER_SECOND = 1000L;
    /**
     * Seconds_per_day
    */
    private static final long SECONDS_PER_DAY = 24L * 60L * 60L;

    /**
     * SYSAIAPP密钥映射器
    */
    private final SysAiAppKeyMapper sysAiAppKeyMapper;
    /**
     * 充值/消费流水映射器
     */
    private final SysAiAppKeyTopupLogMapper sysAiAppKeyTopupLogMapper;

    @Override
    @Cacheable(
            cacheManager = REDIS_CACHE_MIN,
            cacheNames = REDIS_CACHE_MIN,
            key = "'sys:ai:app-key:' + #appKey",
            keyGenerator = "customTenantedKeyGenerator",
            unless = "#result == null"
    )
    /**
     * 获取byappKey
     *
     * @param appKey appKey
     * @return 获取byappKey的结果
     */
    public SysAiAppKey getByAppKey(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return null;
        }
        return sysAiAppKeyMapper.selectByAppKey(appKey);
    }

    @Override
    public SysAiAppKey getByAppKeyLive(String appKey) {
        return queryAppKeyUncached(appKey);
    }

    @Override
    /**
     * 列表
     * @return 返回值
    */
    public List<SysAiAppKey> list() {
        return sysAiAppKeyMapper.selectList(
                Wrappers.lambdaQuery(SysAiAppKey.class)
                        .eq(SysAiAppKey::getDeleted, 0)
                        .orderByDesc(SysAiAppKey::getId)
        );
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true)
    /**
     * 保存
     *
     * @param entity 实体
     * @return 保存的结果
     */
    public SysAiAppKey save(SysAiAppKey entity) {
        // 自动生成 sk- 格式密钥
        if (StringUtils.isBlank(entity.getAppKey())) {
            entity.setAppKey(KeyGenerator.generateKey());
        }
        if (StringUtils.isBlank(entity.getSecret())) {
            entity.setSecret(entity.getAppKey());
        }
        entity.setStatus(entity.getStatus() == null ? 1 : entity.getStatus());
        entity.setDeleted(entity.getDeleted() == null ? 0 : entity.getDeleted());
        entity.setUseCount(entity.getUseCount() == null ? 0 : entity.getUseCount());
        sysAiAppKeyMapper.insert(entity);
        return entity;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true)
    /**
     * 更新
     *
     * @param entity 实体
     * @return 更新的结果
     */
    public boolean update(SysAiAppKey entity) {
        return sysAiAppKeyMapper.updateById(entity) > 0;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true)
    /**
     * 删除byid
     *
     * @param id 标识
     * @return 删除byid的结果
     */
    public boolean deleteById(Integer id) {
        if (id == null) {
            return false;
        }
 // 使用 mybatis-Plus 内置 删除byid：@table逻辑 逻辑删除（设置 删除=1）
        return sysAiAppKeyMapper.deleteById(id) > 0;
    }

    @Override
    /**
     * 验证
     * @param appKey 字符串
     * @param ip 字符串
     * @param secret 字符串
     * @return 返回值
    */
    public boolean verify(String appKey, String secret, String ip) {
        SysAiAppKey dbAppKey = queryAppKeyUncached(appKey);
        if (!isValidAppKey(dbAppKey, ip)) {
            return false;
        }
        return StringUtils.equals(secret, dbAppKey.getSecret());
    }

    @Override
    /**
     * 验证appKeyonly
     * @param appKey 字符串
     * @param ip 字符串
     * @return 返回值
    */
    public boolean verifyAppKeyOnly(String appKey, String ip) {
        return isValidAppKey(queryAppKeyUncached(appKey), ip);
    }

    /**
     * 绕过缓存实时查询 appKey（限流/配额检查需要最新 DB 值）。
     *
     * @param appKey appKey
     * @return AppKey 记录；不存在返回 空
     */
    private SysAiAppKey queryAppKeyUncached(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return null;
        }
        return sysAiAppKeyMapper.selectByAppKey(appKey);
    }

    /**
     * 校验 appKey 记录自身有效性（存在、启用、未过期、次数上限、Token 总量、IP 白名单）。
     *
     * <p>判定本身交给 {@link AppKeyQuotaGate}：这是与「服务实例端口」共用的唯一口径，
     * 两处各写一遍曾导致「网关永不过期、实例已过期」这类不一致。</p>
     *
     * @param dbAppKey appKey 记录，可为 null
     * @param ip       客户端 IP
     * @return 是否有效
     */
    private boolean isValidAppKey(SysAiAppKey dbAppKey, String ip) {
        AppKeyQuotaGate.Deny deny = AppKeyQuotaGate.check(dbAppKey, ip);
        if (!deny.denied()) {
            return true;
        }
        log.warn("[SysAiAppKey] AppKey {} 校验未通过: {}", dbAppKey != null ? dbAppKey.getAppKey() : null, deny);
        return false;
    }

    @Override
    @Async("taskExecutor")
    /**
     * 更新use信息byappKey
     *
     * @param appKey appKey
     */
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
    /**
     * 更新令牌配额byappKey
     *
     * @param appKey appKey
     * @param tokens 令牌
     */
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
    /**
     * 获取分组idbyappKey
     *
     * @param appKey appKey
     * @return 获取群体idbyappKey的结果
     */
    public Integer getGroupIdByAppKey(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return null;
        }
        var wrapper = Wrappers.lambdaQuery(SysAiAppKey.class)
                .select(SysAiAppKey::getGroupId)
                .eq(SysAiAppKey::getAppKey, appKey)
                .eq(SysAiAppKey::getDeleted, 0)
                .last("LIMIT 1");
        SysAiAppKey result = sysAiAppKeyMapper.selectOne(wrapper);
        return result != null ? result.getGroupId() : null;
    }

    

    /**
     * 是否已过期（历史私有入口，判定已收口到 {@link AppKeyQuotaGate#expired}）。
     *
     * @param appKey appKey
     * @return 是否expired的结果
     */
    private boolean isExpired(SysAiAppKey appKey) {
        return AppKeyQuotaGate.expired(appKey, System.currentTimeMillis());
    }

    @Override
    public BigDecimal getBalance(String appKey) {
        SysAiAppKey entity = queryAppKeyUncached(appKey);
        return entity != null && entity.getBalance() != null ? entity.getBalance() : BigDecimal.ZERO;
    }

    @Override
    public boolean isBillable(String appKey) {
        SysAiAppKey entity = queryAppKeyUncached(appKey);
        AppKeyQuotaGate.Deny deny = AppKeyQuotaGate.checkBillable(entity);
        if (deny.denied()) {
            log.warn("[SysAiAppKey] AppKey {} 余额不足({}) 且不允许透支，拒绝计量计费请求",
                    appKey, entity != null ? entity.getBalance() : null);
            return false;
        }
        return true;
    }

    /**
     * 能否扣费（辅助方法）：余额为负且不允许透支 = 不可扣费。
     */
    private boolean canDeduct(BigDecimal balance, Integer overdraftEnabled) {
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }
        if (balance.compareTo(BigDecimal.ZERO) < 0 && !Boolean.TRUE.equals(overdraftEnabled)) {
            return false;
        }
        return true;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_MIN, cacheNames = REDIS_CACHE_MIN, key = "'sys:ai:app-key:' + #appKey")
    @Transactional(rollbackFor = Exception.class)
    public int recharge(String appKey, BigDecimal amount, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        SysAiAppKey entity = queryAppKeyUncached(appKey);
        if (entity == null) {
            return 0;
        }
        BigDecimal balanceBefore = entity.getBalance() != null ? entity.getBalance() : BigDecimal.ZERO;
        BigDecimal balanceAfter = balanceBefore.add(amount);
        int updated = sysAiAppKeyMapper.update(
                null,
                Wrappers.lambdaUpdate(SysAiAppKey.class)
                        .eq(SysAiAppKey::getAppKey, appKey)
                        .set(SysAiAppKey::getBalance, balanceAfter));
        if (updated > 0) {
            appendTopupLog(entity, appKey, SysAiAppKeyTopupLog.Types.RECHARGE, amount,
                    balanceBefore, balanceAfter, remark);
        }
        return updated;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_MIN, cacheNames = REDIS_CACHE_MIN, key = "'sys:ai:app-key:' + #appKey")
    @Transactional(rollbackFor = Exception.class)
    public int deduct(String appKey, BigDecimal amount, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        SysAiAppKey entity = queryAppKeyUncached(appKey);
        if (entity == null) {
            return 0;
        }
        BigDecimal balanceBefore = entity.getBalance() != null ? entity.getBalance() : BigDecimal.ZERO;
        if (!canDeduct(balanceBefore, entity.getOverdraftEnabled())) {
            log.warn("[SysAiAppKey] AppKey {} 余额不足({}) 且不允许透支，拒绝扣费", entity.getAppKey(), balanceBefore);
            return 0;
        }
        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        int updated = sysAiAppKeyMapper.update(
                null,
                Wrappers.lambdaUpdate(SysAiAppKey.class)
                        .eq(SysAiAppKey::getAppKey, appKey)
                        .set(SysAiAppKey::getBalance, balanceAfter)
                        .set(SysAiAppKey::getTotalCost, entity.getTotalCost() != null
                                ? entity.getTotalCost().add(amount) : amount));
        if (updated > 0) {
            appendTopupLog(entity, appKey, SysAiAppKeyTopupLog.Types.CONSUME, amount.negate(),
                    balanceBefore, balanceAfter, remark);
        }
        return updated;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_MIN, cacheNames = REDIS_CACHE_MIN, key = "'sys:ai:app-key:' + #appKey")
    public boolean updatePlan(String appKey, Long planId) {
        return sysAiAppKeyMapper.update(
                null,
                Wrappers.lambdaUpdate(SysAiAppKey.class)
                        .eq(SysAiAppKey::getAppKey, appKey)
                        .set(SysAiAppKey::getPlanId, planId)) > 0;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_MIN, cacheNames = REDIS_CACHE_MIN, key = "'sys:ai:app-key:' + #appKey")
    public boolean updateOverdraft(String appKey, boolean enabled) {
        return sysAiAppKeyMapper.update(
                null,
                Wrappers.lambdaUpdate(SysAiAppKey.class)
                        .eq(SysAiAppKey::getAppKey, appKey)
                        .set(SysAiAppKey::getOverdraftEnabled, enabled ? 1 : 0)) > 0;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_MIN, cacheNames = REDIS_CACHE_MIN, key = "'sys:ai:app-key:' + #appKey")
    public boolean updateRate(String appKey, BigDecimal minuteRate) {
        return sysAiAppKeyMapper.update(
                null,
                Wrappers.lambdaUpdate(SysAiAppKey.class)
                        .eq(SysAiAppKey::getAppKey, appKey)
                        .set(SysAiAppKey::getMinuteRate, minuteRate)) > 0;
    }

    @Override
    public List<SysAiAppKeyTopupLog> topupLogs(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return Collections.emptyList();
        }
        return sysAiAppKeyTopupLogMapper.selectByAppKey(appKey);
    }

    /**
     * 向流水表追加一条账目记录。
     *
     * @param entity        appKey 实体
     * @param appKey        appKey
     * @param type          流水类型
     * @param amount        金额
     * @param balanceBefore 变动前余额
     * @param balanceAfter  变动后余额
     * @param remark        备注
     */
    private void appendTopupLog(SysAiAppKey entity, String appKey, String type, BigDecimal amount,
                                BigDecimal balanceBefore, BigDecimal balanceAfter, String remark) {
        SysAiAppKeyTopupLog topupLog = new SysAiAppKeyTopupLog();
        topupLog.setAppKeyId(entity.getId().longValue());
        topupLog.setAppKey(appKey);
        topupLog.setType(type);
        topupLog.setAmount(amount);
        topupLog.setBalanceBefore(balanceBefore);
        topupLog.setBalanceAfter(balanceAfter);
        topupLog.setRemark(remark);
        sysAiAppKeyTopupLogMapper.insert(topupLog);
    }
}
