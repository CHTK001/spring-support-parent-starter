package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.report.server.starter.entity.MonitorProxyPlugin;
import com.chua.report.server.starter.service.MonitorProxyPluginService;
import com.github.xiaoymin.knife4j.annotations.Ignore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
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
@RequestMapping("v1/proxy/plugin")
@Tag(name = "代理插件")
@RequiredArgsConstructor
public class MonitorProxyPluginController {

    private final MonitorProxyPluginService monitorProxyPluginService;

    private final TransactionTemplate transactionTemplate;
    /**
     * 添加数据
     *
     * @param t 实体
     * @return 分页结果
     */
    @ResponseBody
    @Operation(summary = "添加数据")
    @PostMapping("save")
    public ReturnResult<Boolean> save(@Validated(AddGroup.class) @RequestBody String json, @Ignore BindingResult bindingResult) {
        List<MonitorProxyPlugin> t = Json.fromJsonToList(json, MonitorProxyPlugin.class);
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        if(t.isEmpty()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, "插件不能为空");
        }

        if(CollectionUtils.isEmpty(t) && null == t.get(0).getProxyId()) {
            return ReturnResult.illegal(REQUEST_PARAM_ERROR, "插件不能为空/proxyId不能为空");
        }

        return ReturnResult.ok(transactionTemplate.execute(status -> {
            monitorProxyPluginService.remove(Wrappers.<MonitorProxyPlugin>lambdaQuery()
                    .eq(MonitorProxyPlugin::getProxyId, t.get(0).getProxyId()));
            monitorProxyPluginService.saveBatch(t);
            return true;
        }));
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
    public ReturnResult<List<MonitorProxyPlugin>> list(MonitorProxyPlugin entity) {
        return ReturnResult.ok(monitorProxyPluginService.list(
                Wrappers.<MonitorProxyPlugin>lambdaQuery().eq(MonitorProxyPlugin::getProxyId, entity.getProxyId())));
    }


}
