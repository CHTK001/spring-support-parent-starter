package com.chua.starter.monitor.server.controller;


import com.chua.common.support.json.JsonObject;
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
@RequestMapping("v1/oshi")
@Tag(name = "系统监控")
@RequiredArgsConstructor
public class MonitorOshiController {
    private final MonitorServerFactory monitorServerFactory;
    /**
     * 系统监控
     *
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "系统监控")
    @GetMapping
    public ReturnResult<List<JsonObject>> get(String appName, String serverAddress) {
        if (StringUtils.isEmpty(appName)) {
            return ReturnResult.illegal("应用不存在");
        }
        return ReturnResult.ok(null);
    }
    /**
     * 系统监控
     *
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "系统列表")
    @GetMapping("list")
    public ReturnResult<List<ServiceTarget>> get() {
        return ReturnResult.ok(monitorServerFactory.listSys());
    }
}
