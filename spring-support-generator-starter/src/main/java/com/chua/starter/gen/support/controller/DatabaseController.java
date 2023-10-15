package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.backup.Backup;
import com.chua.common.support.database.sqldialect.Dialect;
import com.chua.common.support.session.Session;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.entity.SysGenConfig;
import com.chua.starter.gen.support.properties.GenProperties;
import com.chua.starter.gen.support.query.DeleteFileQuery;
import com.chua.starter.gen.support.service.SysGenService;
import com.chua.starter.gen.support.vo.DataSourceResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 生成器控制器
 *
 * @author CH
 */
@RestController
@SuppressWarnings("ALL")
@RequestMapping("v1/db")
public class DatabaseController {

    @Resource
    private SysGenService sysGenService;

    @Resource
    private GenProperties genProperties;
    @Resource
    private ApplicationContext applicationContext;
    private static final String MYSQL = "mysql";
    /**
     * 删除文件
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("deleteFile")
    public ReturnResult<Boolean> deleteFile(@RequestBody DeleteFileQuery query) {
        SysGen sysGen = sysGenService.getById(query.getGenId());
        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), query.getGenId() + ""));
        try {
            FileUtils.forceDelete(new File(sysGen.getGenDatabaseFile()));
        } catch (IOException e) {
            e.printStackTrace();
            return ReturnResult.illegal("卸载失败");
        }
        sysGen.setGenDatabaseFile("");
        sysGenService.updateById(sysGen);
        return ReturnResult.ok();
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @GetMapping("list")
    public ReturnPageResult<SysGen> list(@RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        String username = RequestUtils.getUsername();
        Page<SysGen> genType = sysGenService.page(
                new Page<>(pageNum, pageSize),
                new MPJLambdaWrapper<SysGen>()
                        .selectAll(SysGenConfig.class)
                        .selectAll(SysGen.class)
                        .selectAs(SysGenConfig::getDbcType, "genType")
                        .selectAs(SysGenConfig::getDbcName, "dbcName")
                        .eq(SysGen::getCreateBy, username)
                        .innerJoin(SysGenConfig.class, SysGenConfig::getDbcId, SysGen::getDbcId)
        );
        Map<String, Class<Backup>> stringClassMap = ServiceProvider.of(Backup.class).listType();
        for (SysGen record : genType.getRecords()) {
            record.setGenPassword("");
            record.setBackup(stringClassMap.containsKey(record.getDbcName()));
        }
        return PageResultUtils.<SysGen>ok(genType);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("save")
    public ReturnResult<SysGen> save(@RequestBody SysGen sysGen) {
        if (StringUtils.isNotEmpty(sysGen.getGenUrl()) && sysGen.getGenUrl().contains(MYSQL) && !sysGen.getGenUrl().contains("?")) {
            sysGen.setGenUrl(sysGen.getGenUrl() + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true");
        }

        sysGen.setCreateTime(new Date());
        sysGen.setCreateBy(RequestUtils.getUsername());
        Dialect dialect = Dialect.createDriver(sysGen.getGenDriver());
        if(null != dialect) {
            sysGen.setGenUrl(dialect.getUrl(sysGen.newDatabaseOptions()));
        }
        sysGenService.save(sysGen);
        return ReturnResult.ok(sysGen);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("update")
    public ReturnResult<SysGen> update(@RequestBody SysGen sysGen) {
        SysGen sysGen1 = sysGenService.getByIdWithType(sysGen.getGenId());
        if(null == sysGen1) {
            return ReturnResult.illegal("数据不存在");
        }
        Dialect dialect = Dialect.createDriver(sysGen.getGenDriver());
        if(null != dialect) {
            sysGen.setGenUrl(dialect.getUrl(sysGen.newDatabaseOptions()));
        }
        if(StringUtils.isEmpty(sysGen.getGenPassword())){
            sysGen.setGenPassword(null);
        }
        sysGen.setCreateBy(RequestUtils.getUsername());
        ServiceProvider.of(Session.class).closeKeepExtension(sysGen.getGenId() + "");

        sysGenService.updateById(sysGen);
        return ReturnResult.ok(sysGen);
    }
    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("uploadDriver")
    public ReturnResult<SysGen> uploadDrvier(SysGen sysGen, @RequestParam(value = "getDatabaseFile", required = false) MultipartFile getDatabaseFile) {
        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGen.getGenId() + ""));

        if(null != getDatabaseFile) {
            ReturnResult driver = MultipartFileUtils.transferTo(getDatabaseFile, mkdir, "database", true);
            if(!driver.isOk()) {
                return driver;
            }
            sysGen.setGenDatabaseFile(driver.getData().toString());
            Dialect dialect = Dialect.createDriver(sysGen.getGenDriver());
            if(null != dialect) {
                sysGen.setGenUrl(dialect.getUrl(sysGen.newDatabaseOptions()));
            }
        }

        sysGenService.updateById(sysGen);
        return ReturnResult.ok(sysGen);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @GetMapping("delete")
    public ReturnResult<Boolean> delete(String id) {
        SysGen sysGen = sysGenService.getByIdWithType(id);
        if(null == sysGen) {
            return ReturnResult.illegal("数据信息不存在");
        }
        sysGenService.removeById(id);
        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), id));
        try {
            FileUtils.forceDelete(mkdir);
        } catch (IOException e) {
        }
        ServiceProvider.of(Session.class).closeKeepExtension(sysGen.getGenId() + "");
        return ReturnResult.ok(true);
    }

}
