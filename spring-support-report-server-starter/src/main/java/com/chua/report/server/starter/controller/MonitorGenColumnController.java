package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.server.starter.entity.MonitorSysGenColumn;
import com.chua.report.server.starter.entity.MonitorSysGenTable;
import com.chua.report.server.starter.query.SysGenColumnUpdate;
import com.chua.report.server.starter.service.MonitorSysGenColumnService;
import com.chua.report.server.starter.service.MonitorSysGenService;
import com.chua.report.server.starter.service.MonitorSysGenTableService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字段接口
 *
 * @author CH
 */
@RestController
@Tag(name = "字段接口")
@RequestMapping("v1/gen/column")
@RequiredArgsConstructor
public class MonitorGenColumnController {

    private final MonitorSysGenService sysGenService;
    private final MonitorSysGenTableService sysGenTableService;
    private final MonitorSysGenColumnService sysGenColumnService;
    private final ApplicationContext applicationContext;
    private final TransactionTemplate transactionTemplate;
    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}
     */
    @Operation(summary = "表列表")
    @GetMapping("column")
    public ReturnPageResult<MonitorSysGenColumn> columnList(String tableId, @RequestParam(value = "page", defaultValue = "1") Integer pageNum, @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
            ) {
        MonitorSysGenTable sysGenTable = sysGenTableService.getById(tableId);
        if(null == sysGenTable) {
            return ReturnPageResult.error("表不存在");
        }

        return PageResultUtils.ok(sysGenColumnService.page(new Page<>(pageNum, pageSize),
                Wrappers.<MonitorSysGenColumn>lambdaQuery()
                        .eq(MonitorSysGenColumn::getTabId, tableId)
        ));
    }

    /**
     * 更新表字段信息
     *
     * @return {@link ReturnPageResult}
     */
    @Operation(summary = "更新表字段信息")
    @PutMapping("update")
    public ReturnResult<Boolean> updateColumn(@RequestBody SysGenColumnUpdate sysGenColumnUpdate) {
        List<MonitorSysGenColumn> columns = sysGenColumnUpdate.getColumns();
        String tabId = sysGenColumnUpdate.getTabId();
        return ReturnResult.ok(transactionTemplate.execute(status -> {
            sysGenColumnService.remove( Wrappers.<MonitorSysGenColumn>lambdaQuery().eq(MonitorSysGenColumn::getTabId, tabId));
            sysGenColumnService.saveBatch(columns);
            return true;
        }));
    }

    /**
     * 查询表信息
     *
     * @return {@link ReturnPageResult}
     */
    @Operation(summary = "查询表信息")
    @GetMapping("info")
    public ReturnResult<List<MonitorSysGenColumn>> info(String tabId) {
        if(StringUtils.isEmpty(tabId)) {
            return ReturnResult.illegal("暂无信息");
        }
        return ReturnResult.ok(sysGenColumnService.list(Wrappers.<MonitorSysGenColumn>lambdaQuery().eq(MonitorSysGenColumn::getTabId, tabId)));
    }

}
