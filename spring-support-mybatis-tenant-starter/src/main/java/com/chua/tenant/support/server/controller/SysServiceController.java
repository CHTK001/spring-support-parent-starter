package com.chua.tenant.support.server.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.common.support.result.Result;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.tenant.support.common.entity.SysService;
import com.chua.tenant.support.server.pojo.SysServiceBindV1Request;
import com.chua.tenant.support.server.service.SysServiceService;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * 服务接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
@RestController
@Slf4j
@Api(tags = "服务接口")
@Tag(name = "服务接口")
@RequestMapping("/v2/service")
@RequiredArgsConstructor
@SuppressWarnings("ALL")
public class SysServiceController {

    private final SysServiceService sysServiceService;

    /**
     * 查询服务列表
     *
     * @param sysService 服务查询条件
     * @return 服务列表
     */
    @GetMapping("list")
    @Operation(summary = "查询服务列表")
    public ReturnResult<List<SysService>> listForSysService(@ParameterObject SysService sysService) {
        return sysServiceService.listForSysService(sysService);
    }

    /**
     * 分页查询服务
     *
     * @param query      分页查询对象
     * @param sysService 服务查询条件
     * @return 分页结果
     */
    @GetMapping("page")
    @Operation(summary = "分页查询服务")
    @Permission("sys:service:page")
    public ReturnPageResult<SysService> pageForSysService(@ParameterObject Query<SysService> query,
                                                          @ParameterObject SysService sysService) {
        return ReturnPageResultUtils.ok(sysServiceService.pageForSysService(query, sysService));
    }

    /**
     * 保存服务
     *
     * @param sysService    服务对象
     * @param bindingResult 校验结果
     * @return 保存结果
     */
    @PostMapping("save")
    @Operation(summary = "保存服务")
    @Permission("sys:service:save")
    public ReturnResult<SysService> saveForSysService(@RequestBody @Validated(AddGroup.class) SysService sysService,
                                                      BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return Result.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return sysServiceService.saveForSysService(sysService);
    }

    /**
     * 更新服务
     *
     * @param sysService    服务对象
     * @param bindingResult 校验结果
     * @return 更新结果
     */
    @PutMapping("update")
    @Operation(summary = "更新服务")
    @Permission("sys:service:update")
    public ReturnResult<Boolean> updateForSysService(@RequestBody @Validated(UpdateGroup.class) SysService sysService,
                                                     BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return Result.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return sysServiceService.updateForSysService(sysService);
    }

    /**
     * 删除服务
     *
     * @param sysServiceId 服务ID
     * @return 删除结果
     */
    @DeleteMapping("delete")
    @Operation(summary = "删除服务")
    @Permission("sys:service:delete")
    public ReturnResult<Boolean> deleteForSysService(Long sysServiceId) {
        return sysServiceService.deleteForSysService(sysServiceId);
    }

    /**
     * 绑定服务模块
     *
     * @param request 绑定请求
     * @return 绑定结果
     */
    @PutMapping("bind")
    @Operation(summary = "绑定服务模块")
    @Permission("sys:service:bind")
    public ReturnResult<Boolean> bindForSysService(@RequestBody SysServiceBindV1Request request) {
        return sysServiceService.bindForSysService(request);
    }
}
