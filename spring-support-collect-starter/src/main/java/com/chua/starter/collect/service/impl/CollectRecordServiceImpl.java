package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.dto.CollectParamDTO;
import com.chua.starter.collect.entity.CollectRecord;
import com.chua.starter.collect.mapper.CollectRecordMapper;
import com.chua.starter.collect.service.CollectRecordService;
import com.chua.starter.collect.store.CollectRecordSearchIndex;
import com.chua.starter.collect.support.CollectJson;
import com.chua.starter.collect.vo.CollectRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 采集结果服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectRecordServiceImpl extends ServiceImpl<CollectRecordMapper, CollectRecord>
        implements CollectRecordService {

    /**
     * 批量删除单批大小。
     */
    private static final int BATCH_SIZE = 500;

    /**
     * 结果全文索引提供者；未启用或初始化失败时检索降级为数据库 LIKE 查询。
     */
    private final ObjectProvider<CollectRecordSearchIndex> searchIndexProvider;

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
        boolean removed = removeById(recordId);
        if (removed) {
            searchIndex().delete(recordId);
        }
        return removed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteRecords(List<Long> recordIds) {
        if (CollectionUtils.isEmpty(recordIds)) {
            throw new IllegalArgumentException("至少要指定一条记录");
        }
        int deleted = 0;
        for (int from = 0; from < recordIds.size(); from += BATCH_SIZE) {
            List<Long> batch = recordIds.subList(from, Math.min(from + BATCH_SIZE, recordIds.size()));
            if (removeByIds(batch)) {
                deleted += batch.size();
                batch.forEach(id -> searchIndex().delete(id));
            }
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
        if (deleted > 0) {
            searchIndex().deleteByFlowTask(flowId, taskId);
        }
        return Math.max(deleted, 0);
    }

    @Override
    public long countRecords(Integer flowId) {
        return count(Wrappers.<CollectRecord>lambdaQuery()
                .eq(flowId != null, CollectRecord::getCollectRecordFlowId, flowId));
    }

    @Override
    public IPage<CollectRecordVO> searchRecords(String keyword, CollectRecord condition, IPage<CollectRecord> page) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("检索关键词不能为空");
        }
        CollectRecord safe = condition == null ? new CollectRecord() : condition;
        CollectRecordSearchIndex index = searchIndex();
        if (index == null || !index.available()) {
            return likeSearch(keyword, safe, page);
        }
        try {
            CollectRecordSearchIndex.SearchResult hit = index.search(keyword, safe.getCollectRecordFlowId(),
                    safe.getCollectRecordTaskId(), (int) page.getCurrent(), (int) page.getSize());
            IPage<CollectRecordVO> result = new Page<>(page.getCurrent(), page.getSize(), hit.total());
            if (hit.ids().isEmpty()) {
                result.setRecords(new ArrayList<>());
                return result;
            }
            Map<Long, CollectRecord> rows = listByIds(hit.ids()).stream()
                    .collect(Collectors.toMap(CollectRecord::getCollectRecordId, Function.identity()));
            // 保持索引排序（相关性与更新时间），按命中顺序回填
            result.setRecords(hit.ids().stream()
                    .map(rows::get)
                    .filter(Objects::nonNull)
                    .map(this::toVo)
                    .toList());
            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[collect] 全文检索失败，降级为数据库模糊查询 keyword={} err={}", keyword, ex.getMessage());
            return likeSearch(keyword, safe, page);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rebuildSearchIndex() {
        CollectRecordSearchIndex index = searchIndex();
        if (index == null || !index.available()) {
            throw new IllegalStateException("全文索引不可用，无法重建");
        }
        try {
            index.prepareRebuild();
            int total = 0;
            long lastId = 0L;
            while (true) {
                List<CollectRecord> batch = list(Wrappers.<CollectRecord>lambdaQuery()
                        .gt(CollectRecord::getCollectRecordId, lastId)
                        .orderByAsc(CollectRecord::getCollectRecordId)
                        .last("LIMIT " + BATCH_SIZE));
                if (batch.isEmpty()) {
                    break;
                }
                for (CollectRecord record : batch) {
                    index.index(record);
                    lastId = Math.max(lastId, record.getCollectRecordId());
                }
                total += batch.size();
            }
            index.finishRebuild();
            return total;
        } catch (Exception ex) {
            log.warn("[collect] 全文索引重建失败 err={}", ex.getMessage());
            throw new IllegalStateException("全文索引重建失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 取全文索引组件，不可用或缺失时返回 null。
     *
     * @return 索引组件
     */
    private CollectRecordSearchIndex searchIndex() {
        return searchIndexProvider.getIfAvailable();
    }

    /**
     * 降级检索：对 payload、业务主键与抓取地址做数据库 LIKE 匹配。
     *
     * @param keyword   关键词
     * @param condition 附加过滤条件
     * @param page      分页对象
     * @return 结果分页
     */
    private IPage<CollectRecordVO> likeSearch(String keyword, CollectRecord condition, IPage<CollectRecord> page) {
        IPage<CollectRecord> entityPage = page(page, Wrappers.<CollectRecord>lambdaQuery()
                .eq(condition.getCollectRecordFlowId() != null,
                        CollectRecord::getCollectRecordFlowId, condition.getCollectRecordFlowId())
                .eq(condition.getCollectRecordTaskId() != null,
                        CollectRecord::getCollectRecordTaskId, condition.getCollectRecordTaskId())
                .and(w -> w.like(CollectRecord::getCollectRecordPayload, keyword)
                        .or().like(CollectRecord::getCollectRecordKey, keyword)
                        .or().like(CollectRecord::getCollectRecordUrl, keyword))
                .orderByDesc(CollectRecord::getCollectRecordId));
        return entityPage.convert(this::toVo);
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
