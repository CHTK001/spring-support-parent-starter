package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.entity.ServerAlertEvent;
import com.chua.starter.server.support.model.ServerAlertSettings;
import com.chua.starter.server.support.service.ServerAlertService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/server")
public class ServerAlertController {

    private final ServerAlertService serverAlertService;

    @GetMapping("/alert-settings")
    public ReturnResult<ServerAlertSettings> alertSettings() {
        return ReturnResult.ok(serverAlertService.getGlobalSettings());
    }

    @PutMapping("/alert-settings")
    public ReturnResult<ServerAlertSettings> updateAlertSettings(@RequestBody ServerAlertSettings settings) {
        return ReturnResult.ok(serverAlertService.saveGlobalSettings(settings));
    }

    @GetMapping("/alerts")
    public ReturnResult<List<ServerAlertEvent>> alerts(
            @RequestParam(required = false) Integer serverId,
            @RequestParam(required = false) Integer limit
    ) {
        return ReturnResult.ok(serverAlertService.listAlerts(serverId, limit));
    }
}
