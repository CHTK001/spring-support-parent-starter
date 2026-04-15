package com.chua.starter.spider.support.repository;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.mapper.SpiderFlowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 爬虫编排 Repository。
 *
 * <p>提供编排定义的读写，以及与任务定义的原子性持久化。</p>
 *
 * @author CH
 */
@Repository
@RequiredArgsConstructor
public class SpiderFlowRepository extends ServiceImpl<SpiderFlowMapper, SpiderFlowDefinition> {

    private final SpiderTaskRepository taskRepository;

    /**
     * 根据任务 ID 查询编排定义（含反序列化 nodes/edges）。
     */
    public Optional<SpiderFlowDefinition> findByTaskId(Long taskId) {
        SpiderFlowDefinition flow = getOne(
                new LambdaQueryWrapper<SpiderFlowDefinition>()
                        .eq(SpiderFlowDefinition::getTaskId, taskId));
        if (flow != null) {
            deserializeInPlace(flow);
        }
        return Optional.ofNullable(flow);
    }

    /**
     * 保存编排定义（序列化 nodes/edges 到 JSON 列）。
     */
    public void saveFlow(SpiderFlowDefinition flow) {
        serializeInPlace(flow);
        saveOrUpdate(flow);
    }

    /**
     * 原子性保存任务定义和编排定义（同一事务）。
     *
     * @param task 任务定义
     * @param flow 编排定义
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveTaskAndFlow(SpiderTaskDefinition task, SpiderFlowDefinition flow) {
        taskRepository.saveOrUpdateWithLock(task);
        flow.setTaskId(task.getId());
        saveFlow(flow);
    }

    // ── 序列化/反序列化辅助 ───────────────────────────────────────────────────

    private void serializeInPlace(SpiderFlowDefinition flow) {
        if (flow.getNodes() != null) {
            flow.setNodesJson(JSON.toJSONString(flow.getNodes()));
        }
        if (flow.getEdges() != null) {
            flow.setEdgesJson(JSON.toJSONString(flow.getEdges()));
        }
    }

    private void deserializeInPlace(SpiderFlowDefinition flow) {
        if (flow.getNodesJson() != null && !flow.getNodesJson().isBlank()) {
            flow.setNodes(JSON.parseArray(flow.getNodesJson(), SpiderFlowNode.class));
        }
        if (flow.getEdgesJson() != null && !flow.getEdgesJson().isBlank()) {
            flow.setEdges(JSON.parseArray(flow.getEdgesJson(), SpiderFlowEdge.class));
        }
    }
}
