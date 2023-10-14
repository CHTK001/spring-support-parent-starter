package com.chua.starter.gen.support.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.backup.Backup;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.entity.SysGenBackup;
import com.chua.starter.gen.support.service.SysGenBackupService;
import com.chua.starter.gen.support.service.SysGenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 备份控制器
 *
 * @author CH
 */
@RestController
@RequestMapping("v1/backup")
public class BackupController {


    @Resource
    private SysGenBackupService sysGenBackupService;

    /**
     * 开始
     *
     * @param genId gen id
     * @return {@link ReturnResult}<{@link SysGenBackup}>
     */
    @GetMapping("start")
    public ReturnResult<SysGenBackup> start(Integer genId) {
        return sysGenBackupService.start(genId);
    }

    /**
     * 开始
     *
     * @param genId gen id
     * @return {@link ReturnResult}<{@link SysGenBackup}>
     */
    @GetMapping("stop")
    public ReturnResult<Boolean> stop(Integer genId) {
        return sysGenBackupService.stop(genId);
    }
}
