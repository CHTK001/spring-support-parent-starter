package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.datasourcehub.support.model.PrometheusApplyResultVO;
import com.chua.starter.datasourcehub.support.model.PrometheusCommandVO;
import com.chua.starter.datasourcehub.support.model.PrometheusConfigVO;
import com.chua.starter.datasourcehub.support.model.PrometheusDashboardVO;
import com.chua.starter.datasourcehub.support.model.PrometheusFileVO;
import com.chua.starter.datasourcehub.support.model.dto.PrometheusDashboardDTO;
import com.chua.starter.datasourcehub.support.model.dto.PrometheusSaveConfigDTO;
import com.chua.starter.datasourcehub.support.service.PrometheusManageService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Prometheus 控制台 REST 接口。
 *
 * <p>查询走 Prometheus HTTP API，配置管理经 SSH（参考 nginx 控制台），自定义看板
 * 整存整取并支持令牌化的公开只读嵌入。{@code /dashboard/embed/**} 三个接口设计为
 * 免登录使用，需由部署侧把该前缀加入 OAuth 白名单（见模块说明）。</p>
 *
 * <p>datasourceId 可缺省，服务端退化为第一个 prometheus 数据源。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Tag(name = "监控管理", description = "Prometheus 管理")
@RestController
@RequestMapping("/ms/monitor/prometheus")
@RequiredArgsConstructor
public class PrometheusManageController {

    /**
     * Prometheus 管理服务
     */
    private final PrometheusManageService prometheusManageService;

    // ==================== 总览 ====================

    /**
     * 读取总览。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 总览
     */
    @Operation(summary = "Prometheus 总览")
    @GetMapping("/overview")
    public ReturnResult<Map<String, Object>> overview(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Prometheus 总览", () -> prometheusManageService.overview(datasourceId));
    }

    // ==================== 查询 ====================

    /**
     * 即时查询。
     *
     * @param datasourceId 数据源 ID，可空
     * @param query        PromQL
     * @param time         求值时刻，可空
     * @return Prometheus 原始响应
     */
    @Operation(summary = "Prometheus 即时查询")
    @GetMapping("/query")
    public ReturnResult<JsonNode> query(@RequestParam(required = false) Long datasourceId,
                                        @RequestParam String query,
                                        @RequestParam(required = false) String time) {
        return call("Prometheus 即时查询", () -> prometheusManageService.query(datasourceId, query, time));
    }

    /**
     * 范围查询。
     *
     * @param datasourceId 数据源 ID，可空
     * @param query        PromQL
     * @param start        起始时刻
     * @param end          结束时刻
     * @param step         步长
     * @return Prometheus 原始响应
     */
    @Operation(summary = "Prometheus 范围查询")
    @GetMapping("/query_range")
    public ReturnResult<JsonNode> queryRange(@RequestParam(required = false) Long datasourceId,
                                             @RequestParam String query,
                                             @RequestParam String start,
                                             @RequestParam String end,
                                             @RequestParam String step) {
        return call("Prometheus 范围查询",
                () -> prometheusManageService.queryRange(datasourceId, query, start, end, step));
    }

    /**
     * 抓取目标。
     *
     * @param datasourceId 数据源 ID，可空
     * @return data 节点
     */
    @Operation(summary = "Prometheus 抓取目标")
    @GetMapping("/targets")
    public ReturnResult<JsonNode> targets(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Prometheus 目标", () -> prometheusManageService.targets(datasourceId));
    }

    /**
     * 规则列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @return data 节点
     */
    @Operation(summary = "Prometheus 规则列表")
    @GetMapping("/rules")
    public ReturnResult<JsonNode> rules(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Prometheus 规则", () -> prometheusManageService.rules(datasourceId));
    }

    /**
     * 活跃告警。
     *
     * @param datasourceId 数据源 ID，可空
     * @return data 节点
     */
    @Operation(summary = "Prometheus 活跃告警")
    @GetMapping("/alerts")
    public ReturnResult<JsonNode> alerts(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Prometheus 告警", () -> prometheusManageService.alerts(datasourceId));
    }

    /**
     * 标签名列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 标签名
     */
    @Operation(summary = "Prometheus 标签名列表")
    @GetMapping("/labels")
    public ReturnResult<List<String>> labels(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Prometheus 标签", () -> prometheusManageService.labels(datasourceId));
    }

    /**
     * 标签取值列表。
     *
     * @param datasourceId 数据源 ID，可空
     * @param name         标签名
     * @return 标签值
     */
    @Operation(summary = "Prometheus 标签取值")
    @GetMapping("/label/{name}/values")
    public ReturnResult<List<String>> labelValues(@RequestParam(required = false) Long datasourceId,
                                                  @PathVariable String name) {
        return call("读取 Prometheus 标签取值",
                () -> prometheusManageService.labelValues(datasourceId, name));
    }

    // ==================== 配置管理 ====================

    /**
     * 读取配置文件。
     *
     * @param datasourceId 数据源 ID，可空
     * @param path         文件路径，空则主配置
     * @return 文件内容
     */
    @Operation(summary = "读取 Prometheus 配置文件")
    @GetMapping("/config")
    public ReturnResult<PrometheusConfigVO> readConfig(@RequestParam(required = false) Long datasourceId,
                                                       @RequestParam(required = false) String path) {
        return call("读取 Prometheus 配置", () -> prometheusManageService.readConfig(datasourceId, path));
    }

    /**
     * 列出配置文件。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 文件列表
     */
    @Operation(summary = "列出 Prometheus 配置文件")
    @GetMapping("/config/files")
    public ReturnResult<List<PrometheusFileVO>> files(@RequestParam(required = false) Long datasourceId) {
        return call("列出 Prometheus 配置文件", () -> prometheusManageService.listFiles(datasourceId));
    }

    /**
     * 保存配置文件（校验 → 备份 → 覆盖 → 重载）。
     *
     * @param request 保存请求
     * @return 执行结果
     */
    @Operation(summary = "保存 Prometheus 配置文件")
    @PutMapping("/config")
    public ReturnResult<PrometheusApplyResultVO> saveConfig(@RequestBody PrometheusSaveConfigDTO request) {
        return call("保存 Prometheus 配置", () -> prometheusManageService.saveConfig(request));
    }

    /**
     * 触发配置重载。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 执行结果
     */
    @Operation(summary = "重载 Prometheus 配置")
    @PostMapping("/config/reload")
    public ReturnResult<PrometheusCommandVO> reload(@RequestParam(required = false) Long datasourceId) {
        return call("重载 Prometheus 配置", () -> prometheusManageService.reload(datasourceId));
    }

    /**
     * 列出备份。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 备份列表
     */
    @Operation(summary = "列出 Prometheus 配置备份")
    @GetMapping("/config/backups")
    public ReturnResult<List<PrometheusFileVO>> backups(@RequestParam(required = false) Long datasourceId) {
        return call("列出 Prometheus 备份", () -> prometheusManageService.listBackups(datasourceId));
    }

    /**
     * 从备份恢复。
     *
     * @param datasourceId 数据源 ID，可空
     * @param path         备份文件路径
     * @return 执行结果
     */
    @Operation(summary = "从备份恢复 Prometheus 配置")
    @PostMapping("/config/restore")
    public ReturnResult<PrometheusApplyResultVO> restore(@RequestParam(required = false) Long datasourceId,
                                                         @RequestParam String path) {
        return call("恢复 Prometheus 配置", () -> prometheusManageService.restoreBackup(datasourceId, path));
    }

    // ==================== 自定义看板 ====================

    /**
     * 读取自定义看板。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 看板
     */
    @Operation(summary = "读取 Prometheus 自定义看板")
    @GetMapping("/dashboard")
    public ReturnResult<PrometheusDashboardVO> dashboard(@RequestParam(required = false) Long datasourceId) {
        return call("读取 Prometheus 看板", () -> prometheusManageService.getDashboard(datasourceId));
    }

    /**
     * 保存自定义看板（实时保存）。
     *
     * @param request 保存请求
     * @return 保存后的看板
     */
    @Operation(summary = "保存 Prometheus 自定义看板")
    @PutMapping("/dashboard")
    public ReturnResult<PrometheusDashboardVO> saveDashboard(@RequestBody PrometheusDashboardDTO request) {
        return call("保存 Prometheus 看板", () -> prometheusManageService.saveDashboard(request));
    }

    /**
     * 重新生成嵌入令牌。
     *
     * @param datasourceId 数据源 ID，可空
     * @return 看板
     */
    @Operation(summary = "生成 Prometheus 看板嵌入令牌")
    @PostMapping("/dashboard/token")
    public ReturnResult<PrometheusDashboardVO> token(@RequestParam(required = false) Long datasourceId) {
        return call("生成嵌入令牌", () -> prometheusManageService.regenerateToken(datasourceId));
    }

    /**
     * 按令牌读取看板（公开只读，需部署侧放行 OAuth 白名单）。
     *
     * @param token 嵌入令牌
     * @return 看板
     */
    @Operation(summary = "读取嵌入看板（公开）")
    @GetMapping("/dashboard/embed/{token}")
    public ReturnResult<PrometheusDashboardVO> embedDashboard(@PathVariable String token) {
        return call("读取嵌入看板", () -> prometheusManageService.embedDashboard(token));
    }

    /**
     * 嵌入场景即时查询（公开只读）。
     *
     * @param token 嵌入令牌
     * @param query PromQL
     * @param time  求值时刻，可空
     * @return Prometheus 原始响应
     */
    @Operation(summary = "嵌入看板即时查询（公开）")
    @GetMapping("/dashboard/embed/{token}/query")
    public ReturnResult<JsonNode> embedQuery(@PathVariable String token,
                                             @RequestParam String query,
                                             @RequestParam(required = false) String time) {
        return call("嵌入看板查询", () -> prometheusManageService.embedQuery(token, query, time));
    }

    /**
     * 嵌入场景范围查询（公开只读）。
     *
     * @param token 嵌入令牌
     * @param query PromQL
     * @param start 起始时刻
     * @param end   结束时刻
     * @param step  步长
     * @return Prometheus 原始响应
     */
    @Operation(summary = "嵌入看板范围查询（公开）")
    @GetMapping("/dashboard/embed/{token}/query_range")
    public ReturnResult<JsonNode> embedQueryRange(@PathVariable String token,
                                                  @RequestParam String query,
                                                  @RequestParam String start,
                                                  @RequestParam String end,
                                                  @RequestParam String step) {
        return call("嵌入看板范围查询", () -> prometheusManageService.embedQueryRange(token, query, start, end, step));
    }

    /**
     * 执行一次管理操作，把远端失败换成可读原因而不是 500 堆栈。
     *
     * @param action   操作描述
     * @param supplier 业务调用
     * @param <T>      返回类型
     * @return 响应
     */
    private <T> ReturnResult<T> call(String action, Supplier<T> supplier) {
        try {
            return ReturnResult.ok(supplier.get());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("[prometheus] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + e.getMessage());
        }
    }
}
