package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.entity.CollectRecord;
import com.chua.starter.collect.mapper.CollectRecordMapper;
import com.chua.starter.collect.service.CollectRecordService;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.vo.CollectRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 采集结果服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class CollectRecordServiceImpl extends ServiceImpl<CollectRecordMapper, CollectRecord>
        implements CollectRecordService {

    /**
     * 批量删除单批大小。
     */
    private static final int BATCH_SIZE = 500;

    @Override
    public IPage<CollectRecordVO> pageRecords(IPage<CollectRecord> page, CollectRecord condition) {
        CollectRecord safe = condition == null ? new CollectRecord() : condition;
        IPage<CollectRecord> entityPage = page(page, Wrappers.<CollectRecord>lambdaQuery()
                .eq(safe.getCollectRecordFlowId() != null,
                        CollectRecord::getCollectRecordFlowId, safe.getCollectRecordFlowId())
                .eq(safe.getCollectRecordTaskId() != null,
                        CollectRecord::getCollectRecordTaskId, safe.getCollectRecordTaskId())
                .eq(notBlank(safe.getCollectRecordNodeId()),
                        CollectRecord::getCollectRecordNodeId, safe.getCollectRecordNodeId())
                .like(notBlank(safe.getCollectRecordKey()),
                        CollectRecord::getCollectRecordKey, safe.getCollectRecordKey())
                .like(notBlank(safe.getCollectRecordUrl()),
                        CollectRecord::getCollectRecordUrl, safe.getCollectRecordUrl())
                .orderByDesc(CollectRecord::getCollectRecordId));
        return entityPage.convert(this::toVo);
    }

    @Override
    public CollectRecordVO detail(Long recordId) {
        return toVo(requireRecord(recordId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRecord(Long recordId) {
        requireRecord(recordId);
        return removeById(recordId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRecords(List<Long> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            throw new IllegalArgumentException("至少要指定一条记录");
        }
        int deleted = 0;
        for (int from = 0; from < recordIds.size(); from += BATCH_SIZE) {
            List<Long> batch = recordIds.subList(from, Math.min(from + BATCH_SIZE, recordIds.size()));
            deleted += removeByIds(batch) ? batch.size() : 0;
        }
        return deleted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearRecords(Integer flowId, Long taskId) {
        if (flowId == null && taskId == null) {
            throw new IllegalArgumentException("必须指定流程或任务");
        }
        int deleted = baseMapper.delete(Wrappers.<CollectRecord>lambdaQuery()
                .eq(flowId != null, CollectRecord::getCollectRecordFlowId, flowId)
                .eq(taskId != null, CollectRecord::getCollectRecordTaskId, taskId));
        return Math.max(deleted, 0);
    }

    @Override
    public long countRecords(Integer flowId) {
        return count(Wrappers.<CollectRecord>lambdaQuery()
                .eq(flowId != null, CollectRecord::getCollectRecordFlowId, flowId));
    }

    /**
     * 按主键取记录。
     *
     * @param recordId 记录ID
     * @return 记录
     */
    private CollectRecord requireRecord(Long recordId) {
        if (recordId == null) {
            throw new IllegalArgumentException("记录ID不能为空");
        }
        CollectRecord record = getById(recordId);
        if (record == null) {
            throw new IllegalArgumentException("记录不存在: " + recordId);
        }
        return record;
    }

    /**
     * 实体转出参，payload 拆成有序字段。
     *
     * @param record 记录
     * @return 出参
     */
    private CollectRecordVO toVo(CollectRecord record) {
        CollectRecordVO vo = new CollectRecordVO();
        vo.setCollectRecordId(record.getCollectRecordId());
        vo.setCollectRecordFlowId(record.getCollectRecordFlowId());
        vo.setCollectRecordTaskId(record.getCollectRecordTaskId());
        vo.setCollectRecordNodeId(record.getCollectRecordNodeId());
        vo.setCollectRecordKey(record.getCollectRecordKey());
        vo.setCollectRecordUrl(record.getCollectRecordUrl());
        vo.setCreateTime(record.getCreateTime());
        vo.setUpdateTime(record.getUpdateTime());
        List<CollectParamDTO> cells = new ArrayList<>();
        for (Map.Entry<String, Object> entry : CollectJson.parseMap(record.getCollectRecordPayload()).entrySet()) {
            cells.add(new CollectParamDTO(entry.getKey(), text(entry.getValue()), null));
        }
        vo.setCells(cells);
        return vo;
    }

    /**
     * 单元格取文本，复杂结构序列化为 JSON。
     *
     * @param value 原值
     * @return 文本
     */
    private String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return CollectJson.toJson(value);
    }

    /**
     * 文本是否非空。
     *
     * @param text 文本
     * @return 非空返回 true
     */
    private boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }
}
