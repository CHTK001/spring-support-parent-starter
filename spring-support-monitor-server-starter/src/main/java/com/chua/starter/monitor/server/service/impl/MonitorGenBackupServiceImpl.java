package com.chua.starter.monitor.server.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.backup.Backup;
import com.chua.common.support.backup.BackupSetting;
import com.chua.common.support.backup.listener.BackupData;
import com.chua.common.support.backup.strategy.DayBackupStrategy;
import com.chua.common.support.constant.EventType;
import com.chua.common.support.datasource.dialect.Dialect;
import com.chua.common.support.datasource.dialect.DialectFactory;
import com.chua.common.support.datasource.jdbc.option.DataSourceOptions;
import com.chua.common.support.function.Joiner;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.redis.support.search.SearchIndex;
import com.chua.redis.support.search.SearchQuery;
import com.chua.redis.support.search.SearchResultItem;
import com.chua.redis.support.search.SearchSchema;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.monitor.server.entity.MonitorSysGen;
import com.chua.starter.monitor.server.properties.GenProperties;
import com.chua.starter.monitor.server.query.LogTimeQuery;
import com.chua.starter.monitor.server.service.MonitorGenBackupService;
import com.chua.starter.monitor.server.service.MonitorSysGenService;
import com.chua.starter.redis.support.service.RedisSearchService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.chua.starter.monitor.server.constant.RedisConstant.REDIS_SEARCH_MONITOR_GEN_PREFIX;
import static com.chua.starter.redis.support.service.impl.RedisSearchServiceImpl.LANGUAGE;

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
    private SocketSessionTemplate socketSessionTemplate;

    @Resource
    private GenProperties genProperties;
    @Resource
    private ApplicationContext applicationContext;

    private static final Map<Integer, Backup> BACKUP_MAP = new ConcurrentHashMap<>();
    @Resource
    private RedisSearchService redisSearchService;
    @Resource
    private TransactionTemplate transactionTemplate;
    private static final String MYSQL = "mysql";
    @Override
    public ReturnResult<Boolean> start(MonitorSysGen monitorSysGen) {
        Integer genBackupStatus = monitorSysGen.getGenBackupStatus();
        if(null != genBackupStatus && genBackupStatus == 1) {
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
                    .path("./backup/" + monitorSysGen.getGenId())
                    .build();
            Backup backup = ServiceProvider.of(Backup.class).getNewExtension(driver.protocol(), databaseOptions, backupSetting);
            try {
                backup.addStrategy(new DayBackupStrategy());
                backup.addListener((event, data) -> {
                    socketSessionTemplate.send("log-gen-" + monitorSysGen.getGenId(), data.getMessage());
                    checkIndex(monitorSysGen.getGenId());
                    registerDocument(event, data, monitorSysGen.getGenId());
                });
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
    private void registerDocument(EventType event, BackupData backupData, Integer genId) {
        Map<String, String> document = new HashMap<>(2);
        document.put("text",  backupData.getMessage());
        document.put("event",  event.name());
        document.put("threadId", StringUtils.toString(backupData.getId()));
        document.put("threadAddress", StringUtils.toString(backupData.getAddress()));
        document.put("from",  backupData.getFrom());
        document.put("newValue", Joiner.on(",").join(backupData.getNewValue()));
        document.put("oldValue", Joiner.on(",").join(backupData.getOldValue()));
        document.put("timestamp", String.valueOf(System.currentTimeMillis()));
        redisSearchService.addDocument(REDIS_SEARCH_MONITOR_GEN_PREFIX + genId, document);
    }
    /**
     * 检查索引
     *
     * @param genId 请求
     */
    private void checkIndex(Integer genId) {
        SearchIndex searchIndex = new SearchIndex();
        searchIndex.setName(REDIS_SEARCH_MONITOR_GEN_PREFIX + genId);
        searchIndex.setLanguage("chinese");
        SearchSchema searchSchema = new SearchSchema();
        searchSchema.addTextField("text", 10);
        searchSchema.addSortableNumericField("timestamp");
        searchIndex.setSchema(searchSchema);
        redisSearchService.createIndex(searchIndex);
    }
    @Override
    public ReturnResult<Boolean> stop(MonitorSysGen monitorSysGen) {
        Integer genBackupStatus = monitorSysGen.getGenBackupStatus();
        if(null == genBackupStatus || genBackupStatus != 1) {
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
    public byte[] downloadBackup(Integer genId, Date startDay, Date endDay) {
        Backup backup = BACKUP_MAP.get(genId);
        if(null == backup) {
            MonitorSysGen monitorSysGen = sysGenService.getById(genId);
            Map<String, Class<Backup>> stringClassMap = ServiceProvider.of(Backup.class).listType();
            Dialect driver = DialectFactory.createDriver(monitorSysGen.getGenDriver());
            if (!stringClassMap.containsKey(driver.protocol().toUpperCase())) {
                throw new IllegalStateException(String.format("不支持备份%s", driver.protocol()));
            }

            BackupSetting backupSetting = BackupSetting.builder()
                    .path("./backup/" + monitorSysGen.getGenId())
                    .build();
            backup = ServiceProvider.of(Backup.class).getNewExtension(driver.protocol(), monitorSysGen.newDatabaseOptions(), backupSetting);
            backup.addStrategy(new DayBackupStrategy());
        }
        return backup.getBackup(startDay, endDay);
    }

    @Override
    public ReturnResult<SearchResultItem> queryForLog(LogTimeQuery timeQuery, MonitorSysGen monitorSysGen) {
        String key = REDIS_SEARCH_MONITOR_GEN_PREFIX + monitorSysGen.getGenId();
        SearchQuery searchQuery = new SearchQuery();
        searchQuery.setIndex(key);
        searchQuery.setLanguage(LANGUAGE);
        StringBuilder keyword = createKeyword(timeQuery);
        searchQuery.setKeyword(keyword.toString());
        searchQuery.setSort("timestamp");
        return redisSearchService.queryAll(searchQuery, (timeQuery.getPage() - 1) * timeQuery.getSize(), timeQuery.getSize());
    }

    private StringBuilder createKeyword(LogTimeQuery timeQuery) {
        StringBuilder keyword = new StringBuilder();

        if(null != timeQuery.getStartDate()) {
            if(null != timeQuery.getEndDate()) {
                keyword.append("timestamp:[").append(timeQuery.getStartDate().getTime()).append("~").append(timeQuery.getEndDate().getTime()).append("]");
            } else {
                keyword.append("timestamp>=").append(timeQuery.getStartDate().getTime()).append(" ");
            }
        } else {
            if(null != timeQuery.getEndDate()) {
                keyword.append("timestamp<= ").append(timeQuery.getEndDate().getTime());
            }
        }

        if(StringUtils.isNotEmpty(timeQuery.getTableName())) {
            keyword.append(" and table:").append(timeQuery.getTableName());
        }

        if(StringUtils.isNotEmpty(timeQuery.getKeyword())) {
            keyword.append(" and text:").append(timeQuery.getKeyword());
        }

        if(StringUtils.isNotEmpty(timeQuery.getAction())) {
            keyword.append(" and event:").append(timeQuery.getAction());
        }

        return keyword.isEmpty() ? keyword.append("*") : keyword;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ThreadUtils.newStaticThreadPool().execute(() -> {
            List<MonitorSysGen> list = sysGenService.list(Wrappers.<MonitorSysGen>lambdaQuery().eq(MonitorSysGen::getGenBackupStatus, 1));
            for (MonitorSysGen monitorSysGen : list) {
                try {
                    monitorSysGen.setGenBackupStatus(0);
                    start(monitorSysGen);
                } catch (Exception ignored) {
                }
            }
        });
    }
}
