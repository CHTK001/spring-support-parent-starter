package com.chua.starter.monitor.server.controller;


import com.chua.common.support.annotations.Group;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.protocol.protocol.CommandType;
import com.chua.common.support.utils.ObjectUtils;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.monitor.server.entity.MonitorMybatis;
import com.chua.starter.monitor.server.factory.MonitorServerFactory;
import com.chua.starter.monitor.server.service.MonitorAppService;
import com.chua.starter.monitor.server.service.MonitorMybatisService;
import com.chua.starter.mybatis.controller.AbstractSwaggerController;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * 监控应用控制器
 */
@RestController
@RequestMapping("v1/mybatis")
@Tag(name = "mybatis")
@RequiredArgsConstructor
public class MonitorMybatisController extends AbstractSwaggerController<MonitorMybatisService, MonitorMybatis> {

    @Getter
    private final MonitorMybatisService service;
    private final MonitorAppService monitorAppService;
    private final MonitorServerFactory monitorServerFactory;
    /**
     * 添加数据
     *
     * @param monitorMybatis 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "下发配置")
    @PostMapping("upload")
    public ReturnResult<Boolean> upload(@Validated(Group.class) @RequestBody MonitorMybatis monitorMybatis, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.failure(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        MonitorMybatis config = service.getById(monitorMybatis.getMonitorMybatisId());
        if(null == config) {
            return ReturnResult.illegal("数据不存在");
        }

        if(config.getMonitorMybatisStatus() == 0) {
            return ReturnResult.illegal("配置已禁用");
        }

        List<MonitorRequest> heart = monitorServerFactory.getHeart(config.getMonitorAppname());
        if(ObjectUtils.isEmpty(heart)) {
            return ReturnResult.illegal("应用不存在");
        }

        for (MonitorRequest monitorRequest : heart) {
            monitorAppService.upload(null, monitorRequest, Json.toJSONString(config), "MYBATIS", CommandType.REQUEST);
        }
        return ReturnResult.success();
    }
}
