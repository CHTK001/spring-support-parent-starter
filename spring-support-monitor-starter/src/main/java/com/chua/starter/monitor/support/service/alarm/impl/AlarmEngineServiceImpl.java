// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.monitor.support.service.alarm.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.spi.definition.ServiceDefinition;
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
import com.chua.starter.monitor.support.model.AlarmStatsSummaryVO;
import com.chua.starter.monitor.support.model.AlarmTrendPointVO;
import com.chua.starter.monitor.support.service.alarm.AlarmEngineService;
import com.chua.starter.monitor.support.service.alarm.NotifySender;
import com.chua.starter.monitor.support.service.impl.MonitorPushService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
   * consecutive数量 才进入静默判定；</li>
   *   <li>静默期：Redis SETNX + TTL，静默窗口内不重复告警；</li>
   *   <li>真实触发：落事件表 + 遍历全部 NotifySender 渠道投递。</li>
   * </ol>
   *
   * @author CH
   * @since 4.0.0.42
   */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmEngineServiceImpl implements AlarmEngineService {

      /**
       * 事件查询条数上限
      */
    private static final int EVENT_LIMIT = 200;

      /**
       * 趋势统计最大回溯小时数
      */
    private static final int MAX_TREND_HOURS = 720;

      /**
       * 趋势跨度不超过该小时数时，横轴标签省略日期
      */
    private static final int DAY_SPAN_HOURS = 24;

      /**
       * 连续触发计数的 Redis 前缀（跨重启保留静默窗口外的语义无需持久化）
       */
    private static final String SILENCE_KEY_PREFIX = "monitor:alarm:silence:";

      /**
       * 策略 映射器
      */
    private final MsMonitorAlarmPolicyMapper policyMapper;

      /**
       * 规则 映射器
      */
    private final MsMonitorAlarmRuleMapper ruleMapper;

      /**
       * 事件 映射器
      */
    private final MsMonitorAlarmEventMapper eventMapper;

      /**
       * 监控目标 映射器（事件冗余名称用）
      */
    private final MsMonitorMapper monitorMapper;

      /**
       * Redis 模板（静默期 设置nx）
      */
    private final StringRedisTemplate redisTemplate;

      /**
       * 通知渠道 扩展点（@Spi 发现，首次投递时解析）
      */
    private final ServiceProvider<NotifySender> notifyExtensions = ServiceProvider.of(NotifySender.class);

      /**
       * 通知渠道实现缓存（容器就绪后首次投递时构建）
      */
    private volatile List<NotifySender> cachedSenders;

      /**
       * 实时推送服务（Socket 未启用时静默跳过）
      */
    private final MonitorPushService monitorPushService;

      /**
       * 规则缓存：指标名称 -> 启用中的规则列表
      */
    private volatile Map<String, List<MsMonitorAlarmRule>> ruleCache = Map.of();

      /**
       * 连续触发计数器：ruleid:监控标识 -> 计数
      */
    private final Map<String, AtomicInteger> consecutiveCounter = new ConcurrentHashMap<>();

      /**
       * Redis 不可用时的内存静默期降级表：silence键 → 静默截止 轮次 毫秒。
       * <p>独立部署(无 Redis)或 Redis 短暂不可用时, 以此表完成 SETNX+TTL 的等价语义,
       * 保证告警事件仍能落库, 而非被异常吞掉导致一条告警都不记录。</p>
       */
    private final Map<String, Long> memorySilence = new ConcurrentHashMap<>();

      /**
       * 维护窗口 映射器
      */
    private final MsMonitorAlarmMaintenanceWindowMapper maintenanceWindowMapper;

      /**
       * 启动时加载规则缓存
       */
    @PostConstruct
    public void init() {
          /**
           * reload。
           */
        reload();
    }

    @Override
    public void evaluate(Long monitorId, String monitorName, String metricName, double value) {
        evaluateInternal(monitorId, null, monitorName, metricName, value);
    }

    @Override
    public void evaluateHost(Long hostId, String hostName, String metricName, double value) {
        evaluateInternal(hostId, hostId, hostName, metricName, value);
    }

      /**
       * 指标评估主流程（采集类监控项与主机自身指标共用）
       *
       * at param targetId   评估目标 标识（监控项 或 主机）
       * at param hostScope  主机 标识：非空表示目标就是这台主机；为空表示采集类监控项，先反查其绑定主机
       * at param targetName 目标名
       * at param metricName 指标名
       * at param value      指标值
       */
    private void evaluateInternal(Long targetId, Long hostScope, String targetName, String metricName, double value) {
        if (targetId == null || metricName == null || value != value) {
   // nan 直接忽略
            return;
        }
        List<MsMonitorAlarmRule> rules = ruleCache.get(metricName);
        if (rules == null || rules.isEmpty()) {
            return;
        }
        Long hostId = hostScope != null ? hostScope : resolveMonitorHostId(targetId);
        for (MsMonitorAlarmRule rule : rules) {
            // 策略归属主机：本主机或全局（空）的规则才参与，其他主机的策略不跨机生效
            if (!hostAllows(rule, hostId)) {
                continue;
            }
              // 目标限定规则优先级高于全局规则：先匹配专属，再匹配通用
            boolean scoped = rule.getMsMonitorAlarmRuleMonitorId() != null;
            if (scoped && !rule.getMsMonitorAlarmRuleMonitorId().equals(targetId)) {
                continue;
            }
            BigDecimal current = BigDecimal.valueOf(value);
            int cmp = current.compareTo(rule.getMsMonitorAlarmRuleThreshold());
            if (!conditionHit(rule.getMsMonitorAlarmRuleCondition(), cmp)) {
                  // 未超限：清零该规则在该目标上的连击计数
                consecutiveCounter.remove(counterKey(rule.getId(), targetId));
                continue;
            }
            String key = counterKey(rule.getId(), targetId);
            int required = rule.getMsMonitorAlarmRuleConsecutiveCount() == null
                    ? 1 : Math.max(1, rule.getMsMonitorAlarmRuleConsecutiveCount());
            int hits = consecutiveCounter
                    .computeIfAbsent(key, k -> new AtomicInteger())
                    .incrementAndGet();
            if (hits < required) {
                log.debug("[alarm] 连击中 {}/{} rule={} monitor={}", hits, required, rule.getId(), targetId);
                continue;
            }
            consecutiveCounter.remove(key);
            try {
                  /**
                   * fireifnotsilent。
                   * @param rule rule
                   * @param monitorId 监控标识
                   * @param monitorName 监控名称
                   * @param metricName 指标名称
                   * @param current 当前
                   */
                fireIfNotSilent(rule, targetId, targetName, metricName, current);
            } catch (Exception e) {
                  // 静默期 Redis 异常或通知失败不阻断指标写入主链路
                log.warn("[alarm] 告警触发处理异常 rule={} monitor={}: {}", rule.getId(), targetId, e.getMessage());
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
            // 策略归属主机先落到内存：规则缓存按指标分组，逐条再反查策略会 N 次查库
            Map<Long, Long> policyHostMap = new HashMap<>();
            for (MsMonitorAlarmPolicy policy : policies) {
                policyHostMap.put(policy.getId(), policy.getMsServerHostId());
            }
            List<MsMonitorAlarmRule> rules = ruleMapper.selectList(
                    new LambdaQueryWrapper<MsMonitorAlarmRule>()
                            .eq(MsMonitorAlarmRule::getMsMonitorAlarmRuleEnabled, 1)
                            .in(MsMonitorAlarmRule::getMsMonitorAlarmRulePolicyId, enabledIds));
            for (MsMonitorAlarmRule rule : rules) {
                rule.setMsMonitorAlarmRulePolicyHostId(policyHostMap.get(rule.getMsMonitorAlarmRulePolicyId()));
            }
            Map<String, List<MsMonitorAlarmRule>> cache = new ConcurrentHashMap<>();
            for (MsMonitorAlarmRule rule : rules) {
                cache.computeIfAbsent(rule.getMsMonitorAlarmRuleMetricName(), k -> new ArrayList<>()).add(rule);
            }
            ruleCache = cache;
            long hosted = rules.stream().filter(r -> r.getMsMonitorAlarmRulePolicyHostId() != null).count();
            log.info("[alarm] 规则缓存已加载: {} 条规则 / {} 个指标（其中主机专属 {} 条）",
                    rules.size(), cache.size(), hosted);
        } catch (Exception e) {
            log.warn("[alarm] 规则缓存加载失败: {}", e.getMessage());
        }
    }

      // ==================== 策略 CRUD ====================

    @Override
    @CacheEvict(cacheNames = {"monitor:alarm:policies", "monitor:alarm:rules"}, allEntries = true)
      /**
       * 创建policy。
       * @param policy policy
       * @return 创建policy的结果
       */
    public ReturnResult<MsMonitorAlarmPolicy> createPolicy(MsMonitorAlarmPolicy policy) {
        if (policy == null || isBlank(policy.getMsMonitorAlarmPolicyName())) {
            return ReturnResult.error("策略名称不能为空");
        }
        policy.setMsMonitorAlarmPolicyEnabled(policy.getMsMonitorAlarmPolicyEnabled() == null ? 1 : policy.getMsMonitorAlarmPolicyEnabled());
        policyMapper.insert(policy);
        // 策略本身也是规则缓存的输入（停用策略等于整组失效），新增后必须重载
        reload();
        return ReturnResult.ok(policy);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = {"monitor:alarm:policies", "monitor:alarm:rules"}, allEntries = true)
      /**
       * 更新policy。
       * @param id 标识
       * @param policy policy
       * @return 更新policy的结果
       */
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
        if (Boolean.TRUE.equals(policy.getGlobalScope())) {
            // 只给非空值 set 时无法把「归属某主机」改回「全局」，用显式开关表达
            exists.setMsServerHostId(null);
        } else if (policy.getMsServerHostId() != null) {
            exists.setMsServerHostId(policy.getMsServerHostId());
        }
        policyMapper.updateById(exists);
          /**
           * reload。
           */
        reload();
        return ReturnResult.ok(exists);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = {"monitor:alarm:policies", "monitor:alarm:rules"}, allEntries = true)
      /**
       * 删除policy。
       * @param id 标识
       * @return 删除policy的结果
       */
    public ReturnResult<Void> deletePolicy(Long id) {
        ruleMapper.delete(new LambdaQueryWrapper<MsMonitorAlarmRule>()
                .eq(MsMonitorAlarmRule::getMsMonitorAlarmRulePolicyId, id));
        policyMapper.deleteById(id);
          /**
           * reload。
           */
        reload();
        return ReturnResult.success();
    }

    @Override
    @Cacheable(cacheNames = "monitor:alarm:policies", key = "#hostId != null ? 'policies:' + #hostId : 'policies'")
      /**
       * 列表policies。
       * at param hostId 主机标识，非空时只返回归属该主机的策略
       * @return 列表policies的结果
       */
    public ReturnResult<List<MsMonitorAlarmPolicy>> listPolicies(Long hostId) {
        LambdaQueryWrapper<MsMonitorAlarmPolicy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(hostId != null, MsMonitorAlarmPolicy::getMsServerHostId, hostId);
        wrapper.orderByDesc(MsMonitorAlarmPolicy::getId);
        return ReturnResult.list(policyMapper.selectList(wrapper));
    }

      // ==================== 规则 CRUD ====================

    @Override
    @CacheEvict(cacheNames = {"monitor:alarm:rules", "monitor:alarm:events"}, allEntries = true)
      /**
       * 创建rule。
       * @param rule rule
       * @return 创建rule的结果
       */
    public ReturnResult<MsMonitorAlarmRule> createRule(MsMonitorAlarmRule rule) {
        var error = validateRule(rule);
        if (error != null) {
            return error;
        }
        rule.setMsMonitorAlarmRuleEnabled(rule.getMsMonitorAlarmRuleEnabled() == null ? 1 : rule.getMsMonitorAlarmRuleEnabled());
        rule.setMsMonitorAlarmRuleConsecutiveCount(rule.getMsMonitorAlarmRuleConsecutiveCount() == null ? 1 : rule
            .getMsMonitorAlarmRuleConsecutiveCount());
        rule.setMsMonitorAlarmRuleSilenceMinutes(rule.getMsMonitorAlarmRuleSilenceMinutes() == null ? 10 : rule
            .getMsMonitorAlarmRuleSilenceMinutes());
        ruleMapper.insert(rule);
          /**
           * reload。
           */
        reload();
        return ReturnResult.ok(rule);
    }

    @Override
    @CacheEvict(cacheNames = {"monitor:alarm:rules", "monitor:alarm:events"}, allEntries = true)
      /**
       * 更新rule。
       * @param id 标识
       * @param rule rule
       * @return 更新rule的结果
       */
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
          /**
           * reload。
           */
        reload();
        return ReturnResult.ok(exists);
    }

    @Override
    @CacheEvict(cacheNames = {"monitor:alarm:rules", "monitor:alarm:events"}, allEntries = true)
      /**
       * 删除rule。
       * @param id 标识
       * @return 删除rule的结果
       */
    public ReturnResult<Void> deleteRule(Long id) {
        ruleMapper.deleteById(id);
          /**
           * reload。
           */
        reload();
        return ReturnResult.success();
    }

    @Override
    @Cacheable(cacheNames = "monitor:alarm:rules", key = "#policyId != null ? 'rules:' + #policyId : 'rules:all'")
      /**
       * 列表rules。
       * @param policyId policyid
       * @return 列表rules的结果
       */
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
        String newStatus = falseAlarm ? "FALSE_ALARM" : "PROCESSED";
        event.setMsMonitorAlarmEventStatus(newStatus);
        event.setMsMonitorAlarmEventHandleRemark(remark);
        event.setMsMonitorAlarmEventHandleTime(LocalDateTime.now());
        eventMapper.updateById(event);
          // 广播告警状态变更（前端徽标实时 -1）
        try {
            monitorPushService.pushAlarm(MonitorPushService.buildAlarmPayload(
                    event.getMsMonitorAlarmEventMonitorId(),
                    event.getMsMonitorAlarmEventMonitorName(),
                    event.getMsMonitorAlarmEventMetricName(),
                    event.getMsMonitorAlarmEventAlarmValue() == null ? 0 : event.getMsMonitorAlarmEventAlarmValue().doubleValue(),
                    event.getMsMonitorAlarmEventThreshold() == null ? 0 : event.getMsMonitorAlarmEventThreshold().doubleValue(),
                    event.getMsMonitorAlarmEventLevel(), newStatus));
        } catch (Exception e) {
            log.debug("[alarm] 处理事件广播跳过: {}", e.getMessage());
        }
        return ReturnResult.success();
    }

      // ==================== 内部逻辑 ====================

      /**
       * 获取静默期「锁」。优先走 Redis SETNX+TTL；Redis 不可用时降级为 JVM 内存表,
       * 使独立(无 Redis)部署下告警事件仍能正常落库, 而不被 Redis 异常吞掉。
       *
       * @param silenceKey    静默键(rule:监控)
       * @param silenceMinutes 静默分钟
       * @return true=首次触发(放行); false=静默期内(跳过)或获取失败(保守跳过)
       */
    private Boolean acquireSilence(String silenceKey, int silenceMinutes) {
          // 0/负数表示不静默：Redis 对非正 TTL 会抛异常，直接放行避免退化成"永不告警"
        if (silenceMinutes <= 0) {
            return true;
        }
          // 1) 优先 Redis：可用时返回其 SETNX 结果
        try {
            return redisTemplate.opsForValue()
                    .setIfAbsent(silenceKey, "1", Duration.ofMinutes(silenceMinutes));
        } catch (Exception ignored) {
            log.debug("[alarm] Redis 不可用, 使用内存静默期降级: {}", silenceKey);
        }
          // 2) 内存降级：SETNX+TTL 等价语义
        long now = System.currentTimeMillis();
        long until = now + (long) silenceMinutes * 60_000L;
        Long prev = memorySilence.putIfAbsent(silenceKey, until);
        if (prev != null) {
              // 已有锁：判断是否过期
            if (prev <= now) {
                  // 已过期, 尝试抢占(TTL 续期语义)
                long replaced = memorySilence.replace(silenceKey, prev, until) ? until : prev;
                return replaced == until;
            }
            return false; // 静默期内
        }
        return true; // 首次设置成功
    }

      /**
       * 判断规则当前是否落在生效的维护窗口内。
       * <p>
       * 窗口级别为空 = 全局静默（命中任意级别的规则，与实体注释一致）；级别非空 = 仅静默同级别规则。
       * 单个窗口的星期/起止时间配置残缺只跳过该窗口，不影响的其余窗口判定。
       *
       * @param rule 待判定规则
       * @return true=处于维护窗口内，应跳过告警
       */
    private boolean inMaintenanceWindow(MsMonitorAlarmRule rule) {
        List<MsMonitorAlarmMaintenanceWindow> windows;
        try {
            String ruleLevel = isBlank(rule.getMsMonitorAlarmRuleLevel()) ? null : rule.getMsMonitorAlarmRuleLevel().trim();
            windows = maintenanceWindowMapper.selectList(
                    new LambdaQueryWrapper<MsMonitorAlarmMaintenanceWindow>()
                            .eq(MsMonitorAlarmMaintenanceWindow::getMsMonitorAlarmMaintenanceWindowEnabled, 1)
                            .and(wrapper -> {
                                wrapper.isNull(MsMonitorAlarmMaintenanceWindow::getMsMonitorAlarmMaintenanceWindowLevel)
                                        .or()
                                        .eq(MsMonitorAlarmMaintenanceWindow::getMsMonitorAlarmMaintenanceWindowLevel, "");
                                if (ruleLevel != null) {
                                    wrapper.or()
                                            .eq(MsMonitorAlarmMaintenanceWindow::getMsMonitorAlarmMaintenanceWindowLevel, ruleLevel);
                                }
                            }));
        } catch (Exception e) {
            log.warn("[alarm] 维护窗口查询异常, 本次不静默: {}", e.getMessage());
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        int nowMinutes = now.getHour() * 60 + now.getMinute();
        String nowWeekday = String.valueOf(now.getDayOfWeek().getValue() % 7);
        for (MsMonitorAlarmMaintenanceWindow window : windows) {
            try {
                if (!matchesWeekday(window.getWeekdays(), nowWeekday)) {
                    continue;
                }
                Integer start = toDayMinutes(window.getStartTime());
                Integer end = toDayMinutes(window.getEndTime());
                if (start == null || end == null) {
                    log.warn("[alarm] 维护窗口[{}]起止时间缺失或非法({}~{}), 已跳过该窗口",
                            window.getId(), window.getStartTime(), window.getEndTime());
                    continue;
                }
                  // 支持跨午夜（如 22:00~06:00）；起止相同视为全天静默
                boolean inRange = start < end
                        ? nowMinutes >= start && nowMinutes < end
                        : nowMinutes >= start || nowMinutes < end;
                if (inRange) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("[alarm] 维护窗口[{}]判定异常, 跳过该窗口: {}", window.getId(), e.getMessage());
            }
        }
        return false;
    }

      /**
       * 星期命中判定
       *
       * @param weekdays   窗口配置，逗号分隔的 0~6（周日=0），为空表示每天
       * @param nowWeekday 当前星期数字
       * @return 是否命中
       */
    private boolean matchesWeekday(String weekdays, String nowWeekday) {
        if (isBlank(weekdays)) {
            return true;
        }
        for (String token : weekdays.split(",")) {
            if (token.trim().equals(nowWeekday)) {
                return true;
            }
        }
        return false;
    }

      /**
       * "HH:mm" 解析为当日分钟数
       *
       * @param hhmm 待解析时间串
       * @return 分钟数，格式非法返回 空
       */
    private Integer toDayMinutes(String hhmm) {
        if (isBlank(hhmm)) {
            return null;
        }
        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            return null;
        }
    }

      /**
       * 级别归一：空白统一为 空串，使「全局静默」在数据层只有一种表示。
       * 不能归一为 null——updateById 默认跳过 null 字段，"改回全局静默" 会静默失效。
       *
       * @param level 原始级别
       * @return 归一后的级别
       */
    private String normalizeLevel(String level) {
        return isBlank(level) ? "" : level.trim();
    }

      /**
       * 维护窗口起止时间校验
       *
       * @param startTime 起始时间 HH:mm
       * @param endTime   结束时间 HH:mm
       * @return 错误文案，校验通过返回 空
       */
    private String validateWindowTime(String startTime, String endTime) {
        if (toDayMinutes(startTime) == null) {
            return "起始时间格式非法，需为 HH:mm（如 22:00）";
        }
        if (toDayMinutes(endTime) == null) {
            return "结束时间格式非法，需为 HH:mm（如 06:00）";
        }
        return null;
    }

      /**
       * 通过静默期判定后真实触发告警
       *
       * @param rule        规则
       * @param monitorId   目标 标识
       * @param monitorName 目标名
       * @param metricName  指标名
       * @param value       实际值
       */
