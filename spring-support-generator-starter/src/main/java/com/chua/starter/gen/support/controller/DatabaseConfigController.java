package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.http.HttpClient;
import com.chua.common.support.http.HttpResponse;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.UrlUtils;
import com.chua.starter.common.support.result.ReturnPageResult;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import com.chua.starter.gen.support.entity.SysGenConfig;
import com.chua.starter.gen.support.properties.GenProperties;
import com.chua.starter.gen.support.service.SysGenConfigService;
import com.chua.starter.gen.support.vo.DataSourceResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;

/**
 * 生成器控制器
 *
 * @author CH
 */
@RestController
@SuppressWarnings("ALL")
@RequestMapping("v1/dbc")
public class DatabaseConfigController {

    @Resource
    private SysGenConfigService sysGenConfigService;

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
    public ReturnResult<Boolean> deleteFile(@RequestBody SysGenConfig query) {
        SysGenConfig genConfig = sysGenConfigService.getById(query.getDbcId());
        try {
            FileUtils.forceDelete(new File(genConfig.getDbcDriverUrl()));
        } catch (IOException e) {
            e.printStackTrace();
            return ReturnResult.illegal("卸载失败");
        }
        genConfig.setDbcDriverUrl("");
        sysGenConfigService.updateById(genConfig);
        return ReturnResult.ok();
    }
    /**
     * 支持列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @GetMapping("support")
    public ReturnResult<List<SysGenConfig>> support() {
        return ReturnResult.ok(sysGenConfigService.list(Wrappers.<SysGenConfig>lambdaQuery().eq(SysGenConfig::getDbcStatus, 1)));
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @GetMapping("list")
    public ReturnPageResult<SysGenConfig> list(@RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        return PageResultUtils.<SysGenConfig>ok(sysGenConfigService.page(new Page<>(pageNum, pageSize)));
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("save")
    public ReturnResult<SysGenConfig> save(@RequestBody SysGenConfig sysGenConfig) {
        sysGenConfig.setCreateTime(new Date());
        sysGenConfigService.save(sysGenConfig);
        return ReturnResult.ok(sysGenConfig);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("update")
    public ReturnResult<SysGenConfig> update(@RequestBody SysGenConfig sysGenConfig) {
        sysGenConfigService.updateById(sysGenConfig);
        return ReturnResult.ok(sysGenConfig);
    }
    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("download")
    public ReturnResult<SysGenConfig> download(@RequestBody SysGenConfig sysGenConfig) {
        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGenConfig.getDbcId() + ""));
        String dbcDriverLink = sysGenConfig.getDbcDriverLink();
        if(StringUtils.isEmpty(dbcDriverLink)) {
            return ReturnResult.illegal("下载地址无效/不存在");
        }
        HttpResponse httpResponse = HttpClient.get().url(dbcDriverLink).newInvoker().execute();
        ReturnResult driver = null;
        try {
            driver = MultipartFileUtils.transferTo(httpResponse.content(byte[].class), UrlUtils.getFileName(new URL(dbcDriverLink).openConnection()), mkdir, "driver", true);
        } catch (Exception e) {
            e.printStackTrace();
            return ReturnResult.illegal("下载地址无效/不存在");
        }
        if(!driver.isOk()) {
            return driver;
        }
        sysGenConfig.setDbcDriverUrl(driver.getData().toString());

        sysGenConfigService.updateById(sysGenConfig);
        return ReturnResult.ok(sysGenConfig);
    }
    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("uploadDriver")
    public ReturnResult<SysGenConfig> uploadDrvier(SysGenConfig sysGenConfig, @RequestParam(value = "getDriverFile", required = false) MultipartFile getDriverFile) {
        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGenConfig.getDbcId() + ""));

        if(null != getDriverFile) {
            ReturnResult driver = MultipartFileUtils.transferTo(getDriverFile, mkdir, "driver", true);
            if(!driver.isOk()) {
                return driver;
            }
            sysGenConfig.setDbcDriverUrl(driver.getData().toString());
        }

        sysGenConfigService.updateById(sysGenConfig);
        return ReturnResult.ok(sysGenConfig);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @GetMapping("delete")
    public ReturnResult<Boolean> delete(String id) {
        sysGenConfigService.removeById(id);
        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), id));
        try {
            FileUtils.forceDelete(mkdir);
        } catch (IOException e) {
        }
        return ReturnResult.ok(true);
    }

}
