package com.chua.report.server.starter.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.report.server.starter.entity.MonitorNginxConfig;
import com.chua.report.server.starter.service.MonitorNginxConfigService;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PutMapping("start")
    @Schema(description = "启动nginx")
    public ReturnResult<String> start(@ParameterObject @RequestBody MonitorNginxConfig nginxConfig) {
        return ReturnResult.optional(monitorNginxConfigService.start(nginxConfig.getMonitorNginxConfigId()))
                .withErrorMessage("配置不存在")
                .asResult();
    }
    @PutMapping("restart")
    @Schema(description = "重启nginx")
    public ReturnResult<String> restart(@ParameterObject @RequestBody MonitorNginxConfig nginxConfig) {
        return ReturnResult.optional(monitorNginxConfigService.restart(nginxConfig.getMonitorNginxConfigId()))
                .withErrorMessage("配置不存在")
                .asResult();
    }

    @PutMapping("stop")
    @Schema(description = "停止nginx")
    public ReturnResult<String> stop(@ParameterObject @RequestBody MonitorNginxConfig nginxConfig) {
        return ReturnResult.optional(monitorNginxConfigService.stop(nginxConfig.getMonitorNginxConfigId()))
                .withErrorMessage("配置不存在")
                .asResult();
    }

    /**
     * 生成配置
     * @param nginxConfig 配置
     * @return 是否成功
     */
    @PostMapping("save")
    @Schema(description = "生成配置")
    public ReturnResult<Boolean> save(@ParameterObject @RequestBody MonitorNginxConfig nginxConfig) {
        return ReturnResult.optional(monitorNginxConfigService.createConfigString(nginxConfig.getMonitorNginxConfigId()))
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


    /**
     * 解析配置
     * @param file 配置
     * @return 是否成功
     */
    @PutMapping("analysis")
    @Schema(description = "解析配置")
    public ReturnResult<Boolean> analyzeConfig(MultipartFile file) {
        return ReturnResult.optional(monitorNginxConfigService.analyzeConfig(file))
                .withErrorMessage("配置解析失败")
                .asResult();
    }
}
