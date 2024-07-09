package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.backup.Backup;
import com.chua.common.support.backup.BackupSetting;
import com.chua.common.support.datasource.dialect.Dialect;
import com.chua.common.support.datasource.dialect.DialectFactory;
import com.chua.common.support.datasource.jdbc.option.DataSourceOptions;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.starter.monitor.server.entity.MonitorSysGen;
import com.chua.starter.monitor.server.properties.GenProperties;
import com.chua.starter.monitor.server.service.MonitorGenBackupService;
import com.chua.starter.monitor.server.service.MonitorSysGenService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库备份
 * @author CH
 * @since 2024/7/9
 */
@Service
public class MonitorGenBackupServiceImpl implements MonitorGenBackupService, InitializingBean {

    @Resource
    private MonitorSysGenService sysGenService;
    @Resource
    private MonitorGenBackupService monitorGenBackupService;

    @Resource
    private GenProperties genProperties;
    @Resource
    private ApplicationContext applicationContext;

    private static final Map<Integer, Backup> BACKUP_MAP = new ConcurrentHashMap<>();

    @Resource
    private TransactionTemplate transactionTemplate;
    private static final String MYSQL = "mysql";
    @Override
    public ReturnResult<Boolean> start(MonitorSysGen monitorSysGen) {
        Integer genBackupStatus = monitorSysGen.getGenBackupStatus();
        if(null == genBackupStatus || genBackupStatus != 1) {
            return ReturnResult.error("已开启");
        }

        Map<String, Class<Backup>> stringClassMap = ServiceProvider.of(Backup.class).listType();
        Dialect driver = DialectFactory.createDriver(monitorSysGen.getGenDriver());
        if (!stringClassMap.containsKey(driver.protocol().toUpperCase())) {
            return ReturnResult.error(String.format("不支持备份%s", driver.protocol()));
        }

        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(action -> {
            DataSourceOptions databaseOptions = monitorSysGen.newDatabaseOptions();
            BackupSetting backupSetting = BackupSetting.builder()
                    .build();
            Backup backup = ServiceProvider.of(Backup.class).getNewExtension(driver.protocol(), databaseOptions, backupSetting);
            try {
                backup.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            BACKUP_MAP.put(monitorSysGen.getGenId(), backup);
            monitorSysGen.setGenBackupStatus(1);
            sysGenService.updateById(monitorSysGen);
            return true;
        })));
    }

    @Override
    public ReturnResult<Boolean> stop(MonitorSysGen monitorSysGen) {
        Integer genBackupStatus = monitorSysGen.getGenBackupStatus();
        if(null == genBackupStatus || genBackupStatus != 0) {
            return ReturnResult.error("已停止");
        }

        Map<String, Class<Backup>> stringClassMap = ServiceProvider.of(Backup.class).listType();
        Dialect driver = DialectFactory.createDriver(monitorSysGen.getGenDriver());
        if (!stringClassMap.containsKey(driver.protocol().toUpperCase())) {
            return ReturnResult.error(String.format("不支持备份%s", driver.protocol()));
        }

        return ReturnResult.of(Boolean.TRUE.equals(transactionTemplate.execute(action -> {
            Backup backup = BACKUP_MAP.get(monitorSysGen.getGenId());
            if(null != backup) {
                try {
                    backup.stop();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            monitorSysGen.setGenBackupStatus(0);
            sysGenService.updateById(monitorSysGen);
            return true;
        })));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ThreadUtils.newStaticThreadPool().execute(() -> {
            List<MonitorSysGen> list = sysGenService.list(Wrappers.<MonitorSysGen>lambdaQuery().eq(MonitorSysGen::getGenBackupStatus, 1));
            for (MonitorSysGen monitorSysGen : list) {
                try {
                    start(monitorSysGen);
                } catch (Exception ignored) {
                }
            }
        });
    }
}
