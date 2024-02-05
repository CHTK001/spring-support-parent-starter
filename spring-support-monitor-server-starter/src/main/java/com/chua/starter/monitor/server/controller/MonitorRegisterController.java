package com.chua.starter.monitor.server.controller;


import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.resolver.adator.CommandAdaptor;
import com.chua.starter.monitor.server.resolver.adator.RegisterCenterRequestCommandAdaptor;
import com.chua.starter.monitor.service.ServiceInstance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * 监控应用控制器
 */
@RestController
@RequestMapping("v1/register")
@Tag(name = "注册中心")
@RequiredArgsConstructor
public class MonitorRegisterController {

    /**
     * 添加数据
     *
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "获取注册中心服务")
    @GetMapping
    public ReturnResult<List<ServiceInstance>> get(String appName) {
        if(StringUtils.isEmpty(appName)) {
            return ReturnResult.illegal("应用不存在");
        }
        return ReturnResult.ok(Collections.singletonList(getServiceInstance(appName)));
    }

    private ServiceInstance getServiceInstance(String appName) {
        RegisterCenterRequestCommandAdaptor commandAdaptor = (RegisterCenterRequestCommandAdaptor) ServiceProvider.of(CommandAdaptor.class).getNewExtension("register_center_request");
        return commandAdaptor.getServiceInstance(appName);
    }
}
