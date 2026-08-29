package com.chua.starter.monitor.support.service.alarm.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMonitor;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmEvent;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmMaintenanceWindow;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmPolicy;
import com.chua.starter.monitor.support.entity.MsMonitorAlarmRule;
import com.chua.starter.monitor.support.mapper.MsMonitorAlarmEventMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorAlarmMaintenanceWindowMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorAlarmPolicyMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorAlarmRuleMapper;
import com.chua.starter.monitor.support.mapper.MsMonitorMapper;
import com.chua.starter.monitor.support.service.alarm.AlarmEngineService;
import com.chua.starter.monitor.support.service.alarm.NotifySender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 告警引擎服务实现
 * <p>
 * 评估流程（参考 orion-visor 思想，结构自研）：
 * <ol>
 *   <li>规则全量缓存于内存（metricName -> 规则组），CRUD 后手动 reload；</li>
 *   <li>evaluate：目标限定规则优先于全局规则，命中即做阈值比较
 *       （GT/GE/EQ/LE/LT）；</li>
 *   <li>连续触发计数：内存计数器，未超限即清零，累计达到
 *       consecutiveCount 才进入静默判定；</li>
 *   <li>静默期：Redis SETNX + TTL，静默窗口内不重复告警；</li>
 *   <li>真实触发：落事件表 + 遍历全部 NotifySender 渠道投递。</li>
 * </ol>
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmEngineServiceImpl implements AlarmEngineService {

    /** 事件查询条数上限 */
    private static final int EVENT_LIMIT = 200;

    /** 连续触发计数的 Redis 前缀（跨重启保留静默窗口外的语义无需持久化） */
    private static final String SILENCE_KEY_PREFIX = "monitor:alarm:silence:";

    /** 策略 Mapper */
    private final MsMonitorAlarmPolicyMapper policyMapper;

    /** 规则 Mapper */
    private final MsMonitorAlarmRuleMapper ruleMapper;

    /** 事件 Mapper */
    private final MsMonitorAlarmEventMapper eventMapper;

    /** 监控目标 Mapper（事件冗余名称用） */
    private final MsMonitorMapper monitorMapper;

    /** Redis 模板（静默期 setNX） */
    private final StringRedisTemplate redisTemplate;

    /** 通知渠道集合（Spring 收集全部实现） */
    private final List<NotifySender> notifySenders;

    /** 规则缓存：metricName -> 启用中的规则列表 */
    private volatile Map<String, List<MsMonitorAlarmRule>> ruleCache = Map.of();

    /** 连续触发计数器：ruleId:monitorId -> 计数 */
    private final Map<String, AtomicInteger> consecutiveCounter = new ConcurrentHashMap<>();

    /** 维护窗口 Mapper */
    private final MsMonitorAlarmMaintenanceWindowMapper maintenanceWindowMapper;

    /**
     * 启动时加载规则缓存
     */
    @PostConstruct
    public void init() {
        reload();
    }

    @Override
    public void evaluate(Long monitorId, String monitorName, String metricName, double value) {
        if (monitorId == null || metricName == null || value != value) {
            // NaN 直接忽略
            return;
        }
        List<MsMonitorAlarmRule> rules = ruleCache.get(metricName);
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (MsMonitorAlarmRule rule : rules) {
            // 目标限定规则优先级高于全局规则：先匹配专属，再匹配通用
            boolean scoped = rule.getMsMonitorAlarmRuleMonitorId() != null;
            if (scoped && !rule.getMsMonitorAlarmRuleMonitorId().equals(monitorId)) {
                continue;
            }
            BigDecimal current = BigDecimal.valueOf(value);
            int cmp = current.compareTo(rule.getMsMonitorAlarmRuleThreshold());
            if (!conditionHit(rule.getMsMonitorAlarmRuleCondition(), cmp)) {
                // 未超限：清零该规则在该目标上的连击计数
                consecutiveCounter.remove(counterKey(rule.getId(), monitorId));
                continue;
            }
            String key = counterKey(rule.getId(), monitorId);
            int required = rule.getMsMonitorAlarmRuleConsecutiveCount() == null
                    ? 1 : Math.max(1, rule.getMsMonitorAlarmRuleConsecutiveCount());
            int hits = consecutiveCounter
                    .computeIfAbsent(key, k -> new AtomicInteger())
                    .incrementAndGet();
            if (hits < required) {
                log.debug("[alarm] 连击中 {}/{} rule={} monitor={}", hits, required, rule.getId(), monitorId);
                continue;
            }
            consecutiveCounter.remove(key);
            try {
                fireIfNotSilent(rule, monitorId, monitorName, metricName, current);
            } catch (Exception e) {
                // 静默期 Redis 异常或通知失败不阻断指标写入主链路
                log.warn("[alarm] 告警触发处理异常 rule={} monitor={}: {}", rule.getId(), monitorId, e.getMessage());
            }
        }
    }

    @Override
    public void reload() {
        try {
            List<MsMonitorAlarmPolicy> policies = policyMapper.selectList(
                    new LambdaQueryWrapper<MsMonitorAlarmPolicy>()
                            .eq(MsMonitorAlarmPolicy::getMsMonitorAlarmPolicyEnabled, 1));
            if (policies.isEmpty()) {
                ruleCache = Map.of();
                return;
            }
            List<Long> enabledIds = policies.stream().map(MsMonitorAlarmPolicy::getId).toList();
            List<MsMonitorAlarmRule> rules = ruleMapper.selectList(
                    new LambdaQueryWrapper<MsMonitorAlarmRule>()
                            .eq(MsMonitorAlarmRule::getMsMonitorAlarmRuleEnabled, 1)
                            .in(MsMonitorAlarmRule::getMsMonitorAlarmRulePolicyId, enabledIds));
            Map<String, List<MsMonitorAlarmRule>> cache = new ConcurrentHashMap<>();
            for (MsMonitorAlarmRule rule : rules) {
                cache.computeIfAbsent(rule.getMsMonitorAlarmRuleMetricName(), k -> new ArrayList<>()).add(rule);
            }
            ruleCache = cache;
            log.info("[alarm] 规则缓存已加载: {} 条规则 / {} 个指标", rules.size(), cache.size());
        } catch (Exception e) {
            log.warn("[alarm] 规则缓存加载失败: {}", e.getMessage());
        }
    }

    // ==================== 策略 CRUD ====================

    @Override
    public ReturnResult<MsMonitorAlarmPolicy> createPolicy(MsMonitorAlarmPolicy policy) {
        if (policy == null || isBlank(policy.getMsMonitorAlarmPolicyName())) {
            return ReturnResult.error("策略名称不能为空");
        }
        policy.setMsMonitorAlarmPolicyEnabled(policy.getMsMonitorAlarmPolicyEnabled() == null ? 1 : policy.getMsMonitorAlarmPolicyEnabled());
        policyMapper.insert(policy);
        return ReturnResult.ok(policy);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<MsMonitorAlarmPolicy> updatePolicy(Long id, MsMonitorAlarmPolicy policy) {
        MsMonitorAlarmPolicy exists = policyMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("策略不存在: " + id);
        }
        if (!isBlank(policy.getMsMonitorAlarmPolicyName())) {
            exists.setMsMonitorAlarmPolicyName(policy.getMsMonitorAlarmPolicyName());
        }
        if (policy.getMsMonitorAlarmPolicyEnabled() != null) {
            exists.setMsMonitorAlarmPolicyEnabled(policy.getMsMonitorAlarmPolicyEnabled());
        }
        if (policy.getMsMonitorAlarmPolicyRemark() != null) {
            exists.setMsMonitorAlarmPolicyRemark(policy.getMsMonitorAlarmPolicyRemark());
        }
        policyMapper.updateById(exists);
        reload();
        return ReturnResult.ok(exists);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Void> deletePolicy(Long id) {
        ruleMapper.delete(new LambdaQueryWrapper<MsMonitorAlarmRule>()
                .eq(MsMonitorAlarmRule::getMsMonitorAlarmRulePolicyId, id));
        policyMapper.deleteById(id);
        reload();
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<List<MsMonitorAlarmPolicy>> listPolicies() {
        return ReturnResult.list(policyMapper.selectList(
                new LambdaQueryWrapper<MsMonitorAlarmPolicy>().orderByDesc(MsMonitorAlarmPolicy::getId)));
    }

    // ==================== 规则 CRUD ====================

    @Override
    public ReturnResult<MsMonitorAlarmRule> createRule(MsMonitorAlarmRule rule) {
        var error = validateRule(rule);
        if (error != null) {
            return error;
        }
        rule.setMsMonitorAlarmRuleEnabled(rule.getMsMonitorAlarmRuleEnabled() == null ? 1 : rule.getMsMonitorAlarmRuleEnabled());
        rule.setMsMonitorAlarmRuleConsecutiveCount(rule.getMsMonitorAlarmRuleConsecutiveCount() == null ? 1 : rule.getMsMonitorAlarmRuleConsecutiveCount());
        rule.setMsMonitorAlarmRuleSilenceMinutes(rule.getMsMonitorAlarmRuleSilenceMinutes() == null ? 10 : rule.getMsMonitorAlarmRuleSilenceMinutes());
        ruleMapper.insert(rule);
        reload();
        return ReturnResult.ok(rule);
    }

    @Override
    public ReturnResult<MsMonitorAlarmRule> updateRule(Long id, MsMonitorAlarmRule rule) {
        MsMonitorAlarmRule exists = ruleMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("规则不存在: " + id);
        }
        if (rule.getMsMonitorAlarmRuleCondition() != null) {
            exists.setMsMonitorAlarmRuleCondition(rule.getMsMonitorAlarmRuleCondition());
        }
        if (rule.getMsMonitorAlarmRuleThreshold() != null) {
            exists.setMsMonitorAlarmRuleThreshold(rule.getMsMonitorAlarmRuleThreshold());
        }
        if (rule.getMsMonitorAlarmRuleConsecutiveCount() != null) {
            exists.setMsMonitorAlarmRuleConsecutiveCount(rule.getMsMonitorAlarmRuleConsecutiveCount());
        }
        if (rule.getMsMonitorAlarmRuleSilenceMinutes() != null) {
            exists.setMsMonitorAlarmRuleSilenceMinutes(rule.getMsMonitorAlarmRuleSilenceMinutes());
        }
        if (rule.getMsMonitorAlarmRuleLevel() != null) {
            exists.setMsMonitorAlarmRuleLevel(rule.getMsMonitorAlarmRuleLevel());
        }
        if (rule.getMsMonitorAlarmRuleEnabled() != null) {
            exists.setMsMonitorAlarmRuleEnabled(rule.getMsMonitorAlarmRuleEnabled());
        }
        if (rule.getMsMonitorAlarmRuleMonitorId() != null) {
            exists.setMsMonitorAlarmRuleMonitorId(rule.getMsMonitorAlarmRuleMonitorId());
        }
        ruleMapper.updateById(exists);
        reload();
        return ReturnResult.ok(exists);
    }

    @Override
    public ReturnResult<Void> deleteRule(Long id) {
        ruleMapper.deleteById(id);
        reload();
        return ReturnResult.success();
    }

    @Override
    public ReturnResult<List<MsMonitorAlarmRule>> listRules(Long policyId) {
        LambdaQueryWrapper<MsMonitorAlarmRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(policyId != null, MsMonitorAlarmRule::getMsMonitorAlarmRulePolicyId, policyId);
        wrapper.orderByDesc(MsMonitorAlarmRule::getId);
        return ReturnResult.list(ruleMapper.selectList(wrapper));
    }

    // ==================== 事件管理 ====================

    @Override
    public ReturnResult<List<MsMonitorAlarmEvent>> listEvents(String status) {
        LambdaQueryWrapper<MsMonitorAlarmEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(!isBlank(status), MsMonitorAlarmEvent::getMsMonitorAlarmEventStatus, status);
        wrapper.orderByDesc(MsMonitorAlarmEvent::getId).last("LIMIT " + EVENT_LIMIT);
        return ReturnResult.list(eventMapper.selectList(wrapper));
    }

    @Override
    public ReturnResult<Void> handleEvent(Long id, String remark, boolean falseAlarm) {
        MsMonitorAlarmEvent event = eventMapper.selectById(id);
        if (event == null) {
            return ReturnResult.error("事件不存在: " + id);
        }
        event.setMsMonitorAlarmEventStatus(falseAlarm ? "FALSE_ALARM" : "PROCESSED");
        event.setMsMonitorAlarmEventHandleRemark(remark);
        event.setMsMonitorAlarmEventHandleTime(LocalDateTime.now());
        eventMapper.updateById(event);
        return ReturnResult.success();
    }

    // ==================== 内部逻辑 ====================

    /**
     * 通过静默期判定后真实触发告警
     *
     * @param rule        规则
     * @param monitorId   目标 ID
     * @param monitorName 目标名
     * @param metricName  指标名
     * @param value       实际值
     */
private void fireIfNotSilent(MsMonitorAlarmRule rule, Long monitorId, String monitorName,
                             String metricName, BigDecimal value) {
            // ---- 新增：维护窗口检查 ----
            // 如果当前时间在活跃的维护窗口内，则跳过告警（不受静默期影响）
            try {
                LocalDateTime now = LocalDateTime.now();
                int nowWeekday = now.getDayOfWeek().getValue() % 7; // Monday=1 -> 1, Sunday=7 -> 0
                String weekdays = String.valueOf(nowWeekday);
                // 查询启用中的维护窗口
                LambdaQueryWrapper<MsMonitorAlarmMaintenanceWindow> mwWrapper = new LambdaQueryWrapper<MsMonitorAlarmMaintenanceWindow>()
                        .eq(MsMonitorAlarmMaintenanceWindow::getMsMonitorAlarmMaintenanceWindowEnabled, 1);
                if (rule.getMsMonitorAlarmRuleLevel() != null) {
                    mwWrapper.eq(MsMonitorAlarmMaintenanceWindow::getWindowLevel, rule.getMsMonitorAlarmRuleLevel());
                } else {
                    // 全局级别的窗口（level 为空）
                    mwWrapper.isNull(MsMonitorAlarmMaintenanceWindow::getWindowLevel);
                }
                List<MsMonitorAlarmMaintenanceWindow> windows = maintenanceWindowMapper.selectList(mwWrapper);
                boolean inMaintenance = false;
                for (MsMonitorAlarmMaintenanceWindow window : windows) {
                    // 检查周日目标：weekdays 为空则每天生效，否则匹配
                    if (window.getWeekdays() == null || window.getWeekdays().isEmpty() || window.getWeekdays().contains(weekdays)) {
                        // 检查时间范围：每天的 startTime ~ endTime
                        String[] startParts = window.getStartTime().split(":");
                        String[] endParts = window.getEndTime().split(":");
                        int startHour = Integer.parseInt(startParts[0]);
                        int startMin = Integer.parseInt(startParts[1]);
                        int endHour = Integer.parseInt(endParts[0]);
                        int endMin = Integer.parseInt(endParts[1]);
                        // 将当前时间转换为分钟数便于比较
                        int nowMinutes = now.getHour() * 60 + now.getMinute();
                        int startMinutes = startHour * 60 + startMin;
                        int endMinutes = endHour * 60 + endMin;
                        // 处理跨午夜的情况（例如 22:00 ~ 06:00）
                        boolean inRange;
                        if (startMinutes < endMinutes) {
                            inRange = nowMinutes >= startMinutes && nowMinutes < endMinutes;
                        } else {
                            inRange = nowMinutes >= startMinutes || nowMinutes < endMinutes;
                        }
                        if (inRange) {
                            inMaintenance = true;
                            break;
                        }
                    }
                }
                if (inMaintenance) {
                    log.debug("[alarm] 维护窗口内跳过告警 rule={} monitor={}", rule.getId(), monitorId);
                    return;
                }
            } catch (Exception e) {
                log.warn("[alarm] 维护窗口检查异常: {}", e.getMessage());
                // 异常时不阻断主链路，继续走常规静默期逻辑
            }
            // --------------------------------------------

            String silenceKey = SILENCE_KEY_PREFIX + rule.getId() + ":" + monitorId;
            int silenceMinutes = rule.getMsMonitorAlarmRuleSilenceMinutes() == null ? 10 : rule.getMsMonitorAlarmRuleSilenceMinutes();
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(silenceKey, "1", Duration.ofMinutes(silenceMinutes));
            if (!Boolean.TRUE.equals(first)) {
                log.debug("[alarm] 静默期内跳过 rule={} monitor={}", rule.getId(), monitorId);
                return;
            }
            // 目标名兜底查询
            String resolvedName = monitorName;
            if (isBlank(resolvedName)) {
                MsMonitor monitor = monitorMapper.selectById(monitorId);
                resolvedName = monitor != null ? monitor.getMonitorName() : String.valueOf(monitorId);
            }
            // 落事件
            var event = new MsMonitorAlarmEvent();
            event.setMsMonitorAlarmEventMonitorId(monitorId);
            event.setMsMonitorAlarmEventMonitorName(resolvedName);
            event.setMsMonitorAlarmEventRuleId(rule.getId());
            event.setMsMonitorAlarmEventMetricName(metricName);
            event.setMsMonitorAlarmEventAlarmValue(value);
            event.setMsMonitorAlarmEventThreshold(rule.getMsMonitorAlarmRuleThreshold());
            event.setMsMonitorAlarmEventLevel(isBlank(rule.getMsMonitorAlarmRuleLevel()) ? "P2" : rule.getMsMonitorAlarmRuleLevel());
            event.setMsMonitorAlarmEventStatus("NEW");
            event.setMsMonitorAlarmEventTriggerTime(LocalDateTime.now());
            eventMapper.insert(event);
            // 渠道投递（单渠道失败不影响其他）
            String title = String.format("[%s] %s %s=%s", event.getMsMonitorAlarmEventLevel(),
                    resolvedName, metricName, value.toPlainString());
            String content = String.format("指标 %s 当前值 %s 已连续超过阈值 %s（条件 %s），请及时处理。",
                    metricName, value.toPlainString(), rule.getMsMonitorAlarmRuleThreshold().toPlainString(),
                    rule.getMsMonitorAlarmRuleCondition());
            for (NotifySender sender : notifySenders) {
                try {
                    sender.send(title, content, null);
                } catch (Exception e) {
                    log.warn("[alarm] 通知渠道 {} 发送失败: {}", sender.channel(), e.getMessage());
                }
            }
            log.info("[alarm] 告警触发 rule={} monitor={} metric={} value={}",
                    rule.getId(), monitorId, metricName, value);
    }

    /**
     * 校验规则参数
     *
     * @param rule 规则
     * @return 校验失败结果，合法返回 null
     */
    private ReturnResult<MsMonitorAlarmRule> validateRule(MsMonitorAlarmRule rule) {
        if (rule == null || isBlank(rule.getMsMonitorAlarmRuleMetricName())
                || isBlank(rule.getMsMonitorAlarmRuleCondition())
                || rule.getMsMonitorAlarmRuleThreshold() == null
                || rule.getMsMonitorAlarmRulePolicyId() == null) {
            return ReturnResult.error("策略ID、指标名、条件与阈值均不能为空");
        }
        if (!"GT".equals(rule.getMsMonitorAlarmRuleCondition()) && !"GE".equals(rule.getMsMonitorAlarmRuleCondition())
                && !"EQ".equals(rule.getMsMonitorAlarmRuleCondition()) && !"LE".equals(rule.getMsMonitorAlarmRuleCondition())
                && !"LT".equals(rule.getMsMonitorAlarmRuleCondition())) {
            return ReturnResult.error("触发条件仅支持 GT/GE/EQ/LE/LT");
        }
        return null;
    }

    /**
     * 条件命中判断
     *
     * @param condition 条件串
     * @param cmp       compareTo 结果
     * @return 是否命中
     */
    private boolean conditionHit(String condition, int cmp) {
        return switch (condition == null ? "" : condition) {
            case "GT" -> cmp > 0;
            case "GE" -> cmp >= 0;
            case "EQ" -> cmp == 0;
            case "LE" -> cmp <= 0;
            case "LT" -> cmp < 0;
            default -> false;
        };
    }

    /**
     * 连击计数键
     *
     * @param ruleId   规则 ID
     * @param monitorId 目标 ID
     * @return 键
     */
    private String counterKey(Long ruleId, Long monitorId) {
        return ruleId + ":" + monitorId;
    }

/**
     * 空串判断
     *
     * @param value 待判断字符串
     * @return 是否为空
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    /** 新增维护窗口 */
    public ReturnResult<MsMonitorAlarmMaintenanceWindow> createMaintenanceWindow(
            MsMonitorAlarmMaintenanceWindow window) {
        if (window == null || isBlank(window.getWindowName())) {
            return ReturnResult.error("窗口名称不能为空");
        }
        window.setMsMonitorAlarmMaintenanceWindowEnabled(window.getMsMonitorAlarmMaintenanceWindowEnabled() == null ? 1 : window.getMsMonitorAlarmMaintenanceWindowEnabled());
        maintenanceWindowMapper.insert(window);
        return ReturnResult.ok(window);
    }

    @Override
    /** 更新维护窗口 */
    public ReturnResult<MsMonitorAlarmMaintenanceWindow> updateMaintenanceWindow(Long id,
                                                                              MsMonitorAlarmMaintenanceWindow window) {
        MsMonitorAlarmMaintenanceWindow exists = maintenanceWindowMapper.selectById(id);
        if (exists == null) {
            return ReturnResult.error("维护窗口不存在: " + id);
        }
        if (!isBlank(window.getWindowName())) {
            exists.setWindowName(window.getWindowName());
        }
        if (window.getMsMonitorAlarmMaintenanceWindowLevel() != null) {
            exists.setWindowLevel(window.getMsMonitorAlarmMaintenanceWindowLevel());
        }
        if (window.getStartTime() != null) {
            exists.setStartTime(window.getStartTime());
        }
        if (window.getEndTime() != null) {
            exists.setEndTime(window.getEndTime());
        }
        if (window.getWeekdays() != null) {
            exists.setWeekdays(window.getWeekdays());
        }
        if (window.getMsMonitorAlarmMaintenanceWindowEnabled() != null) {
            exists.setMsMonitorAlarmMaintenanceWindowEnabled(window.getMsMonitorAlarmMaintenanceWindowEnabled());
        }
        if (!isBlank(window.getRemark())) {
            exists.setRemark(window.getRemark());
        }
        maintenanceWindowMapper.updateById(exists);
        return ReturnResult.ok(exists);
    }

    @Override
    /** 删除维护窗口 */
    public ReturnResult<Void> deleteMaintenanceWindow(Long id) {
        maintenanceWindowMapper.deleteById(id);
        return ReturnResult.success();
    }

    @Override
    /** 维护窗口列表 */
    public ReturnResult<List<MsMonitorAlarmMaintenanceWindow>> listMaintenanceWindows(
            Long policyId) {
        LambdaQueryWrapper<MsMonitorAlarmMaintenanceWindow> wrapper = new LambdaQueryWrapper<MsMonitorAlarmMaintenanceWindow>()
                .eq(policyId != null, MsMonitorAlarmMaintenanceWindow::getPolicyId, policyId)
                .eq(MsMonitorAlarmMaintenanceWindow::getMsMonitorAlarmMaintenanceWindowEnabled, 1)
                .orderByDesc(MsMonitorAlarmMaintenanceWindow::getId);
        return ReturnResult.list(maintenanceWindowMapper.selectList(wrapper));
    }
}

    @Override
    /** 测试告警规则 */
    public ReturnResult<Boolean> testRule(Long ruleId, Long monitorId, String metricName, Double value) {
        try {
            // 1. 获取规则详情
            MsMonitorAlarmRule rule = ruleMapper.selectById(ruleId);
            if (rule == null) {
                return ReturnResult.error("规则不存在: " + ruleId);
            }
            if (rule.getMsMonitorAlarmRuleEnabled() != 1) {
                return ReturnResult.error("规则已禁用");
            }

            // 2. 临时将规则加入缓存进行测试
            Map<String, List<MsMonitorAlarmRule>> originalCache = new java.util.HashMap<>(ruleCache);
            ruleCache.computeIfAbsent(rule.getMsMonitorAlarmRuleMetricName(), k -> new java.util.ArrayList<>()).add(rule);

            // 3. 执行阈值比较判断
            BigDecimal current = value != null ? BigDecimal.valueOf(value) : BigDecimal.ZERO;
            int cmp = current.compareTo(rule.getMsMonitorAlarmRuleThreshold());
            boolean conditionHit = false;
            switch (rule.getMsMonitorAlarmRuleCondition()) {
                case "GT": conditionHit = cmp > 0; break;
                case "GE": conditionHit = cmp >= 0; break;
                case "EQ": conditionHit = cmp == 0; break;
                case "LE": conditionHit = cmp <= 0; break;
                case "LT": conditionHit = cmp < 0; break;
                default: conditionHit = false;
            }

            // 4. 在测试模式下，仅判断阈值条件是否命中（不计入连续计数）
            boolean wouldTrigger = conditionHit;

            // 5. 恢复原缓存
            ruleCache = originalCache;

            return ReturnResult.ok(wouldTrigger);
        } catch (Exception e) {
            log.error("告警规则测试失败", e);
            return ReturnResult.error(e.getMessage());
        }
    }
}
