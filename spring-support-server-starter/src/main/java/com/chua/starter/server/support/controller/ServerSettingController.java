package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.ServerRemoteGatewaySettings;
import com.chua.starter.server.support.service.ServerRemoteGatewaySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/server/settings")
public class ServerSettingController {

    private final ServerRemoteGatewaySettingsService serverRemoteGatewaySettingsService;

    @GetMapping("/remote-gateway")
    public ReturnResult<ServerRemoteGatewaySettings> remoteGateway() {
        return ReturnResult.ok(serverRemoteGatewaySettingsService.getGlobalSettings());
    }

    @PutMapping("/remote-gateway")
    public ReturnResult<ServerRemoteGatewaySettings> updateRemoteGateway(@RequestBody ServerRemoteGatewaySettings settings) {
        return ReturnResult.ok(serverRemoteGatewaySettingsService.saveGlobalSettings(settings));
    }
}
