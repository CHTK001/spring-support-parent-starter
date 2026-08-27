package com.chua.starter.ai.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.entity.SysAiToken;
import com.chua.starter.ai.support.mapper.SysAiTokenMapper;
import com.chua.starter.ai.support.service.SysAiTokenService;
import com.chua.starter.ai.support.util.KeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;

/**
 * AI 令牌服务实现。
 * <p>
 * 负责 Token 的 CRUD、校验、分组绑定、用量统计和限流控制。
 *
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiTokenServiceImpl implements SysAiTokenService {

    /** Status_enabled */
    private static final int STATUS_ENABLED = 1;

    /** SYSAI令牌mapper */
    private final SysAiTokenMapper sysAiTokenMapper;

    @Override
    /** List */
    public List<SysAiToken> list() {
        return sysAiTokenMapper.selectList(
                Wrappers.lambdaQuery(SysAiToken.class)
                        .eq(SysAiToken::getDelete, 0)
                        .orderByDesc(SysAiToken::getId)
        );
    }

    @Override
    /** ListBy分组Id */
    public List<SysAiToken> listByGroupId(Integer groupId) {
        if (groupId == null) {
            return list();
        }
        return sysAiTokenMapper.selectList(
                Wrappers.lambdaQuery(SysAiToken.class)
                        .eq(SysAiToken::getGroupId, groupId)
                        .eq(SysAiToken::getDelete, 0)
                        .orderByDesc(SysAiToken::getId)
        );
    }

    @Override
    /** 保存 */
    public SysAiToken save(SysAiToken token) {
        if (StringUtils.isBlank(token.getKey())) {
            String rawKey = KeyGenerator.generateKey();
            token.setKey(rawKey);
            token.setKeyPrefix("sk-");
            token.setKeyHash(sha256(rawKey));
        }
        if (token.getStatus() == null) {
            token.setStatus(1);
        }
        if (token.getDelete() == null) {
            token.setDelete(0);
        }
        if (token.getUseCount() == null) {
            token.setUseCount(0L);
        }
        if (token.getTotalFee() == null) {
            token.setTotalFee(BigDecimal.ZERO);
        }
        sysAiTokenMapper.insert(token);
        return token;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true)
    /** 更新 */
    public boolean update(SysAiToken token) {
        return sysAiTokenMapper.updateById(token) > 0;
    }

    @Override
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true)
    /** 删除ById */
    public boolean deleteById(Integer tokenId) {
        if (tokenId == null) {
            return false;
        }
        SysAiToken entity = new SysAiToken();
        entity.setId(tokenId);
        entity.setDelete(1);
        return sysAiTokenMapper.updateById(entity) > 0;
    }

    @Override
    /** 查找ByTokenKey */
    public SysAiToken findByTokenKey(String tokenKey) {
        if (StringUtils.isBlank(tokenKey)) {
            return null;
        }
        String hash = sha256(tokenKey);
        return sysAiTokenMapper.selectOne(
                Wrappers.lambdaQuery(SysAiToken.class)
                        .eq(SysAiToken::getKeyHash, hash)
                        .eq(SysAiToken::getDelete, 0)
                        .last("LIMIT 1")
        );
    }

    @Override
    /** 验证 */
    public SysAiToken verify(String tokenKey, Integer groupId) {
        SysAiToken token = findByTokenKey(tokenKey);
        if (token == null) {
            return null;
        }
        // 状态检查
        if (!Integer.valueOf(STATUS_ENABLED).equals(token.getStatus())) {
            return null;
        }
        // 过期检查
        if (token.getExpireTime() != null
                && LocalDateTime.now().isAfter(token.getExpireTime())) {
            return null;
        }
        // 分组检查——Token 只能访问其绑定的分组
        if (groupId != null && !groupId.equals(token.getGroupId())) {
            return null;
        }
        return token;
    }

    @Override
    /** 获取分组IdByTokenKey */
    public Integer getGroupIdByTokenKey(String tokenKey) {
        SysAiToken token = findByTokenKey(tokenKey);
        return token != null ? token.getGroupId() : null;
    }

    @Override
    @Async("taskExecutor")
    /** RecordUsage */
    public void recordUsage(String tokenKey, int promptTokens, int completionTokens, BigDecimal cost) {
        SysAiToken token = findByTokenKey(tokenKey);
        if (token == null) {
            return;
        }
        // 累加使用次数和 token 数
        SysAiToken update = new SysAiToken();
        update.setId(token.getId());
        update.setUseCount(
                (token.getUseCount() == null ? 0 : token.getUseCount()) + 1
        );
        update.setTotalTokens(
                (token.getTotalTokens() == null ? 0 : token.getTotalTokens())
                        + promptTokens + completionTokens
        );
        update.setLastUsedTime(LocalDateTime.now());
        // 费用累加
        if (cost != null) {
            BigDecimal currentInputFee = token.getTotalInputFee() == null
                    ? BigDecimal.ZERO : token.getTotalInputFee();
            BigDecimal currentOutputFee = token.getTotalOutputFee() == null
                    ? BigDecimal.ZERO : token.getTotalOutputFee();
            BigDecimal currentTotal = token.getTotalFee() == null
                    ? BigDecimal.ZERO : token.getTotalFee();
            update.setTotalInputFee(currentInputFee.add(cost));
            update.setTotalOutputFee(currentOutputFee.add(cost));
            update.setTotalFee(currentTotal.add(cost));
            // 今日费用
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            if (!today.equals(token.getTodayDate())) {
                update.setTodayFee(cost);
                update.setTodayDate(today);
            } else {
                BigDecimal todayFee = token.getTodayFee() == null
                        ? BigDecimal.ZERO : token.getTodayFee();
                update.setTodayFee(todayFee.add(cost));
            }
        }
        sysAiTokenMapper.updateById(update);
    }

    @Override
    /** 校验RateLimit */
    public boolean checkRateLimit(String tokenKey) {
        SysAiToken token = findByTokenKey(tokenKey);
        if (token == null) {
            return false;
        }
        Integer rateLimit = token.getRateLimit();
        if (rateLimit == null || rateLimit <= 0) {
            // 无限流
            return true;
        }
        Integer rateCount = token.getRateCount() == null ? 0 : token.getRateCount();
        LocalDateTime windowStart = token.getRateWindowStart();
        LocalDateTime now = LocalDateTime.now();
        // 检查是否在新窗口
        if (windowStart == null || windowStart.plusMinutes(1).isBefore(now)) {
            // 新窗口：重置计数器
            SysAiToken update = new SysAiToken();
            update.setId(token.getId());
            update.setRateCount(1);
            update.setRateWindowStart(now);
            sysAiTokenMapper.updateById(update);
            return true;
        }
        if (rateCount < rateLimit) {
            // 窗口内未触发限流
            SysAiToken update = new SysAiToken();
            update.setId(token.getId());
            update.setRateCount(rateCount + 1);
            sysAiTokenMapper.updateById(update);
            return true;
        }
        // 触发限流
        return false;
    }

    @Override
    /** 校验RateLimitBy分组 */
    public boolean checkRateLimitByGroup(Integer groupId) {
        if (groupId == null) {
            return true;
        }
        List<SysAiToken> tokens = listByGroupId(groupId);
        if (tokens == null || tokens.isEmpty()) {
            // 分组未配置 Token：不阻断（未启用计量限流的分组放行）
            return true;
        }
        // 遍历启用 Token，直接使用已加载的实体检查限流，避免逐条再查库
        for (SysAiToken token : tokens) {
            if (token.getStatus() != null && token.getStatus() == 1
                    && token.getKey() != null
                    && !checkRateLimit(token)) {
                log.warn("[SysAiToken] 分组 {} 触达限流，拒绝请求", groupId);
                return false;
            }
        }
        return true;
    }

    /**
     * 基于已加载的 Token 实体检查限流，避免额外 DB 查询。
     *
     * <p>复用 {@link #listByGroupId(Integer)} 已加载的数据，直接检查限流计数器，
     * 无需再按 Token Key 逐条查库。将原 N 次 DB 查询降为 1 次。</p>
     *
     * @param token Token 实体（需包含 rateLimit/rateCount/rateWindowStart 字段）
     * @return true 表示未触发限流，可以继续请求
     */
    private boolean checkRateLimit(SysAiToken token) {
        Integer rateLimit = token.getRateLimit();
        if (rateLimit == null || rateLimit <= 0) {
            // 未配置限流，放行
            return true;
        }
        Integer rateCount = token.getRateCount() == null ? 0 : token.getRateCount();
        LocalDateTime windowStart = token.getRateWindowStart();
        LocalDateTime now = LocalDateTime.now();
        // 检查是否进入新的 1 分钟窗口
        if (windowStart == null || windowStart.plusMinutes(1).isBefore(now)) {
            // 新窗口：重置计数器为 1（本次请求）
            token.setRateCount(1);
            token.setRateWindowStart(now);
            persistRateLimit(token);
            return true;
        }
        if (rateCount < rateLimit) {
            // 窗口内未超限，递增计数
            token.setRateCount(rateCount + 1);
            persistRateLimit(token);
            return true;
        }
        // 窗口内已达上限，拒绝请求
        return false;
    }

    /**
     * 持久化限流计数到数据库。
     *
     * <p>将内存中的 {@link SysAiToken#getSysAiTokenRateCount()} 和
     * {@link SysAiToken#getSysAiTokenRateWindowStart()} 写回数据库，
     * 确保限流状态在重启后仍可延续。</p>
     *
     * @param token 更新后的 Token 实体
     */
    private void persistRateLimit(SysAiToken token) {
        try {
            SysAiToken update = new SysAiToken();
            update.setId(token.getId());
            update.setRateCount(token.getRateCount());
            update.setRateWindowStart(token.getRateWindowStart());
            sysAiTokenMapper.updateById(update);
        } catch (Exception e) {
            log.warn("[SysAiToken] 持久化限流计数失败: {}", e.getMessage());
        }
    }

    /**
     * SHA-256 哈希，用于 Token 安全存储。
     */
    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

