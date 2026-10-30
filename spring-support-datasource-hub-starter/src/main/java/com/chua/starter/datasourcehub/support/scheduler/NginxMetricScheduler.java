package com.chua.starter.datasourcehub.support.scheduler;

import com.chua.starter.datasourcehub.support.model.NginxMetricPushVO;
import com.chua.starter.datasourcehub.support.model.NginxRuntimeVO;
import com.chua.starter.datasourcehub.support.model.NginxTrendVO;
import com.chua.starter.datasourcehub.support.nginx.NginxMetricStreamRegistry;
import com.chua.starter.datasourcehub.support.service.NginxManageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nginx 指标采集调度器。
 *
 * <p>每 {@code monitor.nginx.collect-interval-ms}（默认 30s）读一次各 nginx 数据源的
 * {@code stub_status} 并落库，供趋势图使用。</p>
 *
 * <p><strong>为什么是 30s 而不是沿用监控的 10s</strong>：读 stub_status 走的是
 * 「新建 SSH 短连接 → 执行命令 → 读 HTTP」整条链路，10s 一次对被管主机会产生
 * 明显压力（连接数抖动 + 反复 fork 远端进程），而趋势图本身分钟级精度就够了。</p>
 *
 * <p>采集失败只记日志不抛出：某个数据源不可达不应影响其它数据源，也不应让调度线程挂掉。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NginxMetricScheduler {

    /**
     * nginx 管理服务（可选依赖：未装配时本调度器不工作）
     */
    private final ObjectProvider<NginxManageService> nginxServiceProvider;

    /**
     * SSE 订阅者注册表（可选依赖：未装配时只落库不推送）
     */
    private final ObjectProvider<NginxMetricStreamRegistry> streamRegistryProvider;

    /**
     * 定时采集 stub_status。
     *
     * <p>首次延迟 20s：避开应用刚启动时数据源尚未就绪的窗口，否则头几轮必然全失败并刷警告。</p>
     */
    @Scheduled(fixedRate = 30_000L, initialDelay = 20_000L)
    public void collect() {
        NginxManageService service = nginxServiceProvider.getIfAvailable();
        if (service == null) {
            return;
        }
        NginxMetricStreamRegistry streamRegistry = streamRegistryProvider.getIfAvailable();
        List<Long> ids = service.listNginxDatasourceIds();
        if (ids.isEmpty()) {
            return;
        }
        int total = 0;
        for (Long id : ids) {
            try {
                int written = service.collectMetric(id);
                total += written;
                if (streamRegistry != null) {
                    // 采集有落库才推趋势点：没写成时不推，前端曲线就不会出现「跳一个空档再跳回来」
                    if (written > 0) {
                        pushTrend(service, streamRegistry, id);
                    }
                    // 实时态与落库无关，照推：nginx 被 stop / 崩溃时页面要立刻变状态
                    pushRuntime(service, streamRegistry, id);
                }
            } catch (RuntimeException e) {
                log.warn("[nginx] 指标采集失败 datasourceId={} reason={}", id, e.getMessage());
            }
        }
        if (total > 0) {
            log.debug("[nginx] 指标采集完成 数据源 {} 个, 写入 {} 条", ids.size(), total);
        }
    }

    /**
     * 广播趋势新点。 <p>推送失败只记日志：SSE 是展示增强，订阅端异常不该中断本轮其它数据源的采集。</p>
     * @param id 数据源 id
     * @param service nginx 服务
     * @param streamRegistry 参数
     * @return 返回值
    */
    private void pushTrend(NginxManageService service, NginxMetricStreamRegistry streamRegistry, Long id) {
        try {
            List<NginxTrendVO.Point> points = service.latestPoints(id);
            if (points.isEmpty()) {
                return;
            }
            int sent = streamRegistry.broadcast(id, NginxMetricStreamRegistry.EVENT_METRIC,
                    new NginxMetricPushVO(id, points));
            log.trace("[nginx] 推送趋势点 datasourceId={} 点数={} 送达={}", id, points.size(), sent);
        } catch (RuntimeException e) {
            log.debug("[nginx] 推送趋势点失败 datasourceId={} reason={}", id, e.getMessage());
        }
    }

    /**
     * 广播实时运行态。
     * @param id 数据源 id
     * @param service nginx 服务
     * @param streamRegistry 参数
     * @return 返回值
    */
    private void pushRuntime(NginxManageService service, NginxMetricStreamRegistry streamRegistry, Long id) {
        try {
            NginxRuntimeVO runtime = service.collectRealtime(id);
            int sent = streamRegistry.broadcast(id, NginxMetricStreamRegistry.EVENT_RUNTIME, runtime);
            log.trace("[nginx] 推送运行态 datasourceId={} running={} 送达={}", id, runtime.running(), sent);
        } catch (RuntimeException e) {
            log.debug("[nginx] 推送运行态失败 datasourceId={} reason={}", id, e.getMessage());
        }
    }
}
