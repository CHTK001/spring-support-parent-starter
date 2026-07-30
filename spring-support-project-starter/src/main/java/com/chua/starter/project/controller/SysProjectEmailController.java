package com.chua.starter.project.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.message.result.PushResult;
import com.chua.common.support.utils.group.AddGroup;
import com.chua.common.support.utils.group.SelectGroup;
import com.chua.common.support.utils.group.UpdateGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.oauth.client.support.contants.AuthConstant;
import com.chua.starter.project.service.SysEmailService;
import com.chua.starter.entity.project.SysEmailLog;
import com.chua.starter.entity.project.SysEmailTemplate;
import com.chua.starter.project.pojo.email.EmailPageV1Request;
import com.chua.starter.project.pojo.email.EmailRequest;
import com.chua.starter.project.pojo.email.EmailSyncV1Request;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 邮件接口
 *
 * @author CH
 * @since 2024/9/4
 */
@RestController
@Slf4j
@Api(tags = "邮件接口")
@Tag(name = "邮件接口")
@RequestMapping("/v2/project/template/email")
@RequiredArgsConstructor
public class SysProjectEmailController {

    final SysEmailService sysEmailService;


    /**
     * 邮件日志分页
     *
     * @param request 请求
     */
    @ApiOperation("邮件日志分页")
    @Operation(summary = "邮件日志分页")
    @GetMapping("/log/page")
    public ReturnPageResult<SysEmailLog> pageForLog(@ParameterObject EmailPageV1Request request) {
        return ReturnPageResultUtils.ok(sysEmailService.pageForLog(request));
    }

    /**
     * 邮件阅读回调
     */
    @ApiOperation("邮件阅读回调")
    @GetMapping("/callback")
    public ResponseEntity<byte[]> callback(@RequestParam("target") String target,
                                           @RequestParam("traceId") String traceId) {

        return sysEmailService.callback(target, traceId);
    }
    /**
     * 发送邮件
     *
     * @param request 请求
     */
    @ApiOperation("发送邮件")
    @Operation(summary = "发送邮件")
    @PutMapping
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<PushResult>> send(@RequestBody EmailRequest request) {
        return sysEmailService.send(request);
    }

    /**
     * 邮件分页
     *
     * @param request 请求
     */
    @ApiOperation("邮件分页")
    @Operation(summary = "邮件分页")
    @GetMapping("/page")
    public ReturnPageResult<SysEmailTemplate> page(@ParameterObject EmailPageV1Request request) {
        return ReturnPageResultUtils.ok(sysEmailService.pageFor(request));
    }

    /**
     * 同步邮件模板
     *
     * @param request 密钥
     */
    @Operation(summary = "同步邮件模板")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    @PutMapping("/syncTemplate")
    @ApiOperation("同步邮件模板")
    public ReturnResult<Boolean> syncTemplate(@Validated(SelectGroup.class) @ParameterObject @RequestBody EmailSyncV1Request request,
                                              BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return sysEmailService.syncTemplate(request);
    }

    /**
     * 删除邮件模板
     *
     * @param sysEmailTemplate 密钥
     */
    @Operation(summary = "删除邮件模板")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    @PutMapping("/delete")
    @ApiOperation("删除邮件模板")
    public ReturnResult<Boolean> delete(@Validated(SelectGroup.class) @ParameterObject @RequestBody SysEmailTemplate sysEmailTemplate,
                                        BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return sysEmailService.delete(sysEmailTemplate);
    }

    /**
     * 更新邮件模板
     *
     * @param sysEmailTemplate 密钥
     */
    @Operation(summary = "更新邮件模板")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    @PutMapping("/update")
    @ApiOperation("更新邮件模板")
    public ReturnResult<Boolean> update(@Validated(UpdateGroup.class) @ParameterObject @RequestBody SysEmailTemplate sysEmailTemplate,
                                        BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return sysEmailService.update(sysEmailTemplate);
    }
    /**
     * 新增邮件模板
     *
     * @param sysEmailTemplate 密钥
     */
    @Operation(summary = "新增邮件模板")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    @PostMapping("/save")
    @ApiOperation("新增邮件模板")
    public ReturnResult<SysEmailTemplate> save(@Validated(AddGroup.class) @ParameterObject @RequestBody SysEmailTemplate sysEmailTemplate,
                                        BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return sysEmailService.save(sysEmailTemplate);
    }
}
