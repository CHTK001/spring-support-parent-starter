package com.chua.starter.monitor.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.monitor.support.entity.MsMaintenancePlan;
import com.chua.starter.monitor.support.model.dto.MaintenancePlanDTO;
import com.chua.starter.monitor.support.service.impl.MaintenancePlanService;
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

import java.util.List;

/**
 * 维护计划 REST 接口。
 *
 * @author CH
 * @since 4.0.0.44
 */
@Slf4j
@Tag(name = "监控管理", description = "维护计划")
@RestController
@RequestMapping("/ms/server/maintenance-plan")
@RequiredArgsConstructor
public class MaintenancePlanController {

    /**
     * 维护计划服务
     */
    private final MaintenancePlanService maintenancePlanService;

    /**
     * 列出全部维护计划。
     *
     * @return 维护计划列表
     */
    @Operation(summary = "维护计划列表")
    @GetMapping
    public ReturnResult<List<MsMaintenancePlan>> list() {
        return maintenancePlanService.list();
    }

    /**
     * 新建维护计划。
     *
     * @param request 入参
     * @return 新建后的计划
     */
    @Operation(summary = "新建维护计划")
    @PostMapping
    public ReturnResult<MsMaintenancePlan> create(@RequestBody MaintenancePlanDTO request) {
        return maintenancePlanService.create(request);
    }

    /**
     * 修改维护计划。
     *
     * @param id      计划 ID
     * @param request 入参
     * @return 修改后的计划
     */
    @Operation(summary = "修改维护计划")
    @PutMapping("/{id}")
    public ReturnResult<MsMaintenancePlan> update(@PathVariable Long id,
                                                  @RequestBody MaintenancePlanDTO request) {
        return maintenancePlanService.update(id, request);
    }

    /**
     * 删除维护计划。
     *
     * @param id 计划 ID
     * @return 是否删除成功
     */
    @Operation(summary = "删除维护计划")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id) {
        return maintenancePlanService.delete(id);
    }
}