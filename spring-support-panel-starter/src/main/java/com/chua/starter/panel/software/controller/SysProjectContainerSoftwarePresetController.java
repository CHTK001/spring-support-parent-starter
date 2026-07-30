package com.chua.starter.panel.software.controller;

import com.chua.common.support.utils.group.AddGroup;
import com.chua.common.support.utils.group.UpdateGroup;
import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.chua.starter.oauth.client.support.annotation.UserValue;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.panel.software.entity.SysProjectContainerSoftwarePreset;
import com.chua.starter.panel.software.service.SysProjectContainerSoftwarePresetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Tag(name = "项目容器软件预设接口")
@RestController
@RequestMapping("/v2/project/container/software/preset")
@RequiredArgsConstructor
public class SysProjectContainerSoftwarePresetController {

    private final SysProjectContainerSoftwarePresetService presetService;

    @Operation(summary = "项目容器软件预设分页")
    @GetMapping("/page")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnPageResult<SysProjectContainerSoftwarePreset> page(@ParameterObject Query<SysProjectContainerSoftwarePreset> query,
                                                                    @ParameterObject SysProjectContainerSoftwarePreset request,
                                                                    @Parameter(hidden = true) @UserValue("roles") Set<String> roles) {
        return PageResultUtils.ok(presetService.pageFor(query, request, roles));
    }

    @Operation(summary = "项目容器软件预设列表")
    @GetMapping("/list")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<SysProjectContainerSoftwarePreset>> list(SysProjectContainerSoftwarePreset request) {
        return ReturnResult.ok(presetService.lambdaQuery()
                .like(null != request && null != request.getSoftwareName() && !request.getSoftwareName().trim().isEmpty(),
                        SysProjectContainerSoftwarePreset::getSoftwareName, request.getSoftwareName())
                .eq(null != request && null != request.getSoftwareCategory(),
                        SysProjectContainerSoftwarePreset::getSoftwareCategory, request.getSoftwareCategory())
                .eq(null != request && null != request.getPresetStatus(),
                        SysProjectContainerSoftwarePreset::getPresetStatus, request.getPresetStatus())
                .eq(null != request && null != request.getPresetDelete(),
                        SysProjectContainerSoftwarePreset::getPresetDelete, request.getPresetDelete())
                .orderByAsc(SysProjectContainerSoftwarePreset::getPresetSort)
                .orderByAsc(SysProjectContainerSoftwarePreset::getSysProjectContainerSoftwarePresetId)
                .list());
    }

    @Operation(summary = "新增项目容器软件预设")
    @PostMapping("/save")
    @Permission(value = "sys:project:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<SysProjectContainerSoftwarePreset> save(@Parameter(hidden = true) @UserValue("roles") Set<String> roles,
                                                                @Validated(AddGroup.class) @RequestBody SysProjectContainerSoftwarePreset request,
                                                                BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return presetService.save(request, roles);
    }

    @Operation(summary = "更新项目容器软件预设")
    @PutMapping("/update")
    @Permission(value = "sys:project:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> update(@Parameter(hidden = true) @UserValue("roles") Set<String> roles,
                                        @Validated(UpdateGroup.class) @RequestBody SysProjectContainerSoftwarePreset request,
                                        BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return presetService.update(roles, request);
    }

    @Operation(summary = "删除项目容器软件预设")
    @DeleteMapping("/delete")
    @Transactional(rollbackFor = Exception.class)
    @Permission(value = "sys:project:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> delete(Integer sysProjectContainerSoftwarePresetId) {
        if (null == sysProjectContainerSoftwarePresetId) {
            return ReturnResult.illegal("预设软件不存在");
        }
        return presetService.delete(sysProjectContainerSoftwarePresetId);
    }
}
