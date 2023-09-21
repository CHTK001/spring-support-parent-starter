package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
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
import com.chua.starter.gen.support.vo.TableResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.annotations.Api;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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
public class TableController {

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
    @GetMapping("table")
    public ReturnPageResult<TableResult> tableList(TableQuery query) {
        SysGen sysGen = getSysGen(query);
        List<TableResult> results = new LinkedList<>();
        try (Connection connection = getConnection(sysGen, query);) {
            DatabaseMetaData metaData = connection.getMetaData();
            String database = null != sysGen ? sysGen.getGenDatabase() : null;

            ResultSet resultSet = metaData.getTables(database, null, "%", new String[]{"table"});
            while (resultSet.next()) {
                TableResult item = new TableResult();
                item.setTableName(resultSet.getString("TABLE_NAME"));
                item.setDatabase(resultSet.getString("TABLE_CAT"));
                item.setRemark(resultSet.getString("REMARKS"));
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
                        .page((int) query.getPage()).build()
        );
    }

    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @PostMapping("importColumn")
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> importColumn(@RequestBody TableQuery query) {
        String[] tableName = query.getTableName();
        if (ArrayUtils.isEmpty(tableName)) {
            return ReturnResult.error(null, "表不存在");
        }

        try (Connection connection = getConnection(query);) {
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


        } catch (Exception e) {
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
    @GetMapping("page")
    public ReturnPageResult<SysGenTable> page(TableQuery query) {
        return PageResultUtils.ok(sysGenTableService.page(
                new Page<>(query.getPage(), query.getPageSize()),
                Wrappers.<SysGenTable>lambdaQuery()
                        .like(StringUtils.isNotBlank(query.getKeyword()), SysGenTable::getTabName, query.getKeyword())
        ));
    }

    /**
     * 更新表信息
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
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
    @GetMapping("deleteTable")
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> deleteTable(String tableId) {
        sysGenTableService.removeById(tableId);
        sysGenColumnService.remove(Wrappers.<SysGenColumn>lambdaQuery().eq(SysGenColumn::getTabId, tableId));
        return ReturnResult.ok();
    }

    /**
     * 获取连接
     *
     * @param query 查询
     * @return {@link Connection}
     * @throws SQLException SQLException
     */
    private Connection getConnection(TableQuery query) throws SQLException {
        if (null == query.getGenId()) {
            DataSource dataSource = applicationContext.getBean(query.getDataSourceName(), DataSource.class);
            return dataSource.getConnection();
        }

        SysGen sysGen = sysGenService.getById(query.getGenId());
        return DriverManager.getConnection(sysGen.getGenUrl(), sysGen.getGenUser(), sysGen.getGenPassword());
    }

    /**
     * 获取连接
     *
     * @param query 查询
     * @return {@link Connection}
     * @throws SQLException SQLException
     */
    private Connection getConnection(SysGen sysGen, TableQuery query) throws SQLException {
        if (null == query.getGenId()) {
            DataSource dataSource = applicationContext.getBean(query.getDataSourceName(), DataSource.class);
            return dataSource.getConnection();
        }

        return DriverManager.getConnection(sysGen.getGenUrl(), sysGen.getGenUser(), sysGen.getGenPassword());
    }

    /**
     * 获取数据库
     *
     * @param query 查询
     * @return {@link String}
     */
    private String getDatabase(TableQuery query) {
        if (null == query.getGenId()) {
            return null;
        }

        SysGen sysGen = sysGenService.getById(query.getGenId());
        return null != sysGen ? sysGen.getGenDatabase() : null;
    }

    /**
     * 获取数据库
     *
     * @param query 查询
     * @return {@link String}
     */
    private SysGen getSysGen(TableQuery query) {
        if (null == query.getGenId()) {
            return null;
        }

        return sysGenService.getById(query.getGenId());
    }
}
