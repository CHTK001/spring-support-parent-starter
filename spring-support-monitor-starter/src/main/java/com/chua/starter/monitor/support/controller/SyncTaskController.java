package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.datasync.SyncWriteMode;
import com.chua.starter.monitor.support.entity.MsSyncTask;
import com.chua.starter.monitor.support.model.SyncPreviewVO;
import com.chua.starter.monitor.support.model.SyncTaskRunVO;
import com.chua.starter.monitor.support.model.dto.SyncPreviewDTO;
import com.chua.starter.monitor.support.model.dto.SyncTaskDTO;
import com.chua.starter.monitor.support.service.SyncTaskService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据同步任务 REST 接口。
 *
 * <p>任务描述「从哪个 jdbc 数据源按什么 SQL 读、怎么写进哪个 jdbc 数据源的哪张表」，
 * 数据面复用本仓 datasync 引擎。执行类操作返回的是**执行结果**而不是抛异常：
 * 「同步失败」是业务结果，界面需要看到读了几行、失败在哪一步。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Tag(name = "监控管理", description = "数据同步任务")
@RestController
@RequestMapping("/ms/monitor/datasync")
@RequiredArgsConstructor
public class SyncTaskController {

    /**
     * 数据同步任务服务
     */
    private final SyncTaskService syncTaskService;

    /**
     * 列出全部同步任务。
     *
     * @return 任务列表
     */
    @Operation(summary = "同步任务列表")
    @GetMapping("/tasks")
    public ReturnResult<List<MsSyncTask>> list() {
        return syncTaskService.list();
    }

    /**
     * 查询单个同步任务。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @Operation(summary = "同步任务详情")
    @GetMapping("/tasks/{id}")
    public ReturnResult<MsSyncTask> get(@PathVariable Long id) {
        return syncTaskService.get(id);
    }

    /**
     * 新建同步任务。
     *
     * @param request 任务入参
     * @return 新建后的任务
     */
    @Operation(summary = "新建同步任务")
    @PostMapping("/tasks")
    public ReturnResult<MsSyncTask> create(@RequestBody SyncTaskDTO request) {
        return syncTaskService.create(request);
    }

    /**
     * 修改同步任务。
     *
     * @param id      任务 ID
     * @param request 任务入参
     * @return 修改后的任务
     */
    @Operation(summary = "修改同步任务")
    @PutMapping("/tasks/{id}")
    public ReturnResult<MsSyncTask> update(@PathVariable Long id, @RequestBody SyncTaskDTO request) {
        return syncTaskService.update(id, request);
    }

    /**
     * 删除同步任务。
     *
     * @param id 任务 ID
     * @return 是否删除成功
     */
    @Operation(summary = "删除同步任务")
    @DeleteMapping("/tasks/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return syncTaskService.delete(id);
    }

    /**
     * 立即执行一次同步。
     *
     * @param id 任务 ID
     * @return 本次执行结果
     */
    @Operation(summary = "立即执行一次同步")
    @PostMapping("/tasks/{id}/run")
    public ReturnResult<SyncTaskRunVO> runOnce(@PathVariable Long id) {
        return syncTaskService.runOnce(id);
    }

    /**
     * 启用任务的定时调度。
     *
     * @param id 任务 ID
     * @return 是否更新成功
     */
    @Operation(summary = "启用同步任务定时")
    @PostMapping("/tasks/{id}/enable")
    public ReturnResult<Boolean> enable(@PathVariable Long id) {
        return syncTaskService.setEnabled(id, true);
    }

    /**
     * 停用任务的定时调度。
     *
     * @param id 任务 ID
     * @return 是否更新成功
     */
    @Operation(summary = "停用同步任务定时")
    @PostMapping("/tasks/{id}/disable")
    public ReturnResult<Boolean> disable(@PathVariable Long id) {
        return syncTaskService.setEnabled(id, false);
    }

    /**
     * 预览字段映射。
     *
     * @param request 预览入参
     * @return 预览结果
     */
    @Operation(summary = "预览字段映射")
    @PostMapping("/preview")
    public ReturnResult<SyncPreviewVO> preview(@RequestBody SyncPreviewDTO request) {
        return syncTaskService.preview(request);
    }

    /**
     * 列出可用的写入模式。
     *
     * @return 写入模式列表（编码 + 中文说明）
     */
    @Operation(summary = "写入模式字典")
    @GetMapping("/write-modes")
    public ReturnResult<List<Map<String, String>>> writeModes() {
        List<Map<String, String>> modes = new ArrayList<>();
        for (SyncWriteMode mode : SyncWriteMode.values()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("code", mode.code());
            item.put("label", mode.label());
            modes.add(item);
        }
        return ReturnResult.ok(modes);
    }
}