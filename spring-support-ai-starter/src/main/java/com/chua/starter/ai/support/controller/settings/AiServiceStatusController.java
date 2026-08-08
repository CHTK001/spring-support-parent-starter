package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.model.AiServiceStatusDTO;
import com.chua.starter.ai.support.service.settings.AiServiceStatusService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 聚合服务运行状态控制器，提供状态查询与服务启停。
 *
 * @author CH
 * @since 2026-08-03
 */
@RestController
@RequestMapping("/v2/services")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - 服务状态")
public class AiServiceStatusController {

    /**
     * 服务状态服务
     */
    private final AiServiceStatusService statusService;

    /**
     * 查询所有服务运行状态。
     *
     * @return 服务状态 DTO 列表
     */
    @Operation(summary = "查询所有服务运行状态")
    @GetMapping("/status")
    public List<AiServiceStatusDTO> status() {
        return statusService.list();
    }

    /**
     * 启动指定服务。
     *
     * @param serviceName 服务名称
     */
    @Permission("admin")
    @Operation(summary = "启动服务")
    @PostMapping("/{serviceName}/start")
    public void start(@Parameter(description = "服务名称") @PathVariable String serviceName) {
        statusService.start(serviceName);
    }

    /**
     * 停止指定服务。
     *
     * @param serviceName 服务名称
     */
    @Permission("admin")
    @Operation(summary = "停止服务")
    @PostMapping("/{serviceName}/stop")
    public void stop(@Parameter(description = "服务名称") @PathVariable String serviceName) {
        statusService.stop(serviceName);
    }
}
