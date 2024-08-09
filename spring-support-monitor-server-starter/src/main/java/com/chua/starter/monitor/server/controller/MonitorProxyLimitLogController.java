package com.chua.starter.monitor.server.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.server.entity.MonitorProxyLimitLog;
import com.chua.starter.monitor.server.pojo.LogStatistic;
import com.chua.starter.monitor.server.pojo.MonitorProxyLimitLogResult;
import com.chua.starter.monitor.server.service.MonitorProxyLimitLogService;
import com.chua.starter.monitor.server.service.MonitorProxyLimitService;
import com.chua.starter.monitor.server.service.MonitorProxyService;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 代理限流器
 * @author CH
 * @since 2024/6/21
 */
@RestController
@RequestMapping("v1/proxy/limit/log")
@Tag(name = "代理限流器")
@RequiredArgsConstructor
public class MonitorProxyLimitLogController {
    
    final MonitorProxyLimitService service;
    final MonitorProxyService monitorProxyService;
    final MonitorProxyLimitLogService monitorProxyLimitLogService;
    final TransactionTemplate transactionTemplate;

    /**
     * 分页查询数据
     *
     * @return 分页结果
     */
    @Operation(summary = "删除")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> logDelete(@RequestParam(defaultValue = "3")Integer limitMonth ) {
        return ReturnResult.of(monitorProxyLimitLogService.delete(limitMonth));
    }
    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @Operation(summary = "日志分页查询基础数据")
    @GetMapping("page")
    public ReturnPageResult<MonitorProxyLimitLogResult> logPage(Query<MonitorProxyLimitLog> page, MonitorProxyLimitLog entity) {
        Page<MonitorProxyLimitLogResult> tPage = monitorProxyLimitLogService.pageForLog(page.createPage(), entity);
        return PageResultUtils.ok(tPage);
    }

    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @Operation(summary = "根据IP统计")
    @GetMapping("statistic")
    public ReturnResult<LogStatistic> statistic(Query<MonitorProxyLimitLog> page, MonitorProxyLimitLog entity) {
        return ReturnResult.ok(monitorProxyLimitLogService.listForGeo(page.createPage(), entity));
    }

}
