package com.chua.starter.gen.support.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.backup.Backup;
import com.chua.common.support.backup.listener.SimpleBackupListener;
import com.chua.common.support.backup.strategy.BackupStrategy;
import com.chua.common.support.bean.BeanUtils;
import com.chua.common.support.constant.Action;
import com.chua.common.support.json.Json;
import com.chua.common.support.lang.formatter.DdlFormatter;
import com.chua.common.support.lang.formatter.HighlightingFormatter;
import com.chua.common.support.lang.formatter.SqlFormatter;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.common.support.result.ReturnCode;
import com.chua.starter.common.support.result.ReturnResult;
import com.chua.starter.gen.support.entity.SysGen;
import com.chua.starter.gen.support.entity.SysGenBackup;
import com.chua.starter.gen.support.entity.SysGenConfig;
import com.chua.starter.gen.support.mapper.SysGenBackupMapper;
import com.chua.starter.gen.support.service.SysGenBackupService;
import com.chua.starter.gen.support.service.SysGenService;
import com.chua.starter.sse.support.SseMessage;
import com.chua.starter.sse.support.SseTemplate;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * @author CH
 */
@Service
@Slf4j
public class SysGenBackupServiceImpl extends ServiceImpl<SysGenBackupMapper, SysGenBackup> implements SysGenBackupService, InitializingBean {
    private static final Map<Integer, Backup> BACKUP_MAP = new ConcurrentHashMap<>();
    private static final ExecutorService SERVICE = ThreadUtils.newStaticThreadPool();
    @Resource
    private SysGenService sysGenService;
    @Resource
    private SseTemplate sseTemplate;

    @Override
    public ReturnResult<Boolean> stop(Integer genId) {
        if (null == genId) {
            return ReturnResult.error("备份信息不存在");
        }

        if (!BACKUP_MAP.containsKey(genId)) {
            return ReturnResult.illegal("未开启备份");
        }
        SysGen sysGen = sysGenService.getByIdWithType(genId);
        if (null == sysGen) {
            return ReturnResult.illegal("配置信息不存在");
        }

        Backup backup = BACKUP_MAP.get(genId);
        if (null == backup) {
            return ReturnResult.illegal("配置信息不存在");
        }
        try {
            backup.stop();
        } catch (IOException e) {
            return ReturnResult.illegal("备份服务停止失败");
        }
        sysGen.setGenBackupStatus(0);
        sysGenService.updateById(sysGen);
        BACKUP_MAP.remove(genId);
        log.info("{}停止成功", sysGen.getGenName());
        return ReturnResult.ok(true);
    }

    @Override
    public ReturnResult<SysGenBackup> start(Integer genId) {
        if (null == genId) {
            return ReturnResult.error("备份信息不存在");
        }

        if (BACKUP_MAP.containsKey(genId)) {
            return ReturnResult.illegal("已开启备份");
        }
        SysGen sysGen = sysGenService.getByIdWithType(genId);
        if (null == sysGen) {
            return ReturnResult.illegal("服务信息不存在");
        }

        return startSync(sysGen);
    }

    @Override
    public ReturnResult<Boolean> update(SysGenBackup sysGenBackup) {
        if(null == sysGenBackup.getGenId()) {
            return ReturnResult.illegal("配置不存在");
        }
        SysGenBackup genBackup = getOne(Wrappers.<SysGenBackup>lambdaQuery().eq(SysGenBackup::getGenId, sysGenBackup.getGenId()));
        if(null == genBackup) {
            return ReturnResult.illegal("备份配置不存在");
        }
        BeanUtils.copyProperties(sysGenBackup, genBackup);
        Backup backup = BACKUP_MAP.get(sysGenBackup.getGenId());
        if(null != backup) {
            backup.refresh(sysGenBackup.newBackupOption());
        }
        return Result.success(updateById(genBackup));
    }

    private ReturnResult<SysGenBackup> startSync(SysGen sysGen) {
        SysGenBackup sysGenBackup = getOne(Wrappers.<SysGenBackup>lambdaQuery().eq(SysGenBackup::getGenId, sysGen.getGenId()));
        if (null == sysGenBackup) {
            return ReturnResult.illegal("配置信息不存在");
        }
        Backup backup = ServiceProvider.of(Backup.class).getNewExtension(sysGen.getDbcName(), sysGen.newDatabaseOptions(), sysGenBackup.newBackupOption());
        if (null == backup) {
            return ReturnResult.illegal("无法开启备份功能(当前系统不支持备份)");
        }
        try {
            BackupStrategy backupStrategy = ServiceProvider.of(BackupStrategy.class).getNewExtension(sysGenBackup.getBackupStrategy(),
                    sysGenBackup.getBackupAction(), sysGenBackup.getBackupPath(), sysGenBackup.getBackupPeriod());
            backup.addEvent(backupStrategy);
            backup.addListener(new SimpleBackupListener() {
                @Override
                public void listen(Action action, String from, String message, Serializable[] newValue, Serializable[] oldValue) {
                    sseTemplate.emit(SseMessage.builder().event(sysGenBackup.getGenId() + "")
                                    .message(Json.toJson(new JSONObject().fluentPut("action", action.name().toLowerCase()).fluentPut("message", message)))
                            .build(), sysGenBackup.getGenId() + "");
                }
            });
            backup.start();
            log.info("{}({})启动成功", sysGenBackup.getBackupId(), sysGen.getGenName());
        } catch (IOException e) {
            return ReturnResult.illegal("备份服务启动失败");
        }
        sysGen.setGenBackupStatus(1);
        sysGenService.updateById(sysGen);

        BACKUP_MAP.put(sysGen.getGenId(), backup);
        return ReturnResult.ok(sysGenBackup);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        SERVICE.execute(() -> {
            try {
                List<SysGenBackup> sysGenBackups = baseMapper.selectList(
                        new MPJLambdaWrapper<SysGenBackup>()
                                .selectAll(SysGenBackup.class)
                                .innerJoin(SysGen.class, SysGen::getGenId, SysGenBackup::getGenId)
                                .eq(SysGen::getGenBackupStatus, 1)
                                .eq(SysGenBackup::getBackupStatus, 1)
                );
                for (SysGenBackup sysGenBackup : sysGenBackups) {
                    SysGen sysGen = null;
                    try {
                        sysGen = sysGenService.getOne(new MPJLambdaWrapper<SysGen>()
                                .selectAll(SysGen.class)
                                .selectAs(SysGenConfig::getDbcType, "genType")
                                .selectAs(SysGenConfig::getDbcName, "dbcName")
                                .innerJoin(SysGenConfig.class, SysGenConfig::getDbcId, SysGen::getDbcId)
                                .eq(SysGen::getGenId, sysGenBackup.getGenId()));
                        if(null == sysGen) {
                            continue;
                        }
                        ReturnResult<SysGenBackup> sync = startSync(sysGen);
                        if(sync.getCode().equalsIgnoreCase(ReturnCode.OK.getCode())) {
                            log.info("{}({})启动成功", sysGenBackup.getBackupId(), sysGen.getGenName());
                            return;
                        }
                    } catch (Exception e) {
                        log.error("{}启动失败: {}", sysGenBackup.getBackupId(), e.getMessage());
                    }
                    if(null != sysGen) {
                        sysGen.setGenBackupStatus(0);
                        sysGenService.updateById(sysGen);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
