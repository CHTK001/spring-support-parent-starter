package com.chua.starter.gen.support.controller;

import com.chua.common.support.database.entity.ColumnResult;
import com.chua.common.support.database.entity.DatabaseResult;
import com.chua.common.support.database.entity.TableResult;
import com.chua.common.support.lang.formatter.HighlightingFormatter;
import com.chua.common.support.lang.formatter.SqlFormatter;
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
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.properties.GenProperties;
import com.chua.starter.gen.support.query.TableQuery;
import com.chua.starter.gen.support.service.SysGenService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
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

    @Resource
    private GenProperties genProperties;
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
            return ReturnResult.ok(session.getTables(query.getDatabaseId(), "%", query.createSessionQuery()));
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
        String database = StringUtils.defaultString(query.getDatabaseId(), sysGen.getGenDatabase());
        List<DatabaseResult> results1 = new LinkedList<>();
        Session session = ServiceProvider.of(Session.class).getKeepExtension(query.getGenId() + "", sysGen.getGenType(), sysGen.newDatabaseConfig());
        try{
            List<DatabaseResult> database1 = session.getDatabase(query.getKeyword());
            if(CollectionUtils.isNotEmpty(database1)) {
                return ReturnResult.ok(database1);
            }

            List<TableResult> results = session.getTables(database, "%",  query.createSessionQuery());
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
            sessionResultSet = session.executeQuery("explain " + explainQuery.getContent(), explainQuery);
            stringBuffer.append("explain\r\n " + HighlightingFormatter.INSTANCE.format(SqlFormatter.format(explainQuery.getContent())));
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

        SysGen sysGen = sysGenService.getByIdWithType(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        StringBuilder stringBuffer = new StringBuilder();
        long startTime = System.nanoTime();
        Session session = ServiceProvider.of(Session.class).getKeepExtension(executeQuery.getGenId(), sysGen.getGenType(), sysGen.newDatabaseConfig());
        SessionResultSet sessionResultSet = null;
        try {
            sessionResultSet = session.executeQuery(executeQuery.getContent(), executeQuery);
            if(StringUtils.isNotEmpty(executeQuery.getContent())) {
                stringBuffer.append(HighlightingFormatter.INSTANCE.format(SqlFormatter.format(executeQuery.getContent())));
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

            return ReturnResult.illegal();
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

        SysGen sysGen = sysGenService.getByIdWithType(executeQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());
        SessionResultSet sessionResultSet = null;
        try {
            sessionResultSet = session.log(executeQuery);
        } catch (Exception e) {
            e.printStackTrace();
            return ReturnResult.illegal();
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

        SysGen sysGen = sysGenService.getByIdWithType(saveQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getKeepExtension(saveQuery.getGenId(), sysGen.getGenType(), sysGen.newDatabaseConfig());
        SessionInfo sessionInfo = null;
        File file1 = null;
        try {
            file1 = MultipartFileUtils.toFile(file);
        } catch (Exception ignored) {
        }
        try {
            sessionInfo = session.save(saveQuery, file1);
        } catch (Exception e) {
            e.printStackTrace();
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

        SysGen sysGen = sysGenService.getByIdWithType(deleteQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        Session session = ServiceProvider.of(Session.class).getKeepExtension(deleteQuery.getGenId(), sysGen.getGenType(), sysGen.newDatabaseConfig());
        SessionInfo sessionInfo = null;
        try {
            sessionInfo = session.delete(deleteQuery);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

        SysGen sysGen = sysGenService.getByIdWithType(updateQuery.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }
        File file1 = null;
        try {
            file1 = MultipartFileUtils.toFile(file);
        } catch (Exception ignored) {
        }
        Session session = ServiceProvider.of(Session.class).getKeepExtension(updateQuery.getGenId(), sysGen.getGenType(), sysGen.newDatabaseConfig());
        SessionInfo sessionInfo = null;
        try {
            sessionInfo = session.update(updateQuery, file1);
        } catch (Exception e) {
            e.printStackTrace();
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

        SysGen sysGen = sysGenService.getByIdWithType(query.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ResponseEntity.notFound().build();
        }

        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGen.getGenId() + ""));
        File file = new File(mkdir, "doc");
        file = new File(file, sysGen.getGenId() + ".html");
        FileUtils.mkParentDirs(file);
        byte[] result = null;
        try {
            if(file.exists()) {
                try(FileInputStream fis = new FileInputStream(file)) {
                    result = IoUtils.toByteArray(fis);
                }
            } else {
                try {
                    Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());
                    result = session.previewDoc(query);
                    if(session.docCache()) {
                        try(OutputStream os = new FileOutputStream(file)) {
                            IoUtils.write(result, os);
                        }
                    }
                } catch (Exception e) {
                    if(null == result) {
                        return ResponseEntity
                                .ok()
                                .contentType(MediaType.TEXT_HTML)
                                .body("文档不存在".getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity
                .ok()
                .contentType(MediaType.TEXT_HTML)
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

        SysGen sysGen = sysGenService.getByIdWithType(query.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGen.getGenId() + ""));
        File file = new File(mkdir, "doc");
        file = new File(file, sysGen.getGenId() + ".html");
        try {
            if(file.exists()) {
                try {
                    FileUtils.forceDelete(file);
                } catch (IOException ignored) {
                }
            }
            Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                IoUtils.write(session.previewDoc(query), fileOutputStream);
            }
            return ReturnResult.ok(true);
        } catch (IOException e) {
            e.printStackTrace();
            return ReturnResult.illegal();
        }
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

        SysGen sysGen = sysGenService.getByIdWithType(query.getGenId());
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ResponseEntity.notFound().build();
        }
        Session session = ServiceProvider.of(Session.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseConfig());
        byte[] result = null;
        try {
            result = session.previewDoc(query);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity
                .ok()
                .header("Content-Disposition", "attachment;filename="+ sysGen.getGenDatabase() +"." + query.getType().toLowerCase())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(result.length)
                .body(result);
    }


}
