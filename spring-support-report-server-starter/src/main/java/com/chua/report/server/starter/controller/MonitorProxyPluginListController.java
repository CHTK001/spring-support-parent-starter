package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.report.server.starter.entity.MonitorProxyPluginLimit;
import com.chua.report.server.starter.entity.MonitorProxyPluginList;
import com.chua.report.server.starter.service.MonitorProxyPluginLimitService;
import com.chua.report.server.starter.service.MonitorProxyPluginListService;
import com.chua.report.server.starter.service.MonitorProxyPluginListService;
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
 * 代理
 * @author CH
 * @since 2024/5/13
 */
@RestController
@RequestMapping("v1/proxy/plugin/list")
@Tag(name = "代理名单")
@RequiredArgsConstructor
public class MonitorProxyPluginListController {

    final MonitorProxyPluginListService monitorProxyPluginListService;
    final MonitorProxyService monitorProxyService;
    final TransactionTemplate transactionTemplate;

    /**
     * 查询代理所有的名单组件
     * @param proxyId
     * @return
     */
    @Operation(summary = "查询代理所有的名单组件")
    @GetMapping("list")
    public ReturnResult<List<MonitorProxyPluginList>> get(String proxyId, String proxyPluginId) {
        return ReturnResult.success(monitorProxyPluginListService.list(Wrappers.<MonitorProxyPluginList>lambdaQuery()
                .eq(MonitorProxyPluginList::getProxyId, proxyId)
                .eq(MonitorProxyPluginList::getProxyPluginId, proxyPluginId)
        ));
    }
    /**
     * 查询代理所有的限流组件
     * @return
     */
    @Operation(summary = "查询代理所有的限流组件")
    @GetMapping("page")
    public ReturnPageResult<MonitorProxyPluginList> page(Query<MonitorProxyPluginList> query, MonitorProxyPluginList monitorProxyPluginList) {
        return PageResultUtils.ok(monitorProxyPluginListService.page(query.createPage(), Wrappers.<MonitorProxyPluginList>lambdaQuery()
                .eq(MonitorProxyPluginList::getProxyId, monitorProxyPluginList.getProxyId())
                .eq(MonitorProxyPluginList::getProxyConfigListType, monitorProxyPluginList.getProxyConfigListType())
        ));
    }
    /**
     * 更新名单组件配置
     * @param entity
     * @return
     */
    @Operation(summary = "更新名单组件配置")
    @PutMapping("update")
    public ReturnResult<Boolean> update(@RequestBody MonitorProxyPluginList entity) {
        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            MonitorProxyPluginList byId = monitorProxyPluginListService.getById(entity.getProxyConfigListId());
            monitorProxyPluginListService.updateById(entity);
            monitorProxyService.refresh(String.valueOf(byId.getProxyId()));
            return true;
        })));
    }

    /**
     * 删除名单组件配置
     * @param proxyConfigLimitId
     * @return
     */
    @Operation(summary = "删除名单组件配置")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(@RequestParam("proxyConfigLimitId") String proxyConfigLimitId) {
        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            MonitorProxyPluginList byId = monitorProxyPluginListService.getById(proxyConfigLimitId);
            monitorProxyPluginListService.removeById(proxyConfigLimitId);
            monitorProxyService.refresh(String.valueOf(byId.getProxyId()));
            return true;
        })));
    }

    /**
     * 添加名单组件配置
     * @param entity
     * @return
     * */
    @Operation(summary = "添加名单组件配置")
    @PostMapping("save")
    public ReturnResult<Boolean> save(@RequestBody MonitorProxyPluginList entity) {
        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            monitorProxyPluginListService.save(entity);
            monitorProxyService.refresh(String.valueOf(entity.getProxyId()));
            return true;
        })));
    }
}
