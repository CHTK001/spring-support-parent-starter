package com.chua.starter.datalake.controller;

import com.chua.common.support.lang.json.Json;
import com.chua.datalake.support.manager.SinkManager;
import com.chua.datalake.support.manager.SubscriberManager;
import com.chua.datalake.support.pipeline.DefaultPipelineManager;
import com.chua.datalake.support.pipeline.JdbcPipelineManager;
import com.chua.datalake.support.server.DatalakeServer;
import com.chua.datalake.support.sink.StatisticSink;
import com.chua.datalake.support.spi.pipeline.PipelineConfig;
import com.chua.datalake.support.spi.sink.DataSink;
import com.chua.datalake.support.subscriber.Subscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据中台管理端点（/v2/datalake/**）。
 *
 * <p>为控制台提供四类只读/变更能力：管线配置 CRUD（含 DSL 原文）、运行统计
 * （StatisticSink 计数）、已登记订阅器、已注册 Sink。数据面（管线执行、投递）
 * 不在本控制器职责内。</p>
 *
 * <p>仅当宿主装配了 {@link DatalakeServer}（即 {@code datalake.enabled=true}）时，
 * 本控制器才由 {@code DatalakeManageAutoConfiguration} 装配；未启用时端点整体 404，
 * 由前端呈现「数据中台未启用」空态。鉴权沿用宿主统一接口鉴权体系，不设免鉴权白名单。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@RestController
@RequestMapping("/v2/datalake")
public class DatalakeManageController {

    /**
     * 数据湖服务端（宿主已启用的数据中台实例）
     */
    private final DatalakeServer server;

    /**
     * 构造管理端点。
     *
     * @param server 数据湖服务端
     */
    public DatalakeManageController(DatalakeServer server) {
        this.server = server;
    }

    /**
     * 管线清单：id、DSL 原文、审计时间与运行计数。
     *
     * @return 管线列表（按 id 升序）
     */
    @GetMapping("/pipelines")
    public List<Map<String, Object>> pipelines() {
        DefaultPipelineManager manager = pipelineManager();
        List<Map<String, Object>> result = new ArrayList<>();
        for (JdbcPipelineManager.PipelineMeta meta : jdbcMeta(manager)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", meta.getId());
            row.put("dsl", manager.getPipeline(meta.getId()));
            row.put("gmtCreate", meta.getGmtCreate());
            row.put("gmtModified", meta.getGmtModified());
            row.put("count", statisticSink() == null ? 0L : statisticSink().countOf(meta.getId()));
            result.add(row);
        }
        return result;
    }

    /**
     * 单条管线详情（含 DSL 原文与运行计数）。
     *
     * @param id 管线标识
     * @return 管线详情
     */
    @GetMapping("/pipelines/{id}")
    public Map<String, Object> pipeline(@PathVariable("id") String id) {
        DefaultPipelineManager manager = pipelineManager();
        String dsl = manager.getPipeline(id);
        if (dsl == null) {
            throw new IllegalArgumentException("管线不存在: " + id);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("dsl", dsl);
        StatisticSink statistic = statisticSink();
        row.put("count", statistic == null ? 0L : statistic.countOf(id));
        return row;
    }

    /**
     * 新建或更新管线：先校验 DSL 可解析，再写穿数据库并更新引擎编译缓存。
     *
     * @param body 请求体，含 id 与 dsl
     * @return 保存后的管线标识
     */
    @PostMapping("/pipelines")
    public Map<String, Object> savePipeline(@RequestBody Map<String, String> body) {
        String id = body == null ? null : body.get("id");
        String dsl = body == null ? null : body.get("dsl");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("管线 id 不能为空");
        }
        if (dsl == null || dsl.isBlank()) {
            throw new IllegalArgumentException("管线 DSL 不能为空");
        }
        PipelineConfig config = parseDsl(dsl);
        pipelineManager().savePipeline(id.trim(), dsl);
        log.info("[datalake-manage] 管线已保存: id={}, stages={}", id,
                config == null || config.getStages() == null ? 0 : config.getStages().size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id.trim());
        return result;
    }

    /**
     * 删除管线（同步清除数据库与引擎编译缓存）。
     *
     * @param id 管线标识
     * @return 删除结果
     */
    @DeleteMapping("/pipelines/{id}")
    public Map<String, Object> deletePipeline(@PathVariable("id") String id) {
        DefaultPipelineManager manager = pipelineManager();
        if (manager.getPipeline(id) == null) {
            throw new IllegalArgumentException("管线不存在: " + id);
        }
        manager.deletePipeline(id);
        log.info("[datalake-manage] 管线已删除: id={}", id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("deleted", true);
        return result;
    }

    /**
     * 运行统计：总吞吐、管线/订阅器/Sink 数量与单管线计数。
     *
     * @return 统计快照
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>();
        StatisticSink statistic = statisticSink();
        result.put("total", statistic == null ? 0L : statistic.total());
        result.put("pipelines", countPipelines());
        result.put("subscribers", subscriberManager().subscriberCount());
        result.put("sinks", sinkManager().allSinks().size());
        List<Map<String, Object>> perPipeline = new ArrayList<>();
        if (statistic != null) {
            for (String id : pipelineManager().pipelineIds()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("count", statistic.countOf(id));
                perPipeline.add(row);
            }
        }
        result.put("perPipeline", perPipeline);
        return result;
    }

    /**
     * 已登记订阅器清单。
     *
     * @return 订阅器列表
     */
    @GetMapping("/subscribers")
    public List<Map<String, Object>> subscribers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Subscriber subscriber : subscriberManager().subscribers()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subscriberId", subscriber.subscriberId());
            row.put("topics", subscriber.topics());
            result.add(row);
        }
        return result;
    }

    /**
     * 已注册 Sink 清单（类型 + 是否带数据源）。
     *
     * @return sink 列表
     */
    @GetMapping("/sinks")
    public List<Map<String, Object>> sinks() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DataSink sink : sinkManager().allSinks()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", sink.type());
            row.put("hasDataSource", sink.getDataSource() != null);
            result.add(row);
        }
        return result;
    }

    /**
     * 参数/状态类错误统一映射为 400，避免落入容器兜底变成无信息的 500。
     *
     * @param ex 参数或状态类异常
     * @return 携带原始错误信息的错误体
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Map<String, Object> handleBadRequest(RuntimeException ex) {
        log.warn("[datalake-manage] 请求被拒绝: {}", ex.getMessage());
        return Map.of("error", String.valueOf(ex.getMessage()));
    }

    /**
     * 取管线管理器（必须由宿主装配）。
     */
    private DefaultPipelineManager pipelineManager() {
        if (!(server.getPipelineManager() instanceof DefaultPipelineManager manager)) {
            throw new IllegalStateException("管线管理器不可用");
        }
        return manager;
    }

    /**
     * 取 Sink 管理器。
     */
    private SinkManager sinkManager() {
        return server.getSinkManager();
    }

    /**
     * 取订阅管理器。
     */
    private SubscriberManager subscriberManager() {
        return server.getSubscriberManager();
    }

    /**
     * 从已注册 Sink 中定位统计 Sink（未注册时返回 null，计数按 0 处理）。
     */
    private StatisticSink statisticSink() {
        for (DataSink sink : sinkManager().allSinks()) {
            if (sink instanceof StatisticSink statistic) {
                return statistic;
            }
        }
        return null;
    }

    /**
     * 管线元信息：JdbcPipelineManager 提供审计时间；纯内存模式退化为仅 id。
     */
    private List<JdbcPipelineManager.PipelineMeta> jdbcMeta(DefaultPipelineManager manager) {
        if (manager instanceof JdbcPipelineManager jdbc) {
            return jdbc.listMeta();
        }
        List<JdbcPipelineManager.PipelineMeta> result = new ArrayList<>();
        for (String id : manager.pipelineIds()) {
            result.add(new JdbcPipelineManager.PipelineMeta(id, null, null));
        }
        return result;
    }

    /**
     * 统计内存中的管线数量。
     */
    private int countPipelines() {
        int count = 0;
        for (String ignored : pipelineManager().pipelineIds()) {
            count++;
        }
        return count;
    }

    /**
     * 校验 DSL 可解析；失败抛 400，避免把坏配置写进库后引擎侧静默 warn。
     */
    private PipelineConfig parseDsl(String dsl) {
        try {
            return Json.fromJson(dsl, PipelineConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("管线 DSL 解析失败: " + e.getMessage(), e);
        }
    }
}
