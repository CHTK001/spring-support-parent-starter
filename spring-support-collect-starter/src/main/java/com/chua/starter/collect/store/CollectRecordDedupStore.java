package com.chua.starter.collect.store;

import com.chua.starter.collect.engine.CollectDedupStore;
import com.chua.starter.collect.mapper.CollectRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 基于采集结果表的判重存储。
 *
 * <p>直接查 {@code collect_record} 的业务主键，因此判重跨任务、跨重启生效；
 * 只在结果落库开启时有效，试运行不落库时永远判为新条目。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectRecordDedupStore implements CollectDedupStore {

    /**
     * 结果表访问。
     */
    private final CollectRecordMapper recordMapper;

    @Override
    public boolean seen(Integer flowId, boolean global, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String text = key.trim();
        if (flowId == null) {
            return recordMapper.countByKey(text) > 0;
        }
        return global
                ? recordMapper.countByKey(text) > 0
                : recordMapper.countByFlowKey(flowId, text) > 0;
    }
}
