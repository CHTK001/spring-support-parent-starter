package com.chua.starter.gen.support.controller;

import com.chua.common.support.database.entity.ColumnResult;
import com.chua.common.support.database.entity.DatabaseResult;
import com.chua.common.support.database.entity.TableResult;
import com.chua.common.support.lang.formatter.HighlightingFormatter;
import com.chua.common.support.lang.formatter.SqlFormatter;
import com.chua.common.support.session.Session;
import com.chua.common.support.session.query.DeleteQuery;
import com.chua.common.support.session.query.SaveQuery;
import com.chua.common.support.session.query.UpdateQuery;
import com.chua.common.support.session.result.SessionInfo;
import com.chua.common.support.session.result.SessionResult;
import com.chua.common.support.session.result.SessionResultSet;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.query.ExecuteQuery;
import com.chua.starter.gen.support.query.ExplainQuery;
import com.chua.starter.gen.support.query.TableQuery;
import com.chua.starter.gen.support.service.SysGenService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.constant.NameConstant.SYMBOL_EXCEPTION;

/**
 * 生成器控制器
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/session")
public class SessionController {

    @Resource
    private SysGenService sysGenService;
    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @GetMapping("children")
    public ReturnResult<List<TableResult>> children(TableQuery query) {
        SysGen sysGen = sysGenService.getByIdWithType(query.getGenId());
        if(null == sysGen) {
            return ReturnResult.illegal("表不存在");
        }

        try (Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());) {
            return ReturnResult.ok(session.getTables(query.getDatabaseId(), "%"));
        } catch (Exception e) {
            e.printStackTrace();
            return ReturnResult.ok(Collections.emptyList());
        }
    }
    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link TableResult}>
     */
    @GetMapping("keyword")
    public ReturnResult<List<DatabaseResult>> keyword(TableQuery query) {
        SysGen sysGen = sysGenService.getByIdWithType(query.getGenId());
        if(null == sysGen) {
            return ReturnResult.illegal("表不存在");
        }
        String database = sysGen.getGenDatabase();
        List<DatabaseResult> results1 = new LinkedList<>();
        try (Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());) {
            List<DatabaseResult> database1 = session.getDatabase();
            if(CollectionUtils.isNotEmpty(database1)) {
                return ReturnResult.ok(database1);
            }

            List<TableResult> results = session.getTables(database, "%");
            DatabaseResult item = new DatabaseResult();
            item.setName("table");
            item.setLabel("表");
            item.setChildren(results);
            results1.add(item);

            for (TableResult tableResult : results) {
                List<ColumnResult> columns = session.getColumns(sysGen.getGenDatabase(), tableResult.getTableName());
                tableResult.setChildren(columns);
            }

            List<TableResult> viewResult = session.getView(database, "%");
            DatabaseResult item1 = new DatabaseResult();
            item1.setName("view");
            item1.setLabel("视图");
            item1.setChildren(Optional.ofNullable(viewResult).orElse(Collections.emptyList()));
            for (TableResult child : item1.getChildren()) {
                child.setType("VIEW");
            }
            results1.add(item1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(CollectionUtils.isEmpty(results1)) {
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
    @PostMapping("explain")
   public ReturnResult<SessionResult> explain(@RequestBody ExplainQuery explainQuery) {
        if (StringUtils.isEmpty(explainQuery.getGenId())){
            return ReturnResult.error("未配置生成器");
        }

        SysGen sysGen = sysGenService.getByIdWithType(explainQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }
        StringBuilder stringBuffer = new StringBuilder();
        long startTime = System.nanoTime();
        Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());
        SessionResultSet sessionResultSet = null;
        try {
            sessionResultSet = session.executeQuery("explain " + explainQuery.getSql());
            stringBuffer.append("explain\r\n " + HighlightingFormatter.INSTANCE.format(SqlFormatter.format(explainQuery.getSql())));
        } catch (Exception e) {
            String localizedMessage = e.getLocalizedMessage();
            if(null != localizedMessage) {
                int i = localizedMessage.indexOf(SYMBOL_EXCEPTION);
                while (i > -1) {
                    localizedMessage = localizedMessage.substring(SYMBOL_EXCEPTION.length() + i + 1);
                    i = localizedMessage.indexOf(SYMBOL_EXCEPTION);
                }
                stringBuffer.append(localizedMessage);
            }

            return ReturnResult.illegal();
        }
        stringBuffer.append("\r\n").append("耗时: ").append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
        stringBuffer.append(" ms");
        SessionResult sessionResult = new SessionResult();
        sessionResult.setFields(sessionResultSet.toFields());
        sessionResult.setData(sessionResultSet.toData());
        sessionResult.setMessage(stringBuffer.toString());
        return ReturnResult.ok(sessionResult);
    }
    /**
     * 解释
     *
     * @param executeQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @PostMapping("execute")
   public ReturnResult<SessionResult> execute(@RequestBody ExecuteQuery executeQuery) {
        if (StringUtils.isEmpty(executeQuery.getGenId())){
            return ReturnResult.error("未配置生成器");
        }

        SysGen sysGen = sysGenService.getByIdWithType(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        StringBuilder stringBuffer = new StringBuilder();
        long startTime = System.nanoTime();
        Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());
        SessionResultSet sessionResultSet = null;
        try {
            sessionResultSet = session.executeQuery(executeQuery.getSql());
            stringBuffer.append(HighlightingFormatter.INSTANCE.format(SqlFormatter.format(executeQuery.getSql())));
        } catch (Exception e) {
            String localizedMessage = e.getLocalizedMessage();
            if(null != localizedMessage) {
                int i = localizedMessage.indexOf(SYMBOL_EXCEPTION);
                while (i > -1) {
                    localizedMessage = localizedMessage.substring(SYMBOL_EXCEPTION.length() + i + 1);
                    i = localizedMessage.indexOf(SYMBOL_EXCEPTION);
                }
                stringBuffer.append(localizedMessage);
            }

            return ReturnResult.illegal();
        }
        stringBuffer.append("\r\n").append("耗时: ").append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
        stringBuffer.append(" ms");

        SessionResult sessionResult = new SessionResult();
        sessionResult.setFields(sessionResultSet.toFields());
        sessionResult.setData(sessionResultSet.toData());
        sessionResult.setMessage(stringBuffer.toString());
        return ReturnResult.ok(sessionResult);
    }
    /**
     * 基本信息
     *
     * @param executeQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @PostMapping("info")
   public ReturnResult<SessionResult> info(@RequestBody ExecuteQuery executeQuery) {
        if (StringUtils.isEmpty(executeQuery.getGenId())){
            return ReturnResult.error("未配置生成器");
        }

        SysGen sysGen = sysGenService.getByIdWithType(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());
        SessionInfo sessionInfo = null;
        try {
            sessionInfo = session.info();
        } catch (Exception e) {
            e.printStackTrace();
            return ReturnResult.illegal();
        }
        return ReturnResult.ok(sessionInfo.toResult());
    }
    /**
     * 基本信息
     *
     * @param saveQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @PostMapping("save")
   public ReturnResult<SessionResult> save(@RequestBody SaveQuery saveQuery) {
        if (StringUtils.isEmpty(saveQuery.getGenId())){
            return ReturnResult.error("未配置生成器");
        }

        SysGen sysGen = sysGenService.getByIdWithType(saveQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());
        SessionInfo sessionInfo = null;
        try {
            sessionInfo = session.save(saveQuery);
        } catch (Exception e) {
            e.printStackTrace();
            return ReturnResult.illegal();
        }
        return ReturnResult.ok(sessionInfo.toResult());
    }
    /**
     * 基本信息
     *
     * @param deleteQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @PostMapping("delete")
   public ReturnResult<SessionResult> delete(@RequestBody DeleteQuery deleteQuery) {
        if (StringUtils.isEmpty(deleteQuery.getGenId())){
            return ReturnResult.error("未配置生成器");
        }

        SysGen sysGen = sysGenService.getByIdWithType(deleteQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());
        SessionInfo sessionInfo = null;
        try {
            sessionInfo = session.delete(deleteQuery);
        } catch (Exception e) {
            e.printStackTrace();
            return ReturnResult.illegal();
        }
        return ReturnResult.ok(sessionInfo.toResult());
    }
    /**
     * 基本信息
     *
     * @param updateQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @PostMapping("update")
   public ReturnResult<SessionResult> update(@RequestBody UpdateQuery updateQuery) {
        if (StringUtils.isEmpty(updateQuery.getGenId())){
            return ReturnResult.error("未配置生成器");
        }

        SysGen sysGen = sysGenService.getByIdWithType(updateQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());
        SessionInfo sessionInfo = null;
        try {
            sessionInfo = session.update(updateQuery);
        } catch (Exception e) {
            e.printStackTrace();
            return ReturnResult.illegal();
        }
        return ReturnResult.ok(sessionInfo.toResult());
    }
}
