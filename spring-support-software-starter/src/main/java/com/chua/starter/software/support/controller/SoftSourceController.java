package com.chua.starter.software.support.controller;

import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.chua.starter.software.support.contants.AuthConstant;
import com.chua.starter.software.support.entity.SoftSource;
import com.chua.starter.software.support.pojo.source.SoftwareSourcePageRequest;
import com.chua.starter.software.support.service.SoftSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
* 软件源接口
*
* @since 2026/7/25
* @author CH
 */
@Tag(name = "软件源接口")
@RestController
@RequestMapping("/v2/soft/source")
@RequiredArgsConstructor
public class SoftSourceController {

    /** Soft来源服务 */
    private final SoftSourceService softSourceService;

    @Operation(summary = "软件源列表")
    @GetMapping("/page")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /** Page */
    public ReturnPageResult<SoftSource> page(
            @ParameterObject Query<SoftSource> query,
            @ParameterObject SoftSource request) {
        return PageResultUtils.ok(softSourceService.pageFor(query, request));
    }

    @Operation(summary = "保存软件源")
    @PostMapping("/save")
    @Permission(value = "sys:soft:source:add", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * 保存
    *
    * @param @Validated(AddGroup.class @校验(添加群体.类
    * @return 保存的结果
    * @param @Validated(AddGroup.class @校验(添加群体.类
    * @param @Validated(AddGroup.class @Validated(AddGroup.class
     */
    public ReturnResult<SoftSource> save(@Validated(AddGroup.class) @RequestBody SoftSource source, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return softSourceService.saveSource(source);
    }

    @Operation(summary = "更新软件源")
    @PutMapping("/update")
    @Permission(value = "sys:soft:source:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * 更新
    *
    * @param @Validated(UpdateGroup.class @校验(更新群体.类
    * @return 更新的结果
    * @param @Validated(UpdateGroup.class @校验(更新群体.类
    * @param @Validated(UpdateGroup.class @Validated(UpdateGroup.class
     */
    public ReturnResult<SoftSource> update(@Validated(UpdateGroup.class) @RequestBody SoftSource source, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return softSourceService.updateSource(source);
    }

    @Operation(summary = "删除软件源")
    @DeleteMapping("/delete")
    @Permission(value = "sys:soft:source:delete", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * 删除
    *
    * @param sourceId 源标识
    * @return 删除的结果
     */
    public ReturnResult<Boolean> delete(String sourceId) {
        return softSourceService.deleteSource(sourceId);
    }

    @Operation(summary = "软件源详情")
    @GetMapping("/detail")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * Detail
    *
    * @param sourceId 源标识
    * @return detail的结果
     */
    public ReturnResult<SoftSource> detail(String sourceId) {
        return ReturnResult.ok(softSourceService.getById(sourceId));
    }

    @Operation(summary = "启用软件源")
    @PutMapping("/enable")
    @Permission(value = "sys:soft:source:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * 启用
    *
    * @param sourceId 源标识
    * @return enable的结果
     */
    public ReturnResult<Boolean> enable(String sourceId) {
        return softSourceService.enableSource(sourceId);
    }

    @Operation(summary = "禁用软件源")
    @PutMapping("/disable")
    @Permission(value = "sys:soft:source:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * 禁用
    *
    * @param sourceId 源标识
    * @return disable的结果
     */
    public ReturnResult<Boolean> disable(String sourceId) {
        return softSourceService.disableSource(sourceId);
    }

    @Operation(summary = "同步软件源")
    @PostMapping("/sync")
    @Permission(value = "sys:soft:source:sync", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * 同步
    *
    * @param sourceId 源标识
    * @return 同步的结果
     */
    public ReturnResult<Integer> sync(String sourceId) {
        return softSourceService.syncSource(sourceId);
    }

    @Operation(summary = "获取默认软件源")
    @GetMapping("/default")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * 获取默认
    *
    * @return 获取默认的结果
     */
    public ReturnResult<SoftSource> getDefault() {
        return ReturnResult.ok(softSourceService.getDefaultSource());
    }
}