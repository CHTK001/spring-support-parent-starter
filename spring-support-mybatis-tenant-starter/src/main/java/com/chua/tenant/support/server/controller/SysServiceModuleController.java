package com.chua.tenant.support.server.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.tenant.support.common.entity.SysServiceModule;
import com.chua.tenant.support.server.service.SysServiceModuleService;
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
 * 服务模块接口
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/07
 */
@RestController
@Slf4j
@Api(tags = "服务模块接口")
@Tag(name = "服务模块接口")
@RequestMapping("/v2/service/module")
@RequiredArgsConstructor
@SuppressWarnings("ALL")
public class SysServiceModuleController {

    private final SysServiceModuleService sysServiceModuleService;

    /**
     * 查询服务模块列表
     *
     * @param sysServiceModule 服务模块查询条件
     * @return 服务模块列表
     */
    @GetMapping("list")
    @Operation(summary = "查询服务模块列表")
    public ReturnResult<List<SysServiceModule>> listForSysServiceModule(@ParameterObject SysServiceModule sysServiceModule) {
        return sysServiceModuleService.listForSysServiceModule(sysServiceModule);
    }

    /**
     * 分页查询服务模块
     *
     * @param query            分页查询对象
     * @param sysServiceModule 服务模块查询条件
     * @return 分页结果
     */
    @GetMapping("page")
    @Operation(summary = "分页查询服务模块")
    @Permission("sys:service:module:page")
    public ReturnPageResult<SysServiceModule> pageForSysServiceModule(@ParameterObject Query<SysServiceModule> query,
                                                                      @ParameterObject SysServiceModule sysServiceModule) {
        return ReturnPageResultUtils.ok(sysServiceModuleService.pageForSysServiceModule(query, sysServiceModule));
    }

    /**
     * 保存服务模块
     *
     * @param sysServiceModule 服务模块对象
     * @param bindingResult    校验结果
     * @return 保存结果
     */
    @PostMapping("save")
    @Operation(summary = "保存服务模块")
    @Permission("sys:service:module:save")
    public ReturnResult<SysServiceModule> saveForSysServiceModule(
            @RequestBody @Validated(AddGroup.class) SysServiceModule sysServiceModule,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return sysServiceModuleService.saveForSysServiceModule(sysServiceModule);
    }

    /**
     * 更新服务模块
     *
     * @param sysServiceModule 服务模块对象
     * @param bindingResult    校验结果
     * @return 更新结果
     */
    @PutMapping("update")
    @Operation(summary = "更新服务模块")
    @Permission("sys:service:module:update")
    public ReturnResult<Boolean> updateForSysServiceModule(
            @RequestBody @Validated(UpdateGroup.class) SysServiceModule sysServiceModule,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return sysServiceModuleService.updateForSysServiceModule(sysServiceModule);
    }

    /**
     * 删除服务模块
     *
     * @param sysServiceModuleId 服务模块ID
     * @return 删除结果
     */
    @DeleteMapping("delete")
    @Operation(summary = "删除服务模块")
    @Permission("sys:service:module:delete")
    public ReturnResult<Boolean> deleteForSysServiceModule(Long sysServiceModuleId) {
        return sysServiceModuleService.deleteForSysServiceModule(sysServiceModuleId);
    }
}
