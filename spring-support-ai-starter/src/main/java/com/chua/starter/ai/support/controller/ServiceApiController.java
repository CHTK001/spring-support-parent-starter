// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiServiceAccessLog;
import com.chua.starter.ai.support.entity.SysAiServiceInstance;
import com.chua.starter.ai.support.mapper.SysAiServiceAccessLogMapper;
import com.chua.starter.ai.support.mapper.SysAiServiceInstanceMapper;
import com.chua.starter.ai.support.service.RuntimeLogService;
import com.chua.starter.ai.support.service.ServiceInstanceManager;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 对外服务管理控制器。
 *
 * <p>负责聚合 ChatClient 对外服务实例的管理（多端口、启停、协议开关、访问日志）。
 * 真正的 OpenAI / Claude / Gemini 协议面运行在每个实例独立绑定的端口上（见
 * {@link ServiceInstanceManager}），不在主端口重复暴露协议端点；鉴权凭证统一是 AppKey。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ServiceApiController {

    /**
     * 端口卡统计窗口（小时）。与页面上的「近 24 小时」文案必须一致。
     */
    private static final int STATS_WINDOW_HOURS = 24;

    private final SysAiServiceInstanceMapper instanceMapper;
    private final SysAiServiceAccessLogMapper accessLogMapper;
    private final ServiceInstanceManager instanceManager;
    private final RuntimeLogService runtimeLogService;

    /**
     * 应用就绪后恢复启用的服务实例（默认低优先级，晚于注册中心初始化）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnReady() {
        try {
            instanceManager.recoverInstances();
        } catch (Exception e) {
            log.error("[ServiceApi] 启动恢复服务实例失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 实例管理（admin） ====================

    /**
     * 列出全部对外服务实例及其运行状态。
     *
     * @return 服务实例列表响应
     */
    @GetMapping("/v2/service/instances")
    public Map<String, Object> listInstances(@RequestParam(defaultValue = "24") int hours) {
        int windowHours = Math.min(Math.max(hours, 1), 720);
        List<SysAiServiceInstance> list = instanceMapper.selectList(null);
        Map<String, SysAiServiceAccessLogMapper.InstanceStatRow> stats = loadInstanceStats(windowHours);
        Map<String, long[]> series = loadInstanceSeries(windowHours);
        Map<String, Long> p95 = loadInstanceP95(windowHours);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysAiServiceInstance i : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("instanceId", i.getInstanceId());
            m.put("instanceName", i.getInstanceName());
            m.put("port", i.getPort());
            m.put("enabled", i.getEnabled());
            m.put("running", instanceManager.isRunning(i.getInstanceId()));
            m.put("openaiEnabled", i.getOpenaiEnabled());
            m.put("ccEnabled", i.getCcEnabled());
            m.put("geminiEnabled", i.getGeminiEnabled());
            m.put("recordUsage", i.getRecordUsage());
            // 端口卡上的四个读数：来自访问日志按 instance_id 的聚合，不做任何猜测
            SysAiServiceAccessLogMapper.InstanceStatRow stat = stats.get(i.getInstanceId());
            long calls = stat == null || stat.calls() == null ? 0L : stat.calls();
            long ok = stat == null || stat.successCalls() == null ? 0L : stat.successCalls();
            m.put("calls", calls);
            m.put("successCalls", ok);
            m.put("errors", Math.max(calls - ok, 0L));
            m.put("tokens", stat == null || stat.tokens() == null ? 0L : stat.tokens());
            m.put("avgLatencyMs", stat == null || stat.avgLatencyMs() == null
                    ? null : Math.round(stat.avgLatencyMs()));
            m.put("lastAt", stat == null ? null : toMillis(stat.lastAt()));
            long[] spark = series.get(i.getInstanceId());
            m.put("spark", spark == null ? new long[SPARK_BUCKETS] : spark);
            m.put("p95Ms", p95.get(i.getInstanceId()));
            result.add(m);
        }
        Map<String, Object> response = new LinkedHashMap<>(4);
        response.put("instances", result);
        response.put("statsWindowHours", windowHours);
        return response;
    }

    /**
     * 本地时间 → 毫秒时间戳（前端按毫秒比较新旧）。
     *
     * @param time 本地时间，可为 null
     * @return 毫秒时间戳；{@code null} 原样返回
     */
    private static Long toMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * 读取「近 {@link #STATS_WINDOW_HOURS} 小时」的实例聚合。
     *
     * <p>统计失败不能把实例列表打挂：日志表结构异常时返回空表，页面退化成
     * 「只有端口和状态」而不是整块空白。</p>
     *
     * @return instanceId → 聚合行
     */
    private Map<String, SysAiServiceAccessLogMapper.InstanceStatRow> loadInstanceStats(int windowHours) {
        try {
            List<SysAiServiceAccessLogMapper.InstanceStatRow> rows =
                    accessLogMapper.selectInstanceStats(LocalDateTime.now().minusHours(windowHours));
            Map<String, SysAiServiceAccessLogMapper.InstanceStatRow> map = new HashMap<>(Math.max(rows.size(), 4));
            for (SysAiServiceAccessLogMapper.InstanceStatRow row : rows) {
                if (row.instanceId() != null && !row.instanceId().isBlank()) {
                    map.put(row.instanceId(), row);
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("[ServiceApi] 读取实例统计失败，列表仅返回状态: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 创建对外服务实例。
     *
     * @param body 实例名称、端口、协议开关和用量记录配置
     * @return 新建实例信息
     */
    @PostMapping("/v2/service/instances")
    public Map<String, Object> createInstance(@RequestBody Map<String, Object> body) {
        SysAiServiceInstance inst = new SysAiServiceInstance();
        inst.setInstanceId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        inst.setInstanceName(String.valueOf(body.getOrDefault("name", "新实例")));
        inst.setPort(Integer.valueOf(String.valueOf(body.getOrDefault("port", 8790))));
        inst.setEnabled(0);
        inst.setOpenaiEnabled(Boolean.TRUE.equals(body.get("openai")) ? 1 : 0);
        inst.setCcEnabled(Boolean.TRUE.equals(body.get("cc")) ? 1 : 0);
        inst.setGeminiEnabled(Boolean.TRUE.equals(body.get("gemini")) ? 1 : 0);
        inst.setRecordUsage(Boolean.TRUE.equals(body.getOrDefault("recordUsage", true)) ? 1 : 0);
        inst.setUserId(AiUserContext.currentUserId());
        instanceMapper.insert(inst);
        return Map.of("ok", true, "instance", inst);
    }

    /**
     * 启动指定对外服务实例。
     *
     * @param id 服务实例标识
     * @return 启动结果
     */
    @PostMapping("/v2/service/instances/{id}/start")
    public Map<String, Object> startInstance(@PathVariable String id) {
        SysAiServiceInstance inst = instanceMapper.selectById(id);
        if (inst == null) {
            return Map.of("ok", false, "error", "实例不存在");
        }
        boolean ok = instanceManager.startInstance(inst);
        if (ok) {
            inst.setEnabled(1);
            instanceMapper.updateById(inst);
        }
        return Map.of("ok", ok);
    }

    /**
     * 停止指定对外服务实例。
     *
     * @param id 服务实例标识
     * @return 停止结果
     */
    @PostMapping("/v2/service/instances/{id}/stop")
    public Map<String, Object> stopInstance(@PathVariable String id) {
        SysAiServiceInstance inst = instanceMapper.selectById(id);
        instanceManager.stopInstance(id);
        if (inst != null) {
            inst.setEnabled(0);
            instanceMapper.updateById(inst);
        }
        return Map.of("ok", true);
    }

    /**
     * 删除已停止的对外服务实例及其关联数据。
     *
     * @param id 服务实例标识
     * @return 删除结果
     */
    @DeleteMapping("/v2/service/instances/{id}")
    public Map<String, Object> deleteInstance(@PathVariable String id) {
        SysAiServiceInstance inst = instanceMapper.selectById(id);
        if (inst == null) {
            return Map.of("ok", false, "error", "实例不存在");
        }
        // 必须先停止（运行中或仍处于启用态）才能删除，防止误删对外服务
        if (instanceManager.isRunning(id)
                || (inst.getEnabled() != null && inst.getEnabled() == 1)) {
            return Map.of("ok", false, "error", "请先停止该服务实例，再删除");
        }
        // 清理关联的访问日志，避免孤儿数据
        accessLogMapper.delete(new LambdaQueryWrapper<SysAiServiceAccessLog>()
                .eq(SysAiServiceAccessLog::getInstanceId, id));
        instanceManager.stopInstance(id);
        instanceMapper.deleteById(id);
        return Map.of("ok", true);
    }

    // ==================== 访问日志（admin） ====================

    /**
     * 查询服务访问日志。
     *
     * @param instanceId 服务实例标识，为空时不限定实例
     * @param limit 返回记录数上限
     * @return 访问日志列表响应
     */
    @GetMapping("/v2/service/logs")
    public Map<String, Object> listLogs(@RequestParam(required = false) String instanceId,
                                        @RequestParam(defaultValue = "20") int limit,
                                        @RequestParam(defaultValue = "24") int hours,
                                        @RequestParam(defaultValue = "false") boolean onlyFailed) {
        LambdaQueryWrapper<SysAiServiceAccessLog> wrapper = new LambdaQueryWrapper<>();
        // 空串等于「不限定实例」：前端下拉的「全部实例」value 是 ''，
        // 若照旧 eq(instance_id, '') 会一条都查不到（历史缺陷）。
        if (instanceId != null && !instanceId.isBlank()) {
            wrapper.eq(SysAiServiceAccessLog::getInstanceId, instanceId);
        }
        int windowHours = Math.min(Math.max(hours, 1), 720);
        wrapper.ge(SysAiServiceAccessLog::getCreateTime, LocalDateTime.now().minusHours(windowHours));
        if (onlyFailed) {
            wrapper.ge(SysAiServiceAccessLog::getStatus, 400);
        }
        int size = Math.min(Math.max(limit, 1), 500);
        wrapper.orderByDesc(SysAiServiceAccessLog::getCreateTime).last("LIMIT " + size);
        Map<String, Object> result = new LinkedHashMap<>(4);
        result.put("logs", accessLogMapper.selectList(wrapper));
        result.put("windowHours", windowHours);
        return result;
    }

    // ==================== 运行日志 / 系统日志（admin） ====================

    /**
     * 读取当前后端进程的运行日志（tail），用于在服务配置页排查卡住 / 报错。
     *
     * @param source out=标准输出（默认），err=标准错误
     * @param lines  返回的最大行数
     */
    @GetMapping("/v2/service/runtime-logs")
    public Map<String, Object> runtimeLogs(@RequestParam(defaultValue = "out") String source,
                                           @RequestParam(defaultValue = "300") int lines) {
        if (!AiUserContext.isAdmin()) {
            return Map.of("ok", false, "error", "无权限：仅管理员可查看运行日志");
        }
        RuntimeLogService.TailResult result = runtimeLogService.tail(source, lines);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", result.path());
        m.put("exists", result.exists());
        m.put("lastModified", result.lastModified());
        m.put("truncated", result.truncated());
        List<Map<String, Object>> lineList = new ArrayList<>();
        for (RuntimeLogService.LogLine l : result.lines()) {
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("raw", l.raw());
            lm.put("level", l.level());
            lineList.add(lm);
        }
        m.put("lines", lineList);
        return m;
    }
}
