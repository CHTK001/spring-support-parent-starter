package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.ServerService;
import com.chua.starter.server.support.entity.ServerServiceOperationLog;
import com.chua.starter.server.support.enums.ServerServiceOperationType;
import com.chua.starter.server.support.model.ServerAiTaskTicket;
import com.chua.starter.server.support.model.ServerServiceCommandResult;
import com.chua.starter.server.support.model.ServerServiceConfigWriteRequest;
import com.chua.starter.server.support.service.ServerServiceService;
import java.util.List;
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
@RequestMapping("/server/services")
public class ServerServiceController {

    private final ServerServiceService serverServiceService;

    @GetMapping
    public ReturnResult<List<ServerService>> list(
            @RequestParam(required = false) Integer serverId,
            @RequestParam(required = false) Boolean enabled
    ) {
        return ReturnResult.ok(serverServiceService.listServices(serverId, enabled));
    }

    @GetMapping("/{id}")
    public ReturnResult<ServerService> detail(@PathVariable Integer id) {
        return ReturnResult.ok(serverServiceService.getService(id));
    }

    @GetMapping("/{id}/operation-logs")
    public ReturnResult<List<ServerServiceOperationLog>> operationLogs(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer limit
    ) {
        return ReturnResult.ok(serverServiceService.listOperationLogs(id, limit));
    }

    @PostMapping("/{id}/ai-draft")
    public ReturnResult<ServerAiTaskTicket> aiDraft(@PathVariable Integer id) {
        return ReturnResult.ok(serverServiceService.generateAiDraft(id));
    }

    @PostMapping("/{id}/config/write")
    public ReturnResult<ServerServiceCommandResult> writeConfig(
            @PathVariable Integer id,
            @RequestBody(required = false) ServerServiceConfigWriteRequest request) throws Exception {
        return ReturnResult.ok(serverServiceService.writeConfig(id, request == null ? new ServerServiceConfigWriteRequest() : request));
    }

    @GetMapping("/by-installation/{installationId}")
    public ReturnResult<ServerService> detailByInstallation(@PathVariable Integer installationId) {
        return ReturnResult.ok(serverServiceService.getBySoftInstallationId(installationId));
    }

    @PostMapping
    public ReturnResult<ServerService> create(@RequestBody ServerService service) {
        return ReturnResult.ok(serverServiceService.saveService(service));
    }

    @PutMapping("/{id}")
    public ReturnResult<ServerService> update(@PathVariable Integer id, @RequestBody ServerService service) {
        service.setServerServiceId(id);
        return ReturnResult.ok(serverServiceService.saveService(service));
    }

    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Integer id) {
        serverServiceService.deleteService(id);
        return ReturnResult.ok(Boolean.TRUE);
    }

    @PostMapping("/{id}/register")
    public ReturnResult<ServerServiceCommandResult> register(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverServiceService.operate(id, ServerServiceOperationType.REGISTER));
    }

    @PostMapping("/{id}/unregister")
    public ReturnResult<ServerServiceCommandResult> unregister(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverServiceService.operate(id, ServerServiceOperationType.UNREGISTER));
    }

    @PostMapping("/{id}/start")
    public ReturnResult<ServerServiceCommandResult> start(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverServiceService.operate(id, ServerServiceOperationType.START));
    }

    @PostMapping("/{id}/stop")
    public ReturnResult<ServerServiceCommandResult> stop(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverServiceService.operate(id, ServerServiceOperationType.STOP));
    }

    @PostMapping("/{id}/restart")
    public ReturnResult<ServerServiceCommandResult> restart(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverServiceService.operate(id, ServerServiceOperationType.RESTART));
    }

    @PostMapping("/{id}/status")
    public ReturnResult<ServerServiceCommandResult> status(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverServiceService.operate(id, ServerServiceOperationType.STATUS));
    }

    @PostMapping("/{id}/ai-fix-start")
    public ReturnResult<ServerServiceCommandResult> aiFixStart(@PathVariable Integer id) throws Exception {
        return ReturnResult.ok(serverServiceService.operate(id, ServerServiceOperationType.AI_FIX));
    }

    @PostMapping("/by-installation/{installationId}/register")
    public ReturnResult<ServerServiceCommandResult> registerByInstallation(@PathVariable Integer installationId) throws Exception {
        return ReturnResult.ok(serverServiceService.operateBySoftInstallationId(installationId, ServerServiceOperationType.REGISTER));
    }

    @PostMapping("/by-installation/{installationId}/unregister")
    public ReturnResult<ServerServiceCommandResult> unregisterByInstallation(@PathVariable Integer installationId) throws Exception {
        return ReturnResult.ok(serverServiceService.operateBySoftInstallationId(installationId, ServerServiceOperationType.UNREGISTER));
    }

    @PostMapping("/by-installation/{installationId}/start")
    public ReturnResult<ServerServiceCommandResult> startByInstallation(@PathVariable Integer installationId) throws Exception {
        return ReturnResult.ok(serverServiceService.operateBySoftInstallationId(installationId, ServerServiceOperationType.START));
    }

    @PostMapping("/by-installation/{installationId}/stop")
    public ReturnResult<ServerServiceCommandResult> stopByInstallation(@PathVariable Integer installationId) throws Exception {
        return ReturnResult.ok(serverServiceService.operateBySoftInstallationId(installationId, ServerServiceOperationType.STOP));
    }

    @PostMapping("/by-installation/{installationId}/restart")
    public ReturnResult<ServerServiceCommandResult> restartByInstallation(@PathVariable Integer installationId) throws Exception {
        return ReturnResult.ok(serverServiceService.operateBySoftInstallationId(installationId, ServerServiceOperationType.RESTART));
    }

    @PostMapping("/by-installation/{installationId}/status")
    public ReturnResult<ServerServiceCommandResult> statusByInstallation(@PathVariable Integer installationId) throws Exception {
        return ReturnResult.ok(serverServiceService.operateBySoftInstallationId(installationId, ServerServiceOperationType.STATUS));
    }

    @PostMapping("/by-installation/{installationId}/ai-fix-start")
    public ReturnResult<ServerServiceCommandResult> aiFixStartByInstallation(@PathVariable Integer installationId) throws Exception {
        return ReturnResult.ok(serverServiceService.operateBySoftInstallationId(installationId, ServerServiceOperationType.AI_FIX));
    }
}
