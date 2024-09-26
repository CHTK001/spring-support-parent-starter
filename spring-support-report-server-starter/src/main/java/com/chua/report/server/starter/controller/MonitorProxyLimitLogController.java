package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.report.server.starter.entity.MonitorProxyLog;
import com.chua.report.server.starter.pojo.LogStatistic;
import com.chua.report.server.starter.pojo.MonitorProxyLimitLogResult;
import com.chua.report.server.starter.service.MonitorProxyLogService;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 代理限流器日志
 * @author CH
 * @since 2024/6/21
 */
@RestController
@RequestMapping("v1/proxy/log")
@Tag(name = "代理限流器日志")
@RequiredArgsConstructor
public class MonitorProxyLimitLogController {
    
    final MonitorProxyLogService monitorProxyLogService;
    final TransactionTemplate transactionTemplate;

    /**
     * 分页查询数据
     *
     * @return 分页结果
     */
    @Operation(summary = "删除")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> logDelete(@RequestParam(defaultValue = "3")Integer limitMonth ) {
        return ReturnResult.of(monitorProxyLogService.delete(limitMonth));
    }
    /**
     * 分页查询数据
     *
     * @param entity 结果
     * @return 分页结果
     */
    @Operation(summary = "日志分页查询基础数据")
    @GetMapping("page")
    public ReturnPageResult<MonitorProxyLimitLogResult> logPage(Query<MonitorProxyLog> page, MonitorProxyLog entity) {
        Page<MonitorProxyLimitLogResult> tPage = monitorProxyLogService.pageForLog(page.createPage(), entity);
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
    public ReturnResult<LogStatistic> statistic(Query<MonitorProxyLog> page, MonitorProxyLog entity) {
        return ReturnResult.ok(monitorProxyLogService.listForGeo(page.createPage(), entity));
    }

}
