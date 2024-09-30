package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.constant.CommonConstant;
import com.chua.common.support.datasource.dialect.Dialect;
import com.chua.common.support.datasource.meta.Column;
import com.chua.common.support.datasource.meta.Table;
import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.date.DateTime;
import com.chua.common.support.lang.date.constant.DateFormatConstant;
import com.chua.common.support.lang.file.adaptor.univocity.parsers.conversions.Validator;
import com.chua.common.support.utils.ArrayUtils;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.server.starter.entity.MonitorSysGen;
import com.chua.report.server.starter.entity.MonitorSysGenColumn;
import com.chua.report.server.starter.entity.MonitorSysGenTable;
import com.chua.report.server.starter.pojo.GenTable;
import com.chua.report.server.starter.pojo.TemplateResult;
import com.chua.report.server.starter.properties.ReportGenProperties;
import com.chua.report.server.starter.query.Download;
import com.chua.report.server.starter.query.NodeChildrenQuery;
import com.chua.report.server.starter.query.TableQuery;
import com.chua.report.server.starter.service.MonitorSysGenColumnService;
import com.chua.report.server.starter.service.MonitorSysGenService;
import com.chua.report.server.starter.service.MonitorSysGenTableService;
import com.chua.report.server.starter.util.DatabaseHandler;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 表信息接口
 *
 * @author CH
 */
@RestController
@Slf4j
@RequestMapping("v1/gen/table")
@Tag(name = "表信息接口")
@RequiredArgsConstructor
public class MonitorGenTableController {
    private final MonitorSysGenService sysGenService;
    private final MonitorSysGenTableService sysGenTableService;
    private final MonitorSysGenColumnService sysGenColumnService;
    private final ReportGenProperties genProperties;


    /**
     * 更新表
     *
     * @param table 表
     * @return {@link ReturnResult}<{@link Boolean}>
     */
    @Operation(summary = "更新表")
    @ApiOperation("更新表")
    @PutMapping("updateTableConstruct")
    public ReturnResult<Boolean> updateTable(@RequestBody GenTable table) {
        if(null == table.getGenId()) {
            return ReturnResult.error("表不存在");
        }

        if(null == table.getChildren()) {
            return ReturnResult.error("表字段不存在");
        }

        MonitorSysGen sysGen = getSysGen(table.getGenId());
        if(null == sysGen) {
            return ReturnResult.illegal("配置不存在");
        }

        table.setColumn(table.getChildren());
        return ReturnResult.ok(sysGenTableService.updateTable(sysGen, table));

    }

