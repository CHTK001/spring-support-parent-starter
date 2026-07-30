package com.chua.starter.project.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.group.AddGroup;
import com.chua.common.support.utils.group.UpdateGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.chua.starter.oauth.client.support.annotation.UserValue;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.project.service.SysSecretService;
import com.chua.starter.entity.project.SysSecret;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

/**
 * 密钥接口
 *
 * @author CH
 * @since 2024/8/7
 */
@Tag(name = "密钥接口")
@RestController
@RequestMapping("/v2/secret")
@RequiredArgsConstructor
public class SysSecretController {

    private final SysSecretService sysSecretService;
    /**
     * 密钥列表
     *
     * @return 密钥列表
     */
    @Operation(summary = "角色密钥列表")
    @GetMapping("/page")
    public ReturnPageResult<SysSecret> page(@ParameterObject Query<SysSecret> query, @ParameterObject SysSecret sysSecret, @Parameter(hidden = true) @UserValue("roles") Set<String> roles) {
        return PageResultUtils.ok(sysSecretService.pageForSecret(query, sysSecret, roles));
    }
    /**
     * 删除密钥
     *
     * @return 更新密钥
     */
    @Operation(summary = "删除密钥")
    @DeleteMapping("/delete")
    @Transactional(rollbackFor = Exception.class)
    @Permission(value = "sys:secret:delete", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> delete(Long sysSecretId) {
        if (null == sysSecretId) {
            return ReturnResult.illegal("密钥信息不存在");
        }

        return sysSecretService.delete(sysSecretId);
    }


    /**
     * 更新密钥
     *
     * @return 更新密钥
     */
    @Operation(summary = "更新密钥")
    @PutMapping("/update")
    @Permission(value = "sys:secret:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> update(
            @Parameter(hidden = true) @UserValue("roles") Set<String> roles,
            @Validated(UpdateGroup.class) @RequestBody SysSecret sysSecret,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }

        return sysSecretService.update(roles, sysSecret);
    }

    /**
     * 新增密钥
     *
     * @return 新增密钥
     */
    @Operation(summary = "新增密钥")
    @PostMapping("/save")
    @Permission(value = "sys:secret:add", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<SysSecret> save(
            @Parameter(hidden = true) @UserValue("roles") Set<String> roles,
            @Validated(AddGroup.class) @RequestBody SysSecret sysSecret,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return sysSecretService.save(sysSecret, roles);
    }
}
