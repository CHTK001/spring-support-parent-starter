package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.ServerHost;
import com.chua.starter.server.support.model.ServerGuacamoleConfig;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import com.chua.starter.server.support.model.ServerMetricsDetail;
import com.chua.starter.server.support.model.ServerMetricsTaskSettings;
import com.chua.starter.server.support.model.ServerMetricsTaskSettingsRequest;
import com.chua.starter.server.support.model.ServerExposurePortView;
import com.chua.starter.server.support.model.ServerExposurePortMeta;
import com.chua.starter.server.support.model.ServerExposureSummary;
import com.chua.starter.server.support.model.ServerAlertSettings;
import com.chua.starter.server.support.model.ServerRemoteGatewaySettings;
import com.chua.starter.server.support.model.ServerAiTaskTicket;
import com.chua.starter.server.support.model.ServerProcessAiAdvice;
import com.chua.starter.server.support.model.ServerProcessCommandResult;
import com.chua.starter.server.support.model.ServerProcessView;
import com.chua.starter.server.support.service.ServerAlertService;
import com.chua.starter.server.support.service.ServerGuacamoleService;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.service.ServerExposureService;
import com.chua.starter.server.support.service.ServerProcessService;
import com.chua.starter.server.support.service.ServerHostViewAssembler;
import com.chua.starter.server.support.service.ServerMetricsService;
import com.chua.starter.server.support.service.ServerRemoteGatewaySettingsService;
import com.chua.starter.server.support.service.ServerServiceService;
import com.chua.starter.server.support.service.impl.ServerHostAiTaskService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/server/hosts")
public class ServerHostController {

    private final ServerHostService serverHostService;
    private final ServerHostViewAssembler serverHostViewAssembler;
    private final ServerMetricsService serverMetricsService;
    private final ServerGuacamoleService serverGuacamoleService;
    private final ServerAlertService serverAlertService;
    private final ServerRemoteGatewaySettingsService serverRemoteGatewaySettingsService;
    private final ServerServiceService serverServiceService;
    private final ServerHostAiTaskService serverHostAiTaskService;
    private final ServerProcessService serverProcessService;
    private final ServerExposureService serverExposureService;

