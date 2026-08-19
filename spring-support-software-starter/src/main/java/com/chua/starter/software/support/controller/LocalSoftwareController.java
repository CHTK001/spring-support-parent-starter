package com.chua.starter.software.support.controller;

import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.chua.starter.software.support.contants.AuthConstant;
import com.chua.starter.software.support.entity.SoftSoftware;
import com.chua.starter.software.support.pojo.install.InstallRequest;
import com.chua.starter.software.support.pojo.software.SoftwarePageRequest;
import com.chua.starter.software.support.service.SoftSoftwareService;
import com.chua.starter.software.support.service.local.LocalSoftwareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 本地软件管理接口
 *
 * <p>提供本地软件扫描、列表查询、安装、卸载等功能。</p>
 *
 * @since 2026/7/25
 * @author CH
 */
@Tag(name = "本地软件管理接口")
@RestController
@RequestMapping("/v2/soft/local")
@RequiredArgsConstructor
public class LocalSoftwareController {

    /** 本地software服务 */
    private final LocalSoftwareService localSoftwareService;
    /** Softsoftware服务 */
    private final SoftSoftwareService softSoftwareService;

    /**
     * 触发本地软件扫描
     *
     * <p>扫描当前操作系统已安装的软件，同步到本地软件源（source_type=3）。</p>
     *
     * @param sourceId 软件源ID（可选，为空时使用默认本地源）
     * @return 扫描结果，包含扫描数量
     */
    @Operation(summary = "扫描本地软件")
    @PostMapping("/scan")
    @Permission(value = "sys:soft:local:scan", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Integer> scan(
            @Parameter(description = "软件源ID（可选）") @RequestParam(required = false) String sourceId) {
        int count = localSoftwareService.scanLocalSoftware(sourceId);
        return ReturnResult.ok(count);
    }

    /**
     * 清空本地软件（指定源）
     *
     * <p>将指定本地源下的所有软件标记为删除状态（软删除）。</p>
     *
     * @param sourceId 软件源ID
     * @return 操作结果
     */
    @Operation(summary = "清空本地软件")
    @DeleteMapping("/clear")
    @Permission(value = "sys:soft:local:delete", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Boolean> clear(
            @Parameter(description = "软件源ID") @RequestParam String sourceId) {
        boolean result = localSoftwareService.clearLocalSoftware(sourceId);
        return ReturnResult.ok(result);
    }

    /**
     * 本地软件列表（分页）
     *
     * <p>查询本地源（source_type=3）下的软件列表。</p>
     *
     * @param query   分页参数
     * @param request 查询条件
     * @return 分页结果
     */
    @Operation(summary = "本地软件列表")
    @GetMapping("/page")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnPageResult<SoftSoftware> page(
            @ParameterObject Query<SoftSoftware> query,
            @ParameterObject SoftwarePageRequest request) {
        request.setSoftwareSourceId(localSoftwareService.getLocalSourceId());
        return PageResultUtils.ok(softSoftwareService.pageFor(query, request, request.getInstalledSoftwareIds()));
    }

    /**
     * 本地软件详情
     *
     * @param softwareId 软件ID
     * @return 软件详情
     */
    @Operation(summary = "本地软件详情")
    @GetMapping("/detail")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<SoftSoftware> detail(
            @Parameter(description = "软件ID") @RequestParam String softwareId) {
        return ReturnResult.ok(softSoftwareService.getById(softwareId));
    }

    /**
     * 安装本地软件
     *
     * <p>从本地软件列表中安装软件到指定路径。</p>
     *
     * @param request 安装请求
     * @return 安装结果
     */
    @Operation(summary = "安装本地软件")
    @PostMapping("/install")
    @Permission(value = "sys:soft:local:install", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Object> install(
            @Validated(AddGroup.class) @RequestBody InstallRequest request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return localSoftwareService.installLocalSoftware(request);
    }

    /**
     * 卸载本地软件
     *
     * <p>从管理系统中移除本地软件记录。</p>
     *
     * @param softwareId 软件ID
     * @param userId     用户ID
     * @param deviceId   设备ID
     * @return 卸载结果
     */
    @Operation(summary = "卸载本地软件")
    @DeleteMapping("/uninstall")
    @Permission(value = "sys:soft:local:uninstall", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<Object> uninstall(
            @Parameter(description = "软件ID") @RequestParam String softwareId,
            @Parameter(description = "用户ID") @RequestParam String userId,
            @Parameter(description = "设备ID") @RequestParam(required = false) String deviceId) {
        return localSoftwareService.uninstallLocalSoftware(softwareId, userId, deviceId);
    }

    /**
     * 添加本地软件（手动添加）
     *
     * <p>管理员可以手动添加本地发现的软件到管理系统。</p>
     *
     * @param software 软件信息
     * @return 添加结果
     */
    @Operation(summary = "添加本地软件")
    @PostMapping("/add")
    @Permission(value = "sys:soft:local:add", role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<SoftSoftware> add(
            @RequestBody SoftSoftware software) {
        return localSoftwareService.addLocalSoftware(software);
    }

    /**
     * 批量获取本地软件（用于下拉选择等）
     *
     * @return 软件列表
     */
    @Operation(summary = "本地软件列表（不分页）")
    @GetMapping("/list")
    @Permission(role = {AuthConstant.ADMIN, AuthConstant.SUPER_ADMIN})
    public ReturnResult<List<SoftSoftware>> list(
            @Parameter(description = "软件名称关键词（可选）") @RequestParam(required = false) String keyword) {
        return ReturnResult.ok(localSoftwareService.listLocalSoftware(keyword));
    }
}