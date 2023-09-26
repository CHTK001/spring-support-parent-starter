package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGenColumn;
import com.chua.starter.gen.support.entity.SysGenTable;
import com.chua.starter.gen.support.query.SysGenColumnUpdate;
import com.chua.starter.gen.support.service.SysGenColumnService;
import com.chua.starter.gen.support.service.SysGenService;
import com.chua.starter.gen.support.service.SysGenTableService;
import com.chua.starter.gen.support.vo.TableResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 生成器控制器
 *
 * @author CH
 */
@Api(tags = "表信息接口")
@RestController
@RequestMapping("v1/column")
public class ColumnController {

    @Resource
    private SysGenService sysGenService;

    @Resource
    private SysGenTableService sysGenTableService;

    @Resource
    private SysGenColumnService sysGenColumnService;
    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private TransactionTemplate transactionTemplate;
    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @Operation(summary = "获取表的字段列表")
    @GetMapping("column")
    public ReturnPageResult<SysGenColumn> columnList(
            @ApiParam("表ID") String tableId,
            @ApiParam("页码") @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
            @ApiParam("每页数量") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
            ) {
        SysGenTable sysGenTable = sysGenTableService.getById(tableId);
        if(null == sysGenTable) {
            return ReturnPageResult.error("表不存在");
        }

        return PageResultUtils.ok(sysGenColumnService.page(new Page<>(pageNum, pageSize),
                Wrappers.<SysGenColumn>lambdaQuery()
                        .eq(SysGenColumn::getTabId, tableId)
        ));
    }

    /**
     * 更新表字段信息
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @Operation(summary = "更新表字段信息")
    @PutMapping("update")
    public ReturnResult<Boolean> updateColumn(@RequestBody SysGenColumnUpdate sysGenColumnUpdate) {
        List<SysGenColumn> columns = sysGenColumnUpdate.getColumns();
        String tabId = sysGenColumnUpdate.getTabId();
        return ReturnResult.ok(transactionTemplate.execute(status -> {
            sysGenColumnService.remove( Wrappers.<SysGenColumn>lambdaQuery().eq(SysGenColumn::getTabId, tabId));
            sysGenColumnService.saveBatch(columns);
            return true;
        }));
    }

    /**
     * 查询表信息
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @GetMapping("info")
    public ReturnResult<List<SysGenColumn>> info(String tabId) {
        if(StringUtils.isEmpty(tabId)) {
            return ReturnResult.illegal("暂无信息");
        }
        return ReturnResult.ok(sysGenColumnService.list(Wrappers.<SysGenColumn>lambdaQuery().eq(SysGenColumn::getTabId, tabId)));
    }

}
