package com.chua.starter.gen.support.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.backup.Backup;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.entity.SysGenBackup;
import com.chua.starter.gen.support.mapper.SysGenBackupMapper;
import com.chua.starter.gen.support.service.SysGenBackupService;
import com.chua.starter.gen.support.service.SysGenService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *    
 * @author CH
 */     
@Service
public class SysGenBackupServiceImpl extends ServiceImpl<SysGenBackupMapper, SysGenBackup> implements SysGenBackupService{
    private static final Map<Integer, Backup> BACKUP_MAP = new ConcurrentHashMap<>();
    @Resource
    private SysGenService sysGenService;

    @Override
    public ReturnResult<Boolean> stop(Integer genId) {
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

        Backup backup = BACKUP_MAP.get(genId);
        if(null == backup) {
            return ReturnResult.illegal("配置信息不存在");
        }
        try {
            backup.stop();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        sysGen.setGenBackupStatus(0);
        sysGenService.updateById(sysGen);
        BACKUP_MAP.remove(genId);
        return ReturnResult.ok(true);
    }

    @Override
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

        SysGenBackup sysGenBackup = getOne(Wrappers.<SysGenBackup>lambdaQuery().eq(SysGenBackup::getGenId, genId));
        Backup backup = ServiceProvider.of(Backup.class).getNewExtension(sysGenBackup.getBackupId() + "", sysGenBackup.newBackupOption());
        if(null == backup) {
            return ReturnResult.illegal("无法开启备份功能(当前系统不支持备份)");
        }
        sysGen.setGenBackupStatus(1);
        sysGenService.updateById(sysGen);

        BACKUP_MAP.put(genId, backup);
        return ReturnResult.ok(sysGenBackup);
    }
}
