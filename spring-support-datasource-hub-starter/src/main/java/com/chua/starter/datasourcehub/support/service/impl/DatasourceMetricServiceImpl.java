// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.entity.MsDatasourceMetric;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceMapper;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceMetricMapper;
import com.chua.starter.datasourcehub.support.model.DataSourceTestVO;
import com.chua.starter.datasourcehub.support.model.DatasourceMetricPushVO;
import com.chua.starter.datasourcehub.support.model.DatasourceMetricTrendVO;
import com.chua.starter.datasourcehub.support.support.DatasourceProbeSupport;
import com.chua.starter.datasourcehub.support.service.DatasourceMetricService;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据源连通性指标服务实现。
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceMetricServiceImpl implements DatasourceMetricService {

    /**
     * 趋势返回的点数上限。
     *
     * <p>不是性能问题，是可读性问题：把上千个采样点丢给 echarts 画出来的曲线
     * 没有任何可读性，只会让浏览器卡。前端要的是趋势，不是原始数据。</p>
     */
    private static final int MAX_POINTS = 240;

    /**
     * 默认回溯小时数。
     */
    private static final int DEFAULT_HOURS = 6;

    private final DatasourceService datasourceService;

    private final MsDatasourceMetricMapper metricMapper;

    private final MsDatasourceMapper datasourceMapper;

    @Override
    public ReturnResult<DataSourceTestVO> sample(Long datasourceId) {
        if (datasourceId == null) {
            return ReturnResult.error("缺少数据源 ID");
        }
        MsDatasource row;
        try {
            row = datasourceMapper.selectById(datasourceId);
        } catch (RuntimeException e) {
            return ReturnResult.error("读取数据源失败: " + e.getMessage());
        }
        if (row == null) {
            return ReturnResult.error("数据源不存在: " + datasourceId);
        }

        // probeOnly 而不是 test：指标采集是定时行为，不该把台账的健康状态
        // 刷成「刚刚测过」，否则用户只是打开图表就改变了左栏的正常/异常徽标。
        ReturnResult<DataSourceTestVO> probed = datasourceService.probeOnly(datasourceId);
        if (!probed.isOk() || probed.getData() == null) {
            // 探测本身失败（例如该类型没有 probe 实现）也留一条「未判定」的点：
            // 完全不记会让曲线在「探测不了」和「没采过」之间无法区分。
            persist(datasourceId, row.getDatasourceType(), null, null, null, null, null,
                    probed.getMsg() == null ? "探测未执行" : probed.getMsg(), null);
            return probed;
        }
        DataSourceTestVO vo = probed.getData();
        persist(datasourceId, row.getDatasourceType(),
                Boolean.valueOf(vo.success()), vo.connectLatencyMs(), vo.sessionLatencyMs(),
                vo.tunneled(), vo.connectionCount(),
                vo.success() ? null : vo.message(), vo.serverStats());
        return probed;
    }

    @Override
    public ReturnResult<DatasourceMetricPushVO> sampleForPush(Long datasourceId, int windowPoints) {
        ReturnResult<DataSourceTestVO> probed = sample(datasourceId);
        if (!probed.isOk() || probed.getData() == null) {
            return ReturnResult.error(probed.getMsg() == null ? "探测未成功" : probed.getMsg());
        }
        DataSourceTestVO vo = probed.getData();
        int window = windowPoints <= 0 ? 30 : Math.min(windowPoints, MAX_POINTS);
        ReturnResult<DatasourceMetricTrendVO> recent =
                trend(datasourceId, 1, Math.max(1, 3600 / window));
        return ReturnResult.success(new DatasourceMetricPushVO(
                datasourceId,
                vo.success(),
                vo.message(),
                vo.connectLatencyMs(),
                vo.sessionLatencyMs(),
                vo.tunneled(),
                vo.connectionCount(),
                vo.serverStats(),
                recent.isOk() ? recent.getData() : null));
    }

    @Override
    public ReturnResult<DatasourceMetricTrendVO> trend(Long datasourceId, Integer hours, Integer intervalSec) {
        if (datasourceId == null) {
            return ReturnResult.error("缺少数据源 ID");
        }
        int spanHours = hours == null || hours <= 0 ? DEFAULT_HOURS : Math.min(hours, 24 * 7);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(spanHours);

        List<MsDatasourceMetric> rows = metricMapper.selectList(
                new LambdaQueryWrapper<MsDatasourceMetric>()
                        .eq(MsDatasourceMetric::getDatasourceId, datasourceId)
                        .ge(MsDatasourceMetric::getCreateTime, from)
                        .le(MsDatasourceMetric::getCreateTime, to)
                        .orderByAsc(MsDatasourceMetric::getCreateTime)
                        .orderByAsc(MsDatasourceMetric::getId));

        if (rows.isEmpty()) {
            return ReturnResult.success(DatasourceMetricTrendVO.empty(datasourceId, from, to,
                    intervalSec == null ? 0 : intervalSec,
                    "该数据源在被观测之前没有采样点。指标是「有人看才采」的，"
                            + "打开本页后下一次推送（约 " + "1 分钟内" + "）就会开始有数据。"));
        }

        int interval = intervalSec == null || intervalSec <= 0 ? 60 : intervalSec;
        List<MsDatasourceMetric> picked = thin(rows, interval, MAX_POINTS);
        int usedInterval = picked.size() >= 2
                ? (int) Duration.between(picked.get(0).getCreateTime(), picked.get(picked.size() - 1).getCreateTime())
                        .toSeconds() / (picked.size() - 1)
                : interval;

        List<DatasourceMetricTrendVO.Point> points = picked.stream()
                .map(r -> new DatasourceMetricTrendVO.Point(
                        r.getCreateTime(),
                        r.getSuccess() == null ? null : r.getSuccess() == 1,
                        r.getConnectLatency(),
                        r.getSessionLatency(),
                        r.getTunneled() == null ? null : r.getTunneled() == 1,
                        r.getErrorMessage(),
                        r.getConnectionCount(),
                        new DatasourceProbeSupport.ServerStats(
                                r.getSlowQueries(),
                                r.getThreadsRunning(),
                                r.getQuestions(),
                                r.getUptime(),
                                r.getMaxUsedConnections(),
                                r.getMemoryBytes()
                        )))
                .toList();
        return ReturnResult.success(
                new DatasourceMetricTrendVO(datasourceId, from, to, usedInterval, points, null));
    }

    @Override
    public void purge(Long datasourceId) {
        if (datasourceId == null) {
            return;
        }
        metricMapper.delete(new LambdaQueryWrapper<MsDatasourceMetric>()
                .eq(MsDatasourceMetric::getDatasourceId, datasourceId));
    }

    @Override
    public int purgeOlderThan(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        // 整批删而不是 limit 分批：这批数据的价值只在于「最近 N 天」，
        // 超期的行没有任何回溯用途，而表上已有 (datasource_id, create_time) 索引可走。
        return metricMapper.delete(new LambdaQueryWrapper<MsDatasourceMetric>()
                .lt(MsDatasourceMetric::getCreateTime, threshold));
    }

    /**
     * 落一个采样点。
     *
     * @param datasourceId   数据源 ID
     * @param type           数据类型
     * @param success        连通与否
     * @param connectLatency 建连耗时
     * @param sessionLatency 会话耗时
     * @param tunneled       是否经隧道
     * @param connectionCount 采样时刻客户端连接数（可空）
     * @param error          失败原因
     * @param serverStats    采样时刻服务器运行指标（可空）
     */
    private void persist(Long datasourceId, String type, Boolean success, Long connectLatency,
                         Long sessionLatency, Boolean tunneled, Long connectionCount, String error,
                         DatasourceProbeSupport.ServerStats serverStats) {
        try {
            MsDatasourceMetric row = new MsDatasourceMetric();
            row.setDatasourceId(datasourceId);
            row.setDatasourceType(type == null ? "" : type);
            row.setSuccess(success == null ? null : (success ? 1 : 0));
            row.setConnectLatency(connectLatency);
            row.setSessionLatency(sessionLatency);
            row.setConnectionCount(connectionCount);
            row.setTunneled(tunneled == null ? null : (tunneled ? 1 : 0));
            if (serverStats != null) {
                row.setSlowQueries(serverStats.slowQueries());
                row.setThreadsRunning(serverStats.threadsRunning());
                row.setQuestions(serverStats.questions());
                row.setUptime(serverStats.uptimeSeconds());
                row.setMaxUsedConnections(serverStats.maxUsedConnections());
                row.setMemoryBytes(serverStats.memoryBytes());
            }
            if (error != null) {
                row.setErrorMessage(error.length() > 500 ? error.substring(0, 500) : error);
            }
            row.setCreateTime(LocalDateTime.now());
            metricMapper.insert(row);
        } catch (RuntimeException e) {
            // 采样落库失败只影响曲线，不该把推送链路打断
            log.warn("[datasource-metric] 采样落库失败 datasourceId={} reason={}", datasourceId, e.getMessage());
        }
    }

    /**
     * 按时间间隔抽稀。
     *
     * <p>始终保留<b>最后一个</b>点：抽稀若按「间隔到了才取」实现，最后一个点
     * 常常因为还没到间隔而被丢掉，曲线会停在几分钟前，实时看起来就是「卡住了」。</p>
     *
     * @param rows     全部采样点（按时间升序）
     * @param intervalSec 最小间隔（秒）
     * @param maxPoints 最多保留多少点
     * @return 抽稀后的点
     */
    private List<MsDatasourceMetric> thin(List<MsDatasourceMetric> rows, int intervalSec, int maxPoints) {
        if (rows.size() <= maxPoints && withinInterval(rows, intervalSec)) {
            return rows;
        }
        int step = 1;
        if (rows.size() > maxPoints) {
            step = (int) Math.ceil((double) rows.size() / maxPoints);
        } else {
            step = intervalStep(rows, intervalSec);
        }
        List<MsDatasourceMetric> picked = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i += step) {
            picked.add(rows.get(i));
        }
        MsDatasourceMetric last = rows.get(rows.size() - 1);
        // 最后一点必须留下，但追加它可能让点数变成 maxPoints+1 —— 多一个点无所谓，
        // 超上限被前端截掉才是问题：被截的正是最新的那个点。
        if (picked.get(picked.size() - 1) != last) {
            if (picked.size() >= maxPoints) {
                picked.remove(picked.size() - 1);
            }
            picked.add(last);
        }
        return picked;
    }

    /**
     * 相邻点间隔都满足时不需要抽稀。
     *
     * @param rows 采样点
     * @param intervalSec 最小间隔
     * @return 是否满足
     */
    private boolean withinInterval(List<MsDatasourceMetric> rows, int intervalSec) {
        for (int i = 1; i < rows.size(); i++) {
            long gap = Duration.between(rows.get(i - 1).getCreateTime(), rows.get(i).getCreateTime()).toSeconds();
            if (gap < intervalSec) {
                return false;
            }
        }
        return true;
    }

    /**
     * 算出需要按多少个点抽一次，才能把相邻间隔拉到目标值以上。
     *
     * @param rows 采样点
     * @param intervalSec 目标间隔
     * @return 步长（至少 1）
     */
    private int intervalStep(List<MsDatasourceMetric> rows, int intervalSec) {
        if (rows.size() < 2) {
            return 1;
        }
        long total = Duration.between(rows.get(0).getCreateTime(),
                rows.get(rows.size() - 1).getCreateTime()).toSeconds();
        long step = (long) Math.ceil(intervalSec * rows.size() / (double) Math.max(1L, total));
        return (int) Math.max(1L, step);
    }}