    @GetMapping
    public ReturnResult<List<ServerHost>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String serverType,
            @RequestParam(required = false) Boolean enabled
    ) {
        return ReturnResult.ok(serverHostViewAssembler.enrich(serverHostService.listHosts(keyword, serverType, enabled)));
    }

    @GetMapping("/summary")
    public ReturnResult<Map<String, Object>> summary() {
        return ReturnResult.ok(serverHostService.getSummary());
    }

    @GetMapping("/{id}")
    public ReturnResult<ServerHost> detail(@PathVariable Integer id) {
        return ReturnResult.ok(serverHostViewAssembler.enrich(serverHostService.getHost(id)));
    }

    @PostMapping
    public ReturnResult<ServerHost> create(@RequestBody ServerHost host) {
        return ReturnResult.ok(serverHostViewAssembler.enrich(serverHostService.saveHost(host)));
    }

    @PutMapping("/{id}")
    public ReturnResult<ServerHost> update(@PathVariable Integer id, @RequestBody ServerHost host) {
        host.setServerId(id);
        return ReturnResult.ok(serverHostViewAssembler.enrich(serverHostService.saveHost(host)));
    }

    @PatchMapping("/{id}/enabled")
    public ReturnResult<ServerHost> updateEnabled(@PathVariable Integer id, @RequestParam Boolean enabled) {
        return ReturnResult.ok(serverHostViewAssembler.enrich(serverHostService.updateEnabled(id, enabled)));
    }

    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Integer id) {
        serverHostService.deleteHost(id);
        return ReturnResult.ok(Boolean.TRUE);
    }

    @GetMapping("/metrics")
    public ReturnResult<List<ServerMetricsSnapshot>> metrics() {
        return ReturnResult.ok(serverMetricsService.listSnapshots());
    }

    @PostMapping("/metrics/refresh")
    public ReturnResult<List<ServerMetricsSnapshot>> refreshMetrics() {
        return ReturnResult.ok(serverMetricsService.refreshMetrics());
    }

    @GetMapping("/metrics/task-settings")
    public ReturnResult<ServerMetricsTaskSettings> metricsTaskSettings() {
        return ReturnResult.ok(serverMetricsService.getTaskSettings());
    }

    @PutMapping("/metrics/task-settings")
    public ReturnResult<ServerMetricsTaskSettings> updateMetricsTaskSettings(
            @RequestBody ServerMetricsTaskSettingsRequest request
    ) {
        return ReturnResult.ok(serverMetricsService.updateTaskSettings(request));
    }

    @GetMapping("/{id}/metrics")
    public ReturnResult<ServerMetricsSnapshot> hostMetrics(@PathVariable Integer id) {
        return ReturnResult.ok(serverMetricsService.getSnapshot(id));
    }

    @GetMapping("/{id}/metrics/detail")
    public ReturnResult<ServerMetricsDetail> hostMetricsDetail(@PathVariable Integer id) {
        return ReturnResult.ok(serverMetricsService.getDetail(id));
    }

    @PostMapping("/{id}/public-ip/refresh")
    public ReturnResult<ServerHost> refreshHostPublicIp(@PathVariable Integer id) {
        serverMetricsService.getDetail(id);
        return ReturnResult.ok(serverHostViewAssembler.enrich(serverHostService.getHost(id)));
    }

    @GetMapping("/{id}/exposure")
    public ReturnResult<ServerExposureSummary> exposure(@PathVariable Integer id) {
        return ReturnResult.ok(serverExposureService.getSummary(id));
    }

    @PostMapping("/{id}/exposure/refresh")
    public ReturnResult<ServerExposureSummary> refreshExposure(@PathVariable Integer id) {
        return ReturnResult.ok(serverExposureService.refresh(id));
    }

    @GetMapping("/{id}/ports")
    public ReturnResult<List<ServerExposurePortView>> hostPorts(
            @PathVariable Integer id,
            @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "protocol", required = false) String protocol,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ReturnResult.ok(serverExposureService.listPorts(
                id,
                refresh,
                keyword,
                protocol,
                state,
                limit));
    }

    @GetMapping("/{id}/ports/meta")
    public ReturnResult<ServerExposurePortMeta> hostPortsMeta(
            @PathVariable Integer id,
            @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh
    ) {
        return ReturnResult.ok(serverExposureService.portMeta(id, refresh));
    }

    @GetMapping("/{id}/metrics/task-settings")
    public ReturnResult<ServerMetricsTaskSettings> hostMetricsTaskSettings(@PathVariable Integer id) {
        return ReturnResult.ok(serverMetricsService.getTaskSettings(id));
    }

    @PutMapping("/{id}/metrics/task-settings")
    public ReturnResult<ServerMetricsTaskSettings> updateHostMetricsTaskSettings(
            @PathVariable Integer id,
            @RequestBody ServerMetricsTaskSettingsRequest request
    ) {
        return ReturnResult.ok(serverMetricsService.updateTaskSettings(id, request));
    }

    @GetMapping("/{id}/metrics/history")
    public ReturnResult<List<ServerMetricsSnapshot>> hostMetricsHistory(
            @PathVariable Integer id,
            @RequestParam(value = "minutes", required = false) Integer minutes
    ) {
        return ReturnResult.ok(serverMetricsService.listHistory(id, minutes));
    }

    @PostMapping("/{id}/metrics/history/ai-analyze")
    public ReturnResult<ServerAiTaskTicket> analyzeMetricHistory(
            @PathVariable Integer id,
            @RequestParam String metricType,
            @RequestParam(value = "minutes", required = false) Integer minutes,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime,
            @RequestParam(value = "stateFilter", required = false) String stateFilter
    ) {
        return ReturnResult.ok(serverHostAiTaskService.scheduleMetricHistoryAnalysis(
                id,
                metricType,
                minutes,
                startTime,
                endTime,
                stateFilter));
    }

    @PostMapping("/{id}/alerts/ai-analyze")
    public ReturnResult<ServerAiTaskTicket> analyzeAlertHistory(
            @PathVariable Integer id,
            @RequestParam(value = "metricType", required = false) String metricType,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ReturnResult.ok(serverHostAiTaskService.scheduleAlertHistoryAnalysis(
                id,
                metricType,
                severity,
                startTime,
                endTime,
                limit));
    }

    @GetMapping("/{id}/alert-settings")
    public ReturnResult<ServerAlertSettings> hostAlertSettings(@PathVariable Integer id) {
        return ReturnResult.ok(serverAlertService.getHostSettings(id));
    }

    @PutMapping("/{id}/alert-settings")
    public ReturnResult<ServerAlertSettings> updateHostAlertSettings(
            @PathVariable Integer id,
            @RequestBody ServerAlertSettings settings
    ) {
        return ReturnResult.ok(serverAlertService.saveHostSettings(id, settings));
    }

    @GetMapping("/{id}/guacamole")
    public ReturnResult<ServerGuacamoleConfig> guacamole(@PathVariable Integer id) {
        return ReturnResult.ok(serverGuacamoleService.buildConfig(serverHostService.getHost(id)));
    }

    @GetMapping("/{id}/remote-console")
    public ReturnResult<ServerGuacamoleConfig> remoteConsole(@PathVariable Integer id) {
        return ReturnResult.ok(serverGuacamoleService.buildConfig(serverHostService.getHost(id)));
    }

    @GetMapping("/{id}/remote-gateway")
    public ReturnResult<ServerRemoteGatewaySettings> remoteGateway(@PathVariable Integer id) {
        return ReturnResult.ok(serverRemoteGatewaySettingsService.getHostSettings(id));
    }

    @PutMapping("/{id}/remote-gateway")
    public ReturnResult<ServerRemoteGatewaySettings> updateRemoteGateway(
            @PathVariable Integer id,
            @RequestBody ServerRemoteGatewaySettings settings
    ) {
        return ReturnResult.ok(serverRemoteGatewaySettingsService.saveHostSettings(id, settings));
    }

    @PostMapping("/{id}/services/detect")
    public ReturnResult<List<com.chua.starter.server.support.entity.ServerService>> detectServices(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverServiceService.detectServices(id));
    }

    @GetMapping("/{id}/services")
    public ReturnResult<List<com.chua.starter.server.support.entity.ServerService>> listServices(@PathVariable Integer id) {
        return ReturnResult.ok(serverServiceService.listServices(id, null));
    }

    @PostMapping("/{id}/ai-analyze")
    public ReturnResult<ServerAiTaskTicket> analyzeHost(@PathVariable Integer id) {
        return ReturnResult.ok(serverHostAiTaskService.scheduleStabilityAnalysis(id));
    }

    @GetMapping("/{id}/processes")
    public ReturnResult<List<ServerProcessView>> listProcesses(
            @PathVariable Integer id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer limit
    ) throws Exception {
        return ReturnResult.ok(serverProcessService.listProcesses(id, keyword, limit));
    }

    @GetMapping("/{id}/processes/{pid}")
    public ReturnResult<ServerProcessView> getProcess(
            @PathVariable Integer id,
            @PathVariable Long pid
    ) throws Exception {
        return ReturnResult.ok(serverProcessService.getProcess(id, pid));
    }

    @PostMapping("/{id}/processes/{pid}/terminate")
    public ReturnResult<ServerProcessCommandResult> terminateProcess(
            @PathVariable Integer id,
            @PathVariable Long pid,
            @RequestParam(defaultValue = "false") boolean force
    ) throws Exception {
        return ReturnResult.ok(serverProcessService.terminateProcess(id, pid, force));
    }

    @PostMapping("/{id}/processes/{pid}/ai-analyze")
    public ReturnResult<ServerProcessAiAdvice> analyzeProcess(
            @PathVariable Integer id,
            @PathVariable Long pid
    ) throws Exception {
        return ReturnResult.ok(serverProcessService.analyzeProcess(id, pid));
    }
}
