package com.chua.starter.monitor.support.scheduler;

import com.chua.starter.monitor.support.service.impl.TsharkCaptureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;

/**
 * 抓包数据保留清理任务。
 *
 * <p>定期间隔扫描过期会话并删除（会话 + 报文 + pcap + SQLite 工文 + 日志），
 * 同时检查会话目录总空间，满了也清。只负责「删」，不负责给前面的页面提供容量，
 * 避免一个没人看见的旧会话把地占满。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TsharkRetentionCleanupScheduler implements SchedulingConfigurer {

    private final TsharkCaptureService tsharkCaptureService;

    /**
     * 换单独的线程池（每次清理可能跨文件夹删几十 M 的份，不走公共调度器）
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setScheduler(Executors.newScheduledThreadPool(1));
    }

    /**
     * 每 30 分钟清理一次（nightly 风格，不在业务高峰跑）。
     */
    @Scheduled(fixedDelay = 1_800_000L, initialDelay = 60_000L)
    public void cleanup() {
        try {
            int deleted = this.tsharkCaptureService.cleanupRetention();
            log.info("[tshark] 保留清理完成，删除会话 {} 条", deleted);
        } catch (Exception e) {
            log.warn("[tshark] 保留清理异常", e);
        }
    }
}
