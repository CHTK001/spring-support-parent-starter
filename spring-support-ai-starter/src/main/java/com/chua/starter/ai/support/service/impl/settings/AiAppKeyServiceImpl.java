package com.chua.starter.ai.support.service.impl.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiAppKey;
import com.chua.starter.ai.support.entity.SysAiAppKeyTopupLog;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import static com.chua.starter.common.support.constant.CacheConstant.REDIS_CACHE_ALWAYS;
import org.springframework.cache.annotation.CacheEvict;
import com.chua.starter.ai.support.entity.settings.SysAiGroup;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.mapper.SysAiAppKeyMapper;
import com.chua.starter.ai.support.mapper.SysAiAppKeyTopupLogMapper;
import com.chua.starter.ai.support.mapper.settings.AiGroupMapper;
import com.chua.starter.ai.support.model.AiAppKeyDTO;
import com.chua.starter.ai.support.model.AiAppKeyQuotaDTO;
import com.chua.starter.ai.support.service.settings.AiAppKeyService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * appKey 服务实现类。
 *
 * <p>面板层 AppKey 复用 {@link SysAiAppKey} 实体与 {@link SysAiAppKeyMapper}，
 * 与协议层共用同一张 {@code sys_ai_app_key} 表，消除双实体对账写的风险。</p>
 *
 * @since 2026-08-03
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAppKeyServiceImpl implements AiAppKeyService {

    /**
     * 时间格式化器
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * appKey 映射器（与协议层共用）
     */
    private final SysAiAppKeyMapper appKeyMapper;
    /**
     * 提供者映射器
    */
    private final SysAiProviderMapper providerMapper;
    /**
     * 分组映射器
    */
    private final AiGroupMapper groupMapper;
    /**
     * 充值/消费流水映射器
     */
    private final SysAiAppKeyTopupLogMapper topupLogMapper;

    /**
     * 查询 appKey：仅本人名下（管理员在业务数据侧与普通账号同权）。
     *
     * @return 操作结果
     */
    @Override
    public ReturnResult<List<AiAppKeyDTO>> list() {
        String scopeUserId = AiUserContext.canAccessOthersData() ? null : AiUserContext.requireUserId();
        List<SysAiAppKey> keys = appKeyMapper.selectList(
                        new LambdaQueryWrapper<SysAiAppKey>()
                                .eq(scopeUserId != null, SysAiAppKey::getUserId, scopeUserId)
                                .orderByDesc(SysAiAppKey::getId))
                .stream().toList();

        // providerId 是**供应商编码**（String，如 openai / agnes / onnx），
        // 字段名在正确实体上是 getName() 而不是 getProviderName()。
        Map<String, String> providerNames = providerMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysAiProvider::getProviderId, SysAiProvider::getName, (a, b) -> a));
        Map<Integer, String> groupNames = resolveGroupNames();

        return ReturnResult.ok(keys.stream()
                .map(k -> toDto(k, providerNames.get(k.getProviderId()),
                        groupNames.get(k.getGroupId())))
                .collect(Collectors.toList()));
    }

    /**
     * 解析全部分组名称映射。
     *
     * @return groupId → 群体名称 映射
     */
    private Map<Integer, String> resolveGroupNames() {
        return groupMapper.selectList(null).stream()
                .collect(Collectors.toMap(g -> g.getGroupId().intValue(), SysAiGroup::getGroupName, (a, b) -> a));
    }

    /**
     * 新建 appKey。
     *
     * @param dto appKey DTO
     * @return 操作结果
     */
    @Override
    @Transactional
    @CacheEvict(cacheManager = REDIS_CACHE_ALWAYS, cacheNames = REDIS_CACHE_ALWAYS, allEntries = true)
    public ReturnResult<AiAppKeyDTO> create(AiAppKeyDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            return ReturnResult.illegal("AppKey 名称不能为空");
        }
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        SysAiAppKey entity = toEntity(dto);
        entity.setUserId(AiUserContext.requireUserId());
        LocalDateTime now = LocalDateTime.now();
        if (entity.getAppKey() == null || entity.getAppKey().isBlank()) {
            // Key 值由服务端签发：前端生成的串既可能撞车，也可能短得离谱。
            // 形如 sk-<24 hex>，服务实例与网关都用它做 Bearer 凭证。
            String generated = "sk-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            entity.setAppKey(generated);
            entity.setSecret(generated);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getDeleted() == null) {
            entity.setDeleted(0);
        }
        if (entity.getUseCount() == null) {
            entity.setUseCount(0L);
        }
        appKeyMapper.insert(entity);
        log.debug("[SysAiAppKey] 新建成功: id={}, name={}", entity.getId(), entity.getName());
        return ReturnResult.ok(toDto(entity, resolveProvider(entity.getProviderId()),
                resolveGroup(entity.getGroupId())));
    }

    /**
     * 更新 appKey。
     *
     * @param appKeyId appKey 主键
     * @param dto      appKey DTO
     * @return 操作结果
     */
    @Override
    @Transactional
    public ReturnResult<AiAppKeyDTO> update(Long appKeyId, AiAppKeyDTO dto) {
        if (appKeyId == null) {
            return ReturnResult.illegal("AppKey ID 不能为空");
        }
        requireWritableAppKey(appKeyId);
        SysAiAppKey entity = toEntity(dto);
        entity.setId(appKeyId.intValue());
        // 显式 set 而不是 updateById：updateById 会跳过 null，导致「清空分组 / 去掉模型限制 /
        // 改为永不过期」这三类操作静默失效（前端提交了空值，库里还留着老值）。
        LambdaUpdateWrapper<SysAiAppKey> wrapper = new LambdaUpdateWrapper<SysAiAppKey>()
                .eq(SysAiAppKey::getId, appKeyId.intValue())
                .set(SysAiAppKey::getName, entity.getName())
                .set(SysAiAppKey::getProviderId, entity.getProviderId())
                .set(SysAiAppKey::getGroupId, entity.getGroupId())
                .set(SysAiAppKey::getModel, entity.getModel())
                .set(SysAiAppKey::getMaxUse, entity.getMaxUse())
                .set(SysAiAppKey::getMaxTokens, entity.getMaxTokens())
                .set(SysAiAppKey::getIpWhitelist, entity.getIpWhitelist())
                .set(SysAiAppKey::getRemark, entity.getRemark())
                .set(SysAiAppKey::getExpireDatetime, entity.getExpireDatetime());
        if (dto.getOverdraftEnabled() != null) {
            // 只在显式提交时写：老客户端不带这个字段，不能被这次保存悄悄改成「不允许透支」
            wrapper.set(SysAiAppKey::getOverdraftEnabled, entity.getOverdraftEnabled());
        }
        if (entity.getAppKey() != null && !entity.getAppKey().isBlank()) {
            // Key 值不可清空：改动等于换一把钥匙，仅在显式提交新值时才写
            wrapper.set(SysAiAppKey::getAppKey, entity.getAppKey())
                    .set(SysAiAppKey::getSecret, entity.getSecret());
        }
        if (dto.getEnabled() != null) {
            wrapper.set(SysAiAppKey::getEnabled, entity.getEnabled())
                    .set(SysAiAppKey::getStatus, entity.getStatus());
        }
        appKeyMapper.update(null, wrapper);
        SysAiAppKey updated = appKeyMapper.selectById(appKeyId.intValue());
        return ReturnResult.ok(toDto(updated, resolveProvider(updated.getProviderId()),
                resolveGroup(updated.getGroupId())));
    }

    /**
     * 删除 appKey（逻辑删除）。
     *
     * @param appKeyId appKey 主键
     * @return 操作结果
     */
    @Override
    @Transactional
    public ReturnResult<Void> delete(Long appKeyId) {
        if (appKeyId == null) {
            return ReturnResult.illegal("AppKey ID 不能为空");
        }
        requireWritableAppKey(appKeyId);
        appKeyMapper.deleteById(appKeyId.intValue());
        log.debug("[SysAiAppKey] 删除成功: id={}", appKeyId);
        return ReturnResult.ok();
    }

    /**
     * 批量删除 appKey（逻辑删除）。
     *
     * <p>与 {@link #delete(Long)} 同口径：写出口归属守卫整批过完后才删，
     * 有问题的行直接拦截，不出现部分删除。</p>
     *
     * @param appKeyIds appKey 主键列表
     * @return 实际删除的 appKey 数量
     */
    @Override
    @Transactional
    public int batchDelete(List<Long> appKeyIds) {
        if (appKeyIds == null || appKeyIds.isEmpty()) {
            return 0;
        }
        for (Long appKeyId : appKeyIds) {
            if (appKeyId == null || appKeyId <= 0) {
                continue;
            }
            requireWritableAppKey(appKeyId);
        }

        int deleted = 0;
        for (Long appKeyId : appKeyIds) {
            if (appKeyId == null || appKeyId <= 0) {
                continue;
            }
            int removed = appKeyMapper.deleteById(appKeyId.intValue());
            if (removed > 0) {
                deleted++;
            }
            log.debug("[SysAiAppKey] 批量删除成功: id={}", appKeyId);
        }
        return deleted;
    }

    /**
     * 切换 appKey 启用状态。
     *
     * @param appKeyId appKey 主键
     * @param enabled  是否启用
     * @return 操作结果
     */
    @Override
    @Transactional
    public ReturnResult<AiAppKeyDTO> toggle(Long appKeyId, Boolean enabled) {
        if (appKeyId == null) {
            return ReturnResult.illegal("AppKey ID 不能为空");
        }
        requireWritableAppKey(appKeyId);
        LambdaUpdateWrapper<SysAiAppKey> wrapper = new LambdaUpdateWrapper<SysAiAppKey>()
                .eq(SysAiAppKey::getId, appKeyId.intValue())
                .set(SysAiAppKey::getStatus, Boolean.TRUE.equals(enabled) ? 1 : 0)
                .set(SysAiAppKey::getEnabled, enabled);
        appKeyMapper.update(null, wrapper);
        SysAiAppKey entity = appKeyMapper.selectById(appKeyId.intValue());
        return ReturnResult.ok(toDto(entity, resolveProvider(entity.getProviderId()),
                resolveGroup(entity.getGroupId())));
    }

    /**
     * 查询 appKey 余额。
     *
     * @param appKeyId appKey 主键
     * @return 操作结果（data 为余额）
     */
    @Override
    public ReturnResult<BigDecimal> getBalance(Long appKeyId) {
        requireWritableAppKey(appKeyId);
        SysAiAppKey entity = appKeyMapper.selectById(appKeyId.intValue());
        return ReturnResult.ok(entity != null && entity.getBalance() != null
                ? entity.getBalance() : BigDecimal.ZERO);
    }

    /**
     * 充值（增加余额 + 记 RECHARGE 流水）。
     *
     * @param appKeyId appKey 主键
     * @param amount   充值金额
     * @param remark   备注
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<AiAppKeyDTO> recharge(Long appKeyId, BigDecimal amount, String remark) {
        SysAiAppKey entity = requireWritableAppKey(appKeyId);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ReturnResult.error("充值金额须正数");
        }
        // 自增而不是「读余额 + 算新值 + 整字段写回」：并发的扣费才不会把本次充值覆盖掉
        int updated = appKeyMapper.rechargeBalanceById(appKeyId.intValue(), amount);
        if (updated > 0) {
            // 同一事务内回读自己的写入：流水的前后余额才与真实账面对得上
            BigDecimal balanceAfter = currentBalance(appKeyId);
            topupLogMapper.insert(topupLog(entity, SysAiAppKeyTopupLog.Types.RECHARGE, amount,
                    balanceAfter.subtract(amount), balanceAfter, remark));
        }
        return updated > 0
                ? ReturnResult.ok(toDto(appKeyMapper.selectById(appKeyId.intValue()),
                resolveProvider(entity.getProviderId()),
                resolveGroup(entity.getGroupId())))
                : ReturnResult.error("充值失败");
    }

    /**
     * 扣减 appKey 余额（结算/调整，记 CONSUME 流水）。
     *
     * @param appKeyId appKey 主键
     * @param amount   扣减金额
     * @param remark   备注
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<AiAppKeyDTO> deduct(Long appKeyId, BigDecimal amount, String remark) {
        SysAiAppKey entity = requireWritableAppKey(appKeyId);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ReturnResult.error("扣减金额须正数");
        }
        // 「余额不为负 / 允许透支」的判断与扣减在同一条 SQL 内完成：并发下不会各自
        // 读到同一个变动前余额、把对方的扣减覆盖掉
        int updated = appKeyMapper.deductBalanceById(appKeyId.intValue(), amount);
        if (updated <= 0) {
            return ReturnResult.error("余额不足且不允许透支，无法扣减");
        }
        BigDecimal balanceAfter = currentBalance(appKeyId);
        topupLogMapper.insert(topupLog(entity, SysAiAppKeyTopupLog.Types.CONSUME, amount,
                balanceAfter.add(amount), balanceAfter, remark));
        return ReturnResult.ok(toDto(appKeyMapper.selectById(appKeyId.intValue()),
                resolveProvider(entity.getProviderId()),
                resolveGroup(entity.getGroupId())));
    }

    /**
     * 查询 appKey 充值/消费流水（按流水时间倒序，前 100 条）。
     *
     * @param appKeyId appKey 主键
     * @return 操作结果（data 为流水列表）
     */
    @Override
    public ReturnResult<List<SysAiAppKeyTopupLog>> topupLogs(Long appKeyId) {
        requireWritableAppKey(appKeyId);
        SysAiAppKey entity = appKeyMapper.selectById(appKeyId.intValue());
        if (entity == null) {
            return ReturnResult.error("AppKey 不存在");
        }
        List<SysAiAppKeyTopupLog> logs = topupLogMapper.selectByAppKey(entity.getAppKey());
        return ReturnResult.ok(logs != null ? logs : new ArrayList<>());
    }

    /**
     * 更新套餐（按主键）。
     *
     * @param appKeyId appKey 主键
     * @param planId   套餐 ID（0=无套餐）
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<AiAppKeyDTO> updatePlan(Long appKeyId, Long planId) {
        requireWritableAppKey(appKeyId);
        int updated = appKeyMapper.update(
                null,
                new LambdaUpdateWrapper<SysAiAppKey>()
                        .eq(SysAiAppKey::getId, appKeyId.intValue())
                        .set(SysAiAppKey::getPlanId, planId));
        SysAiAppKey entity = appKeyMapper.selectById(appKeyId.intValue());
        return updated > 0
                ? ReturnResult.ok(toDto(entity, resolveProvider(entity.getProviderId()),
                resolveGroup(entity.getGroupId())))
                : ReturnResult.error("套餐更新失败");
    }

    /**
     * 更新透支开关（按主键）。
     *
     * @param appKeyId appKey 主键
     * @param enabled  是否允许透支
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<AiAppKeyDTO> updateOverdraft(Long appKeyId, Boolean enabled) {
        requireWritableAppKey(appKeyId);
        int updated = appKeyMapper.update(
                null,
                new LambdaUpdateWrapper<SysAiAppKey>()
                        .eq(SysAiAppKey::getId, appKeyId.intValue())
                        .set(SysAiAppKey::getOverdraftEnabled, Boolean.TRUE.equals(enabled) ? 1 : 0));
        SysAiAppKey entity = appKeyMapper.selectById(appKeyId.intValue());
        return updated > 0
                ? ReturnResult.ok(toDto(entity, resolveProvider(entity.getProviderId()),
                resolveGroup(entity.getGroupId())))
                : ReturnResult.error("透支开关更新失败");
    }

    /**
     * 更新每分钟单价（按主键）。
     *
     * @param appKeyId   appKey 主键
     * @param minuteRate 每分钟单价
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<AiAppKeyDTO> updateRate(Long appKeyId, BigDecimal minuteRate) {
        requireWritableAppKey(appKeyId);
        if (minuteRate == null || minuteRate.compareTo(BigDecimal.ZERO) < 0) {
            return ReturnResult.error("每分钟单价须正数");
        }
        int updated = appKeyMapper.update(
                null,
                new LambdaUpdateWrapper<SysAiAppKey>()
                        .eq(SysAiAppKey::getId, appKeyId.intValue())
                        .set(SysAiAppKey::getMinuteRate, minuteRate));
        SysAiAppKey entity = appKeyMapper.selectById(appKeyId.intValue());
        return updated > 0
                ? ReturnResult.ok(toDto(entity, resolveProvider(entity.getProviderId()),
                resolveGroup(entity.getGroupId())))
                : ReturnResult.error("单价更新失败");
    }

    /**
     * 调整额度：改次数/Token 上限，并可清零已用。
     *
     * <p>上限用「{@code null} 不改动 / {@code 0} 不限 / 正数新上限」三态表达，是因为
     * MyBatis-Plus 的 {@code updateById} 会跳过 null 字段：想让「改为不限」生效，必须显式
     * set SQL NULL，否则用户把上限清空后库里还留着老值，看起来改了其实没改。</p>
     *
     * @param appKeyId appKey 主键
     * @param quota    额度调整请求
     * @return 操作结果（data 为更新后的 appKey DTO）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<AiAppKeyDTO> updateQuota(Long appKeyId, AiAppKeyQuotaDTO quota) {
        if (quota == null) {
            return ReturnResult.illegal("额度参数不能为空");
        }
        requireWritableAppKey(appKeyId);
        LambdaUpdateWrapper<SysAiAppKey> wrapper = new LambdaUpdateWrapper<SysAiAppKey>()
                .eq(SysAiAppKey::getId, appKeyId.intValue());
        if (quota.getMaxUse() != null) {
            wrapper.setSql(quota.getMaxUse() > 0
                    ? "sys_ai_app_key_max_use = " + quota.getMaxUse()
                    : "sys_ai_app_key_max_use = NULL");
        }
        if (quota.getMaxTokens() != null) {
            wrapper.setSql(quota.getMaxTokens() > 0
                    ? "sys_ai_app_key_max_tokens = " + quota.getMaxTokens()
                    : "sys_ai_app_key_max_tokens = NULL");
        }
        if (Boolean.TRUE.equals(quota.getResetUsed())) {
            wrapper.set(SysAiAppKey::getUseCount, 0L)
                    .set(SysAiAppKey::getTotalTokens, 0L);
        }
        appKeyMapper.update(null, wrapper);
        SysAiAppKey entity = appKeyMapper.selectById(appKeyId.intValue());
        return ReturnResult.ok(toDto(entity, resolveProvider(entity.getProviderId()),
                resolveGroup(entity.getGroupId())));
    }

    /**
     * 回读当前余额（同一事务内读到自己刚写入的值）。
     *
     * <p>流水里的「变动前 / 变动后余额」必须是账面上的真实值，而不是应用侧算出来的拟值：
     * 余额已被原子 SQL 改动后，只有回读才能保证流水与余额对得上。</p>
     *
     * @param appKeyId appKey 主键
     * @return 当前余额，行不存在或为空时返回 0
     */
    private BigDecimal currentBalance(Long appKeyId) {
        SysAiAppKey latest = appKeyMapper.selectById(appKeyId.intValue());
        return latest != null && latest.getBalance() != null ? latest.getBalance() : BigDecimal.ZERO;
    }

    /**
     * 组装一条充值/消费流水记录。
     *
     * @param entity        appKey 实体
     * @param type          流水类型
     * @param amount        金额
     * @param balanceBefore 变动前余额
     * @param balanceAfter  变动后余额
     * @param remark        备注
     * @return 流水记录
     */
    private SysAiAppKeyTopupLog topupLog(SysAiAppKey entity, String type, BigDecimal amount,
                                         BigDecimal balanceBefore, BigDecimal balanceAfter, String remark) {
        SysAiAppKeyTopupLog record = new SysAiAppKeyTopupLog();
        record.setAppKeyId(entity.getId().longValue());
        record.setAppKey(entity.getAppKey());
        record.setType(type);
        record.setAmount(amount);
        record.setBalanceBefore(balanceBefore);
        record.setBalanceAfter(balanceAfter);
        record.setRemark(remark);
        return record;
    }

    /**
     * 写出口归属校验：只认归属人本身，管理员对他人数据只读。
     *
     * @param appKeyId appKey 主键
     * @return 校验通过的 appKey
     */
    private SysAiAppKey requireWritableAppKey(Long appKeyId) {
        SysAiAppKey existing = appKeyMapper.selectById(appKeyId.intValue());
        if (existing == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "AppKey 不存在");
        }
        if (AiUserContext.isReadonly()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "只读账号不允许执行写操作");
        }
        String userId = AiUserContext.requireUserId();
        String owner = existing.getUserId();
        if (owner == null || owner.isBlank()) {
            // 无归属的历史行视为无主：任何登录账号（含管理员）都不能改写，避免被随意接管
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人 AppKey");
        }
        if (!userId.equals(owner)) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "无权访问他人 AppKey");
        }
        return existing;
    }

    /**
     * 解析供应商名称。
     *
     * @param providerId 供应商 标识
     * @return 供应商名称
     */
    private String resolveProvider(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }
        return Optional.ofNullable(providerMapper.selectById(providerId))
                .map(SysAiProvider::getName)
                .orElse(null);
    }

    /**
     * 解析分组名称。
     *
     * @param groupId 分组 标识
     * @return 分组名称
     */
    private String resolveGroup(Integer groupId) {
        if (groupId == null) {
            return null;
        }
        return Optional.ofNullable(groupMapper.selectById(groupId))
                .map(SysAiGroup::getGroupName)
                .orElse(null);
    }

    /**
     * 实体转 DTO。
     *
     * @param entity       appKey 实体
     * @param providerName 供应商名称
     * @param groupName    分组名称
     * @return AppKey DTO
     */
    private AiAppKeyDTO toDto(SysAiAppKey entity, String providerName, String groupName) {
        if (entity == null) {
            return null;
        }
        AiAppKeyDTO dto = new AiAppKeyDTO();
        dto.setId(entity.getId() != null ? entity.getId().longValue() : null);
        dto.setName(entity.getName());
        dto.setKey(entity.getAppKey());
        dto.setProvider(entity.getProviderId());
        dto.setProviderName(providerName);
        dto.setModel(entity.getModel());
        dto.setGroupId(entity.getGroupId() != null ? entity.getGroupId().longValue() : null);
        dto.setGroupName(groupName);
        dto.setUseCount(entity.getUseCount());
        dto.setMaxUse(entity.getMaxUse());
        dto.setBalance(entity.getBalance());
        dto.setPlanId(entity.getPlanId());
        dto.setOverdraftEnabled(entity.getOverdraftEnabled() != null && entity.getOverdraftEnabled() == 1);
        dto.setMinuteRate(entity.getMinuteRate());
        dto.setTotalCost(entity.getTotalCost());
        dto.setExpireTime(entity.getExpireDatetime() != null
                ? entity.getExpireDatetime().format(FORMATTER) : null);
        dto.setEnabled(entity.getEnabled() != null
                ? entity.getEnabled()
                : entity.getStatus() != null && entity.getStatus() == 1);
        dto.setMaxTokens(entity.getMaxTokens());
        dto.setTotalTokens(entity.getTotalTokens());
        dto.setIpWhitelist(entity.getIpWhitelist());
        dto.setRemark(entity.getRemark());
        dto.setLastUsedTime(entity.getLastUsedTime() != null
                ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(entity.getLastUsedTime()),
                java.time.ZoneId.systemDefault()).format(FORMATTER)
                : null);
        dto.setCreatedAt(entity.getCreateTime() != null ? entity.getCreateTime().format(FORMATTER) : null);
        dto.setOwner(entity.getUserId());
        return dto;
    }

    /**
     * DTO 转实体。
     *
     * @param dto appKey DTO
     * @return AppKey 实体
     */
    private SysAiAppKey toEntity(AiAppKeyDTO dto) {
        SysAiAppKey entity = new SysAiAppKey();
        entity.setName(dto.getName());
        if (dto.getKey() != null && !dto.getKey().isBlank()) {
            entity.setAppKey(dto.getKey());
            entity.setSecret(dto.getKey());
        }
        entity.setProviderId(dto.getProvider());
        entity.setModel(dto.getModel());
        if (dto.getGroupId() != null) {
            entity.setGroupId(dto.getGroupId().intValue());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
            entity.setStatus(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        }
        entity.setUseCount(dto.getUseCount());
        entity.setMaxUse(dto.getMaxUse());
        entity.setMaxTokens(dto.getMaxTokens());
        entity.setIpWhitelist(dto.getIpWhitelist());
        entity.setRemark(dto.getRemark());
        entity.setBalance(dto.getBalance());
        entity.setPlanId(dto.getPlanId());
        entity.setOverdraftEnabled(Boolean.TRUE.equals(dto.getOverdraftEnabled()) ? 1 : 0);
        entity.setMinuteRate(dto.getMinuteRate());
        entity.setTotalCost(dto.getTotalCost());
        if (dto.getExpireTime() != null && !dto.getExpireTime().isBlank()) {
            entity.setExpireDatetime(LocalDateTime.parse(dto.getExpireTime(), FORMATTER));
        } else if (dto.getMaxUse() == null) {
            entity.setExpireDatetime(null);
        }
        return entity;
    }
}
