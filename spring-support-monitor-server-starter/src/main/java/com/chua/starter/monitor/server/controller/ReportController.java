package com.chua.starter.monitor.server.controller;


import com.chua.common.support.protocol.boot.BootRequest;
import com.chua.common.support.protocol.boot.BootResponse;
import com.chua.common.support.protocol.boot.CommandType;
import com.chua.common.support.protocol.boot.ModuleType;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.properties.MonitorServerProperties;
import com.chua.starter.monitor.server.request.RemoteRequest;
import com.chua.starter.monitor.server.resolver.ModuleResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Optional;

import static com.chua.common.support.protocol.boot.CommandType.RESPONSE;


/**
 * 报表控制器
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/01/31
 */
@RequestMapping
@Tag(name = "上报数据接口")
@RestController
public class ReportController {

    @Resource
    private MonitorServerProperties monitorServerProperties;
    @PostMapping("/report")
    @Operation(summary = "上报数据")
    public BootResponse home(@RequestBody RemoteRequest remoteRequest) {
        BootRequest request = remoteRequest.getRequest(monitorServerProperties);
        if(null == request) {
            return BootResponse.notSupport();
        }

        CommandType commandType = request.getCommandType();
        if(null == commandType) {
            return BootResponse.notSupport();
        }

        ModuleType moduleType = request.getModuleType();
        if(null == moduleType) {
            return BootResponse.notSupport();
        }

        String appName = request.getAppName();
        if(StringUtils.isBlank(appName)) {
            return BootResponse.builder()
                    .data(BootResponse.DataDTO.builder()
                            .commandType(RESPONSE).content("appName不能为空")
                            .build())
                    .build();
        }

        try {
            return Optional.ofNullable(ServiceProvider.of(ModuleResolver.class).getNewExtension(moduleType)
                    .resolve(request)).orElse(BootResponse.notSupport());
        } catch (Exception e) {
            return BootResponse.notSupport();
        }
    }
}
