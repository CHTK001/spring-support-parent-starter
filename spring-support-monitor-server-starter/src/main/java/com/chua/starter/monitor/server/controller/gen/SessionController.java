package com.chua.starter.monitor.server.controller.gen;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.constant.FileType;
import com.chua.common.support.datasource.meta.Column;
import com.chua.common.support.datasource.meta.Database;
import com.chua.common.support.datasource.meta.Table;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.formatter.HighlightingFormatter;
import com.chua.common.support.lang.formatter.SqlFormatter;
import com.chua.common.support.media.MediaTypeFactory;
import com.chua.common.support.session.Session;
import com.chua.common.support.session.query.*;
import com.chua.common.support.session.result.SessionInfo;
import com.chua.common.support.session.result.SessionResult;
import com.chua.common.support.session.result.SessionResultSet;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.CollectionUtils;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.IoUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import com.chua.starter.monitor.server.entity.MonitorSysGen;
import com.chua.starter.monitor.server.entity.MonitorSysGenRemark;
import com.chua.starter.monitor.server.properties.GenProperties;
import com.chua.starter.monitor.server.query.TableQuery;
import com.chua.starter.monitor.server.service.MonitorSysGenRemarkService;
import com.chua.starter.monitor.server.service.MonitorSysGenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.chua.common.support.constant.NameConstant.SYMBOL_EXCEPTION;

/**
 * 生成器控制器
 *
 * @author CH
 */
@RestController
@Slf4j
@RequestMapping("v1/session")
public class SessionController {

    @Resource
    private MonitorSysGenService sysGenService;

    @Resource
    private GenProperties genProperties;

