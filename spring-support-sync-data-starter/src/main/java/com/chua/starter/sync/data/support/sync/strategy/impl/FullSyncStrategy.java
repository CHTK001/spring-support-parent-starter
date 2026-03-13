package com.chua.starter.sync.data.support.sync.strategy.impl;

import com.chua.starter.sync.data.support.adapter.*;
import com.chua.starter.sync.data.support.sync.strategy.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * 全量同步策略
 */
@Slf4j
public class FullSyncStrategy implements SyncStrategy {
    
    @Override
    public SyncResult execute(DataSourceAdapter source, DataSourceAdapter target, SyncContext context) {
        long startTime = System.currentTimeMillis();
        AtomicLong totalRecords = new AtomicLong(0);
        AtomicLong successRecords = new AtomicLong(0);
        AtomicLong failedRecords = new AtomicLong(0);
        
        try {
            ReadConfig readConfig = context.getReadConfig();
            WriteConfig writeConfig = context.getWriteConfig();
            int batchSize = context.getBatchSize() > 0 ? context.getBatchSize() : 1000;
            
            try (Stream<Map<String, Object>> stream = source.read(readConfig)) {
                List<Map<String, Object>> batch = new ArrayList<>(batchSize);
                
                stream.forEach(record -> {
                    batch.add(record);
                    totalRecords.incrementAndGet();
                    
                    if (batch.size() >= batchSize) {
                        try {
                            target.write(new ArrayList<>(batch), writeConfig);
                            successRecords.addAndGet(batch.size());
                            log.debug("全量同步批次完成: {} 条", batch.size());
                        } catch (Exception e) {
                            failedRecords.addAndGet(batch.size());
                            log.error("全量同步批次失败", e);
                        }
                        batch.clear();
                    }
                });
                
                if (!batch.isEmpty()) {
                    try {
                        target.write(batch, writeConfig);
                        successRecords.addAndGet(batch.size());
                    } catch (Exception e) {
                        failedRecords.addAndGet(batch.size());
                        log.error("全量同步最后批次失败", e);
                    }
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            return SyncResult.builder()
                .success(failedRecords.get() == 0)
                .totalRecords(totalRecords.get())
                .successRecords(successRecords.get())
                .failedRecords(failedRecords.get())
                .duration(duration)
                .build();
                
        } catch (Exception e) {
            log.error("全量同步失败", e);
            return SyncResult.builder()
                .success(false)
                .totalRecords(totalRecords.get())
                .successRecords(successRecords.get())
                .failedRecords(failedRecords.get())
                .build();
        }
    }
    
    @Override
    public SyncMode getMode() {
        return SyncMode.FULL;
    }
}
