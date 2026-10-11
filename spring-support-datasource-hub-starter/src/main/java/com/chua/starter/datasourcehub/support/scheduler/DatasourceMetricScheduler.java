package com.chua.starter.datasourcehub.support.scheduler;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.metric.DatasourceMetricStreamRegistry;
import com.chua.starter.datasourcehub.support.model.DatasourceMetricPushVO;
import com.chua.starter.datasourcehub.support.service.DatasourceMetricService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据源指标采集调度器。
 *
 * <p><strong>采集范围由订阅决定，不由台账决定</strong>：每轮只探测
 * {@link DatasourceMetricStreamRegistry#watchedIds()} 里的数据源。
 * 没人看的台账不产生探测流量 —— 这是本模块与 nginx 指标调度器最大的不同，
 * 也是它敢用「真实连通性探测」而不是「读上次结果」的原因。</p>
 *
 * <p>代价要说清楚：因此历史只覆盖「被观测过的时段」，
 * 新建的数据源第一次打开监控指标页签时，曲线在一轮采集前是空的。
 * 前端据 {@code message} 字段显示这个状态，而不是显示一条平线。</p>
 *
 * <p>单个数据源探测失败不影响其它：一次失败只是少一个点，抛出去会让整轮停摆。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceMetricScheduler {

    /**
     * 随推送带上的最近点数。
     */
    private static final int PUSH_WINDOW_POINTS = 30;

    /**
     * 指标服务（可选依赖：未装配时本调度器不工作）
     */
    private final ObjectProvider<DatasourceMetricService> metricServiceProvider;

    /**
     * SSE 订阅者注册表
     */
    private final DatasourceMetricStreamRegistry streamRegistry;

    /**
     * 指标保留天数。
     *
     * <p>默认 7 天，与趋势查询允许的最大回溯窗口（24×7h）对齐：
     * 保留期短于查询窗口的话，用户拉到最远的那段永远是空的 ——
     * 界面上看不出区别，只会觉得「这个监控图没有历史」。</p>
     */
    @Value("${monitor.datasource.metric-retention-days:7}")
    private int metricRetentionDays = 7;

    /**
     * 定时探测被订阅的数据源。
     *
     * <p>首次延迟与轮询同长：应用刚启动时没人订阅，等一个周期再开始，
     * 免得多跑一轮空转。</p>
     */
    @Scheduled(fixedRateString = "${monitor.datasource.collect-interval-ms:60000}",
            initialDelayString = "${monitor.datasource.collect-interval-ms:60000}")
    public void collect() {
        DatasourceMetricService service = metricServiceProvider.getIfAvailable();
        if (service == null) {
            return;
        }
        Set<Long> watched = streamRegistry.watchedIds();
        if (watched.isEmpty()) {
            return;
        }
        int sent = 0;
        for (Long id : watched) {
            try {
                ReturnResult<DatasourceMetricPushVO> push = service.sampleForPush(id, PUSH_WINDOW_POINTS);
                if (!push.isOk() || push.getData() == null) {
                    // 采集失败也要让页面知道：否则曲线停在旧点上，
                    // 用户看到的是「一切正常」，而实际是「已经测不出来了」。
                    streamRegistry.broadcast(id, DatasourceMetricStreamRegistry.EVENT_METRIC,
                            new DatasourceMetricPushVO(id, null,
                                    push.getMsg() == null ? "探测失败" : push.getMsg(),
                                    null, null, null, null));
                    continue;
                }
                sent += streamRegistry.broadcast(id, DatasourceMetricStreamRegistry.EVENT_METRIC, push.getData());
            } catch (RuntimeException e) {
                log.warn("[datasource-metric] 采集失败 datasourceId={} reason={}", id, e.getMessage());
            }
        }
        log.debug("[datasource-metric] 本轮探测 {} 个数据源，推送 {} 次", watched.size(), sent);
    }

    /**
     * 滚动清理超保留期的历史指标。
     *
     * <p>频率与保留期解耦：清理是幂等的批处理，一天一次足够；
     * 真按采集周期（分钟级）跑，只会给索引平白添写压力。</p>
     *
     * <p>失败不外抛：清理失败最坏是表涨得快一点，不该把调度线程带崩，
     * 连带影响同一线程上的采集任务。</p>
     */
    @Scheduled(cron = "${monitor.datasource.metric-purge-cron:0 20 4 * * ?}")
    public void purgeExpired() {
        DatasourceMetricService service = metricServiceProvider.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            if (metricRetentionDays <= 0) {
                return;
            }
            int removed = service.purgeOlderThan(metricRetentionDays);
            if (removed > 0) {
                log.info("[datasource-metric] 清理 {} 天前的指标 {} 行", metricRetentionDays, removed);
            }
        } catch (RuntimeException e) {
            log.warn("[datasource-metric] 清理历史指标失败 reason={}", e.getMessage());
        }
    }
}