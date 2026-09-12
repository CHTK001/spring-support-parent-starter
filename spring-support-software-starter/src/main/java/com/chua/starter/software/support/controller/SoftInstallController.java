package com.chua.starter.software.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.software.support.contants.AuthConstant;
import com.chua.starter.software.support.entity.SoftInstallRecord;
import com.chua.starter.software.support.pojo.install.InstallDetailResponse;
import com.chua.starter.software.support.pojo.install.InstallRequest;
import com.chua.starter.software.support.service.SoftInstallRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
* 软件安装接口
*
* @since 2026/7/25
* @author CH
 */
@Tag(name = "软件安装接口")
@RestController
@RequestMapping("/v2/soft/install")
@RequiredArgsConstructor
public class SoftInstallController {

    /** Installrecord服务 */
    private final SoftInstallRecordService installRecordService;

    @Operation(summary = "安装软件")
    @PostMapping("/do")
    @Permission(value = "sys:soft:install", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * Install
    *
    * @param @Validated(AddGroup.class @校验(添加群体.类
    * @return install的结果
    * @param @Validated(AddGroup.class @校验(添加群体.类
    * @param @Validated(AddGroup.class @Validated(AddGroup.class
     */
    public ReturnResult<InstallDetailResponse> install(@Validated(AddGroup.class) @RequestBody InstallRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return installRecordService.install(request);
    }

    @Operation(summary = "卸载软件")
    @DeleteMapping("/uninstall")
    @Permission(value = "sys:soft:uninstall", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * Uninstall
    * @param softwareId softwareid
    * @param userId 用户标识
    * @param deviceId deviceid
    * @param userId 用户标识
    * @param deviceId deviceid
    * @param softwareId softwareid
    * @param userId 用户标识
    * @param deviceId deviceid
    * @param targetVersion Target版本
    * @param userId 用户标识
    * @param deviceId deviceid
    * @param targetVersion Target版本
    * @param recordId recordid
    * @param softwareId softwareid
    * @param userId 用户标识
     */
    public ReturnResult<InstallDetailResponse> uninstall(
            @Parameter(description = "软件ID") String softwareId,
            @Parameter(description = "用户ID") String userId,
            @Parameter(description = "设备ID") String deviceId) {
        return installRecordService.uninstall(softwareId, userId, deviceId);
    }

    @Operation(summary = "升级软件")
    @PutMapping("/update")
    @Permission(value = "sys:soft:update", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * 更新版本
    * @param softwareId softwareid
    * @param userId 用户标识
    * @param deviceId deviceid
    * @param targetVersion Target版本
    * @param userId 用户标识
    * @param deviceId deviceid
    * @param targetVersion Target版本
    * @param recordId recordid
    * @param softwareId softwareid
    * @param userId 用户标识
     */
    public ReturnResult<InstallDetailResponse> updateVersion(
            @Parameter(description = "软件ID") String softwareId,
            @Parameter(description = "用户ID") String userId,
            @Parameter(description = "设备ID") String deviceId,
            @Parameter(description = "目标版本") String targetVersion) {
        return installRecordService.updateVersion(softwareId, userId, deviceId, targetVersion);
    }

    @Operation(summary = "安装记录详情")
    @GetMapping("/detail")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * Detail
    *
    * @param recordId recordid
    * @return detail的结果
     */
    public ReturnResult<InstallDetailResponse> detail(String recordId) {
        return installRecordService.getInstallDetail(recordId);
    }

    @Operation(summary = "软件安装记录列表")
    @GetMapping("/list")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * 列表bysoftware
    *
    * @param softwareId softwareid
    * @return 列表bysoftware的结果
     */
    public ReturnResult<List<SoftInstallRecord>> listBySoftware(String softwareId) {
        return ReturnResult.ok(installRecordService.listBySoftwareId(softwareId));
    }

    @Operation(summary = "用户已安装软件")
    @GetMapping("/installed")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    /**
    * 列表installed
    *
    * @param userId 用户标识
    * @return 列表installed的结果
     */
    public ReturnResult<List<SoftInstallRecord>> listInstalled(String userId) {
        return ReturnResult.ok(installRecordService.listInstalledByUserId(userId));
    }
}