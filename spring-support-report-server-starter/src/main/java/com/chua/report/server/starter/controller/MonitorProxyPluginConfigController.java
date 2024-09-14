package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.report.server.starter.entity.MonitorProxyPluginConfig;
import com.chua.report.server.starter.service.MonitorProxyPluginConfigService;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.chua.common.support.lang.code.ReturnCode.REQUEST_PARAM_ERROR;

/**
 * 代理配置
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/proxy/plugin/config")
@Tag(name = "代理插件配置")
@RequiredArgsConstructor
public class MonitorProxyPluginConfigController {

    private final MonitorProxyPluginConfigService monitorProxyPluginConfigService;

    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "添加数据")
    @PostMapping("save")
    public ReturnResult<Boolean> save(@Validated(AddGroup.class) @RequestBody MonitorProxyPluginConfig t, @Ignore BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        if(null == t.getPluginName() || null == t.getPluginSort()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, "pluginName/pluginSort不能为空");
        }
        MonitorProxyPluginConfig one = monitorProxyPluginConfigService.getOne(Wrappers.<MonitorProxyPluginConfig>lambdaQuery()
                .eq(MonitorProxyPluginConfig::getPluginName, t.getPluginName())
                .eq(MonitorProxyPluginConfig::getPluginSort, t.getPluginSort())
                .eq(MonitorProxyPluginConfig::getPluginConfigName, t.getPluginConfigName()));
        if(null != one) {
            one.setPluginConfigValue(t.getPluginConfigValue());
            return ReturnResult.ok(monitorProxyPluginConfigService.updateById(one));
        }
        return ReturnResult.ok(monitorProxyPluginConfigService.save(t));
    }
    /**
     * 查询基础数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "查询基础数据")
    @GetMapping("list")
    public ReturnResult<List<MonitorProxyPluginConfig>> list(MonitorProxyPluginConfig entity) {
        return ReturnResult.ok(monitorProxyPluginConfigService.list( Wrappers.<MonitorProxyPluginConfig>lambdaQuery()
                .eq(MonitorProxyPluginConfig::getPluginName, entity.getPluginName())
                .eq(MonitorProxyPluginConfig::getProxyId, entity.getProxyId())
                .eq(MonitorProxyPluginConfig::getPluginSort, entity.getPluginSort())
        ));
    }


}
