package com.chua.starter.software.support.controller;

import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.chua.starter.software.support.contants.AuthConstant;
import com.chua.starter.software.support.entity.SoftSoftware;
import com.chua.starter.software.support.pojo.software.SoftwarePageRequest;
import com.chua.starter.software.support.service.SoftSoftwareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 软件管理接口
 *
 * @since 2026/7/25
 * @author CH
 */
@Tag(name = "软件管理接口")
@RestController
@RequestMapping("/v2/soft/software")
@RequiredArgsConstructor
public class SoftSoftwareController {

    /** Softsoftware服务 */
    private final SoftSoftwareService softSoftwareService;

    @Operation(summary = "软件列表")
    @GetMapping("/page")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** Page */
    public ReturnPageResult<SoftSoftware> page(@ParameterObject Query<SoftSoftware> query, @ParameterObject SoftwarePageRequest request, @Parameter(hidden = true) @RequestHeader(value = "x-user-roles", required = false) Set<String> roles) {
        return PageResultUtils.ok(softSoftwareService.pageFor(query, request, request.getInstalledSoftwareIds()));
    }

    @Operation(summary = "保存软件")
    @PostMapping("/save")
    @Permission(value = "sys:soft:add", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** 保存 */
    public ReturnResult<SoftSoftware> save(@Validated(AddGroup.class) @RequestBody SoftSoftware software, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return softSoftwareService.saveSoftware(software);
    }

    @Operation(summary = "更新软件")
    @PutMapping("/update")
    @Permission(value = "sys:soft:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** 更新 */
    public ReturnResult<SoftSoftware> update(@Validated(UpdateGroup.class) @RequestBody SoftSoftware software, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return softSoftwareService.updateSoftware(software);
    }

    @Operation(summary = "删除软件")
    @DeleteMapping("/delete")
    @Permission(value = "sys:soft:delete", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** 删除 */
    public ReturnResult<Boolean> delete(String softwareId) {
        return softSoftwareService.deleteSoftware(softwareId);
    }

    @Operation(summary = "软件详情")
    @GetMapping("/detail")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** Detail */
    public ReturnResult<SoftSoftware> detail(String softwareId) {
        return ReturnResult.ok(softSoftwareService.getById(softwareId));
    }
}