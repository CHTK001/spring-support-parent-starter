package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.model.ServerCapabilityView;
import com.chua.starter.server.support.service.ServerCapabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/server")
public class ServerCapabilityController {

    private final ServerCapabilityService serverCapabilityService;

    @GetMapping("/capabilities")
    public ReturnResult<ServerCapabilityView> capabilities() {
        return ReturnResult.ok(serverCapabilityService.getCapabilities());
    }
}
