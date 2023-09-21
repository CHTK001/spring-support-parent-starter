package com.chua.starter.gen.support.controller;

import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.PageResult;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.query.TableQuery;
import com.chua.starter.gen.support.service.SysGenService;
import com.chua.starter.gen.support.vo.ColumnResult;
import com.chua.starter.gen.support.vo.TableResult;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
@RestController
@RequestMapping("v1/gen")
public class GenController {

    @Resource
    private SysGenService sysGenService;


    @Resource
    private ApplicationContext applicationContext;



    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
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
    @GetMapping("column")
    public ReturnPageResult<TableResult> columnList(TableQuery query) {
        String tableName = query.getTableName();
        if(StringUtils.isEmpty(tableName)) {
            return ReturnPageResult.ok();
        }

        List<ColumnResult> results = new LinkedList<>();
        try {
            Connection connection = getConnection(query);
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
        List<ColumnResult> page = CollectionUtils.page((int) query.getPage(), (int) query.getPageSize(), results);
        return ReturnPageResult.ok(
                PageResult.<ColumnResult>builder()
                        .total(results.size())
                        .data(page)
                        .pageSize((int) query.getPageSize())
                        .page((int) query.getPage())
        );
    }

    /**
     * 获取连接
     *
     * @param query 查询
     * @return {@link Connection}
     * @throws SQLException SQLException
     */
    private Connection getConnection(TableQuery query) throws SQLException {
        if(StringUtils.isNotEmpty(query.getDataSourceId())) {
            DataSource dataSource = applicationContext.getBean(query.getDataSourceName(), DataSource.class);
            return dataSource.getConnection();
        }

        SysGen sysGen = sysGenService.getById(query.getDataSourceId());
        return DriverManager.getConnection(sysGen.getGenUrl(), sysGen.getGenUser(), sysGen.getGenPassword());
    }
}
