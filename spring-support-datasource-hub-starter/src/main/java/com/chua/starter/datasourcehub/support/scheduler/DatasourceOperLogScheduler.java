package com.chua.starter.datasourcehub.support.scheduler;

import com.chua.starter.datasourcehub.support.service.DatasourceOperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 操作日志保留窗口调度器。
 *
 * <p>{@code ms_datasource_oper_log} 是只增表：每个写操作一行，而数据源中心的
 * 写操作频率随使用量线性上升。没有保留窗口的话这张表会成为整个库里增长最快的表，
 * 而超过保留期的日志对排查既没有价值还要占着 {@code (datasource_id, create_time)}
 * 索引的空间。</p>
 *
 * <p>默认保留 90 天：比指标（7 天）长得多，因为指标是机器吐的、可再生，
 * 而操作日志常常是「上个月那次误删是谁干的」的唯一证据。</p>
 *
 * <p>用 {@link ObjectProvider} 可选装配：日志服务没引入时本调度器不工作，
 * 而不是让整个应用启动失败。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceOperLogScheduler {

    /**
     * 日志保留天数。
     */
    @Value("${monitor.datasource.oper-log-retention-days:90}")
    private int retentionDays = 90;

    private final ObjectProvider<DatasourceOperLogService> operLogServiceProvider;

    /**
     * 滚动清理超期日志。
     *
     * <p>失败不外抛：清理失败只是表涨得快一点，不该影响业务请求。</p>
     */
    @Scheduled(cron = "${monitor.datasource.oper-log-purge-cron:0 40 4 * * ?}")
    public void purgeExpired() {
        DatasourceOperLogService service = operLogServiceProvider.getIfAvailable();
        if (service == null || retentionDays <= 0) {
            return;
        }
        try {
            int removed = service.purgeOlderThan(retentionDays);
            if (removed > 0) {
                log.info("[datasource-oper-log] 清理 {} 天前的操作日志 {} 行", retentionDays, removed);
            }
        } catch (RuntimeException e) {
            log.warn("[datasource-oper-log] 清理过期日志失败 reason={}", e.getMessage());
        }
    }
}