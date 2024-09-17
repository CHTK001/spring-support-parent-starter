package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.report.server.starter.entity.MonitorProxyPluginConfig;
import com.chua.report.server.starter.service.MonitorProxyPluginConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 代理
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/proxy/plugin/config")
@Tag(name = "代理组件")
@RequiredArgsConstructor
public class MonitorProxyPluginConfigController {

    private final MonitorProxyPluginConfigService monitorProxyPluginConfigService;


    /**
     * 查询代理所有的组件
     * @param proxyId
     * @return
     */
    @Operation(summary = "查询代理所有的组件")
    @GetMapping("list")
    public ReturnResult<List<MonitorProxyPluginConfig>> get(String proxyId) {
        return ReturnResult.success(monitorProxyPluginConfigService.list(Wrappers.<MonitorProxyPluginConfig>lambdaQuery()
                .eq(MonitorProxyPluginConfig::getProxyId, proxyId)));
    }

    /**
     * 更新组件配置
     * @param entity
     * @return
     */
    @Operation(summary = "更新组件配置")
    @PutMapping("update")
    public ReturnResult<Boolean> update(@RequestBody MonitorProxyPluginConfig entity) {
        return ReturnResult.of(monitorProxyPluginConfigService.updateById(entity));
    }

    /**
     * 删除组件配置
     * @param id
     * @return
     */
    @Operation(summary = "删除组件配置")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(@RequestParam("proxyPluginId") String proxyPluginId) {
        return ReturnResult.of(monitorProxyPluginConfigService.removeById(proxyPluginId));
    }

    /**
     * 添加组件配置
     * @param entity
     * @return
     * */
    @Operation(summary = "添加组件配置")
    @PostMapping("save")
    public ReturnResult<Boolean> save(@RequestBody MonitorProxyPluginConfig entity) {
        return ReturnResult.of(monitorProxyPluginConfigService.save(entity));
    }
}