    @Resource
    private MonitorSysGenRemarkService sysGenRemarkService;
    /**
     * 表格列表
     *
     * @return {@link ReturnPageResult}<{@link Table}>
     */
    @GetMapping("children")
    public ReturnResult<List<?>> children(TableQuery query) {
        MonitorSysGen sysGen = sysGenService.getById(query.getGenId());
        if(null == sysGen) {
            return ReturnResult.illegal("表不存在");
        }

        try (Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseOptions());) {
            if(query.getFileType() == FileType.DATABASE) {
                return ReturnResult.ok(session.getTables(query.getDatabaseId(), "%", query.createSessionQuery()));
            }

            List<MonitorSysGenRemark> list = sysGenRemarkService.list(Wrappers.<MonitorSysGenRemark>lambdaQuery()
                    .eq(MonitorSysGenRemark::getGenId, query.getGenId())
                    .eq(MonitorSysGenRemark::getRemarkTable, query.getDatabaseId())
            );
            Map<String, MonitorSysGenRemark> tpl = new HashMap<>(list.size());
            for (MonitorSysGenRemark sysGenRemark : list) {
                tpl.put(sysGenRemark.getRemarkColumn(), sysGenRemark);
            }
            List<Column> columns = session.getColumns(query.getDatabase(), query.getDatabaseId());
            for (Column column : CollectionUtils.wrapper(columns)) {
                String columnName = column.getName();
                MonitorSysGenRemark sysGenRemark = tpl.get(columnName);
                if(null != sysGenRemark) {
                    column.setComment(sysGenRemark.getRemarkName());
                }
            }
            return ReturnResult.ok(columns);
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
    @GetMapping("keyword")
    public ReturnResult<List<Database>> keyword(TableQuery query) {
        MonitorSysGen sysGen = sysGenService.getById(query.getGenId());
        if(null == sysGen) {
            return ReturnResult.illegal("表不存在");
        }
        String database = StringUtils.defaultString(query.getDatabaseId(), sysGen.getGenDatabase());
        List<Database> results1 = new LinkedList<>();
        Session session = ServiceProvider.of(Session.class).getKeepExtension(query.getGenId() + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
        try{
            if(!session.isConnect()) {
                ServiceProvider.of(Session.class).closeKeepExtension(query.getGenId() + "");
                return ReturnResult.illegal("当前服务器不可达");
            }
            List<Database> database1 = session.getDatabase(query.getKeyword());
            if(CollectionUtils.isNotEmpty(database1)) {
                return ReturnResult.ok(database1);
            }

            List<Table> results = session.getTables(database, "%",  query.createSessionQuery());
            Database item = new Database();
            item.setName("table");
            item.setLabel("表");
            item.setChildren(results);
            results1.add(item);

            for (Table tableResult : results) {
                List<Column> columns = session.getColumns(sysGen.getGenDatabase(), tableResult.getTableName());
                tableResult.setChildren(columns);
            }

            List<Table> viewResult = session.getView(database, "%");
            Database item1 = new Database();
            item1.setName("view");
            item1.setLabel("视图");
            item1.setChildren(Optional.ofNullable(viewResult).orElse(Collections.emptyList()));
            for (Table child : item1.getChildren()) {
                child.setType("VIEW");
            }
            results1.add(item1);
        } catch (NullPointerException e) {
            throw new RuntimeException("请安装" + sysGen.getGenType() + "依赖");
        } catch (Exception e) {
            log.error("", e);
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

        MonitorSysGen sysGen = sysGenService.getById(explainQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }
        StringBuilder stringBuffer = new StringBuilder();
        long startTime = System.nanoTime();
        SessionResultSet sessionResultSet;
        try (Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseOptions())) {
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
    @PostMapping("execute")
   public ReturnResult<SessionResult> execute(@RequestBody ExecuteQuery executeQuery) {
        if (StringUtils.isEmpty(executeQuery.getGenId())){
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        StringBuilder stringBuffer = new StringBuilder();
        long startTime = System.nanoTime();
        Session session = ServiceProvider.of(Session.class).getKeepExtension(executeQuery.getGenId(), sysGen.getGenType(), sysGen.newDatabaseOptions());
        if(!session.isConnect()) {
            ServiceProvider.of(Session.class).closeKeepExtension(executeQuery.getGenId() );
            return ReturnResult.illegal("当前服务器不可达");
        }
        SessionResultSet sessionResultSet = null;
        Map<String, String> remark = new HashMap<>();
        try {
            sessionResultSet = session.executeQuery(executeQuery.getContent(), executeQuery);
            if(StringUtils.isNotEmpty(executeQuery.getContent())) {
                stringBuffer.append(HighlightingFormatter.INSTANCE.format(SqlFormatter.format(executeQuery.getContent())));
            }
            String currentDatabase = executeQuery.getCurrentDatabase();
            String currentTable = executeQuery.getCurrentTable();
            if(StringUtils.isNotBlank(currentTable)) {
                List<MonitorSysGenRemark> list = sysGenRemarkService.list(Wrappers.<MonitorSysGenRemark>lambdaQuery()
                        .eq(MonitorSysGenRemark::getGenId, executeQuery.getGenId())
                        .eq(MonitorSysGenRemark::getRemarkTable, currentTable)
                        .eq(StringUtils.isNotEmpty(currentDatabase), MonitorSysGenRemark::getRemarkDatabase, currentDatabase)
                );
                for (MonitorSysGenRemark sysGenRemark : list) {
                    remark.put(sysGenRemark.getRemarkColumn(), sysGenRemark.getRemarkName());
                }
            }
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
    @PostMapping("log")
   public ReturnResult<SessionResult> log(@RequestBody ExecuteQuery executeQuery) {
        if (StringUtils.isEmpty(executeQuery.getGenId())){
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
        if(sessionResultSet.hasMessage()) {
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
    @PostMapping("info")
   public ReturnResult<SessionResult> info(@RequestBody ExecuteQuery executeQuery) {
        if (StringUtils.isEmpty(executeQuery.getGenId())){
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getKeepExtension(sysGen.getGenId() + "", sysGen.getGenType(), sysGen.newDatabaseOptions());
        if(!session.isConnect()) {
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
        if(sessionInfo.hasMessage()) {
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
    @PostMapping("saveForm")
   public ReturnResult<SessionResult> save(SaveQuery saveQuery, @RequestParam("file")MultipartFile file) {
        if (StringUtils.isEmpty(saveQuery.getGenId())){
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(saveQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getKeepExtension(saveQuery.getGenId(), sysGen.getGenType(), sysGen.newDatabaseOptions());
        if(!session.isConnect()) {
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
        if(sessionInfo.hasMessage()) {
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
    @PostMapping("delete")
   public ReturnResult<SessionResult> delete(@RequestBody DeleteQuery deleteQuery) {
        if (StringUtils.isEmpty(deleteQuery.getGenId())){
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(deleteQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getKeepExtension(deleteQuery.getGenId(), sysGen.getGenType(), sysGen.newDatabaseOptions());
        if(!session.isConnect()) {
            ServiceProvider.of(Session.class).closeKeepExtension(deleteQuery.getGenId());
            return ReturnResult.illegal("当前服务器不可达");
        }
        SessionInfo sessionInfo = null;
        try {
            sessionInfo = session.delete(deleteQuery);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if(StringUtils.isNotBlank(sessionInfo.getMessage())) {
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
    @PostMapping("update")
   public ReturnResult<SessionResult> update(@RequestBody UpdateQuery updateQuery) {
        return update(updateQuery, null);
    }
    /**
     * 基本信息
     *
     * @param updateQuery 解释查询
     * @return {@link ReturnResult}<{@link SessionResultSet}>
     */
    @PostMapping("updateForm")
   public ReturnResult<SessionResult> update(UpdateQuery updateQuery, @RequestParam("file")MultipartFile file) {
        if (StringUtils.isEmpty(updateQuery.getGenId())){
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
        if(!session.isConnect()) {
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
        if(sessionInfo.hasMessage()) {
            return ReturnResult.illegal(sessionInfo.getMessage());
        }
        return ReturnResult.ok(sessionInfo.toResult());
    }

    /**
     * 预览文档
     * 基本信息
     *
     * @param query 执行查询
     * @return {@link ResponseEntity}<{@link byte[]}>
     */
    @GetMapping("previewDoc")
   public ResponseEntity<byte[]> previewDoc(DocQuery query) {
        if (StringUtils.isEmpty(query.getGenId())){
            return ResponseEntity.notFound().build();
        }

        MonitorSysGen sysGen = sysGenService.getById(query.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ResponseEntity.notFound().build();
        }

        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGen.getGenId() + ""));
        File file = new File(mkdir, "doc");
        file = new File(file, sysGen.getGenId() + ".html");
        FileUtils.mkParentDirs(file);
        byte[] result = null;
        try {
            if(file.exists() && file.length() > 0) {
                try(FileInputStream fis = new FileInputStream(file)) {
                    result = IoUtils.toByteArray(fis);
                }
                if(result.length == 0) {
                    FileUtils.forceDelete(file);
                    return ResponseEntity
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .body("文档不存在".getBytes(StandardCharsets.UTF_8));
                }
            } else {
                    try (Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseOptions())) {
                        result = session.previewDoc(query);
                        if (session.docCache()) {
                            try (OutputStream os = new FileOutputStream(file)) {
                                IoUtils.write(result, os);
                            }
                        }
                } catch (Exception e) {
                    if(null == result) {
                        return ResponseEntity
                                .ok()
                                .contentType(MediaType.TEXT_HTML)
                                .body("文档不存在/无权限访问".getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (Exception e) {
            log.error("", e);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(MediaTypeFactory.getMediaType(query.getDataId()).orElse(com.chua.common.support.media.MediaType.parse("text/html")).toString()))
                .contentLength(result.length)
                .body(result);
    }
    /**
     * 预览文档
     * 基本信息
     *
     * @param query 执行查询
     * @return {@link ResponseEntity}<{@link byte[]}>
     */
    @PostMapping("syncDoc")
   public ReturnResult<Boolean> syncDoc(@RequestBody DocQuery query) {
        if (StringUtils.isEmpty(query.getGenId())){
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(query.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGen.getGenId() + ""));
        File file = new File(mkdir, "doc");
        file = new File(file, sysGen.getGenId() + ".html");
        if(file.exists()) {
            try {
                FileUtils.forceDelete(file);
            } catch (IOException ignored) {
            }
        }
        try (Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseOptions())) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                IoUtils.write(session.previewDoc(query), fileOutputStream);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ReturnResult.ok(true);
    }

    /**
     * 预览文档
     * 基本信息
     *
     * @param query 执行查询
     * @return {@link ResponseEntity}<{@link byte[]}>
     */
    @GetMapping("downloadDoc")
   public ResponseEntity<byte[]> downloadDoc(DocQuery query) {
        if (StringUtils.isEmpty(query.getGenId())){
            return ResponseEntity.notFound().build();
        }

        MonitorSysGen sysGen = sysGenService.getById(query.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ResponseEntity.notFound().build();
        }
        byte[] result;
        try (Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseOptions())) {
            result = null;
            try {
                result = session.previewDoc(query);
            } catch (Exception e) {
                log.error("", e);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity
                .ok()
                .header("Content-Disposition", "attachment;filename="+ sysGen.getGenDatabase() +"." + query.getType().toLowerCase())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(result.length)
                .body(result);
    }


}