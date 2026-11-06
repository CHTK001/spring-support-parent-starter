package com.chua.starter.collect.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.collect.entity.CollectTask;
import com.chua.starter.collect.service.CollectTaskService;
import com.chua.starter.collect.vo.CollectQualityVO;
import com.chua.starter.collect.vo.CollectStatsVO;
import com.chua.starter.collect.vo.CollectTaskLogVO;
import com.chua.starter.collect.vo.CollectTaskVO;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采集任务接口。
 *
 * <p>任务是流程的一次执行，节点日志按执行顺序返回，节点状态用于设计器回显每个节点的最近结果。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "数据采集-任务")
@RestController
@RequestMapping("/v2/collect/task")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.collect", name = "enable", havingValue = "true", matchIfMissing = true)
public class CollectTaskController {

    private final CollectTaskService taskService;

    @Operation(summary = "任务分页列表")
    @GetMapping("/page")
    public ReturnPageResult<CollectTaskVO> page(@ParameterObject Query<CollectTask> query,
                                                @ParameterObject CollectTask condition) {
        return PageResultUtils.ok(taskService.pageTasks(query.createPage(), condition));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/detail")
    public ReturnResult<CollectTaskVO> detail(@RequestParam Long taskId) {
        return ReturnResult.ok(taskService.detail(taskId));
    }

    @Operation(summary = "节点执行日志")
    @GetMapping("/logs")
    public ReturnResult<List<CollectTaskLogVO>> logs(@RequestParam Long taskId) {
        return ReturnResult.ok(taskService.logs(taskId));
    }

    @Operation(summary = "节点最近状态")
    @GetMapping("/node-states")
    public ReturnResult<List<CollectTaskLogVO>> nodeStates(@RequestParam Long taskId) {
        return ReturnResult.ok(taskService.nodeStates(taskId));
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/delete")
    public ReturnResult<Boolean> delete(@RequestParam Long taskId) {
        return ReturnResult.ok(taskService.deleteTask(taskId));
    }

    @Operation(summary = "清理历史任务")
    @PostMapping("/clean")
    public ReturnResult<Integer> clean(@RequestParam(required = false) Integer flowId,
                                       @RequestParam(defaultValue = "7") int days) {
        return ReturnResult.ok(taskService.cleanTasks(flowId, days));
    }

    /** 任务质量报告：从节点日志聚合质量指标 */
    @Operation(summary = "任务质量报告")
    @GetMapping("/quality")
    public ReturnResult<CollectQualityVO> quality(@RequestParam Long taskId) {
        return ReturnResult.ok(taskService.quality(taskId));
    }

    /** 流程执行历史统计（趋势 + 节点耗时） */
    @Operation(summary = "执行历史统计")
    @GetMapping("/stats")
    public ReturnResult<CollectStatsVO> stats(
            @RequestParam Integer flowId,
            @RequestParam(defaultValue = "30") int days) {
        return ReturnResult.ok(taskService.stats(flowId, days));
    }

    /** 运行中任务的实时节点状态（供前端轮询） */
    @Operation(summary = "实时节点状态")
    @GetMapping("/live")
    public ReturnResult<List<CollectTaskLogVO>> live(@RequestParam Long taskId) {
        return ReturnResult.ok(taskService.liveNodes(taskId));
    }
}
