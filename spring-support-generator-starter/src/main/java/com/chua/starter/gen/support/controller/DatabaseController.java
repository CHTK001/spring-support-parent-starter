package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.json.Json;
import com.chua.common.support.utils.FileUtils;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.properties.GenProperties;
import com.chua.starter.gen.support.result.DatabaseType;
import com.chua.starter.gen.support.service.SysGenService;
import com.chua.starter.gen.support.vo.DataSourceResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;

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
     * 支持列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @GetMapping("support")
    public ReturnResult<List<DatabaseType>> support() {
        return ReturnResult.ok(Json.fromJsonToList(ClassLoader.getSystemResourceAsStream("database.conf"), DatabaseType.class));
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @GetMapping("list")
    public ReturnPageResult<SysGen> list(@RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        return PageResultUtils.<SysGen>ok(sysGenService.page(new Page<>(pageNum, pageSize)));
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("save")
    public ReturnResult<SysGen> save(SysGen sysGen,
                                     @RequestParam(value = "genDriverFile", required = false) MultipartFile genDriverFile,
                                     @RequestParam(value = "getDatabaseFile", required = false) MultipartFile getDatabaseFile
    ) {
        if (sysGen.getGenUrl().contains(MYSQL) && !sysGen.getGenUrl().contains("?")) {
            sysGen.setGenUrl(sysGen.getGenUrl() + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true");
        }
        sysGen.setCreateTime(new Date());
        sysGenService.save(sysGen);
        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGen.getGenId() + ""));
        if(null != genDriverFile) {
            ReturnResult driver = MultipartFileUtils.transferTo(genDriverFile, mkdir, "driver");
            if(!driver.isOk()) {
                return driver;
            }

            sysGen.setGenDriverFile(driver.getData().toString());
        }

        if(null != getDatabaseFile) {
            ReturnResult driver = MultipartFileUtils.transferTo(getDatabaseFile, mkdir, "driver");
            if(!driver.isOk()) {
                return driver;
            }
            sysGen.setGenDatabaseFile(driver.getData().toString());
        }

        if("DATABASE-TYPE".equalsIgnoreCase(sysGen.getGenType())) {
            sysGen.setGenType("");
        }
        if(null != genDriverFile || null != getDatabaseFile) {
            sysGenService.updateById(sysGen);
        }
        return ReturnResult.ok(sysGen);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("update")
    public ReturnResult<SysGen> update(SysGen sysGen,
                                       @RequestParam(value = "genDriverFile", required = false) MultipartFile genDriverFile,
                                       @RequestParam(value = "getDatabaseFile", required = false) MultipartFile getDatabaseFile) {
        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGen.getGenId() + ""));
        if(null != genDriverFile) {
            ReturnResult driver = MultipartFileUtils.transferTo(genDriverFile, mkdir, "driver");
            if(!driver.isOk()) {
                return driver;
            }

            sysGen.setGenDriverFile(driver.getData().toString());
        }

        if(null != getDatabaseFile) {
            ReturnResult driver = MultipartFileUtils.transferTo(getDatabaseFile, mkdir, "driver");
            if(!driver.isOk()) {
                return driver;
            }
            sysGen.setGenDatabaseFile(driver.getData().toString());
        }
        if("DATABASE-FILE".equalsIgnoreCase(sysGen.getGenType())) {
            sysGen.setGenUrl("");
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
        sysGenService.removeById(id);
        return ReturnResult.ok(true);
    }

}
