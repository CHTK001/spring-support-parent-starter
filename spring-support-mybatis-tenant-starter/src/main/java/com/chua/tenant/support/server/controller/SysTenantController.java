package com.chua.tenant.support.server.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.tenant.support.entity.SysTenant;
import com.chua.tenant.support.pojo.SysTenantServiceBindV1Request;
import com.chua.tenant.support.pojo.SysTenantSyncV1Request;
import com.chua.tenant.support.server.service.SysTenantManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 租户管理控制�?
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/06
 */
@Tag(name = "租户管理")
@RestController
@RequestMapping("/v1/tenant")
@RequiredArgsConstructor
public class SysTenantController {

    private final SysTenantManageService sysTenantManageService;

    /**
     * 分页查询租户
     *
     * @param query     分页参数
     * @param sysTenant 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询租户")
    @GetMapping("/page")
    public ReturnResult<IPage<SysTenant>> pageForTenant(Query<SysTenant> query, SysTenant sysTenant) {
        return ReturnResult.ok(sysTenantManageService.pageForTenant(query, sysTenant));
    }

    /**
     * 获取租户详情
     *
     * @param id 租户ID
     * @return 租户详情
     */
    @Operation(summary = "获取租户详情")
    @GetMapping("/{id}")
    public ReturnResult<SysTenant> getByIdForTenant(@PathVariable("id") Integer id) {
        return ReturnResult.ok(sysTenantManageService.getById(id));
    }

    /**
     * 保存租户
     *
     * @param sysTenant 租户信息
     * @return 保存结果
     */
    @Operation(summary = "保存租户")
    @PostMapping
    public ReturnResult<SysTenant> saveForTenant(@RequestBody SysTenant sysTenant) {
        return sysTenantManageService.saveForTenant(sysTenant);
    }

    /**
     * 更新租户
     *
     * @param sysTenant 租户信息
     * @return 更新结果
     */
    @Operation(summary = "更新租户")
    @PutMapping
    public ReturnResult<Boolean> updateForTenant(@RequestBody SysTenant sysTenant) {
        return sysTenantManageService.updateForTenant(sysTenant);
    }

    /**
     * 删除租户
     *
     * @param id 租户ID
     * @return 删除结果
     */
    @Operation(summary = "删除租户")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> deleteForTenant(@PathVariable("id") Long id) {
        return sysTenantManageService.deleteForTenant(id);
    }

    /**
     * 绑定租户服务
     *
     * @param request 绑定请求
     * @return 绑定结果
     */
    @Operation(summary = "绑定租户服务")
    @PostMapping("/bind")
    public ReturnResult<Boolean> bindTenantService(@RequestBody SysTenantServiceBindV1Request request) {
        return sysTenantManageService.bindTenantService(request);
    }

    /**
     * 同步租户数据
     *
     * @param request 同步请求
     * @return 同步结果
     */
    @Operation(summary = "同步租户数据")
    @PostMapping("/sync")
    public ReturnResult<Boolean> syncTenantData(@RequestBody SysTenantSyncV1Request request) {
        return sysTenantManageService.syncTenantData(request);
    }
}
