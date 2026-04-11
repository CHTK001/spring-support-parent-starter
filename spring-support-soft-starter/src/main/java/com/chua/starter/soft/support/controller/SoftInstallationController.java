package com.chua.starter.soft.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.soft.support.entity.SoftConfigSnapshot;
import com.chua.starter.soft.support.entity.SoftInstallation;
import com.chua.starter.soft.support.model.SoftConfigResponse;
import com.chua.starter.soft.support.model.SoftConfigWriteRequest;
import com.chua.starter.soft.support.model.SoftInstallRequest;
import com.chua.starter.soft.support.model.SoftLogResponse;
import com.chua.starter.soft.support.model.SoftLogWatchTicket;
import com.chua.starter.soft.support.model.SoftOperationTicket;
import com.chua.starter.soft.support.service.SoftManagementService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/soft/installations")
public class SoftInstallationController {

    private final SoftManagementService softManagementService;

    @GetMapping
    public ReturnResult<List<SoftInstallation>> list() {
        return ReturnResult.ok(softManagementService.listInstallations());
    }

    @GetMapping("/{id}")
    public ReturnResult<Map<String, Object>> detail(@PathVariable Integer id) {
        return ReturnResult.ok(softManagementService.getInstallationDetail(id));
    }

    @PostMapping
    public ReturnResult<SoftOperationTicket> install(@RequestBody SoftInstallRequest request) {
        return ReturnResult.ok(softManagementService.install(request));
    }

    @DeleteMapping("/{id}")
    public ReturnResult<SoftOperationTicket> uninstall(@PathVariable Integer id) {
        return ReturnResult.ok(softManagementService.uninstall(id));
    }

    @PostMapping("/{id}/service/register")
    public ReturnResult<SoftOperationTicket> register(@PathVariable Integer id) {
        return ReturnResult.ok(softManagementService.registerService(id));
    }

    @PostMapping("/{id}/service/unregister")
    public ReturnResult<SoftOperationTicket> unregister(@PathVariable Integer id) {
        return ReturnResult.ok(softManagementService.unregisterService(id));
    }

    @PostMapping("/{id}/service/start")
    public ReturnResult<SoftOperationTicket> start(@PathVariable Integer id) {
        return ReturnResult.ok(softManagementService.startService(id));
    }

    @PostMapping("/{id}/service/stop")
    public ReturnResult<SoftOperationTicket> stop(@PathVariable Integer id) {
        return ReturnResult.ok(softManagementService.stopService(id));
    }

    @PostMapping("/{id}/service/restart")
    public ReturnResult<SoftOperationTicket> restart(@PathVariable Integer id) {
        return ReturnResult.ok(softManagementService.restartService(id));
    }

    @PostMapping("/{id}/service/status")
    public ReturnResult<SoftOperationTicket> status(@PathVariable Integer id) {
        return ReturnResult.ok(softManagementService.serviceStatus(id));
    }

    @GetMapping("/{id}/logs")
    public ReturnResult<SoftLogResponse> logs(@PathVariable Integer id,
                                              @RequestParam(required = false) String logPath,
                                              @RequestParam(required = false) Integer lines) throws Exception {
        return ReturnResult.ok(softManagementService.readLogs(id, logPath, lines));
    }

    @PostMapping("/{id}/logs/watch")
    public ReturnResult<SoftLogWatchTicket> startLogWatch(@PathVariable Integer id,
                                                          @RequestParam(required = false) String logPath) throws Exception {
        return ReturnResult.ok(softManagementService.startLogWatch(id, logPath));
    }

    @DeleteMapping("/{id}/logs/watch/{watchId}")
    public ReturnResult<Boolean> stopLogWatch(@PathVariable Integer id,
                                              @PathVariable Long watchId) {
        return ReturnResult.ok(softManagementService.stopLogWatch(watchId));
    }

    @GetMapping("/{id}/configs")
    public ReturnResult<SoftConfigResponse> config(@PathVariable Integer id,
                                                   @RequestParam(required = false) String configPath) throws Exception {
        return ReturnResult.ok(softManagementService.readConfig(id, configPath));
    }

    @PutMapping("/{id}/configs")
    public ReturnResult<SoftOperationTicket> writeConfig(@PathVariable Integer id,
                                                         @RequestBody SoftConfigWriteRequest request) throws Exception {
        return ReturnResult.ok(softManagementService.writeConfig(id, request));
    }

    @GetMapping("/{id}/configs/snapshots")
    public ReturnResult<List<SoftConfigSnapshot>> snapshots(@PathVariable Integer id) {
        return ReturnResult.ok(softManagementService.listConfigSnapshots(id));
    }

    @PostMapping("/{id}/configs/snapshots/{snapshotId}/rollback")
    public ReturnResult<SoftOperationTicket> rollback(@PathVariable Integer id,
                                                      @PathVariable Integer snapshotId) throws Exception {
        return ReturnResult.ok(softManagementService.rollbackConfig(id, snapshotId));
    }
}
