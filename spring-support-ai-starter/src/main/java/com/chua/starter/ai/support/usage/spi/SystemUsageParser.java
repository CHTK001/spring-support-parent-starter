package com.chua.starter.ai.support.usage.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.datasearch.usage.spi.UsageParser;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统用量解析器 — 从 sys_AI_usage 数据库表读取 AI 调用用量数据。
 *
 * <p>与外部工具（opencode、codex 等）的本地文件解析器不同，
 * 本解析器直接从系统数据库读取已记录的 LLM 调用用量。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemUsageParser implements UsageParser {

    private final SysAiUsageMapper usageMapper; // usage映射器

    @Override
    public String name() {
        return "system";
    }

    @Override
    public Flux<AiUsage> streamAll() {
        List<SysAiUsage> records = usageMapper.selectList(
                new LambdaQueryWrapper<SysAiUsage>()
                        .isNotNull(SysAiUsage::getSysAiUsageTotalTokens)
                        .orderByDesc(SysAiUsage::getSysAiUsageCallTime));
        log.debug("[SystemUsageParser] 读取 {} 条用量记录", records.size());
        List<AiUsage> result = new ArrayList<>();
        for (SysAiUsage u : records) {
            result.add(toAiUsage(u));
        }
        return Flux.fromIterable(result);
    }

    /**
     * 转为aiusage。
     * @param u u
     * @return 转为AIusage的结果
     */
    private AiUsage toAiUsage(SysAiUsage u) {
        AiUsage.AiUsageBuilder builder = AiUsage.builder()
                .provider(u.getSysAiUsageProvider())
                .model(u.getSysAiUsageModel())
                .inputTokens(u.getSysAiUsagePromptTokens())
                .outputTokens(u.getSysAiUsageCompletionTokens())
                .totalTokens(u.getSysAiUsageTotalTokens())
                .cacheTokens(u.getSysAiUsageCachedTokens())
                .startTime(u.getSysAiUsageCallTime() != null
                        ? u.getSysAiUsageCallTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null)
                .firstTokenLatencyMillis(u.getSysAiUsageFirstTokenLatency())
                .durationMillis(u.getSysAiUsageTotalLatency())
                .tokensPerSecond(u.getSysAiUsageTokensPerSecond())
                .totalTokensPerSecond(u.getSysAiUsageTotalTokensPerSecond())
                .currency("USD");
        if (u.getSysAiUsageTotalCost() != null) {
            builder.totalCost(java.math.BigDecimal.valueOf(u.getSysAiUsageTotalCost()));
        }
        if (u.getSysAiUsageInputCost() != null) {
            builder.inputCost(java.math.BigDecimal.valueOf(u.getSysAiUsageInputCost()));
        }
        if (u.getSysAiUsageOutputCost() != null) {
            builder.outputCost(java.math.BigDecimal.valueOf(u.getSysAiUsageOutputCost()));
        }
        return builder.build();
    }
}
