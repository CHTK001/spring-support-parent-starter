package com.chua.starter.collect.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.collect.entity.CollectField;
import com.chua.starter.collect.mapper.CollectFieldMapper;
import com.chua.starter.collect.service.CollectFieldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采集结果字段服务实现。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class CollectFieldServiceImpl extends ServiceImpl<CollectFieldMapper, CollectField>
        implements CollectFieldService {

    /**
     * 默认字段类型。
     */
    private static final String TYPE_STRING = "STRING";

    /**
     * 单节点自动登记的字段上限，防止脏表达式撑出几百列。
     */
    private static final int MAX_AUTO_FIELDS = 100;

    /**
     * 隐藏列标记。
     */
    private static final int ENABLED = 1;

    /**
     * 非主键标记。
     */
    private static final int NOT_PRIMARY = 0;

    /**
     * 按流程节点分段加锁，避免并发采集重复登记同名字段。
     */
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Override
    public List<CollectField> listFields(Integer flowId, String nodeId) {
        if (flowId == null) {
            throw new IllegalArgumentException("流程ID不能为空");
        }
        return list(Wrappers.<CollectField>lambdaQuery()
                .eq(CollectField::getCollectFieldFlowId, flowId)
                .eq(nodeId != null && !nodeId.isBlank(), CollectField::getCollectFieldNodeId, nodeId)
                .orderByAsc(CollectField::getCollectFieldSort)
                .orderByAsc(CollectField::getCollectFieldId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replaceFields(Integer flowId, String nodeId, List<CollectField> fields) {
        if (flowId == null) {
            throw new IllegalArgumentException("流程ID不能为空");
        }
        String node = normalize(nodeId);
        remove(Wrappers.<CollectField>lambdaQuery()
                .eq(CollectField::getCollectFieldFlowId, flowId)
                .eq(CollectField::getCollectFieldNodeId, node));
        if (fields == null || fields.isEmpty()) {
            return true;
        }
        List<CollectField> saves = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int sort = 0;
        for (CollectField field : fields) {
            String name = field.getCollectFieldName() == null ? "" : field.getCollectFieldName().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("字段名不能为空");
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException("字段名重复: " + name);
            }
            field.setCollectFieldId(null);
            field.setCollectFieldFlowId(flowId);
            field.setCollectFieldNodeId(node);
            field.setCollectFieldName(name);
            if (field.getCollectFieldLabel() == null || field.getCollectFieldLabel().isBlank()) {
                field.setCollectFieldLabel(name);
            }
            if (field.getCollectFieldType() == null || field.getCollectFieldType().isBlank()) {
                field.setCollectFieldType(TYPE_STRING);
            }
            if (field.getCollectFieldPrimary() == null) {
                field.setCollectFieldPrimary(NOT_PRIMARY);
            }
            if (field.getCollectFieldEnabled() == null) {
                field.setCollectFieldEnabled(ENABLED);
            }
            field.setCollectFieldSort(field.getCollectFieldSort() == null ? sort : field.getCollectFieldSort());
            sort++;
            saves.add(field);
        }
        return saveBatch(saves);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int registerMissing(Integer flowId, String nodeId, Collection<String> names) {
        if (flowId == null || names == null || names.isEmpty()) {
            return 0;
        }
        String node = normalize(nodeId);
        Object lock = locks.computeIfAbsent(flowId + "#" + node, key -> new Object());
        synchronized (lock) {
            List<CollectField> exists = listFields(flowId, node);
            if (exists.size() >= MAX_AUTO_FIELDS) {
                log.warn("[collect] 流程 {} 节点 {} 字段数已达上限 {}，跳过自动登记", flowId, node, MAX_AUTO_FIELDS);
                return 0;
            }
            Set<String> known = new HashSet<>();
            int sort = 0;
            for (CollectField field : exists) {
                known.add(field.getCollectFieldName());
                if (field.getCollectFieldSort() != null && field.getCollectFieldSort() >= sort) {
                    sort = field.getCollectFieldSort() + 1;
                }
            }
            List<CollectField> saves = new ArrayList<>();
            for (String name : names) {
                String field = name == null ? "" : name.trim();
                if (field.isEmpty() || !known.add(field)) {
                    continue;
                }
                if (exists.size() + saves.size() >= MAX_AUTO_FIELDS) {
                    break;
                }
                CollectField entity = new CollectField();
                entity.setCollectFieldFlowId(flowId);
                entity.setCollectFieldNodeId(node);
                entity.setCollectFieldName(field);
                entity.setCollectFieldLabel(field);
                entity.setCollectFieldType(TYPE_STRING);
                entity.setCollectFieldPrimary(NOT_PRIMARY);
                entity.setCollectFieldEnabled(ENABLED);
                entity.setCollectFieldSort(sort++);
                saves.add(entity);
            }
            if (saves.isEmpty()) {
                return 0;
            }
            saveBatch(saves);
            return saves.size();
        }
    }

    /**
     * 节点ID规整，流程级字段统一用空串。
     *
     * @param nodeId 节点ID
     * @return 规整后的节点ID
     */
    private String normalize(String nodeId) {
        return nodeId == null ? "" : nodeId.trim();
    }
}