private void fireIfNotSilent(MsMonitorAlarmRule rule, Long monitorId, String monitorName,
                             String metricName, BigDecimal value) {
            if (inMaintenanceWindow(rule)) {
                log.debug("[alarm] 维护窗口内跳过告警 rule={} monitor={}", rule.getId(), monitorId);
                return;
            }

            String silenceKey = SILENCE_KEY_PREFIX + rule.getId() + ":" + monitorId;
            int silenceMinutes = rule.getMsMonitorAlarmRuleSilenceMinutes() == null ? 10 : rule.getMsMonitorAlarmRuleSilenceMinutes();
            Boolean first = acquireSilence(silenceKey, silenceMinutes);
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
              // 落事件（含触发时规则配置快照，规则后续修改可追溯）
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
            event.setMsMonitorAlarmEventRuleSnapshot(buildRuleSnapshot(rule));
            eventMapper.insert(event);
   // 实时推送告警事件到前端（Socket 未启用时静默跳过）
            try {
                monitorPushService.pushAlarm(MonitorPushService.buildAlarmPayload(
                        event.getMsMonitorAlarmEventMonitorId(),
                        event.getMsMonitorAlarmEventMonitorName(),
                        event.getMsMonitorAlarmEventMetricName(),
                        event.getMsMonitorAlarmEventAlarmValue() == null ? 0 : event.getMsMonitorAlarmEventAlarmValue().doubleValue(),
                        event.getMsMonitorAlarmEventThreshold() == null ? 0 : event.getMsMonitorAlarmEventThreshold().doubleValue(),
                        event.getMsMonitorAlarmEventLevel(),
                        event.getMsMonitorAlarmEventStatus()));
            } catch (Exception e) {
                log.debug("[alarm] 实时推送跳过: {}", e.getMessage());
            }
              // 渠道投递（单渠道失败不影响其他）
            String title = String.format("[%s] %s %s=%s", event.getMsMonitorAlarmEventLevel(),
                    resolvedName, metricName, value.toPlainString());
            String content = String.format("指标 %s 当前值 %s 已连续超过阈值 %s（条件 %s），请及时处理。",
                    metricName, value.toPlainString(), rule.getMsMonitorAlarmRuleThreshold().toPlainString(),
                    rule.getMsMonitorAlarmRuleCondition());
            dispatch(title, content);
            log.info("[alarm] 告警触发 rule={} monitor={} metric={} value={}",
                    rule.getId(), monitorId, metricName, value);
    }

      /**
       * 遍历全部通知渠道投递（单渠道失败不影响其他渠道）
       *
       * @param title   标题
       * @param content 内容
       */
    private void dispatch(String title, String content) {
        for (NotifySender sender : notifySenders()) {
            try {
                sender.send(title, content, null);
            } catch (Exception e) {
                log.warn("[alarm] 通知渠道 {} 发送失败: {}", sender.channel(), e.getMessage());
            }
        }
    }

      /**
       * 解析通知渠道实现列表（同一实现可能经多个解析器命中，按类去重）
       *
       * @return 通知渠道实现
       */
    private List<NotifySender> notifySenders() {
        List<NotifySender> senders = this.cachedSenders;
        if (senders != null) {
            return senders;
        }
        synchronized (this) {
            if (this.cachedSenders != null) {
                return this.cachedSenders;
            }
            List<NotifySender> resolved = new ArrayList<>();
            Set<Class<?>> seen = new HashSet<>();
            for (ServiceDefinition definition : notifyExtensions.getPriorityServiceDefinitions()) {
                NotifySender sender = definition.getObj(notifyExtensions.getServiceAutowire());
                if (sender == null || !seen.add(sender.getClass())) {
                    continue;
                }
                resolved.add(sender);
                log.info("[alarm] 注册通知渠道: channel={}, impl={}", sender.channel(), sender.getClass().getSimpleName());
            }
            if (resolved.isEmpty()) {
                log.warn("[alarm] NotifySender 扩展点无任何实现，告警事件没有通知出口");
            }
            this.cachedSenders = resolved = List.copyOf(resolved);
            return resolved;
        }
    }

      /**
       * 构建触发时的规则配置快照（JSON，仅保留判定所需的核心配置）
       *
       * @param rule 规则
       * @return 快照 JSON，异常时返回 空
       */
    private String buildRuleSnapshot(MsMonitorAlarmRule rule) {
        try {
            var map = new java.util.LinkedHashMap<String, Object>();
            map.put("policyId", rule.getMsMonitorAlarmRulePolicyId());
            map.put("monitorId", rule.getMsMonitorAlarmRuleMonitorId());
            map.put("metricName", rule.getMsMonitorAlarmRuleMetricName());
            map.put("condition", rule.getMsMonitorAlarmRuleCondition());
            map.put("threshold", rule.getMsMonitorAlarmRuleThreshold() == null ? null : rule.getMsMonitorAlarmRuleThreshold().toPlainString());
            map.put("consecutiveCount", rule.getMsMonitorAlarmRuleConsecutiveCount());
            map.put("silenceMinutes", rule.getMsMonitorAlarmRuleSilenceMinutes());
            map.put("level", rule.getMsMonitorAlarmRuleLevel());
            map.put("enabled", rule.getMsMonitorAlarmRuleEnabled());
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            log.debug("[alarm] 规则快照序列化失败 rule={}: {}", rule != null ? rule.getId() : null, e.getMessage());
            return null;
        }
    }

      /**
       * 校验规则参数
       *
       * @param rule 规则
       * @return 校验失败结果，合法返回 空
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
       * @param cmp       compare转为 结果
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
       * @param ruleId   规则 标识
       * @param monitorId 目标 标识
       * @return 键
       */
    private String counterKey(Long ruleId, Long monitorId) {
        return ruleId + ":" + monitorId;
    }

      /**
       * 反查监控项绑定的服务器主机
       *
       * at param monitorId 监控项 标识
       * at return 主机 标识，未绑定返回 空
       */
    private Long resolveMonitorHostId(Long monitorId) {
        try {
            MsMonitor monitor = monitorMapper.selectById(monitorId);
            return monitor == null ? null : monitor.getMsServerHostId();
        } catch (Exception e) {
            log.debug("[alarm] 监控项主机反查失败 monitorId={}: {}", monitorId, e.getMessage());
            return null;
        }
    }

      /**
       * 策略归属是否允许该主机命中
       *
       * <p>策略未归属主机（全局）时对所有主机生效；归属主机的策略只在这台主机上生效。
       * 目标侧拿不到主机归属（纯指标类监控项）时按全局处理，
       * 否则「项目守护」这类与主机无关的阈值会因为查不到主机而全部失效。</p>
       *
       * at param rule   规则（已带策略归属）
       * at param hostId 主机 标识
       * at return 是否允许命中
       */
    private boolean hostAllows(MsMonitorAlarmRule rule, Long hostId) {
        Long policyHostId = rule.getMsMonitorAlarmRulePolicyHostId();
        return policyHostId == null || hostId == null || policyHostId.equals(hostId);
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
      /**
       * 新增维护窗口
      */
    public ReturnResult<MsMonitorAlarmMaintenanceWindow> createMaintenanceWindow(
            MsMonitorAlarmMaintenanceWindow window) {
        if (window == null || isBlank(window.getWindowName())) {
            return ReturnResult.error("窗口名称不能为空");
        }
        String error = validateWindowTime(window.getStartTime(), window.getEndTime());
        if (error != null) {
            return ReturnResult.error(error);
        }
        window.setMsMonitorAlarmMaintenanceWindowLevel(normalizeLevel(window.getMsMonitorAlarmMaintenanceWindowLevel()));
        window.setMsMonitorAlarmMaintenanceWindowEnabled(window.getMsMonitorAlarmMaintenanceWindowEnabled() == null ? 1 : window
            .getMsMonitorAlarmMaintenanceWindowEnabled());
        maintenanceWindowMapper.insert(window);
        return ReturnResult.ok(window);
    }

    @Override
      /**
       * 更新维护窗口
      */
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
            exists.setMsMonitorAlarmMaintenanceWindowLevel(normalizeLevel(window.getMsMonitorAlarmMaintenanceWindowLevel()));
        }
        if (window.getStartTime() != null) {
            exists.setStartTime(window.getStartTime());
        }
        if (window.getEndTime() != null) {
            exists.setEndTime(window.getEndTime());
        }
        String error = validateWindowTime(exists.getStartTime(), exists.getEndTime());
        if (error != null) {
            return ReturnResult.error(error);
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
      /**
       * 删除维护窗口
       * @param id 长整数
       * @return 返回值
      */
    public ReturnResult<Void> deleteMaintenanceWindow(Long id) {
        maintenanceWindowMapper.deleteById(id);
        return ReturnResult.success();
    }

    @Override
      /**
       * 维护窗口列表
      */
    public ReturnResult<List<MsMonitorAlarmMaintenanceWindow>> listMaintenanceWindows(
            Long policyId) {
        LambdaQueryWrapper<MsMonitorAlarmMaintenanceWindow> wrapper = new LambdaQueryWrapper<MsMonitorAlarmMaintenanceWindow>()
                .eq(policyId != null, MsMonitorAlarmMaintenanceWindow::getPolicyId, policyId)
                .orderByDesc(MsMonitorAlarmMaintenanceWindow::getId);
        return ReturnResult.list(maintenanceWindowMapper.selectList(wrapper));
    }

    @Override
      /**
       * 测试告警规则
       * @param metricName 字符串
       * @param monitorId 长整数
       * @param ruleId 长整数
       * @param value 双精度数
       * @return 返回值
      */
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
                case "GT":
                    conditionHit = cmp > 0;
                    break;
                case "GE":
                    conditionHit = cmp >= 0;
                    break;
                case "EQ":
                    conditionHit = cmp == 0;
                    break;
                case "LE":
                    conditionHit = cmp <= 0;
                    break;
                case "LT":
                    conditionHit = cmp < 0;
                    break;
                default:
                    conditionHit = false;
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

    @Override
    public ReturnResult<AlarmStatsSummaryVO> statsSummary() {
        long todayTotal = eventMapper.selectCount(new LambdaQueryWrapper<MsMonitorAlarmEvent>()
                .ge(MsMonitorAlarmEvent::getMsMonitorAlarmEventTriggerTime, LocalDate.now().atStartOfDay()));
        Map<String, Long> levelDistribution = new LinkedHashMap<>();
        for (Map<String, Object> row : eventMapper.selectMaps(new QueryWrapper<MsMonitorAlarmEvent>()
                .select("ms_monitor_alarm_event_level AS lvl", "COUNT(1) AS cnt")
                .groupBy("ms_monitor_alarm_event_level"))) {
              // H2 返回未加引号的别名为大写，用大小写无关视图读取以兼容 MySQL
            Map<String, Object> ci = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            ci.putAll(row);
            Object level = ci.get("lvl");
            Object cnt = ci.get("cnt");
            if (level != null && cnt instanceof Number n) {
                levelDistribution.put(String.valueOf(level), n.longValue());
            }
        }
        return ReturnResult.ok(new AlarmStatsSummaryVO(todayTotal, countByStatus("NEW"),
                countByStatus("PROCESSED"), countByStatus("FALSE_ALARM"), levelDistribution));
    }

    @Override
    public ReturnResult<List<AlarmTrendPointVO>> statsTrend(int hours) {
        int span = hours <= 0 ? 24 : Math.min(hours, MAX_TREND_HOURS);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime first = now.minusHours(span).truncatedTo(ChronoUnit.HOURS).plusHours(1);
        List<MsMonitorAlarmEvent> events = eventMapper.selectList(new LambdaQueryWrapper<MsMonitorAlarmEvent>()
                .select(MsMonitorAlarmEvent::getMsMonitorAlarmEventTriggerTime)
                .ge(MsMonitorAlarmEvent::getMsMonitorAlarmEventTriggerTime, first));

        Map<LocalDateTime, Long> buckets = new LinkedHashMap<>();
        for (LocalDateTime cursor = first; !cursor.isAfter(now); cursor = cursor.plusHours(1)) {
            buckets.put(cursor, 0L);
        }
        for (MsMonitorAlarmEvent event : events) {
            LocalDateTime triggered = event.getMsMonitorAlarmEventTriggerTime();
            if (triggered != null) {
                buckets.computeIfPresent(triggered.truncatedTo(ChronoUnit.HOURS), (key, count) -> count + 1);
            }
        }

        DateTimeFormatter label = span <= DAY_SPAN_HOURS
                ? DateTimeFormatter.ofPattern("HH:00")
                : DateTimeFormatter.ofPattern("MM-dd HH:00");
        List<AlarmTrendPointVO> series = new ArrayList<>(buckets.size());
        for (Map.Entry<LocalDateTime, Long> bucket : buckets.entrySet()) {
            series.add(new AlarmTrendPointVO(label.format(bucket.getKey()), bucket.getValue()));
        }
        return ReturnResult.list(series);
    }

      /**
       * 按状态统计事件数量
       *
       * @param status 事件状态
       * @return 数量
       */
    private long countByStatus(String status) {
        return eventMapper.selectCount(new LambdaQueryWrapper<MsMonitorAlarmEvent>()
                .eq(MsMonitorAlarmEvent::getMsMonitorAlarmEventStatus, status));
    }
}
