package com.chua.report.server.starter.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.annotations.Ignore;
import com.chua.common.support.backup.Backup;
import com.chua.common.support.datasource.dialect.Dialect;
import com.chua.common.support.datasource.dialect.DialectFactory;
import com.chua.common.support.doc.Document;
import com.chua.common.support.doc.query.DocQuery;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.objects.ConfigureObjectContext;
import com.chua.common.support.oss.result.GetObjectResult;
import com.chua.common.support.session.Session;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.*;
import com.chua.digest.support.Sm2Codec;
import com.chua.report.server.starter.entity.MonitorSysGen;
import com.chua.report.server.starter.properties.ReportGenProperties;
import com.chua.report.server.starter.query.DeleteFileQuery;
import com.chua.report.server.starter.service.MonitorSysGenService;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据库接口
 *
 * @author CH
 */
@RestController
@SuppressWarnings("ALL")
@Tag(name = "数据库接口")
@Slf4j
@RequiredArgsConstructor
@RequestMapping("v1/gen/db")
public class MonitorGenDatabaseController {

    private final MonitorSysGenService sysGenService;

    private final ConfigureObjectContext configureObjectContext;

    private final ReportGenProperties genProperties;
    private final ApplicationContext applicationContext;
    private static final String MYSQL = "mysql";
    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @ApiOperation(value = "安装文件")
    @PostMapping("install")
    public ReturnResult<MonitorSysGen> install(MonitorSysGen sysGen, @RequestParam(value = "type") String type, @RequestParam(value = "file", required = false) MultipartFile getDatabaseFile) {
        MonitorSysGen newSysGen = sysGenService.getById(sysGen.getGenId());
        File mkdir = genProperties.getTempPathForTemplate(sysGen, type);
        if(null != getDatabaseFile) {
            ReturnResult driver = MultipartFileUtils.transferTo(getDatabaseFile, mkdir, true);
            if(!driver.isOk()) {
                return driver;
            }
            genProperties.register(newSysGen, type, driver.getData().toString());
        }
        if("FILE".equalsIgnoreCase(newSysGen.getGenJdbcCustomType())) {
            ServiceProvider.of(Session.class).closeKeepExtension(newSysGen.getGenId() + "");
        }

        sysGenService.updateById(newSysGen);
        return ReturnResult.ok(newSysGen);
    }
    /**
     * 卸载文件
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @ApiOperation(value = "卸载文件")
    @PostMapping("uninstall")
    public ReturnResult<Boolean> uninstall(@RequestBody DeleteFileQuery query) {
        MonitorSysGen sysGen = sysGenService.getById(query.getGenId());
        File mkdir = genProperties.getTempPathForTemplate(sysGen, query.getType());
        try {
            FileUtils.forceDelete(mkdir);
        } catch (IOException e) {
            log.error("", e);
            return ReturnResult.illegal("卸载失败");
        }
        genProperties.refresh(sysGen, query.getType());
        sysGenService.updateById(sysGen);
        return ReturnResult.ok();
    }
    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @ApiOperation(value = "列表")
    @GetMapping("list")
    public ReturnPageResult<MonitorSysGen> page(@RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                         @RequestParam(value = "databaseType", required = false) String databaseType
                                                ) {

        String username = RequestUtils.getUsername();
        Page<MonitorSysGen> genType = sysGenService.page(
                new Page<>(pageNum, pageSize),
                new MPJLambdaWrapper<MonitorSysGen>()
                        .selectAll(MonitorSysGen.class)
                        .eq(null != databaseType, MonitorSysGen::getGenType, databaseType)
                        .orderByDesc(MonitorSysGen::getGenBackupStatus)
        );
        for (MonitorSysGen record : genType.getRecords()) {
            String genDriver = record.getGenDriver();
            Dialect dialect = DialectFactory.createDriver(genDriver);
            record.setSupportBackup(ServiceProvider.of(Backup.class).isSupport(dialect.protocol().toUpperCase()));
            record.setSupportDocument(ServiceProvider.of(Document.class).isSupport(record.getGenType()));
            record.setSupportDriver(ClassUtils.isPresent(genDriver));
            record.setGenBackupStatus(null == record.getGenBackupStatus() ? 0 : record.getGenBackupStatus());

            if(StringUtils.isNotBlank(record.getGenDatabaseFile())) {
                record.setGenDatabaseFileName(FileUtils.getName(record.getGenDatabaseFile()));
            }
            record.setIsFileDriver(dialect.isFileDriver());
        }
        return PageResultUtils.<MonitorSysGen>ok(genType);
    }
    /**
     * 列表¬
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @ApiOperation(value = "保存")
    @PostMapping("save")
    public ReturnResult<MonitorSysGen> save(@RequestBody MonitorSysGen sysGen) {
        String genDriver = sysGen.getGenDriver();
        Dialect driver = DialectFactory.createDriver(genDriver);
        sysGen.setGenJdbcType (driver.protocol().toUpperCase());
        sysGenService.save(sysGen);
        return ReturnResult.ok(sysGen);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @ApiOperation(value = "更新")
    @PutMapping("update")
    public ReturnResult<MonitorSysGen> update(@RequestBody MonitorSysGen sysGen) {
        MonitorSysGen sysGen1 = sysGenService.getById(sysGen.getGenId());
        if(null == sysGen1) {
            return ReturnResult.illegal("数据不存在");
        }
        Dialect driver = DialectFactory.createDriver(sysGen1.getGenDriver());
        sysGen.setGenJdbcType (driver.protocol().toUpperCase());
        ServiceProvider.of(Session.class).closeKeepExtension(sysGen.getGenId() + "");

        sysGenService.updateFor(sysGen, sysGen1);
        return ReturnResult.ok(sysGen);
    }


    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @ApiOperation(value = "删除文件")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(String id) {
        MonitorSysGen sysGen = sysGenService.getById(id);
        if(null == sysGen) {
            return ReturnResult.illegal("数据信息不存在");
        }
        return ReturnResult.ok(sysGenService.deleteFor(id, sysGen));
    }
    /**
     * 预览文档
     * 基本信息
     *
     * @param query 执行查询
     * @return {@link ResponseEntity}<{@link byte[]}>
     */
    @Operation(summary = "预览文档")
    @Ignore
    @GetMapping("previewDoc")
    public ResponseEntity<byte[]> previewDoc(String genId) {
        if (StringUtils.isEmpty(genId)) {
            return ResponseEntity.notFound().build();
        }

        MonitorSysGen sysGen = sysGenService.getById(genId);
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ResponseEntity.notFound().build();
        }

        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGen.getGenId() + ""));
        File file = genProperties.getTempPathForDoc(sysGen);
        file = new File(file, sysGen.getGenId() + ".html");
        FileUtils.mkParentDirs(file);
        GetObjectResult result = null;
        try {
            if (file.exists() && file.length() > 0) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] result1 = IoUtils.toByteArray(fis);
                    if (result1.length == 0) {
                        FileUtils.forceDelete(file);
                        return ResponseEntity
                                .ok()
                                .contentType(MediaType.TEXT_HTML)
                                .body("文档不存在".getBytes(StandardCharsets.UTF_8));
                    }
                    return ResponseEntity
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .contentLength(result1.length)
                            .body(result1);
                }
            }
            try (Document document = ServiceProvider.of(Document.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseOptions())) {
                result = document.create(DocQuery.builder().build());
                try (OutputStream os = new FileOutputStream(file)) {
                    IoUtils.write(result.getInputStream(), os);
                }
            } catch (Exception e) {
                if (null == result) {
                    return ResponseEntity
                            .ok()
                            .contentType(MediaType.TEXT_HTML)
                            .body("文档不存在/无权限访问".getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            log.error("", e);
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity
                    .ok()
                    .contentType(MediaType.valueOf((null == result || null == result.getMediaType()) ? MediaType.TEXT_HTML.toString() : result.getMediaType().toString()))
                    .contentLength((null == result || null == result.getInputStream()) ? 12 : result.getInputStream().available())
                    .body((null == result || null == result.getInputStream()) ? "暂无文档".getBytes(StandardCharsets.UTF_8): IoUtils.toByteArray(result.getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 预览文档
     * 基本信息
     *
     * @param query 执行查询
     * @return {@link ResponseEntity}<{@link byte[]}>
     */
    @Operation(summary = "同步文档")
    @GetMapping("syncDoc")
    public ReturnResult<Boolean> syncDoc(String genId) {
        if (StringUtils.isEmpty(genId)) {
            return ReturnResult.error("未配置生成器");
        }

        MonitorSysGen sysGen = sysGenService.getById(genId);
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ReturnResult.error("未配置生成器类型");
        }

        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGen.getGenId() + ""));
        File file = genProperties.getTempPathForDoc(sysGen);
        file = new File(file, sysGen.getGenId() + ".html");
        if (file.exists()) {
            try {
                FileUtils.forceDelete(file);
            } catch (IOException ignored) {
            }
        }
        try (Document document = ServiceProvider.of(Document.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseOptions())) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                IoUtils.write(document.create(DocQuery.builder().build()).getInputStream(), fileOutputStream);
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
    @Operation(summary = "下载文档")
    @GetMapping("downloadDoc")
    public ResponseEntity<byte[]> downloadDoc(String genId, String type) {
        if (StringUtils.isEmpty(genId)) {
            return ResponseEntity.notFound().build();
        }

        MonitorSysGen sysGen = sysGenService.getById(genId);
        if (StringUtils.isEmpty(sysGen.getGenType())) {
            return ResponseEntity.notFound().build();
        }
        GetObjectResult result;
        try (Document document = ServiceProvider.of(Document.class).getNewExtension(sysGen.getGenType(), sysGen.newDatabaseOptions())) {
            result = null;
            try {
                result = document.create(DocQuery.builder().type(type).build());
            } catch (Exception e) {
                log.error("", e);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity
                .ok()
                .header("Content-Disposition", "attachment;filename=" + sysGen.getGenDatabase() + "." + StringUtils.defaultString(type, result.getMediaType().subtype()).toLowerCase())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(result.length())
                .body(IoUtils.toByteArrayQuietly(result.getInputStream()));
    }

}
