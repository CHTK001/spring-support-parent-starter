package com.chua.starter.monitor.server.controller;


import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.monitor.server.factory.MonitorServerFactory;
import com.chua.starter.monitor.server.pojo.ServiceTarget;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 监控应用控制器
 */
@RestController
@RequestMapping("v1/link")
@Tag(name = "注册中心")
@RequiredArgsConstructor
public class MonitorLinkController {
    private final MonitorServerFactory monitorServerFactory;

    /**
     * 添加数据
     *
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "获取访问链路")
    @GetMapping
    public ReturnResult<List<ServiceTarget>> get(String appName, String serverAddress) {
        if (StringUtils.isEmpty(appName)) {
            return ReturnResult.illegal("应用不存在");
        }
        return ReturnResult.ok(monitorServerFactory.getServiceInstance(appName, serverAddress));
    }
}
