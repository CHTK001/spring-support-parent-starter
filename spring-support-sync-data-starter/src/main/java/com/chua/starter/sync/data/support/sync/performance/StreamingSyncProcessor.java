package com.chua.starter.sync.data.support.sync.performance;

import com.chua.starter.sync.data.support.adapter.DataSourceAdapter;
import com.chua.starter.sync.data.support.adapter.ReadConfig;
import com.chua.starter.sync.data.support.adapter.WriteConfig;
import com.chua.starter.sync.data.support.sync.transformer.DataTransformer;
import com.chua.starter.sync.data.support.sync.transformer.TransformConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 流式同步处理器
 * 使用流式处理避免OOM
 *
 * @author System
 * @since 2026/03/09
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamingSyncProcessor {

    private final AdaptiveBatchSizeCalculator batchSizeCalculator;

    /**
     * 使用流式处理进行数据同步
     *
     * @param source 源数据适配器
     * @param target 目标数据适配器
     * @param readConfig 读取配置
     * @param writeConfig 写入配置
     * @param transformer 数据转换器（可选）
     * @param transformConfig 转换配置（可选）
     * @param batchSize 批次大小
     * @return 处理的记录数
     */
    public long processInStream(
            DataSourceAdapter source,
            DataSourceAdapter target,
            ReadConfig readConfig,
            WriteConfig writeConfig,
            DataTransformer transformer,
            TransformConfig transformConfig,
            int batchSize) {

        long totalRecords = 0;
        List<Map<String, Object>> buffer = new ArrayList<>(batchSize);

        try (Stream<Map<String, Object>> stream = source.read(readConfig)) {
            for (Map<String, Object> record : (Iterable<Map<String, Object>>) stream::iterator) {
                // 转换数据
                Map<String, Object> transformed = transformer != null && transformConfig != null
                        ? transformer.transform(record, transformConfig)
                        : record;

                if (transformed != null) {
                    buffer.add(transformed);
                }

                // 达到批次大小或内存压力大时写入
                if (buffer.size() >= batchSize || shouldFlush(buffer.size())) {
                    target.write(buffer, writeConfig);
                    totalRecords += buffer.size();
                    buffer.clear();

                    // 动态调整批次大小
                    double memoryUsage = batchSizeCalculator.getCurrentMemoryUsage();
                    batchSize = batchSizeCalculator.calculateBatchSize(memoryUsage, batchSize);
                }
            }

            // 写入剩余数据
            if (!buffer.isEmpty()) {
                target.write(buffer, writeConfig);
                totalRecords += buffer.size();
                buffer.clear();
            }

        } catch (Exception e) {
            log.error("流式处理失败", e);
            throw new RuntimeException("流式处理失败", e);
        }

        return totalRecords;
    }

    /**
     * 判断是否应该刷新缓冲区
     *
     * @param bufferSize 当前缓冲区大小
     * @return 是否应该刷新
     */
    private boolean shouldFlush(int bufferSize) {
        double memoryUsage = batchSizeCalculator.getCurrentMemoryUsage();
        return memoryUsage > 0.85 && bufferSize > 100;
    }
}
