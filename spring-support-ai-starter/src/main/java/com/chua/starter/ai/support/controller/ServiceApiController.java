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

import java.util.ArrayList;
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
    public Map<String, Object> listInstances() {
        List<SysAiServiceInstance> list = instanceMapper.selectList(null);
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
            result.add(m);
        }
        return Map.of("instances", result);
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
                                        @RequestParam(defaultValue = "20") int limit) {
        LambdaQueryWrapper<SysAiServiceAccessLog> wrapper = new LambdaQueryWrapper<>();
        if (instanceId != null) {
            wrapper.eq(SysAiServiceAccessLog::getInstanceId, instanceId);
        }
        wrapper.orderByDesc(SysAiServiceAccessLog::getCreateTime).last("LIMIT " + limit);
        return Map.of("logs", accessLogMapper.selectList(wrapper));
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
