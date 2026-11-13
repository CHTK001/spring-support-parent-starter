package com.chua.starter.monitor.support.scheduler;

import com.chua.starter.monitor.support.service.ServerOperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 服务节点操作日志保留窗口调度器。
 *
 * <p>{@code ms_server_oper_log} 是只增表：每个写操作一行。没有保留窗口的话
 * 这张表会持续增长，而超过保留期的日志对排查既没有价值还要占着
 * {@code (node_id, create_time)} 索引的空间。</p>
 *
 * <p>默认保留 90 天：比指标（7 天）长得多，因为指标是机器吐的、可再生，
 * 而操作日志常常是「上个月那次误删是谁干的」的唯一证据。</p>
 *
 * <p>用 {@link ObjectProvider} 可选装配：日志服务没引入时本调度器不工作，
 * 而不是让整个应用启动失败。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServerOperLogScheduler {

    /**
     * 日志保留天数固定 90 天。
     *
     * <p>不用 {@code @Value} 占位符注入，原因同调度注解：宿主早期创建链上
     * resolver 未就绪时，原样字符串转 int 会直接炸掉 bean 创建。</p>
     */
    // [P3C A20 豁免]
    private int retentionDays = 90;

    private final ObjectProvider<ServerOperLogService> operLogServiceProvider;

    /**
     * 滚动清理超期日志。
     *
     * <p>失败不外抛：清理失败只是表涨得快一点，不该影响业务请求。</p>
     *
     * <p>cron 用常量不用占位符：宿主早期创建链上 embedded value resolver 未就绪时，
     * 占位符会原样进入 cron 解析器炸掉启动。</p>
     */
    @Scheduled(cron = "0 45 4 * * ?")
    public void purgeExpired() {
        ServerOperLogService service = operLogServiceProvider.getIfAvailable();
        if (service == null || retentionDays <= 0) {
            return;
        }
        try {
            int removed = service.purgeOlderThan(retentionDays);
            if (removed > 0) {
                log.info("[server-oper-log] 清理 {} 天前的操作日志 {} 行", retentionDays, removed);
            }
        } catch (RuntimeException e) {
            log.warn("[server-oper-log] 清理过期日志失败 reason={}", e.getMessage());
        }
    }
}
