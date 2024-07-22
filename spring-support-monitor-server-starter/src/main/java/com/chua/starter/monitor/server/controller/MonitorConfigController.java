package com.chua.starter.monitor.server.controller;


import com.chua.common.support.annotations.Group;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.server.entity.MonitorConfig;
import com.chua.starter.monitor.server.service.MonitorAppService;
import com.chua.starter.monitor.server.service.MonitorConfigService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * 监控应用控制器
 */
@RestController
@RequestMapping("v1/config")
@Tag(name = "配置中心")
@RequiredArgsConstructor
public class MonitorConfigController extends AbstractSwaggerController<MonitorConfigService, MonitorConfig> {

    @Getter
    private final MonitorConfigService service;
    private final MonitorAppService monitorAppService;

    /**
     * 添加数据
     *
     * @param monitorConfig 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "下发配置")
    @PostMapping("upload")
    public ReturnResult<Boolean> upload(@Validated(Group.class) @RequestBody MonitorConfig monitorConfig, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.failure(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        MonitorConfig config = service.getById(monitorConfig.getConfigId());
        if(null == config) {
            return ReturnResult.illegal("数据不存在");
        }

        if(config.getConfigStatus() == 0) {
            return ReturnResult.illegal("配置已禁用");
        }

        return ReturnResult.success(monitorAppService.upload(Collections.singletonList(config)));
    }
}
