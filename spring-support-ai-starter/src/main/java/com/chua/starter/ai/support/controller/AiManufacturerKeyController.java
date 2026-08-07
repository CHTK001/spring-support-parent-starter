package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.entity.SysAiManufacturerKey;
import com.chua.starter.ai.support.service.SysAiManufacturerKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 厂商 API Key 管理控制器。
 *
 * <p>提供厂商 API Key 的增删改查、按厂商编码查询与启停切换接口。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "AI Manufacturer Key Controller", description = "AI 厂商 API Key 管理相关接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/ai/manufacturer-key")
public class AiManufacturerKeyController {

    /**
     * 厂商 API Key 服务
     */
    private final SysAiManufacturerKeyService manufacturerKeyService;

    /**
     * 查询全部厂商 API Key。
     *
     * @return API Key 列表
     */
    @Operation(summary = "查询全部厂商 API Key")
    @GetMapping("list")
    public ReturnResult<List<SysAiManufacturerKey>> list() {
        return ReturnResult.ok(manufacturerKeyService.list());
    }

    /**
     * 按厂商编码查询 API Key。
     *
     * @param manufacturerCode 厂商编码
     * @return API Key 列表
     */
    @Operation(summary = "按厂商编码查询 API Key")
    @GetMapping("list-by-code")
    public ReturnResult<List<SysAiManufacturerKey>> listByManufacturerCode(
            @Parameter(description = "厂商编码") @RequestParam String manufacturerCode) {
        return ReturnResult.ok(manufacturerKeyService.listByManufacturerCode(manufacturerCode));
    }

    /**
     * 新增厂商 API Key。
     *
     * @param manufacturerKey API Key 实体
     * @return 保存后的实体
     */
    @Operation(summary = "新增厂商 API Key")
    @PostMapping("save")
    public ReturnResult<SysAiManufacturerKey> save(
            @Parameter(description = "API Key 实体") @RequestBody SysAiManufacturerKey manufacturerKey) {
        return ReturnResult.ok(manufacturerKeyService.save(manufacturerKey));
    }

    /**
     * 更新厂商 API Key。
     *
     * @param manufacturerKey API Key 实体
     * @return 是否成功
     */
    @Operation(summary = "更新厂商 API Key")
    @PutMapping("update")
    public ReturnResult<Boolean> update(
            @Parameter(description = "API Key 实体") @RequestBody SysAiManufacturerKey manufacturerKey) {
        return ReturnResult.of(manufacturerKeyService.update(manufacturerKey));
    }

    /**
     * 删除厂商 API Key。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    @Operation(summary = "删除厂商 API Key")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(
            @Parameter(description = "主键 ID") @RequestParam Long id) {
        return ReturnResult.of(manufacturerKeyService.delete(id));
    }

    /**
     * 切换 API Key 启停状态。
     *
     * @param id      主键 ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    @Operation(summary = "切换 API Key 启停状态")
    @PutMapping("toggle")
    public ReturnResult<Boolean> toggle(
            @Parameter(description = "主键 ID") @RequestParam Long id,
            @Parameter(description = "是否启用") @RequestParam Boolean enabled) {
        return ReturnResult.of(manufacturerKeyService.toggle(id, enabled));
    }
}
