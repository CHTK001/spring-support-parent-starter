package com.chua.report.server.starter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.backup.Backup;
import com.chua.common.support.backup.BackupSetting;
import com.chua.common.support.backup.listener.BackupData;
import com.chua.common.support.backup.strategy.DayBackupStrategy;
import com.chua.common.support.constant.EventType;
import com.chua.common.support.datasource.dialect.Dialect;
import com.chua.common.support.datasource.dialect.DialectFactory;
import com.chua.common.support.datasource.jdbc.option.DataSourceOptions;
import com.chua.common.support.function.Joiner;
import com.chua.common.support.function.Splitter;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.common.support.utils.NumberUtils;
import com.chua.common.support.utils.StringUtils;
import com.chua.common.support.utils.ThreadUtils;
import com.chua.redis.support.constant.RedisConstant;
import com.chua.redis.support.search.SearchIndex;
import com.chua.redis.support.search.SearchQuery;
import com.chua.redis.support.search.SearchResultItem;
import com.chua.redis.support.search.SearchSchema;
import com.chua.report.server.starter.entity.MonitorSysGen;
import com.chua.report.server.starter.mapper.MonitorSysGenMapper;
import com.chua.report.server.starter.query.LogTimeQuery;
import com.chua.report.server.starter.service.MonitorGenBackupService;
import com.chua.socketio.support.session.SocketSessionTemplate;
import com.chua.starter.redis.support.service.RedisSearchService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.chua.starter.redis.support.service.impl.RedisSearchServiceImpl.LANGUAGE;

/**
 * 数据库备份
 * @author CH
 * @since 2024/7/9
 */
@Service
public class MonitorGenBackupServiceImpl extends ServiceImpl<MonitorSysGenMapper, MonitorSysGen> implements MonitorGenBackupService, InitializingBean {
    private static final Map<Integer, Backup> BACKUP_MAP = new ConcurrentHashMap<>();

    private final MonitorSysGenMapper monitorSysGenMapper;
    private final SocketSessionTemplate socketSessionTemplate;

    private final RedisSearchService redisSearchService;
    private final TransactionTemplate transactionTemplate;

    public MonitorGenBackupServiceImpl(MonitorSysGenMapper monitorSysGenMapper, SocketSessionTemplate socketSessionTemplate, RedisSearchService redisSearchService, TransactionTemplate transactionTemplate) {
        this.monitorSysGenMapper = monitorSysGenMapper;
        this.socketSessionTemplate = socketSessionTemplate;
        this.redisSearchService = redisSearchService;
        this.transactionTemplate = transactionTemplate;
        this.initialize();
    }

    @Override
    public ReturnResult<Boolean> upgrade(MonitorSysGen newSysGen) {
        Map<String, Class<Backup>> stringClassMap = ServiceProvider.of(Backup.class).listType();
        Dialect driver = DialectFactory.createDriver(newSysGen.getGenDriver());
        if (!stringClassMap.containsKey(driver.protocol().toUpperCase())) {
            return ReturnResult.error(String.format("不支持备份%s", driver.protocol()));
        }
        Backup backup = BACKUP_MAP.get(newSysGen.getGenId());
        if(null != backup) {
            backup.upgrade(BackupSetting.builder()
                    .path("./backup/" + newSysGen.getGenId())
                    .period(NumberUtils.isPositive(newSysGen.getGenBackupPeriod(), 720))
                    .event(Splitter.on(",").omitEmptyStringsAndTrim().splitToStream(newSysGen.getGenBackupEvent()).map(EventType::valueOf).toArray(EventType[]::new))
                    .build());
        }

        return ReturnResult.ok();

    }

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
                    .period(NumberUtils.isPositive(monitorSysGen.getGenBackupPeriod(), 720))
                    .event(Splitter.on(",").omitEmptyStringsAndTrim().splitToStream(monitorSysGen.getGenBackupEvent()).map(EventType::valueOf).toArray(EventType[]::new))
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
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            BACKUP_MAP.put(monitorSysGen.getGenId(), backup);
            monitorSysGen.setGenBackupStatus(1);
            monitorSysGenMapper.updateById(monitorSysGen);
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
        redisSearchService.addDocument(RedisConstant.REDIS_SEARCH_PREFIX + "GEN" + genId, document);
    }
    /**
     * 检查索引
     *
     * @param genId 请求
     */
    private void checkIndex(Integer genId) {
        SearchIndex searchIndex = new SearchIndex();
        searchIndex.setName(RedisConstant.REDIS_SEARCH_PREFIX + "GEN" + genId);
        searchIndex.setPrefix(RedisConstant.REDIS_SEARCH_PREFIX + "GEN" + genId);
        searchIndex.setLanguage("chinese");
        SearchSchema searchSchema = new SearchSchema();
        searchSchema.addTextField("text", 10);
        searchSchema.addTextField("event", 9);
        searchSchema.addTextField("from", 9);
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
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            monitorSysGen.setGenBackupStatus(0);
            monitorSysGenMapper.updateById(monitorSysGen);
            return true;
        })));
    }
    @Override
    public byte[] downloadBackup(Integer genId, Date startDay, Date endDay) {
        Backup backup = BACKUP_MAP.get(genId);
        if(null == backup) {
            MonitorSysGen monitorSysGen = monitorSysGenMapper.selectById(genId);
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
        String key = RedisConstant.REDIS_SEARCH_PREFIX + monitorSysGen.getGenId();
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

    private void initialize() {

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ThreadUtils.newStaticThreadPool().execute(() -> {
            ThreadUtils.sleep(400);
            List<MonitorSysGen> list = monitorSysGenMapper.selectList(Wrappers.<MonitorSysGen>lambdaQuery().eq(MonitorSysGen::getGenBackupStatus, 1));
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
