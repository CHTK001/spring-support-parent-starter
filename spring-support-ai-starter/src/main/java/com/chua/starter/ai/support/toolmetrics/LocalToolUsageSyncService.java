// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.toolmetrics;

import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.datasearch.usage.spi.UsageParser;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.entity.SysAiUsage;
import com.chua.starter.ai.support.mapper.SysAiUsageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 本地 AI 工具用量同步服务。
 *
 * <p>通过 datasearch 的 {@link UsageParser} SPI 读取本机各 AI 工具
 * （Claude 编码、Codex++、打开编码 等）的本地用量记录，
 * 去重后写入 sys_AI_usage 表（群体名称 = '本地'），供前端展示。</p>
 *
 * <p>去重键：requestId = "{解析器}:{哈希(provider|model|start|in|out)}"。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalToolUsageSyncService {

    /**
     * 用量 映射器
    */
    private final SysAiUsageMapper sysAiUsageMapper;

    /**
     * 全局设置读取器（解析目录配置 usage_parse_dirs）
     */
    private final GlobalSettingReader settingReader;

    /**
     * 同步结果。
     * @param scanned scanned
     * @param inserted 插入
     * @return 同步结果的结果
     */
    public record SyncResult(int scanned, int inserted) {
    }

    /**
     * 同步全部本地工具用量。
     *
     * @return 扫描数与新增数
     */
    public SyncResult syncAll() {
        int scanned = 0;
        int inserted = 0;
        for (UsageParser parser : ServiceProvider.of(UsageParser.class).list(resolveParseHomes()).values()) {
            String source = parser.name();
            try {
                List<AiUsage> batch = new ArrayList<>();
                // 响应式流：限流消费，避免一次性全量装载
                parser.streamAll()
                        .doOnNext(batch::add)
                        .blockLast(java.time.Duration.ofSeconds(120));
                scanned += batch.size();
                for (AiUsage u : batch) {
                    if (insertOne(source, u)) {
                        inserted++;
                    }
                }
            } catch (Exception e) {
                log.warn("[LocalUsageSync] 解析器[{}]同步失败: {}", source, e.getMessage());
            }
        }
        log.info("[LocalUsageSync] 完成：扫描 {} 条，新增 {} 条", scanned, inserted);
        /**
         * 同步结果。
         * @param scanned scanned
         * @param inserted 插入
         * @return 同步结果的结果
         */
        return new SyncResult(scanned, inserted);
    }

    /**
     * 解析本地用量根目录配置（{@code usage_parse_dirs}）。
     *
     * <p>配置值为逗号分隔的附加用户主目录；当前用户主目录（{@code user.home}）
     * 始终参与解析并排在首位，去重后以逗号分隔串返回，
     * 供 {@code UsageParser} SPI 的 csv 构造参数使用。</p>
     *
     * @return 逗号分隔的解析根目录列表（至少含当前用户主目录）
     */
    private String resolveParseHomes() {
        LinkedHashSet<String> homes = new LinkedHashSet<>();
        homes.add(System.getProperty("user.home", "."));
        try {
            String raw = settingReader.getStr("global", "usage_parse_dirs");
            if (raw != null && !raw.isBlank()) {
                for (String part : raw.split("[,，;；\\n\\r]+")) {
                    String dir = part.trim();
                    if (!dir.isEmpty()) {
                        homes.add(dir);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[LocalUsageSync] 读取解析目录配置失败，仅解析当前用户主目录: {}", e.getMessage());
        }
        String joined = String.join(",", homes);
        if (homes.size() > 1) {
            log.info("[LocalUsageSync] 启用多目录用量解析: {}", joined);
        } else {
            log.debug("[LocalUsageSync] 用量解析目录: {}", joined);
        }
        return joined;
    }

    /**
     * 单条落库（请求标识 去重）。
     *
     * @param source 解析器名
     * @param u      用量记录
     * @return true 表示新增写入
     */
    private boolean insertOne(String source, AiUsage u) {
        if (u == null) { return false; }
        String requestId = dedupKey(source, u);
        Long exists = sysAiUsageMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysAiUsage>lambdaQuery()
                        .eq(SysAiUsage::getSysAiUsageRequestId, requestId));
        if (exists != null && exists > 0) { return false; }
        SysAiUsage row = new SysAiUsage();
        row.setSysAiUsageGroupName("local");
        row.setSysAiUsageProvider(u.getProvider() == null ? source : u.getProvider());
        row.setSysAiUsageModel(u.getModel());
        row.setSysAiUsageCallType("local-tool");
        row.setSysAiUsagePromptTokens(u.getInputTokens());
        row.setSysAiUsageCompletionTokens(u.getOutputTokens());
        row.setSysAiUsageTotalTokens(u.getTotalTokens());
        row.setSysAiUsageCachedTokens(u.getCacheTokens());
        row.setSysAiUsageTotalLatency(u.getDurationMillis());
        row.setSysAiUsageFirstTokenLatency(u.getFirstTokenLatencyMillis());
        // 解码速度 / 端到端吞吐
        row.setSysAiUsageTokensPerSecond(u.tokensPerSecond());
        row.setSysAiUsageTotalTokensPerSecond(u.totalTokensPerSecond());
        Double inCost = u.getInputCost() == null ? null : u.getInputCost().doubleValue();
        Double outCost = u.getOutputCost() == null ? null : u.getOutputCost().doubleValue();
        Double totCost = u.getTotalCost() == null ? null : u.getTotalCost().doubleValue();
        if (totCost == null && (inCost != null || outCost != null)) {
            totCost = nvl(inCost) + nvl(outCost);
        }
        row.setSysAiUsageInputCost(inCost);
        row.setSysAiUsageOutputCost(outCost);
        row.setSysAiUsageTotalCost(totCost);
        row.setSysAiUsageRequestId(requestId);
        row.setSysAiUsageCallTime(toDateTime(u.getStartTime()));
        try {
            sysAiUsageMapper.insert(row);
            return true;
        } catch (Exception e) {
            log.debug("[LocalUsageSync] 插入失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 去重键
     *
     * @param source 源
     * @param u u
     * @return 去重Key的结果
     */
    private static String dedupKey(String source, AiUsage u) {
        String base = source + '|' + u.getProvider() + '|' + u.getModel() + '|'
                + u.getStartTime() + '|' + u.getInputTokens() + '|' + u.getOutputTokens();
        return source + ':' + Integer.toHexString(base.hashCode());
    }

    /**
     * 转为日期时间。
     * @param ms ms
     * @return 转为日期时间的结果
     */
    private static LocalDateTime toDateTime(Long ms) {
        return ms == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    /**
     * nvl。
     * @param v v
     * @return nvl的结果
     */
    private static double nvl(Double v) {
        return v == null ? 0.0 : v;
    }
}