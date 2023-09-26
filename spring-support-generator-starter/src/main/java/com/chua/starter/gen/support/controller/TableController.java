package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.database.DatabaseHandler;
import com.chua.common.support.database.entity.ColumnResult;
import com.chua.common.support.database.entity.TableResult;
import com.chua.common.support.database.sqldialect.Dialect;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.lang.date.constant.DateFormatConstant;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.PageResult;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.entity.SysGenColumn;
import com.chua.starter.gen.support.entity.SysGenTable;
import com.chua.starter.gen.support.properties.GenProperties;
import com.chua.starter.gen.support.query.Download;
import com.chua.starter.gen.support.query.TableQuery;
import com.chua.starter.gen.support.result.TemplateResult;
import com.chua.starter.gen.support.service.SysGenColumnService;
import com.chua.starter.gen.support.service.SysGenService;
import com.chua.starter.gen.support.service.SysGenTableService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.swagger.annotations.Api;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Resource
    private GenProperties genProperties;


    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @GetMapping("table")
    public ReturnPageResult<TableResult> tableList(TableQuery query) {
        SysGen sysGen = getSysGen(query);
        String database = null != sysGen ? sysGen.getGenDatabase() : null;
        List<TableResult> results;
        try (DatabaseHandler handler = new DatabaseHandler(sysGen.newDatabaseConfig())) {
            results = handler.getTables(database, "%");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Map<String, TableResult> tpl = new HashMap<>(results.size());
        for (TableResult tableResult : results) {
            tpl.put(tableResult.getTableName(), tableResult);
        }

        List<SysGenTable> rs = sysGenTableService.list(Wrappers.<SysGenTable>lambdaQuery()
                .eq(SysGenTable::getGenId, query.getGenId())
                .in(SysGenTable::getTabName, tpl.keySet())
        );
        for (SysGenTable r : rs) {
            tpl.remove(r.getTabName());
        }

        results = new LinkedList<>(tpl.values());

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
     * 批生成代码
     * 批量生成代码
     *
     * @param download 选项卡ID
     * @return {@link ResponseEntity}<{@link byte[]}>
     * @throws IOException IOException
     */
    @PostMapping("/batchGenCode")
    public ResponseEntity<byte[]> batchGenCode(@RequestBody Download download) throws IOException {
        byte[] data = sysGenTableService.downloadCode(download);
        return ResponseEntity.ok()
                .header("Content-Length", String.valueOf(data.length))
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .header("Content-Disposition", "attachment; filename=\"code" + DateTime.now().toString(DateFormatConstant.YYYYMMDD) + ".zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);

    }

    /**
     * 批量生成代码
     *
     * @param tabId tabId
     * @return {@link ResponseEntity}<{@link byte[]}>
     * @throws IOException IOException
     */
    @GetMapping("/template")
    public ReturnResult<List<TemplateResult>> template(Integer tabId) throws IOException {
        return ReturnResult.ok(sysGenTableService.template(tabId));
    }

    /**
     * 同步表结构
     *
     * @param tabId tabId
     * @return {@link ResponseEntity}<{@link byte[]}>
     * @throws IOException IOException
     */
    @GetMapping("/sync")
    public ReturnResult<Boolean> sync(Integer tabId) throws Exception {
        if(null == tabId) {
            return ReturnResult.illegal("表信息不存在");
        }
        SysGen sysGen = sysGenService.getOne(new MPJLambdaWrapper<SysGen>()
                .selectAll(SysGen.class)
                    .select(SysGenTable::getTabName)
                    .select(SysGenTable::getTabDesc)
                .leftJoin(SysGenTable.class, SysGenTable::getGenId, SysGen::getGenId)
                .eq(SysGenTable::getTabId, tabId)
        );
        try (DatabaseHandler handler = new DatabaseHandler(sysGen.newDatabaseConfig())) {
            List<SysGenColumn> sysGenColumns = sysGenColumnService.list(Wrappers.<SysGenColumn>lambdaQuery().eq(SysGenColumn::getTabId, tabId));
            TableResult tableResult = new TableResult();
            tableResult.setDatabase(sysGen.getGenDatabase());
            tableResult.setTableName(sysGen.getTabName());
            tableResult.setRemark(sysGen.getTabDesc());
            handler.updateTable(tableResult);
            handler.updateColumn(sysGenColumns.stream().map(it -> {
                ColumnResult columnResult = new ColumnResult();
                columnResult.setDatabaseName(sysGen.getGenDatabase());
                columnResult.setDecimalDigits(it.getColColumnDecimal());
                columnResult.setColumnSize(it.getColColumnLength());
                columnResult.setColumnName(it.getColColumnName());
                columnResult.setRemarks(it.getColColumnComment());
                columnResult.setTypeName(it.getColColumnType());
                columnResult.setTableName(sysGen.getTabName());
                return columnResult;
            }).collect(Collectors.toSet()));
        } catch (Exception e) {
            throw new RuntimeException("同步失败");
        }
        return ReturnResult.ok();
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
        SysGen sysGen = getSysGen(query);
        try (DatabaseHandler handler = new DatabaseHandler(sysGen.newDatabaseConfig())) {
            Dialect dialect = handler.guessDialect();
            for (String s : tableName) {
                List<TableResult> tables = handler.getTables(null, s);
                SysGenTable sysGenTable = null;
                if(!tables.isEmpty()) {
                    sysGenTable = SysGenTable.createSysGenTable(query.getGenId(), s, tables.get(0), genProperties);
                    sysGenTable.setGenName(sysGen.getGenName());
                    sysGenTableService.save(sysGenTable);
                }

                List<SysGenColumn> rs = new LinkedList<>();
                List<ColumnResult> resultSet = handler.getColumns(sysGen.getGenDatabase(),  s);
                for (ColumnResult columnResult : resultSet) {
                    rs.add(SysGenColumn.createSysGenColumn(dialect, sysGenTable, s, columnResult));
                }

                sysGenColumnService.saveBatch(rs);
            }


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("导入失败");
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
    @PutMapping("update")
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> updateTable(@RequestBody SysGenTable sysGenTable) {
        sysGenTableService.updateById(sysGenTable);
        return ReturnResult.ok();
    }

    /**
     * 删除表信息
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @GetMapping("delete")
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> deleteTable(String tableId) {
        if(StringUtils.isEmpty(tableId)) {
            return ReturnResult.illegal("请选择删除的表");
        }
        for (String s : tableId.split(CommonConstant.SYMBOL_COMMA)) {
            sysGenTableService.removeById(s);
            sysGenColumnService.remove(Wrappers.<SysGenColumn>lambdaQuery().eq(SysGenColumn::getTabId, s));
        }
        return ReturnResult.ok();
    }

    /**
     * 查询表信息
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @GetMapping("info")
    public ReturnResult<SysGenTable> info(String tabId) {
        if(StringUtils.isEmpty(tabId)) {
            return ReturnResult.illegal("暂无信息");
        }
        SysGenTable sysGenTable = sysGenTableService.getById(tabId);
        return ReturnResult.ok(sysGenTable);
    }

    /**
     * 获取连接
     *
     * @param query 查询
     * @return {@link Connection}
     * @throws SQLException SQLException
     */
    private Connection getConnection(TableQuery query) throws SQLException {
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
