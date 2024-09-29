package com.chua.report.server.starter.controller;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.constant.FileType;
import com.chua.common.support.datasource.dialect.Dialect;
import com.chua.common.support.datasource.dialect.DialectFactory;
import com.chua.common.support.datasource.meta.Column;
import com.chua.common.support.datasource.meta.Database;
import com.chua.common.support.datasource.meta.Table;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.formatter.HighlightingFormatter;
import com.chua.common.support.lang.formatter.SqlFormatter;
import com.chua.common.support.session.Session;
import com.chua.common.support.session.TableSession;
import com.chua.common.support.session.pojo.SessionModule;
import com.chua.common.support.session.query.*;
import com.chua.common.support.session.result.SessionInfo;
import com.chua.common.support.session.result.SessionResult;
import com.chua.common.support.session.result.SessionResultSet;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.report.server.starter.entity.MonitorSysGen;
import com.chua.report.server.starter.entity.MonitorSysGenRemark;
import com.chua.report.server.starter.pojo.NodeData;
import com.chua.report.server.starter.pojo.TableHit;
import com.chua.report.server.starter.properties.ReportGenProperties;
import com.chua.report.server.starter.query.NodeChildrenQuery;
import com.chua.report.server.starter.service.MonitorSysGenRemarkService;
import com.chua.report.server.starter.service.MonitorSysGenService;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.constant.NameConstant.SYMBOL_EXCEPTION;

/**
 * 会话控制器
 *
 * @author CH
 */
@RestController
@Slf4j
@RequestMapping("v1/gen/session")
@RequiredArgsConstructor
public class MonitorGenSessionController {

