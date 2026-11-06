package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.dto.CollectDslDTO;
import com.chua.starter.collect.dto.CollectRunDTO;
import com.chua.starter.collect.service.CollectExecutionService;
import com.chua.starter.collect.vo.CollectTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采集执行接口。
 *
 * <p>运行请求提交后立即返回任务号，执行在虚拟线程里进行；试运行不落结果，只回任务与节点日志。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-执行")
@RestController
@RequestMapping("/v2/collect/execution")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectExecutionController {

    /**
     * 执行服务。
     */
    private final CollectExecutionService executionService;

    /**
     * 运行已保存的流程。
     *
     * @param dto 运行入参
     * @return 任务出参
     */
    @Operation(summary = "运行流程")
    @PostMapping("/run")
    public ReturnResult<CollectTaskVO> run(@RequestBody CollectRunDTO dto) {
        return ReturnResult.ok(executionService.runFlow(dto));
    }

    /**
     * 试运行未保存的流程图，结果不落库。
     *
     * @param dsl 流程图
     * @return 任务出参
     */
    @Operation(summary = "试运行流程图")
    @PostMapping("/dry-run")
    public ReturnResult<CollectTaskVO> dryRun(@RequestBody CollectDslDTO dsl) {
        return ReturnResult.ok(executionService.runDraft(dsl));
    }

    /**
     * 停止运行中的任务。
     *
     * @param taskId 任务ID
     * @return 是否命中本机运行中的任务
     */
    @Operation(summary = "停止任务")
    @PostMapping("/stop")
    public ReturnResult<Boolean> stop(@RequestParam Long taskId) {
        return ReturnResult.ok(executionService.stop(taskId));
    }

    /**
     * 本机执行中的任务数。
     *
     * @return 任务数
     */
    @Operation(summary = "本机执行中任务数")
    @GetMapping("/running")
    public ReturnResult<Integer> running() {
        return ReturnResult.ok(executionService.runningCount());
    }

    /**
     * 某流程排队与执行中的任务数。
     *
     * @param flowId 流程ID
     * @return 任务数
     */
    @Operation(summary = "流程在途任务数")
    @GetMapping("/inflight")
    public ReturnResult<Integer> inflight(@RequestParam Integer flowId) {
        return ReturnResult.ok(executionService.inflight(flowId));
    }
}
