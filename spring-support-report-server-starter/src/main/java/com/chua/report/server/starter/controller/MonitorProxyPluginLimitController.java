package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.report.server.starter.entity.MonitorProxyPluginLimit;
import com.chua.report.server.starter.service.MonitorProxyPluginLimitService;
import com.chua.report.server.starter.service.MonitorProxyService;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 代理限流
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/proxy/plugin/limit")
@Tag(name = "代理限流")
@RequiredArgsConstructor
public class MonitorProxyPluginLimitController {

    final MonitorProxyPluginLimitService monitorProxyPluginLimitService;
    final MonitorProxyService monitorProxyService;
    final TransactionTemplate transactionTemplate;
    /**
     * 查询代理所有的限流组件
     * @param monitorProxyPluginLimit
     * @return
     */
    @Operation(summary = "查询代理所有的限流组件")
    @GetMapping("list")
    public ReturnResult<List<MonitorProxyPluginLimit>> get( MonitorProxyPluginLimit monitorProxyPluginLimit) {
        return ReturnResult.success(monitorProxyPluginLimitService.list(Wrappers.<MonitorProxyPluginLimit>lambdaQuery()
                .eq(MonitorProxyPluginLimit::getProxyId, monitorProxyPluginLimit.getProxyId())
                .eq(MonitorProxyPluginLimit::getProxyConfigLimitType, monitorProxyPluginLimit.getProxyConfigLimitType())
        ));
    }
    /**
     * 查询代理所有的限流组件
     * @return
     */
    @Operation(summary = "查询代理所有的限流组件")
    @GetMapping("page")
    public ReturnPageResult<MonitorProxyPluginLimit> page(Query<MonitorProxyPluginLimit> query, MonitorProxyPluginLimit monitorProxyPluginLimit) {
        return PageResultUtils.ok(monitorProxyPluginLimitService.page(query.createPage(), Wrappers.<MonitorProxyPluginLimit>lambdaQuery()
                .eq(MonitorProxyPluginLimit::getProxyId, monitorProxyPluginLimit.getProxyId())
                .eq(MonitorProxyPluginLimit::getProxyConfigLimitType, monitorProxyPluginLimit.getProxyConfigLimitType())
        ));
    }

    /**
     * 更新限流组件配置
     * @param entity
     * @return
     */
    @Operation(summary = "更新限流组件配置")
    @PutMapping("update")
    public ReturnResult<Boolean> update(@RequestBody MonitorProxyPluginLimit entity) {
        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            MonitorProxyPluginLimit byId = monitorProxyPluginLimitService.getById(entity.getProxyConfigLimitId());
            monitorProxyPluginLimitService.updateById(entity);
            monitorProxyService.refresh(String.valueOf(byId.getProxyId()));
            return true;
        })));
    }

    /**
     * 删除限流组件配置
     * @param proxyConfigLimitId
     * @return
     */
    @Operation(summary = "删除限流组件配置")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(@RequestParam("proxyConfigLimitId") String proxyConfigLimitId) {
        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            MonitorProxyPluginLimit byId = monitorProxyPluginLimitService.getById(proxyConfigLimitId);
            monitorProxyPluginLimitService.removeById(proxyConfigLimitId);
            monitorProxyService.refresh(String.valueOf(byId.getProxyId()));
            return true;
        })));

    }

    /**
     * 添加限流组件配置
     * @param entity
     * @return
     * */
    @Operation(summary = "添加限流组件配置")
    @PostMapping("save")
    public ReturnResult<Boolean> save(@RequestBody MonitorProxyPluginLimit entity) {
        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            monitorProxyPluginLimitService.save(entity);
            monitorProxyService.refresh(String.valueOf(entity.getProxyId()));
            return true;
        })));
    }
}
