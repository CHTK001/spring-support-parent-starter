package com.chua.report.server.starter.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.SelectGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.report.server.starter.entity.MonitorMqttServer;
import com.chua.report.server.starter.service.MonitorMqttServerService;
import com.chua.starter.mybatis.entity.Query;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * mqtt服务器
 * @author CH
 * @version 1.0.0
 * @since 2020/12/30
 */
@RestController
@RequestMapping("v1/mqtt/server")
@Tag(name = "mqtt服务端")
@RequiredArgsConstructor
public class MonitorMqttServerController {

    final MonitorMqttServerService monitorMqttServerService;

    /**
     * 根据主键删除数据
     *
     * @param monitorMqttId id
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "删除数据")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(@Parameter(name = "主键") Integer monitorMqttId) {
        if(null == monitorMqttId) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR,  "主键不能为空");
        }

        return monitorMqttServerService.deleteFor(monitorMqttId);
    }

    /**
     * 根据主键更新数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "更新数据")
    @PutMapping("update")
    public ReturnResult<Boolean> updateById(@Validated(UpdateGroup.class) @RequestBody MonitorMqttServer t , @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }


        return monitorMqttServerService.updateFor(t);
    }

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "添加数据")
    @PostMapping("save")
    public ReturnResult<MonitorMqttServer> save(@Validated(AddGroup.class) @RequestBody MonitorMqttServer t, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return monitorMqttServerService.saveFor(t);
    }

    /**
     *
     *
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "分页查询基础数据")
    @GetMapping("page")
    public ReturnPageResult<MonitorMqttServer> page(Query<MonitorMqttServer> page, @Validated(SelectGroup.class) MonitorMqttServer entity, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnPageResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return monitorMqttServerService.pageFor(page, entity);
    }


    /**
     * 启动服务
     * @param monitorMqttServerId
     * @return
     */
    @GetMapping("start")
    @Operation(summary = "启动服务")
    public ReturnResult<Boolean> start(Integer monitorMqttServerId) {
        return monitorMqttServerService.start(monitorMqttServerId);
    }

    /**
     * 停止服务
     * @param monitorMqttServerId
     * @return
     */
    @GetMapping("stop")
    @Operation(summary = "停止服务")
    public ReturnResult<Boolean> stop(Integer monitorMqttServerId) {
        return monitorMqttServerService.stop(monitorMqttServerId);
    }

}
