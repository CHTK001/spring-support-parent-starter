package com.chua.report.server.starter.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.report.server.starter.entity.MonitorNginxConfig;
import com.chua.report.server.starter.pojo.NginxInclude;
import com.chua.report.server.starter.service.MonitorNginxConfigService;
import com.chua.starter.mybatis.entity.Query;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * nginx配置接口
 * @author CH
 * @since 2024/12/29
 */
@RestController
@RequestMapping("v1/nginx/config")
@Tag(name = "nginx配置接口")
@RequiredArgsConstructor
public class NginxConfigController {

    private final MonitorNginxConfigService monitorNginxConfigService;


    /**
     * 分页
     * @param query 查询
     * @return 分页
     */
    @GetMapping("page")
    @Schema(description = "分页")
    public ReturnPageResult<MonitorNginxConfig> page(Query<MonitorNginxConfig> query) {
        return monitorNginxConfigService.pageForConfig(query);
    }


    /**
     * 更新配置
     * @param nginxConfig 配置
     * @return 是否成功
     */
    @PutMapping("update")
    @Schema(description = "更新配置")
    public ReturnResult<Boolean> update(@Validated(UpdateGroup.class) @ParameterObject @RequestBody MonitorNginxConfig nginxConfig, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return ReturnResult.optional(monitorNginxConfigService.update(nginxConfig))
                .withErrorMessage("配置不存在")
                .asResult();
    }

    /**
     * 添加配置
     * @param nginxConfig 配置
     * @return 是否成功
     */
    @PostMapping("save")
    @Schema(description = "添加配置")
    public ReturnResult<MonitorNginxConfig> save(@Validated(AddGroup.class) @ParameterObject @RequestBody MonitorNginxConfig nginxConfig, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return ReturnResult.optional(monitorNginxConfigService.saveForConfig(nginxConfig))
                .withErrorMessage("配置新增失败")
                .asResult();
    }
    /**
     * 获取配置
     * @param monitorNginxConfigId nginx配置id
     * @return 配置
     */
    @GetMapping("get")
    @Schema(description = "获取配置")
    public ReturnResult<MonitorNginxConfig> get(Integer monitorNginxConfigId) {
        return ReturnResult.optional(monitorNginxConfigService.getForConfig(monitorNginxConfigId))
                .withErrorMessage("配置新增失败")
                .asResult();
    }
    /**
     * 获取配置
     * @param nginxConfigId nginx配置id
     * @return 配置
     */
    @GetMapping("config")
    @Schema(description = "获取配置")
    public ReturnResult<String> getConfigString(Integer nginxConfigId) {
        return ReturnResult.optional(monitorNginxConfigService.getConfigString(nginxConfigId))
                .withErrorMessage("配置不存在")
                .asResult();
    }
    /**
     * 获取配置
     * @param nginxConfigId nginx配置id
     * @return 配置
     */
    @GetMapping("configFormInclude")
    @Schema(description = "获取配置")
    public ReturnResult<List<NginxInclude>> configFormInclude(Integer nginxConfigId) {
        return ReturnResult.optional(monitorNginxConfigService.configFormInclude(nginxConfigId))
                .withErrorMessage("配置不存在")
                .asResult();
    }
    /**
     * 解析配置
     * @param nginxConfig 配置
     * @return 是否成功
     */
    @PutMapping("configAnalysis")
    @Schema(description = "解析配置")
    public ReturnResult<Boolean> configAnalysis(@RequestBody MonitorNginxConfig nginxConfig) {
        return ReturnResult.optional(monitorNginxConfigService.analyzeConfig(nginxConfig.getMonitorNginxConfigId()))
                .withErrorMessage("配置解析失败")
                .asResult();
    }
    /**
     * 启动nginx
     * @param nginxConfig 配置
     * @return 是否成功
     */
    @PutMapping("start")
    @Schema(description = "启动nginx")
    public ReturnResult<String> start(@ParameterObject @RequestBody MonitorNginxConfig nginxConfig) {
        return ReturnResult.optional(monitorNginxConfigService.start(nginxConfig.getMonitorNginxConfigId()))
                .withErrorMessage("配置不存在")
                .nullIsSuccess()
                .asResult();
    }
    /**
     * 重启nginx
     * @param nginxConfig 配置
     * @return 是否成功
     */
    @PutMapping("restart")
    @Schema(description = "重启nginx")
    public ReturnResult<String> restart(@ParameterObject @RequestBody MonitorNginxConfig nginxConfig) {
        return ReturnResult.optional(monitorNginxConfigService.restart(nginxConfig.getMonitorNginxConfigId()))
                .withErrorMessage("配置不存在")
                .nullIsSuccess()
                .asResult();
    }
    /**
     * 停止nginx
     * @param nginxConfig 配置
     * @return 是否成功
     */

    @PutMapping("stop")
    @Schema(description = "停止nginx")
    public ReturnResult<String> stop(@ParameterObject @RequestBody MonitorNginxConfig nginxConfig) {
        return ReturnResult.optional(monitorNginxConfigService.stop(nginxConfig.getMonitorNginxConfigId()))
                .withErrorMessage("配置不存在")
                .nullIsSuccess()
                .asResult();
    }

    /**
     * 生成配置
     * @param nginxConfig 配置
     * @return 是否成功
     */
    @PostMapping("create")
    @Schema(description = "生成配置")
    public ReturnResult<Boolean> save(@ParameterObject @RequestBody MonitorNginxConfig nginxConfig) {
        return ReturnResult.optional(monitorNginxConfigService.createConfigString(nginxConfig.getMonitorNginxConfigId()))
                .nullIsSuccess()
                .withErrorMessage("配置不存在")
                .asResult();
    }

    /**
     * 备份配置
     * @param nginxConfig 配置
     * @return 是否成功
     */
    @PutMapping("backup")
    @Schema(description = "备份配置")
    public ReturnResult<Boolean> backup(@ParameterObject @RequestBody MonitorNginxConfig nginxConfig) {
        return ReturnResult.optional(monitorNginxConfigService.backup(nginxConfig))
                .withErrorMessage("配置不存在")
                .asResult();
    }


}
