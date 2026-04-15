package com.chua.starter.spider.support.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chua.starter.spider.support.domain.SpiderFlowDefinition;
import com.chua.starter.spider.support.domain.SpiderFlowEdge;
import com.chua.starter.spider.support.domain.SpiderFlowNode;
import com.chua.starter.spider.support.domain.SpiderTaskDefinition;
import com.chua.starter.spider.support.domain.enums.SpiderExecutionType;
import com.chua.starter.spider.support.domain.enums.SpiderNodeType;
import com.chua.starter.spider.support.domain.enums.SpiderTaskStatus;
import com.chua.starter.spider.support.repository.SpiderFlowRepository;
import com.chua.starter.spider.support.repository.SpiderJobBindingRepository;
import com.chua.starter.spider.support.repository.SpiderRuntimeSnapshotRepository;
import com.chua.starter.spider.support.repository.SpiderTaskRepository;
import com.chua.starter.spider.support.security.CredentialSafetyChecker;
import com.chua.starter.spider.support.service.SpiderScheduledJobService;
import com.chua.starter.spider.support.service.SpiderTaskService;
import com.chua.starter.spider.support.service.dto.CreateTaskResult;
import com.chua.starter.spider.support.validator.SpiderFlowValidationResult;
import com.chua.starter.spider.support.validator.SpiderFlowValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 爬虫任务管理服务实现。
 *
 * @author CH
 */
@Service
@RequiredArgsConstructor
public class SpiderTaskServiceImpl implements SpiderTaskService {

    private final SpiderTaskRepository taskRepository;
    private final SpiderFlowRepository flowRepository;
    private final SpiderJobBindingRepository jobBindingRepository;
    private final SpiderRuntimeSnapshotRepository runtimeSnapshotRepository;
    private final SpiderScheduledJobService scheduledJobService;

    private final SpiderFlowValidator flowValidator = new SpiderFlowValidator();
    private final CredentialSafetyChecker credentialSafetyChecker = new CredentialSafetyChecker();

    // ── 7.1 createTask ───────────────────────────────────────────────────────

    @Override
    public CreateTaskResult createTask() {
        // 生成唯一任务编码
        String taskCode = "SPIDER-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        SpiderTaskDefinition task = SpiderTaskDefinition.builder()
                .taskCode(taskCode)
                .status(SpiderTaskStatus.DRAFT)
                .build();

        // 构建默认 START/END 编排
        SpiderFlowNode startNode = SpiderFlowNode.builder()
                .nodeId("start-1")
                .nodeType(SpiderNodeType.START)
                .label("开始")
                .positionX(100.0)
                .positionY(200.0)
                .build();

        SpiderFlowNode endNode = SpiderFlowNode.builder()
                .nodeId("end-1")
                .nodeType(SpiderNodeType.END)
                .label("结束")
                .positionX(400.0)
                .positionY(200.0)
                .build();

        SpiderFlowEdge edge = SpiderFlowEdge.builder()
                .edgeId("edge-start-end")
                .sourceNodeId("start-1")
                .targetNodeId("end-1")
                .build();

        SpiderFlowDefinition flow = SpiderFlowDefinition.builder()
                .nodes(List.of(startNode, endNode))
                .edges(List.of(edge))
                .version(1)
                .build();

        return new CreateTaskResult(null, flow);
    }

    // ── 7.2 saveTask ─────────────────────────────────────────────────────────

    @Override
    public void saveTask(SpiderTaskDefinition task, SpiderFlowDefinition flow) {
        // 必填字段校验
        if (task.getTaskName() == null || task.getTaskName().isBlank()) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (task.getEntryUrl() == null || task.getEntryUrl().isBlank()) {
            throw new IllegalArgumentException("入口 URL 不能为空");
        }

        // 编码唯一性校验
        if (task.getTaskCode() != null && !task.getTaskCode().isBlank()) {
            boolean exists = taskRepository.existsByTaskCode(task.getTaskCode(), task.getId());
            if (exists) {
                throw new IllegalArgumentException("任务编码 [" + task.getTaskCode() + "] 已存在，请使用其他编码");
            }
        }

        // 凭证安全校验
        List<String> credentialWarnings = credentialSafetyChecker.check(task);
        if (!credentialWarnings.isEmpty()) {
            throw new IllegalStateException("凭证安全校验失败：" + String.join("; ", credentialWarnings));
        }

        // 编排合法性校验
        if (flow != null) {
            SpiderFlowValidationResult validationResult = flowValidator.validate(flow);
            if (!validationResult.isValid()) {
                throw new IllegalStateException("编排校验失败：" + validationResult.getErrors());
            }
        }

        // 原子性保存
        flowRepository.saveTaskAndFlow(task, flow != null ? flow : new SpiderFlowDefinition());
    }

    // ── 7.3 deleteTask ───────────────────────────────────────────────────────

    @Override
    public void deleteTask(Long taskId) {
        SpiderTaskDefinition task = taskRepository.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务 [" + taskId + "] 不存在");
        }

        // 若为 SCHEDULED 类型，先删除 job-starter 调度
        if (SpiderExecutionType.SCHEDULED == task.getExecutionType()) {
            scheduledJobService.deleteJob(taskId);
        }

        // 删除 SpiderJobBinding
        jobBindingRepository.deleteByTaskId(taskId);

        // 删除 SpiderRuntimeSnapshot
        runtimeSnapshotRepository.deleteByTaskId(taskId);

        // 删除 SpiderFlow
        flowRepository.remove(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SpiderFlowDefinition>()
                        .eq(SpiderFlowDefinition::getTaskId, taskId)
        );

        // 删除 SpiderTask
        taskRepository.removeById(taskId);
    }
}
