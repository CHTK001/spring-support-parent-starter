package com.chua.starter.monitor.server.controller.gen;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.server.entity.MonitorSysGen;
import com.chua.starter.monitor.server.properties.GenProperties;
import com.chua.starter.monitor.server.service.MonitorGenBackupService;
import com.chua.starter.monitor.server.service.MonitorSysGenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * 生成器控制器
 *
 * @author CH
 */
@RestController
@SuppressWarnings("ALL")
@Tag(name = "数据库接口")
@Slf4j
@RequestMapping("v1/backup")
public class BackupController {

    @Resource
    private MonitorSysGenService sysGenService;
    @Resource
    private MonitorGenBackupService monitorGenBackupService;

    @Resource
    private GenProperties genProperties;
    @Resource
    private ApplicationContext applicationContext;
    private static final String MYSQL = "mysql";

    /**
     * 开启备份
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @Operation(summary = "开启备份")
    @PutMapping("start")
    public ReturnResult<Boolean> start(@RequestBody MonitorSysGen sysGen) {
        MonitorSysGen monitorSysGen = sysGenService.getById(sysGen);
        return monitorGenBackupService.start(monitorSysGen);
    }


    /**
     * 下载备份
     *
     * @return {@link ResponseEntity}<{@link byte}[]>
     */
    @Operation(summary = "下载备份")
    @GetMapping("download")
    public ResponseEntity<byte[]> downloadBackup(Integer genId, Date startDay, Date endDay) {
        if (null == genId) {
            throw new RuntimeException("genId不能为空");
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=backup.zip")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(monitorGenBackupService.downloadBackup(genId, startDay, endDay))
                ;
    }
    /**
     * 开启备份
     *
     * @return {@link ReturnResult}<{@link List}<{@link DataSourceResult}>>
     */
    @Operation(summary = "关闭备份")
    @PutMapping("stop")
    public ReturnResult<Boolean> stop(@RequestBody MonitorSysGen sysGen) {
        MonitorSysGen monitorSysGen = sysGenService.getById(sysGen);
        return monitorGenBackupService.stop(monitorSysGen);
    }

}
