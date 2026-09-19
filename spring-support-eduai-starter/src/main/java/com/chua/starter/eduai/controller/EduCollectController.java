package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.model.EduCollectLog;
import com.chua.starter.eduai.model.EduCollectTask;
import com.chua.starter.eduai.model.dto.CollectTaskQueryDTO;
import com.chua.starter.eduai.model.vo.CollectScheduleOverviewVO;
import com.chua.starter.eduai.model.vo.CollectTriggerVO;
import com.chua.starter.eduai.service.IEduCollectTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题库采集管理控制器（任务 CRUD / 手动触发 / 执行日志 / 调度总览）
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/edu/collect")
@RequiredArgsConstructor
@Tag(name = "题库采集管理")
public class EduCollectController {

    /**
     * 采集任务服务
     */
    private final IEduCollectTaskService collectTaskService;

    /**
     * 分页查询采集任务
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询采集任务")
    @GetMapping("/task/page")
    public ReturnResult<PageResult<EduCollectTask>> pageTasks(CollectTaskQueryDTO query) {
        return ReturnResult.ok(collectTaskService.pageQuery(query));
    }

    /**
     * 新增或更新采集任务
     *
     * @param task 任务数据
     * @return 任务ID
     */
    @Operation(summary = "新增或更新采集任务")
    @PostMapping("/task/save")
    public ReturnResult<Long> saveTask(@RequestBody EduCollectTask task) {
        return ReturnResult.ok(collectTaskService.saveTask(task));
    }

    /**
     * 删除采集任务
     *
     * @param id 任务标识
     * @return 是否成功
     */
    @Operation(summary = "删除采集任务")
    @DeleteMapping("/task/{id}")
    public ReturnResult<Boolean> deleteTask(@PathVariable Long id) {
        return ReturnResult.ok(collectTaskService.deleteTask(id));
    }

    /**
     * 启用/禁用采集任务
     *
     * @param id 任务标识
     * @param status 状态
     * @return 是否成功
     */
    @Operation(summary = "启用/禁用采集任务")
    @PutMapping("/task/status")
    public ReturnResult<Boolean> changeStatus(@RequestParam Long id, @RequestParam Integer status) {
        return ReturnResult.ok(collectTaskService.changeStatus(id, status));
    }

    /**
     * 手动触发一次采集（同步执行）
     *
     * @param id 任务标识
     * @return {status, message}
     */
    @Operation(summary = "手动触发采集")
    @PostMapping("/task/trigger/{id}")
    public ReturnResult<CollectTriggerVO> trigger(@PathVariable Long id) {
        return ReturnResult.ok(collectTaskService.trigger(id));
    }

    /**
     * 分页查询采集执行日志
     *
     * @param taskId 任务标识（可空）
     * @param pageNum 当前页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    @Operation(summary = "分页查询采集日志")
    @GetMapping("/log/page")
    public ReturnResult<PageResult<EduCollectLog>> pageLogs(@RequestParam(required = false) Long taskId,
                                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ReturnResult.ok(collectTaskService.pageLogs(taskId, pageNum, pageSize));
    }

    /**
     * 调度总览（定时任务页）
     *
     * @return 总览数据
     */
    @Operation(summary = "采集调度总览")
    @GetMapping("/schedule/overview")
    public ReturnResult<CollectScheduleOverviewVO> scheduleOverview() {
        return ReturnResult.ok(collectTaskService.scheduleOverview());
    }

    /**
     * 可用采集来源类型（SPI 注册表）
     *
     * @return 类型列表
     */
    @Operation(summary = "可用采集来源类型")
    @GetMapping("/source-types")
    public ReturnResult<List<String>> sourceTypes() {
        return ReturnResult.ok(collectTaskService.availableSourceTypes());
    }
}
