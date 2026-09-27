package com.chua.starter.eduai.controller;

import com.chua.common.support.lang.code.PageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.eduai.collect.support.DbCollectSupport;
import com.chua.starter.eduai.model.EduCollectLog;
import com.chua.starter.eduai.model.dto.CollectTaskQueryDTO;
import com.chua.starter.eduai.model.dto.CollectTaskSaveDTO;
import com.chua.starter.eduai.model.vo.CollectMetaVO;
import com.chua.starter.eduai.model.vo.CollectScriptSampleVO;
import com.chua.starter.eduai.model.vo.CollectTaskVO;
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
import java.util.Map;

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

    private final DbCollectSupport dbCollectSupport;

    /**
     * 分页查询采集任务
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询采集任务")
    @GetMapping("/task/page")
    public ReturnResult<PageResult<CollectTaskVO>> pageTasks(CollectTaskQueryDTO query) {
        return ReturnResult.ok(collectTaskService.pageQuery(query));
    }

    /**
     * 新增或更新采集任务
     *
     * @param dto 任务数据
     * @return 任务ID
     */
    @Operation(summary = "新增或更新采集任务")
    @PostMapping("/task/save")
    public ReturnResult<Long> saveTask(@RequestBody CollectTaskSaveDTO dto) {
        return ReturnResult.ok(collectTaskService.saveTask(dto));
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
     * 采集页元数据（服务器时刻 + 默认 cron）
     *
     * @return 元数据
     */
    @Operation(summary = "采集页元数据")
    @GetMapping("/meta")
    public ReturnResult<CollectMetaVO> meta() {
        return ReturnResult.ok(collectTaskService.collectMeta());
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

    /**
     * 内置 Groovy 采集脚本样例
     *
     * @return 样例列表
     */
    @Operation(summary = "内置采集脚本样例")
    @GetMapping("/script-samples")
    public ReturnResult<List<CollectScriptSampleVO>> scriptSamples() {
        return ReturnResult.ok(collectTaskService.scriptSamples());
    }

    /**
     * 前置处理器试跑：按 JSONPath/Groovy 把来源数据转成「一行一个」，供入库处理器配置字段映射。
     *
     * @param url      数据地址
     * @param mode     jsonpath / groovy
     * @param expr     JSONPath 表达式
     * @param script   Groovy 脚本
     * @param limit    预览行数
     * @return 行集合（一行一个 Map）
     */
    @Operation(summary = "数据表入库-前置处理预览")
    @GetMapping("/preview-rows")
    public ReturnResult<List<Map<String, Object>>> previewRows(
            @RequestParam String url,
            @RequestParam(required = false, defaultValue = "jsonpath") String mode,
            @RequestParam(required = false) String expr,
            @RequestParam(required = false) String script,
            @RequestParam(required = false) String mapping,
            @RequestParam(defaultValue = "3") int limit) {
        String json;
        try {
            json = com.chua.starter.eduai.collect.support.HttpFetchSupport.get(url);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("数据拉取失败: " + e.getMessage(), e);
        }
        List<Map<String, Object>> rows;
        if (mapping != null && !mapping.isBlank() && !mapping.trim().equals("{}")) {
            rows = com.chua.starter.eduai.collect.support.PreProcessor.toRowsByMapping(mapping, json);
        } else {
            rows = com.chua.starter.eduai.collect.support.PreProcessor.toRows(mode, expr, script, null, json);
        }
        return ReturnResult.ok(rows.stream().limit(Math.max(1, limit)).toList());
    }

    /**
     * 可入库的数据表元数据（表名/展示名/建议唯一键/列清单），供「数据表入库」来源选择目标表与字段映射。
     *
     * @return 表元数据列表
     */
    @Operation(summary = "数据表入库-可写表元数据")
    @GetMapping("/db-tables")
    public ReturnResult<List<Map<String, Object>>> dbTables() {
        return ReturnResult.ok(dbCollectSupport.tableMetas());
    }

    /**
     * 三方 JSON 取数预览：按 url/dataPath 取前 N 条原始字段，供映射前确认字段名。
     *
     * @param url      三方接口地址
     * @param dataPath 数组路径（可空）
     * @param limit    预览条数
     * @return 原始条目列表
     */
    @Operation(summary = "数据表入库-字段预览")
    @GetMapping("/db-preview")
    public ReturnResult<List<Map<String, Object>>> dbPreview(
            @RequestParam String url,
            @RequestParam(required = false) String dataPath,
            @RequestParam(defaultValue = "3") int limit) {
        return ReturnResult.ok(dbCollectSupport.preview(url, dataPath, limit));
    }
}
