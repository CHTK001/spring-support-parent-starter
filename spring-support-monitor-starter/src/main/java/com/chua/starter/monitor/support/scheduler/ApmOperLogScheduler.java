package com.chua.starter.monitor.support.scheduler;

import com.chua.starter.monitor.support.service.ApmOperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * APM 操作日志保留窗口调度器。
 *
 * <p>{@code ms_apm_oper_log} 是只增表：每次改设置一行。APM 设置不是高频操作，
 * 增长很慢，但仍然是只增 —— 没有保留窗口就是「永远不清」，
 * 而超过保留期的日志对排查只剩占索引的份。</p>
 *
 * <p>默认保留 90 天，与服务节点日志一致：操作日志是「上个月谁改了设置」的
 * 唯一证据，不该像指标那样几天就清掉。</p>
 *
 * <p>用 {@link ObjectProvider} 可选装配：日志服务没引入时本调度器不工作，
 * 而不是让整个应用启动失败。</p>
 *
 * @author CH
 * @since 4.0.0.56
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApmOperLogScheduler {

    /**
     * 日志保留天数固定 90 天。
     *
     * <p>不用 {@code @Value} 占位符注入：宿主早期创建链上 resolver 未就绪时，
     * 原样字符串转 int 会直接炸掉 bean 创建。</p>
     */
    private int retentionDays = 90;

    private final ObjectProvider<ApmOperLogService> operLogServiceProvider;

    /**
     * 滚动清理超期日志。
     *
     * <p>失败不外抛：清理失败只是表涨得快一点，不该影响业务请求。</p>
     *
     * <p>cron 用常量不用占位符：宿主早期创建链上 embedded value resolver 未就绪时，
     * 占位符会原样进入 cron 解析器炸掉启动。</p>
     */
    @Scheduled(cron = "0 50 4 * * ?")
    public void purgeExpired() {
        ApmOperLogService service = operLogServiceProvider.getIfAvailable();
        if (service == null || retentionDays <= 0) {
            return;
        }
        try {
            int removed = service.purgeOlderThan(retentionDays);
            if (removed > 0) {
                log.info("[apm-oper-log] 清理 {} 天前的操作日志 {} 行", retentionDays, removed);
            }
        } catch (RuntimeException e) {
            log.warn("[apm-oper-log] 清理过期日志失败 reason={}", e.getMessage());
        }
    }
}