    /**
     * 同步表结构
     *
     * @param tabId tabId
     * @return {@link ResponseEntity}<{@link byte[]}>
     * @throws IOException IOException
     */
    @Operation(summary = "同步表结构")
    @ApiOperation("同步表结构")
    @GetMapping("/syncTableConstruct")
    public ReturnResult<Boolean> sync(Integer tabId) throws Exception {
        if(null == tabId) {
            return ReturnResult.illegal("表信息不存在");
        }
        MonitorSysGen sysGen = sysGenService.getOne(new MPJLambdaWrapper<MonitorSysGen>()
                .selectAll(MonitorSysGen.class)
                .select(MonitorSysGenTable::getTabName)
                .select(MonitorSysGenTable::getTabDesc)
                .leftJoin(MonitorSysGenTable.class, MonitorSysGenTable::getGenId, MonitorSysGen::getGenId)
                .eq(MonitorSysGenTable::getTabId, tabId)
        );
        try (DatabaseHandler handler = new DatabaseHandler(sysGen.newDatabaseOptions())) {
            List<MonitorSysGenColumn> sysGenColumns = sysGenColumnService.list(Wrappers.<MonitorSysGenColumn>lambdaQuery().eq(MonitorSysGenColumn::getTabId, tabId));
            Table Table = new Table();
            Table.setDatabase(sysGen.getGenDatabase());
            Table.setTableName(sysGen.getTabName());
            Table.setComment(sysGen.getTabDesc());
            handler.updateTable(Table);
            handler.updateColumn(sysGenColumns.stream().map(it -> {
                Column item = new Column();
                item.setDatabaseName(sysGen.getGenDatabase());
                item.setPrecision(it.getColColumnDecimal());
                item.setLength(it.getColColumnLength());
                item.setNodeId(it.getColColumnName());
                item.setComment(it.getColColumnComment());
                item.setJdbcType(it.getColColumnType());
                item.setNodeName(sysGen.getTabName());
                return item;
            }).collect(Collectors.toSet()));
        } catch (Exception e) {
            if(Validator.hasChinese(e.getMessage())) {
                throw new RuntimeException(e);
            }
            throw new RuntimeException("同步失败");
        }
        return ReturnResult.ok();
    }

    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link Table}>
     */
    @Operation(summary = "同步远程表信息")
    @ApiOperation("同步远程表信息")
    @GetMapping("syncTable")
    public ReturnPageResult<Table> tableList(NodeChildrenQuery query) {
        MonitorSysGen sysGen = getSysGen(query);
        if(null == sysGen) {
            return ReturnPageResult.illegal("表不存在");
        }
        String database = sysGen.getGenDatabase();
        List<Table> results = null;
        try (DatabaseHandler handler = new DatabaseHandler(sysGen.newDatabaseOptions())) {
            results = handler.getTables(database, "%");
        } catch (Exception e) {
            log.error("", e);
        }

        if(CollectionUtils.isEmpty(results)) {
            return ReturnPageResult.ok(Collections.emptyList());
        }

        Map<String, Table> tpl = new HashMap<>(results.size());
        for (Table Table : results) {
            tpl.put(Table.getTableName(), Table);
        }

        List<MonitorSysGenTable> rs = sysGenTableService.list(Wrappers.<MonitorSysGenTable>lambdaQuery()
                .eq(MonitorSysGenTable::getGenId, query.getGenId())
                .in(MonitorSysGenTable::getTabName, tpl.keySet())
        );
        for (MonitorSysGenTable r : rs) {
            tpl.remove(r.getTabName());
        }

        results = new LinkedList<>(tpl.values());

        List<Table> page = CollectionUtils.page(query.getPage(), query.getPageSize(), results);
        return ReturnPageResult.ok(
                PageResult.<Table>builder()
                        .total(results.size())
                        .data(page)
                        .pageSize(query.getPageSize())
                        .pageNo(query.getPage()).build()
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
    @Operation(summary = "批生成代码")
    @ApiOperation("批生成代码")
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
     * 模板列表
     *
     * @param tabId tabId
     * @return {@link ResponseEntity}<{@link byte[]}>
     * @throws IOException IOException
     */
    @Operation(summary = "模板列表")
    @ApiOperation("模板列表")
    @GetMapping("/template")
    public ReturnResult<List<TemplateResult>> template(Integer tabId) throws IOException {
        return ReturnResult.ok(sysGenTableService.template(tabId));
    }


    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link Table}>
     */
    @Operation(summary = "表格列表")
    @PostMapping("importColumn")
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> importColumn(@RequestBody TableQuery query) {
        String[] tableName = query.getTableNames();
        if (ArrayUtils.isEmpty(tableName)) {
            return ReturnResult.error(null, "表不存在");
        }
        MonitorSysGen sysGen = getSysGen(query.getGenId());
        try (DatabaseHandler handler = new DatabaseHandler(sysGen.newDatabaseOptions())) {
            Dialect dialect = handler.guessDialect();
            for (String s : tableName) {
                List<Table> tables = handler.getTables(null, s);
                MonitorSysGenTable sysGenTable = null;
                if(CollectionUtils.isNotEmpty(tables)) {
                    sysGenTable = MonitorSysGenTable.createSysGenTable(query.getGenId(), s, tables.get(0), genProperties);
                    sysGenTable.setGenName(sysGen.getGenName());
                    sysGenTableService.save(sysGenTable);
                }

                List<Column> resultSet = null;
                try {
                    resultSet = handler.getColumns(sysGen.getGenDatabase(),  s);
                } catch (Exception e) {
                    log.error("", e);
                }

                if(CollectionUtils.isNotEmpty(resultSet)) {
                    List<MonitorSysGenColumn> rs = new LinkedList<>();
                    for (Column Column : resultSet) {
                        rs.add(MonitorSysGenColumn.createSysGenColumn(dialect, sysGenTable, s, Column));
                    }

                    sysGenColumnService.saveBatch(rs);
                }
            }


        } catch (Exception e) {
            log.error("", e);
            throw new RuntimeException("导入失败");
        }

        return ReturnResult.ok();
    }

    /**
     * 更新表信息
     *
     * @return {@link ReturnPageResult}<{@link Table}>
     */
    @Operation(summary = "表格列表")
    @GetMapping("page")
    public ReturnPageResult<MonitorSysGenTable> page(TableQuery query) {
        if (query.getGenId() == null) {
            return ReturnPageResult.illegal();
        }

        return PageResultUtils.ok(sysGenTableService.page(
                new Page<>(query.getPage(), query.getPageSize()),
                Wrappers.<MonitorSysGenTable>lambdaQuery()
                        .eq(MonitorSysGenTable::getGenId, query.getGenId())
                        .like(StringUtils.isNotBlank(query.getKeyword()), MonitorSysGenTable::getTabName, query.getKeyword())
        ));
    }

    /**
     * 更新表信息
     *
     * @return {@link ReturnPageResult}<{@link Table}>
     */
    @Operation(summary = "更新表信息")
    @PutMapping("update")
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> updateTable(@RequestBody MonitorSysGenTable sysGenTable) {
        sysGenTableService.updateById(sysGenTable);
        return ReturnResult.ok();
    }

    /**
     * 删除表信息
     *
     * @return {@link ReturnPageResult}<{@link Table}>
     */
    @Operation(summary = "删除表信息")
    @DeleteMapping("delete")
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> deleteTable(String tableId) {
        if(StringUtils.isEmpty(tableId)) {
            return ReturnResult.illegal("请选择删除的表");
        }
        for (String s : tableId.split(CommonConstant.SYMBOL_COMMA)) {
            sysGenTableService.removeById(s);
            sysGenColumnService.remove(Wrappers.<MonitorSysGenColumn>lambdaQuery().eq(MonitorSysGenColumn::getTabId, s));
        }
        return ReturnResult.ok();
    }


    /**
     * 查询表信息
     *
     * @return {@link ReturnPageResult}<{@link Table}>
     */
    @Operation(summary = "查询表信息")
    @GetMapping("info")
    public ReturnResult<MonitorSysGenTable> info(String tabId) {
        if(StringUtils.isEmpty(tabId)) {
            return ReturnResult.illegal("暂无信息");
        }
        MonitorSysGenTable sysGenTable = sysGenTableService.getById(tabId);
        return ReturnResult.ok(sysGenTable);
    }

    /**
     * 获取连接
     *
     * @param query 查询
     * @return {@link Connection}
     * @throws SQLException SQLException
     */
    private Connection getConnection(NodeChildrenQuery query) throws SQLException {
        MonitorSysGen sysGen = sysGenService.getById(query.getGenId());
        return DriverManager.getConnection(sysGen.getGenUrl(), sysGen.getGenUser(), sysGen.getGenPassword());
    }

    /**
     * 获取连接
     *
     * @param query 查询
     * @return {@link Connection}
     * @throws SQLException SQLException
     */
    private Connection getConnection(MonitorSysGen sysGen, NodeChildrenQuery query) throws SQLException {
        return DriverManager.getConnection(sysGen.getGenUrl(), sysGen.getGenUser(), sysGen.getGenPassword());
    }

    /**
     * 获取数据库
     *
     * @param query 查询
     * @return {@link String}
     */
    private String getDatabase(NodeChildrenQuery query) {
        if (null == query.getGenId()) {
            return null;
        }

        MonitorSysGen sysGen = sysGenService.getById(query.getGenId());
        return null != sysGen ? sysGen.getGenDatabase() : null;
    }

    /**
     * 获取数据库
     *
     * @param query 查询
     * @return {@link String}
     */
    private MonitorSysGen getSysGen(NodeChildrenQuery query) {
        if (null == query.getGenId()) {
            return null;
        }

        return getSysGen(query.getGenId());
    }
    /**
     * 获取数据库
     *
     * @param genId 查询
     * @return {@link String}
     */
    private MonitorSysGen getSysGen(Object genId) {
        if (null == genId) {
            return null;
        }

        return CollectionUtils.findFirst(sysGenService.list(new MPJLambdaWrapper<MonitorSysGen>()
                .selectAll(MonitorSysGen.class)
                .eq(MonitorSysGen::getGenId, genId))
        );
    }


}