    final MonitorSysGenService sysGenService;
    final ReportGenProperties genProperties;
    final MonitorSysGenRemarkService sysGenRemarkService;
    /**
     * 查询表信息
     *
     * @return {@link ReturnResult}<{@link Table}>
     */
    @Operation(summary = "查询表结构")
    @GetMapping("getTableConstruct")
    public ReturnResult<Table> getTableConstruct(String genId, String tableName) {
        MonitorSysGen sysGen = sysGenService.getById(genId);
        if (null == sysGen) {
            return ReturnResult.illegal("表不存在");
        }
        try {
            Session session = ServiceProvider.of(Session.class).getKeepExtension(genId + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
            if (!session.isConnect()) {
                ServiceProvider.of(Session.class).closeKeepExtension(String.valueOf(sysGen.getGenId()));
                session = ServiceProvider.of(Session.class).getKeepExtension(genId + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
            }
            if(session instanceof TableSession tableSession) {
                return ReturnResult.ok(tableSession.getTableConstruct(DialectFactory.createDriver(sysGen.getGenDriver()).getDatabaseName(sysGen.getGenUrl()), tableName));
            }
            return ReturnResult.illegal("暂不支持获取");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Operation(summary = "删除表结构")
    @GetMapping("dropTable")
    public ReturnResult<Boolean> dropTable(String genId, String tableName) {
        MonitorSysGen sysGen = sysGenService.getById(genId);
        if (null == sysGen) {
            return ReturnResult.illegal("表不存在");
        }
        try {
            Session session = ServiceProvider.of(Session.class).getKeepExtension(genId + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
            if (!session.isConnect()) {
                ServiceProvider.of(Session.class).closeKeepExtension(String.valueOf(sysGen.getGenId()));
                session = ServiceProvider.of(Session.class).getKeepExtension(genId + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
            }
            if(session instanceof TableSession tableSession) {
                return ReturnResult.ok(
                        tableSession.dropTable(
                                DialectFactory.createDriver(sysGen.getGenDriver()).getDatabaseName(sysGen.getGenUrl()), tableName));
            }
            return ReturnResult.illegal("暂不支持获取");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Operation(summary = "复制表结构")
    @GetMapping("copyTableConstruct")
    public ReturnResult<Boolean> copyTableConstruct(String genId, String tableName) {
        MonitorSysGen sysGen = sysGenService.getById(genId);
        if (null == sysGen) {
            return ReturnResult.illegal("表不存在");
        }
        try {
            Session session = ServiceProvider.of(Session.class).getKeepExtension(genId + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
            if (!session.isConnect()) {
                ServiceProvider.of(Session.class).closeKeepExtension(String.valueOf(sysGen.getGenId()));
                session = ServiceProvider.of(Session.class).getKeepExtension(genId + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
            }
            if(session instanceof TableSession tableSession) {
                return ReturnResult.ok(
                        tableSession.copyTableConstruct(
                                DialectFactory.createDriver(sysGen.getGenDriver()).getDatabaseName(sysGen.getGenUrl()), tableName,
                                tableName + "_copy" + DateTime.now().toString("yyyyMMddHHmmss")).isSuccess());
            }
            return ReturnResult.illegal("暂不支持获取");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 查询表信息
     *
     * @return {@link ReturnResult}<{@link Table}>
     */
    @Operation(summary = "查询表信息")
    @GetMapping("hits")
    public ReturnResult<List<TableHit>> hits(String genId) {
        MonitorSysGen sysGen = sysGenService.getById(genId);
        if (null == sysGen) {
            return ReturnResult.illegal("表不存在");
        }
        try {
            Session session = ServiceProvider.of(Session.class).getKeepExtension(genId + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
            if (!session.isConnect()) {
                ServiceProvider.of(Session.class).closeKeepExtension(String.valueOf(sysGen.getGenId()));
                session = ServiceProvider.of(Session.class).getKeepExtension(genId + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
            }
            List<Table> tables = session.getTables(null, "%", new SessionQuery());
            Session finalSession = session;
            return ReturnResult.ok(tables.stream().map(it -> {
                TableHit tableHit = new TableHit();
                tableHit.setName(it.getName());
                try {
                    List<Column> columns = finalSession.getColumns(null, it.getTableName());
                    tableHit.setFields(columns.stream().map(Column::getName).toArray(String[]::new));
                } catch (Exception ignored) {
                }
                return tableHit;
            }).toList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link Table}>
     */
    @Operation(summary = "查询子节点")
    @GetMapping("children")
    public ReturnResult<List<NodeData>> children(NodeChildrenQuery query) {
        MonitorSysGen sysGen = sysGenService.getById(query.getGenId());
        if (null == sysGen) {
            return ReturnResult.illegal("表不存在");
        }

        try {
            Session session = ServiceProvider.of(Session.class).getKeepExtension(query.getGenId() + "",sysGen.getGenType(), sysGen.newDatabaseOptions());
            if (!session.isConnect()) {
                ServiceProvider.of(Session.class).closeKeepExtension(String.valueOf(sysGen.getGenId()));
                return ReturnResult.illegal("当前服务器不可达");
            }
            if("TABLE".equalsIgnoreCase(query.getNodeType())) {
                List<Table> tables = session.getTables(StringUtils.defaultString(query.getNodeName(), DialectFactory.createDriver(sysGen.getGenDriver()).getDatabaseName(sysGen.getGenUrl())), "%", new SessionQuery());
                return ReturnResult.ok(tables.stream().map(it -> {
                    NodeData nodeData = new NodeData();
                    nodeData.setNodePid(query.getNodeId());
                    nodeData.setNodeId(it.getName());
                    nodeData.setNodeName(it.getTableName());
                    nodeData.setNodeComment(it.getComment());
                    nodeData.setNodeType("TABLE");
                    return nodeData;
                }).toList());
            }

            if("VIEW".equalsIgnoreCase(query.getNodeType())) {
                List<Table> tables = session.getView(query.getNodeName(), "%");
                return ReturnResult.ok(tables.stream().map(it -> {
                    NodeData nodeData = new NodeData();
                    nodeData.setNodePid(query.getNodeId());
                    nodeData.setNodeId(it.getName());
                    nodeData.setNodeName(it.getTableName());
                    nodeData.setNodeComment(it.getComment());
                    nodeData.setNodeType("VIEW");
                    return nodeData;
                }).toList());
            }

            if("COLUMN".equalsIgnoreCase(query.getNodeType())) {
                List<Column> columns = session.getColumns(null, query.getNodeName());
                List<MonitorSysGenRemark> list = sysGenRemarkService.list(Wrappers.<MonitorSysGenRemark>lambdaQuery()
                        .eq(MonitorSysGenRemark::getGenId, query.getGenId())
                        .eq(MonitorSysGenRemark::getRemarkTable, query.getNodeName())
                );
                Map<String, MonitorSysGenRemark> tpl = new HashMap<>(list.size());
                for (MonitorSysGenRemark sysGenRemark : list) {
                    tpl.put(sysGenRemark.getRemarkColumn(), sysGenRemark);
                }
                for (Column column : CollectionUtils.wrapper(columns)) {
                    String columnName = column.getName();
                    MonitorSysGenRemark sysGenRemark = tpl.get(columnName);
                    if (null != sysGenRemark) {
                        column.setComment(sysGenRemark.getRemarkName());
                    }
                }
                return ReturnResult.ok(columns.stream().map(it -> {
                    NodeData nodeData = new NodeData();
                    nodeData.setNodePid(query.getNodeId());
                    nodeData.setNodeId(it.getName());
                    nodeData.setNodeName(it.getName());
                    nodeData.setNodeComment(it.getComment());
                    nodeData.setNodeType("COLUMN");
                    nodeData.setNodeLeaf(true);
                    return nodeData;
                }).toList());
            }
            return ReturnResult.ok(Collections.emptyList());
        } catch (Exception e) {
            log.error("", e);
            return ReturnResult.ok(Collections.emptyList());
        }
    }

    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link Table}>
     */
    @Operation(summary = "关键词列表")
    @GetMapping({"keyword", "root"})
    public ReturnResult<List<NodeData>> keyword(Integer genId) {
        MonitorSysGen sysGen = sysGenService.getById(genId);
        if (null == sysGen) {
            return ReturnResult.illegal("表不存在");
        }
        List<NodeData> results1 = new LinkedList<>();
        Session session = ServiceProvider.of(Session.class).getKeepExtension(genId + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
        try {
            if (!session.isConnect()) {
                ServiceProvider.of(Session.class).closeKeepExtension(genId + "");
                session = ServiceProvider.of(Session.class).getKeepExtension(genId + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
            }

            List<Database> database = session.getDatabase("%");
            if(!CollectionUtils.isEmpty(database)) {
                return ReturnResult.ok(database.stream().map(it -> {
                    NodeData nodeData = new NodeData();
                    nodeData.setNodeId(it.getName());
                    nodeData.setNodeName(it.getLabel());
                    nodeData.setNodeType("DATABASE");
                    return nodeData;
                }).toList());
            }
            NodeData item = new NodeData();
            item.setNodeName("表");
            item.setNodeType("TABLE");
            item.setNodeId("0");

            NodeData item1 = new NodeData();
            item1.setNodeName("视图");
            item1.setNodeType("VIEW");
            item1.setNodeId("0");

            results1.add(item);
            results1.add(item1);
        } catch (NullPointerException e) {
            throw new RuntimeException("请安装" + sysGen.getGenType() + "依赖");
        } catch (Exception e) {
            log.error("", e);
        }

        if (CollectionUtils.isEmpty(results1)) {
            return ReturnResult.ok(Collections.emptyList());
        }

        return ReturnResult.ok(results1);
    }

    /**
     * 解释
     *
     * @param explainQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @Operation(summary = "解释表达式")
    @PostMapping("explain")
    public ReturnResult<SessionResult> explain(@RequestBody ExplainQuery explainQuery) {
        if (StringUtils.isEmpty(explainQuery.getGenId())) {
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(explainQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }
        StringBuilder stringBuffer = new StringBuilder();
        long startTime = System.nanoTime();
        SessionResultSet sessionResultSet;
        try {
            Session session = ServiceProvider.of(Session.class).getKeepExtension(String.valueOf(sysGen.getGenId()), sysGen.getGenType(), sysGen.newDatabaseOptions());
            if (!session.isConnect()) {
                ServiceProvider.of(Session.class).closeKeepExtension(String.valueOf(sysGen.getGenId()));
                return ReturnResult.illegal("当前服务器不可达");
            }
            sessionResultSet = null;
            try {
                sessionResultSet = session.executeQuery("explain " + explainQuery.getContent(), explainQuery);
                stringBuffer.append("explain\r\n " + HighlightingFormatter.INSTANCE.format(SqlFormatter.format(explainQuery.getContent())));
            } catch (Exception e) {
                String localizedMessage = e.getLocalizedMessage();
                if (null != localizedMessage) {
                    int i = localizedMessage.indexOf(SYMBOL_EXCEPTION);
                    while (i > -1) {
                        localizedMessage = localizedMessage.substring(SYMBOL_EXCEPTION.length() + i + 1);
                        i = localizedMessage.indexOf(SYMBOL_EXCEPTION);
                    }
                    stringBuffer.append(localizedMessage);
                }

                return ReturnResult.illegal();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        long toMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        stringBuffer.append("\r\n").append("耗时: ").append(toMillis);
        stringBuffer.append(" ms");
        SessionResult sessionResult = new SessionResult();
        sessionResult.setFields(sessionResultSet.toFields());
        sessionResult.setData(sessionResultSet.toData());
        sessionResult.setCost(toMillis);
        sessionResult.setTotal(sessionResultSet.toTotal());
        sessionResult.setMessage(stringBuffer.toString());
        return ReturnResult.ok(sessionResult);
    }

    /**
     * 解释
     *
     * @param executeQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @Operation(summary = "执行表达式")
    @PostMapping("execute")
    public ReturnResult<SessionResult> execute(@RequestBody ExecuteQuery executeQuery) {
        if (StringUtils.isEmpty(executeQuery.getGenId())) {
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        StringBuilder stringBuffer = new StringBuilder();
        long startTime = System.nanoTime();
        Session session = ServiceProvider.of(Session.class).getKeepExtension(executeQuery.getGenId(), sysGen.getGenType(), sysGen.newDatabaseOptions());
        if (!session.isConnect()) {
            ServiceProvider.of(Session.class).closeKeepExtension(executeQuery.getGenId());
            return ReturnResult.illegal("当前服务器不可达");
        }
        SessionResultSet sessionResultSet = null;
        Map<String, String> remark = new HashMap<>(16);
        try {
            sessionResultSet = session.executeQuery(executeQuery.getContent(), executeQuery);
            if (StringUtils.isNotEmpty(executeQuery.getContent())) {
                stringBuffer.append(HighlightingFormatter.INSTANCE.format(SqlFormatter.format(executeQuery.getContent())));
            }
            Statement statement = CCJSqlParserUtil.parse(executeQuery.getContent());
            String currentDatabase = null;
            String currentTable = null;
            if(statement instanceof Select select) {
                currentTable = ((net.sf.jsqlparser.schema.Table)((PlainSelect)select.getSelectBody()).getFromItem()).getName();
            }

            if (StringUtils.isNotBlank(currentTable)) {
                List<MonitorSysGenRemark> list = sysGenRemarkService.list(Wrappers.<MonitorSysGenRemark>lambdaQuery()
                        .eq(MonitorSysGenRemark::getGenId, executeQuery.getGenId())
                        .eq(MonitorSysGenRemark::getRemarkTable, currentTable)
                        .eq(StringUtils.isNotEmpty(currentDatabase), MonitorSysGenRemark::getRemarkDatabase, currentDatabase)
                );
                List<Column> columns = Optional.ofNullable(session.getColumns(null, currentTable)).orElse(Collections.emptyList());
                for (Column column : columns) {
                    remark.put(column.getName(), column.getComment());
                }
                for (MonitorSysGenRemark sysGenRemark : list) {
                    remark.put(sysGenRemark.getRemarkColumn(), sysGenRemark.getRemarkName());
                }
            }
        } catch (Exception e) {
            String localizedMessage = e.getLocalizedMessage();
            if (null != localizedMessage) {
                int i = localizedMessage.indexOf(SYMBOL_EXCEPTION);
                while (i > -1) {
                    localizedMessage = localizedMessage.substring(SYMBOL_EXCEPTION.length() + i + 1);
                    i = localizedMessage.indexOf(SYMBOL_EXCEPTION);
                }
                stringBuffer.append(localizedMessage);
            }

            return ReturnResult.illegal(stringBuffer.toString());
        }
        long toMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        stringBuffer.append("\r\n").append("耗时: ").append(toMillis);
        stringBuffer.append(" ms");

        SessionResult sessionResult = new SessionResult();
        sessionResult.setFields(sessionResultSet.toFields());
        sessionResult.setData(sessionResultSet.toData());
        sessionResult.setMessage(stringBuffer.toString());
        sessionResult.setCost(toMillis);
        sessionResult.setTotal(sessionResultSet.toTotal());
        sessionResult.setRemark(remark);
        return ReturnResult.ok(sessionResult);
    }

    /**
     * 基本信息
     *
     * @param executeQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @Operation(summary = "日志信息")
    @PostMapping("log")
    public ReturnResult<SessionResult> log(@RequestBody ExecuteQuery executeQuery) {
        if (StringUtils.isEmpty(executeQuery.getGenId())) {
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        SessionResultSet sessionResultSet;
        try (Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseOptions())) {
            sessionResultSet = null;
            try {
                sessionResultSet = session.log(executeQuery);
            } catch (Exception e) {
                log.error("", e);
                return ReturnResult.illegal();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (sessionResultSet.hasMessage()) {
            return ReturnResult.illegal(sessionResultSet.getMessage());
        }
        SessionResult sessionResult = new SessionResult();
        sessionResult.setFields(sessionResultSet.toFields());
        sessionResult.setData(sessionResultSet.toData());
        sessionResult.setTotal(sessionResultSet.toTotal());
        return ReturnResult.ok(sessionResult);
    }
    /**
     * 基本信息
     *
     * @param executeQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @Operation(summary = "查询模块")
    @GetMapping("module")
    public ReturnResult<List<SessionModule>> module(ExecuteQuery executeQuery) {
        if (StringUtils.isEmpty(executeQuery.getGenId())) {
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getKeepExtension(sysGen.getGenId() + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
        if (!session.isConnect()) {
            ServiceProvider.of(Session.class).closeKeepExtension(sysGen.getGenId() + "");
            return ReturnResult.illegal("当前服务器不可达");
        }
        try {
            return ReturnResult.ok(session.moduleList());
        } catch (Exception e) {
            log.error("", e);
            return ReturnResult.illegal(e);
        }
    }
    /**
     * 基本信息
     *
     * @param executeQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @Operation(summary = "基础信息")
    @PostMapping("info")
    public ReturnResult<SessionResult> info(@RequestBody ExecuteQuery executeQuery) {
        if (StringUtils.isEmpty(executeQuery.getGenId())) {
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getKeepExtension(sysGen.getGenId() + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
        if (!session.isConnect()) {
            ServiceProvider.of(Session.class).closeKeepExtension(sysGen.getGenId() + "");
            return ReturnResult.illegal("当前服务器不可达");
        }
        SessionInfo sessionInfo = null;
        try {
            sessionInfo = session.info();
        } catch (Exception e) {
            log.error("", e);
            return ReturnResult.illegal(e);
        }
        if (sessionInfo.hasMessage()) {
            return ReturnResult.illegal(sessionInfo.getMessage());
        }
        return ReturnResult.ok(sessionInfo.toResult());
    }

    /**
     * 基本信息
     *
     * @param saveQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @Operation(summary = "保存基本信息")
    @PostMapping("save")
    public ReturnResult<SessionResult> save(@RequestBody SaveQuery saveQuery) {
        return save(saveQuery, null);
    }

    /**
     * 基本信息
     *
     * @param saveQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @Operation(summary = "保存文件")
    @PostMapping("saveForm")
    public ReturnResult<SessionResult> save(SaveQuery saveQuery, @RequestParam("file") MultipartFile file) {
        if (StringUtils.isEmpty(saveQuery.getGenId())) {
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(saveQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getKeepExtension(saveQuery.getGenId(), sysGen.getGenType(), sysGen.newDatabaseOptions());
        if (!session.isConnect()) {
            ServiceProvider.of(Session.class).closeKeepExtension(saveQuery.getGenId());
            return ReturnResult.illegal("当前服务器不可达");
        }
        SessionInfo sessionInfo = null;
        File file1 = null;
        try {
            file1 = MultipartFileUtils.toFile(file);
        } catch (Exception ignored) {
        }
        try {
            sessionInfo = session.save(saveQuery, file1);
        } catch (Exception e) {
            log.error("", e);
            return ReturnResult.illegal(e);
        } finally {
            try {
                FileUtils.forceDelete(file1);
            } catch (IOException ignored) {
            }
        }
        if (sessionInfo.hasMessage()) {
            return ReturnResult.illegal(sessionInfo.getMessage());
        }
        return ReturnResult.ok(sessionInfo.toResult());
    }

    /**
     * 基本信息
     *
     * @param deleteQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @Operation(summary = "删除基本信息")
    @DeleteMapping("delete")
    public ReturnResult<SessionResult> delete(@RequestBody DeleteQuery deleteQuery) {
        if (StringUtils.isEmpty(deleteQuery.getGenId())) {
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(deleteQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getKeepExtension(deleteQuery.getGenId(), sysGen.getGenType(), sysGen.newDatabaseOptions());
        if (!session.isConnect()) {
            ServiceProvider.of(Session.class).closeKeepExtension(deleteQuery.getGenId());
            return ReturnResult.illegal("当前服务器不可达");
        }
        SessionInfo sessionInfo = null;
        try {
            sessionInfo = session.delete(deleteQuery);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (StringUtils.isNotBlank(sessionInfo.getMessage())) {
            return ReturnResult.illegal(sessionInfo.getMessage());
        }
        return ReturnResult.ok(sessionInfo.toResult());
    }

    /**
     * 基本信息
     *
     * @param updateQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @Operation(summary = "更新基本信息")
    @PutMapping("update")
    public ReturnResult<SessionResult> update(@RequestBody UpdateQuery updateQuery) {
        return update(updateQuery, null);
    }

    /**
     * 基本信息
     *
     * @param updateQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @Operation(summary = "更新文件")
    @PutMapping("updateForm")
    public ReturnResult<SessionResult> update(UpdateQuery updateQuery, @RequestParam("file") MultipartFile file) {
        if (StringUtils.isEmpty(updateQuery.getGenId())) {
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(updateQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }
        File file1 = null;
        try {
            file1 = MultipartFileUtils.toFile(file);
        } catch (Exception ignored) {
        }
        Session session = ServiceProvider.of(Session.class).getKeepExtension(updateQuery.getGenId(), sysGen.getGenType(), sysGen.newDatabaseOptions());
        if (!session.isConnect()) {
            ServiceProvider.of(Session.class).closeKeepExtension(updateQuery.getGenId());
            return ReturnResult.illegal("当前服务器不可达");
        }
        SessionInfo sessionInfo = null;
        try {
            sessionInfo = session.update(updateQuery, file1);
        } catch (Exception e) {
            log.error("", e);
            return ReturnResult.illegal();
        } finally {
            try {
                FileUtils.forceDelete(file1);
            } catch (IOException ignored) {
            }
        }
        if (sessionInfo.hasMessage()) {
            return ReturnResult.illegal(sessionInfo.getMessage());
        }
        return ReturnResult.ok(sessionInfo.toResult());
    }



}
