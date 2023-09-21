package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.starter.common.support.result.PageResult;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.entity.SysGenColumn;
import com.chua.starter.gen.support.entity.SysGenTable;
import com.chua.starter.gen.support.query.TableQuery;
import com.chua.starter.gen.support.service.SysGenColumnService;
import com.chua.starter.gen.support.service.SysGenService;
import com.chua.starter.gen.support.service.SysGenTableService;
import com.chua.starter.gen.support.vo.ColumnResult;
import com.chua.starter.gen.support.vo.TableResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.*;
import java.util.LinkedList;
import java.util.List;

/**
 * 生成器控制器
 *
 * @author CH
 */
@Api(tags = "表信息接口")
@RestController
@RequestMapping("v1/table")
public class GenController {

    @Resource
    private SysGenService sysGenService;

    @Resource
    private SysGenTableService sysGenTableService;

    @Resource
    private SysGenColumnService sysGenColumnService;
    @Resource
    private ApplicationContext applicationContext;



    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @Operation(summary = "获取表列表")
    @GetMapping("table")
    public ReturnPageResult<TableResult> tableList(TableQuery query) {
        List<TableResult> results = new LinkedList<>();
        try {
            Connection connection = getConnection(query);
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet resultSet = metaData.getTables(null, null, "%", new String[]{"table"});
            while (resultSet.next()) {
                TableResult item = new TableResult();
                item.setTableName(resultSet.getString(3));
                results.add(item);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        List<TableResult> page = CollectionUtils.page((int) query.getPage(), (int) query.getPageSize(), results);
        return ReturnPageResult.ok(
                PageResult.<TableResult>builder()
                        .total(results.size())
                        .data(page)
                        .pageSize((int) query.getPageSize())
                        .page((int) query.getPage())
        );
    }

    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @Operation(summary = "导入")
    @GetMapping("importColumn")
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> importColumn(TableQuery query) {
        String[] tableName = query.getTableName();
        if(ArrayUtils.isEmpty(tableName)) {
            return ReturnResult.error(null, "表不存在");
        }

        try (Connection connection = getConnection(query);){
            for (String s : tableName) {
                DatabaseMetaData metaData = connection.getMetaData();
                ResultSet tableResultSet = metaData.getTables(null, null, s, new String[]{"table"});
                SysGenTable sysGenTable = null;
                while (tableResultSet.next()) {
                    sysGenTable = SysGenTable.createSysGenTable(query.getGenId(), s, tableResultSet);
                }
                sysGenTable.setGenName(query.getDataSourceName());

                sysGenTableService.save(sysGenTable);
                List<SysGenColumn> rs = new LinkedList<>();
                ResultSet resultSet = metaData.getColumns(null, null, s, null);
                while (resultSet.next()) {
                    rs.add(SysGenColumn.createSysGenColumn(sysGenTable, s, resultSet));
                }

                sysGenColumnService.saveBatch(rs);
            }


        } catch (SQLException e) {
            e.printStackTrace();
            return ReturnResult.error(null, "导入失败");
        }

        return ReturnResult.ok();
    }
    /**
     * 更新表信息
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @Operation(summary = "更新表信息")
    @GetMapping("updateTable")
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> updateTable(SysGenTable sysGenTable) {
        sysGenTableService.updateById(sysGenTable);
        return ReturnResult.ok();
    }
    /**
     * 删除表信息
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @Operation(summary = "删除表信息")
    @GetMapping("deleteTable")
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> deleteTable(String tableId) {
        sysGenTableService.removeById(tableId);
        sysGenColumnService.remove(Wrappers.<SysGenColumn>lambdaQuery().eq(SysGenColumn::getTabId, tableId));
        return ReturnResult.ok();
    }


    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @Operation(summary = "获取表的字段列表")
    @GetMapping("column")
    public ReturnPageResult<TableResult> columnList(
            @ApiParam("表ID") String tableId,
            @ApiParam("页码") @RequestParam(value = "page", defaultValue = "1") Integer pageNum,
            @ApiParam("每页数量") @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize
            ) {
        SysGenTable sysGenTable = sysGenTableService.getById(tableId);
        if(null == sysGenTable) {
            return ReturnPageResult.error("表不存在");
        }

        String tableName = sysGenTable.getTabName();
        List<ColumnResult> results = new LinkedList<>();
        try (Connection connection = getConnection(sysGenTable)){
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet resultSet = metaData.getColumns(null, null, tableName, null);
            while (resultSet.next()) {
                ColumnResult item = new ColumnResult();
                item.setTableName(tableName);
                item.setColumn(resultSet.getString("COLUMN_NAME"));
                item.setColumnType(resultSet.getString("TYPE_NAME"));
                item.setColumnTypeCode(resultSet.getInt("DATA_TYPE"));

                results.add(item);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        List<ColumnResult> page = CollectionUtils.page(pageNum, pageSize, results);
        return ReturnPageResult.ok(
                PageResult.<ColumnResult>builder()
                        .total(results.size())
                        .data(page)
                        .pageSize(pageSize)
                        .page(pageNum)
        );
    }

    /**
     * 获取连接
     *
     * @param sysGenTable 查询
     * @return {@link Connection}
     * @throws SQLException SQLException
     */
    private Connection getConnection(SysGenTable sysGenTable) throws SQLException {
        if(null == sysGenTable.getGenId()) {
            DataSource dataSource = applicationContext.getBean(sysGenTable.getGenName(), DataSource.class);
            return dataSource.getConnection();
        }

        SysGen sysGen = sysGenService.getById(sysGenTable.getGenId());
        return DriverManager.getConnection(sysGen.getGenUrl(), sysGen.getGenUser(), sysGen.getGenPassword());
    }

    /**
     * 获取连接
     *
     * @param query 查询
     * @return {@link Connection}
     * @throws SQLException SQLException
     */
    private Connection getConnection(TableQuery query) throws SQLException {
        if(null == query.getGenId()) {
            DataSource dataSource = applicationContext.getBean(query.getDataSourceName(), DataSource.class);
            return dataSource.getConnection();
        }

        SysGen sysGen = sysGenService.getById(query.getGenId());
        return DriverManager.getConnection(sysGen.getGenUrl(), sysGen.getGenUser(), sysGen.getGenPassword());
    }
}
