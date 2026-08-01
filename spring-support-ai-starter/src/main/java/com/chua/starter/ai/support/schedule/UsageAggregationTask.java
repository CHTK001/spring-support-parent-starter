package com.chua.starter.ai.support.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.entity.SysAiUsageDaily;
import com.chua.starter.ai.support.mapper.SysAiUsageDailyMapper;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 用量日聚合任务
 * 每天凌晨聚合前一日的用量数据
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsageAggregationTask {

    /**
     * 用量 Mapper
     */
    private final SysAiUsageMapper usageMapper;

    /**
     * 日汇总 Mapper
     */
    private final SysAiUsageDailyMapper dailyMapper;

    /**
     * 每天凌晨1点执行日聚合
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void aggregateDaily() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = yesterday.atTime(LocalTime.MAX);

        LambdaQueryWrapper<SysAiUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(SysAiUsage::getCallTime, start, end);
        List<SysAiUsage> list = usageMapper.selectList(wrapper);

        if (list.isEmpty()) {
            log.info("[UsageAggregation] {} 无用量数据", yesterday);
            return;
        }

        // 按 group, provider, model 分组聚合
        Map<String, List<SysAiUsage>> grouped = list.stream()
                .collect(Collectors.groupingBy(u -> u.getGroupName() + "|" + u.getProvider() + "|" + u.getModel()));

        for (Map.Entry<String, List<SysAiUsage>> entry : grouped.entrySet()) {
            String[] keys = entry.getKey().split("\\|");
            String groupName = keys[0];
            String provider = keys[1];
            String model = keys[2];
            List<SysAiUsage> items = entry.getValue();

            SysAiUsageDaily daily = new SysAiUsageDaily();
            daily.setStatDate(yesterday);
            daily.setGroupName(groupName);
            daily.setProvider(provider);
            daily.setModel(model);
            daily.setTotalCalls(items.size());
            daily.setTotalPromptTokens(items.stream().mapToInt(u -> u.getPromptTokens() != null ? u.getPromptTokens() : 0).sum());
            daily.setTotalCompletionTokens(items.stream().mapToInt(u -> u.getCompletionTokens() != null ? u.getCompletionTokens() : 0).sum());
            daily.setTotalTokens(items.stream().mapToInt(u -> u.getTotalTokens() != null ? u.getTotalTokens() : 0).sum());
            daily.setTotalCost(items.stream().mapToDouble(u -> u.getTotalCost() != null ? u.getTotalCost() : 0).sum());

            dailyMapper.insert(daily);
            log.debug("[UsageAggregation] 汇总: group={}, provider={}, model={}, calls={}",
                    groupName, provider, model, items.size());
        }
        log.info("[UsageAggregation] {} 聚合完成，共 {} 条记录，{} 个分组",
                yesterday, list.size(), grouped.size());
    }
}
