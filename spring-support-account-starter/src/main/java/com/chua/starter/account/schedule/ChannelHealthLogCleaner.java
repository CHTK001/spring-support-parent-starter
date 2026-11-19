package com.chua.starter.account.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.account.channel.mapper.ChannelHealthLogMapper;
import com.chua.starter.account.channel.store.ChannelHealthLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 渠道健康历史清理器。
 *
 * <p>{@code sys_account_channel_health_log} 是追加式时序表（每次探针落一行），
 * 60s 探针间隔下每连接每天约 1440 行；本任务每日清理超过保留期的行，
 * 防止表无限增长。保留期可通过 {@code plugin.account.channel-health-log.retention-days}
 * 调整（默认 7 天）。</p>
 *
 * <p><b>副作用控制（R5）</b>：{@code @ConditionalOnProperty} 按
 * {@code plugin.account.channel-health-log.cleaner-enable} 控制（默认开启）；
 * 不需要健康趋势的宿主可显式关掉，Bean 不注册、任务不进宿主。</p>
 *
 * @author CH
 * @since 4.0.0.66
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(prefix = "plugin.account.channel-health-log", name = "cleaner-enable", havingValue = "true", matchIfMissing = true)
public class ChannelHealthLogCleaner {

    /**
     * 兜底保留天数（配置值非法时使用）
     */
    private static final int DEFAULT_RETENTION_DAYS = 7;

    /**
     * 健康历史表
     */
    private final ChannelHealthLogMapper healthLogMapper;

    /**
     * 保留天数（可由宿主配置覆盖）
     */
    @Value("${plugin.account.channel-health-log.retention-days:7}")
    private int retentionDays;

    /**
     * 每日 03:30 清理超过保留期的健康历史行。
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanExpired() {
        int days = retentionDays > 0 ? retentionDays : DEFAULT_RETENTION_DAYS;
        long cutoff = System.currentTimeMillis() - days * 24L * 3600_000L;
        int deleted = healthLogMapper.delete(new LambdaQueryWrapper<ChannelHealthLog>()
                .lt(ChannelHealthLog::getCreatedAt, cutoff));
        if (deleted > 0) {
            log.info("[ChannelHealthLogCleaner] 清理过期健康历史 {} 行（保留 {} 天）", deleted, days);
        }
    }
}
