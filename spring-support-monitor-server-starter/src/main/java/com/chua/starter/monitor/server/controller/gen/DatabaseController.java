package com.chua.starter.monitor.server.controller.gen;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.backup.Backup;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.session.Session;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.FileUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.common.support.utils.MultipartFileUtils;
import com.chua.starter.common.support.utils.RequestUtils;
import com.chua.starter.monitor.server.entity.MonitorSysGen;
import com.chua.starter.monitor.server.properties.GenProperties;
import com.chua.starter.monitor.server.query.DeleteFileQuery;
import com.chua.starter.monitor.server.service.MonitorSysGenService;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 生成器控制器
 *
 * @author CH
 */
@RestController
@SuppressWarnings("ALL")
@Tag(name = "数据库接口")
@Slf4j
@RequestMapping("v1/db")
public class DatabaseController {

    @Resource
    private MonitorSysGenService sysGenService;

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
        MonitorSysGen sysGen = sysGenService.getById(query.getGenId());
        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), query.getGenId() + ""));
        try {
            FileUtils.forceDelete(new File(sysGen.getGenDatabaseFile()));
        } catch (IOException e) {
            log.error("", e);
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
    public ReturnPageResult<MonitorSysGen> list(@RequestParam(value = "page", defaultValue = "1") Integer pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                         @RequestParam(value = "databaseType") String databaseType
                                                ) {

        String username = RequestUtils.getUsername();
        Page<MonitorSysGen> genType = sysGenService.page(
                new Page<>(pageNum, pageSize),
                new MPJLambdaWrapper<MonitorSysGen>()
                        .selectAll(MonitorSysGen.class)
                        .eq(MonitorSysGen::getGenType, databaseType)
        );
        Map<String, Class<Backup>> stringClassMap = ServiceProvider.of(Backup.class).listType();
        for (MonitorSysGen record : genType.getRecords()) {
            record.setGenPassword("");
            record.setSupportBackup(stringClassMap.containsKey(record.getGenType().toUpperCase()));
        }
        return PageResultUtils.<MonitorSysGen>ok(genType);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("save")
    public ReturnResult<MonitorSysGen> save(@RequestBody MonitorSysGen sysGen) {
        String genDriver = sysGen.getGenDriver();
        sysGenService.save(sysGen);
        return ReturnResult.ok(sysGen);
    }

    /**
     * 列表
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @PostMapping("update")
    public ReturnResult<MonitorSysGen> update(@RequestBody MonitorSysGen sysGen) {
        MonitorSysGen sysGen1 = sysGenService.getById(sysGen.getGenId());
        if(null == sysGen1) {
            return ReturnResult.illegal("数据不存在");
        }
        if(StringUtils.isEmpty(sysGen.getGenPassword())){
            sysGen.setGenPassword(null);
            sysGen.setGenUid(null);
        }
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
    public ReturnResult<MonitorSysGen> uploadDrvier(MonitorSysGen sysGen, @RequestParam(value = "getDatabaseFile", required = false) MultipartFile getDatabaseFile) {
        File mkdir = FileUtils.mkdir(new File(genProperties.getTempPath(), sysGen.getGenId() + ""));

        if(null != getDatabaseFile) {
            ReturnResult driver = MultipartFileUtils.transferTo(getDatabaseFile, mkdir, "database", true);
            if(!driver.isOk()) {
                return driver;
            }
            sysGen.setGenDatabaseFile(driver.getData().toString());
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
        MonitorSysGen sysGen = sysGenService.getById(id);
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
