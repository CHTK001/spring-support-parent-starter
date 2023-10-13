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

    private static final Map<Integer, Backup> BACKUP_MAP = new ConcurrentHashMap<>();
    @Resource
    private SysGenService sysGenService;

    @Resource
    private SysGenBackupService sysGenBackupService;

    @GetMapping("start")
    public ReturnResult<SysGenBackup> start(Integer genId) {
        if(null == genId) {
            return ReturnResult.error("备份信息不存在");
        }

        if(BACKUP_MAP.containsKey(genId)) {
            return ReturnResult.illegal("已开启备份");
        }
        SysGen sysGen = sysGenService.getByIdWithType(genId);
        if(null == sysGen) {
            return ReturnResult.illegal("配置信息不存在");
        }

        SysGenBackup sysGenBackup = sysGenBackupService.getOne(Wrappers.<SysGenBackup>lambdaQuery().eq(SysGenBackup::getGenId, genId));
        Backup backup = ServiceProvider.of(Backup.class).getNewExtension(sysGenBackup.getBackupId() + "", sysGenBackup.newBackupOption());
        if(null == backup) {
            return ReturnResult.illegal("无法开启备份功能(当前系统不支持备份)");
        }
    }
}
