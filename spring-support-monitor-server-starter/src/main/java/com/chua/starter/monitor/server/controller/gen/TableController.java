package com.chua.starter.monitor.server.controller.gen;

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
import com.chua.starter.monitor.server.entity.MonitorSysGen;
import com.chua.starter.monitor.server.entity.MonitorSysGenColumn;
import com.chua.starter.monitor.server.entity.MonitorSysGenTable;
import com.chua.starter.monitor.server.properties.GenProperties;
import com.chua.starter.monitor.server.query.Download;
import com.chua.starter.monitor.server.query.TableQuery;
import com.chua.starter.monitor.server.result.TemplateResult;
import com.chua.starter.monitor.server.service.MonitorSysGenColumnService;
import com.chua.starter.monitor.server.service.MonitorSysGenService;
import com.chua.starter.monitor.server.service.MonitorSysGenTableService;
import com.chua.starter.monitor.server.util.DatabaseHandler;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生成器控制器
 *
 * @author CH
 */
@RestController
@Slf4j
@RequestMapping("v1/table")
@Tag(name = "表信息接口")
public class TableController {
    @Resource
    private MonitorSysGenService sysGenService;

    @Resource
    private MonitorSysGenTableService sysGenTableService;

    @Resource
    private MonitorSysGenColumnService sysGenColumnService;

    @Resource
    private GenProperties genProperties;


    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link Table}>
     */
    @GetMapping("table")
    public ReturnPageResult<Table> tableList(TableQuery query) {
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

        List<Table> page = CollectionUtils.page((int) query.getPageNo(), (int) query.getPageSize(), results);
        return ReturnPageResult.ok(
                PageResult.<Table>builder()
                        .total(results.size())
                        .data(page)
                        .pageSize((int) query.getPageSize())
                        .pageNo((int) query.getPageNo()).build()
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
                Column Column = new Column();
                Column.setDatabaseName(sysGen.getGenDatabase());
                Column.setPrecision(it.getColColumnDecimal());
                Column.setLength(it.getColColumnLength());
                Column.setName(it.getColColumnName());
                Column.setComment(it.getColColumnComment());
                Column.setJdbcType(it.getColColumnType());
                Column.setTableName(sysGen.getTabName());
                return Column;
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
    @PostMapping("importColumn")
    @Transactional(rollbackFor = Exception.class)
    public ReturnResult<Boolean> importColumn(@RequestBody TableQuery query) {
        String[] tableName = query.getTableName();
        if (ArrayUtils.isEmpty(tableName)) {
            return ReturnResult.error(null, "表不存在");
        }
        MonitorSysGen sysGen = getSysGen(query);
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
    @GetMapping("page")
    public ReturnPageResult<MonitorSysGenTable> page(TableQuery query) {
        if (query.getGenId() == null) {
            return ReturnPageResult.illegal();
        }

        return PageResultUtils.ok(sysGenTableService.page(
                new Page<>(query.getPageNo(), query.getPageSize()),
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
    @GetMapping("delete")
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
    private Connection getConnection(TableQuery query) throws SQLException {
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
    private Connection getConnection(MonitorSysGen sysGen, TableQuery query) throws SQLException {
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

        MonitorSysGen sysGen = sysGenService.getById(query.getGenId());
        return null != sysGen ? sysGen.getGenDatabase() : null;
    }

    /**
     * 获取数据库
     *
     * @param query 查询
     * @return {@link String}
     */
    private MonitorSysGen getSysGen(TableQuery query) {
        if (null == query.getGenId()) {
            return null;
        }

        return CollectionUtils.findFirst(sysGenService.list(new MPJLambdaWrapper<MonitorSysGen>()
                .selectAll(MonitorSysGen.class)
                .eq(MonitorSysGen::getGenId, query.getGenId()))
        );
    }


}
