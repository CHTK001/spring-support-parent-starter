package com.chua.starter.software.support.controller;

import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.chua.starter.software.support.contants.AuthConstant;
import com.chua.starter.software.support.entity.SoftConfig;
import com.chua.starter.software.support.pojo.config.SoftwareConfigPageRequest;
import com.chua.starter.software.support.service.SoftConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 软件配置接口
 *
 * @since 2026/7/25
 * @author CH
 */
@Tag(name = "软件配置接口")
@RestController
@RequestMapping("/v2/soft/config")
@RequiredArgsConstructor
public class SoftConfigController {

    /** Soft配置服务 */
    private final SoftConfigService softConfigService;

    @Operation(summary = "配置列表")
    @GetMapping("/page")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** Page */
    public ReturnPageResult<SoftConfig> page(@ParameterObject Query<SoftConfig> query, @ParameterObject SoftwareConfigPageRequest request) {
        return PageResultUtils.ok(softConfigService.pageFor(query, request));
    }

    @Operation(summary = "保存配置")
    @PostMapping("/save")
    @Permission(value = "sys:soft:config:add", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** 保存 */
    public ReturnResult<SoftConfig> save(@Validated(AddGroup.class) @RequestBody SoftConfig config, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return softConfigService.saveConfig(config);
    }

    @Operation(summary = "更新配置")
    @PutMapping("/update")
    @Permission(value = "sys:soft:config:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** 更新 */
    public ReturnResult<SoftConfig> update(@Validated(UpdateGroup.class) @RequestBody SoftConfig config, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return softConfigService.updateConfig(config);
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/delete")
    @Permission(value = "sys:soft:config:delete", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** 删除 */
    public ReturnResult<Boolean> delete(String configId) {
        return softConfigService.deleteConfig(configId);
    }

    @Operation(summary = "软件配置列表")
    @GetMapping("/list")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** List */
    public ReturnResult<java.util.List<SoftConfig>> list(String softwareId) {
        return ReturnResult.ok(softConfigService.listBySoftwareId(softwareId));
    }

    @Operation(summary = "批量保存配置")
    @PostMapping("/batch")
    @Permission(value = "sys:soft:config:add", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** Batch保存 */
    public ReturnResult<Boolean> batchSave(@RequestBody java.util.List<SoftConfig> configs) {
        return softConfigService.batchSaveOrUpdate(configs);
    }
}