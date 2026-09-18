package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.ai.support.entity.SysAiManufacturerKey;
import com.chua.starter.ai.support.service.SysAiManufacturerKeyService;
import com.chua.starter.common.support.annotations.Permission;
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
* <p>所有接口均受 {@code @Permission("admin")} 保护，查询接口对 Key 明文进行脱敏处理。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Tag(name = "AI Manufacturer Key Controller", description = "AI 厂商 API Key 管理相关接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v2/manufacturer-key")
public class AiManufacturerKeyController {

    /**
    * 厂商 API Key 服务
    */
    private final SysAiManufacturerKeyService manufacturerKeyService;

    /**
    * 对 API Key 与 Secret 进行脱敏，避免明文泄露。
    *
    * @param list 原始 Key 列表
    */
    private void maskSecrets(List<SysAiManufacturerKey> list) {
        if (list == null) {
            return;
        }
        for (SysAiManufacturerKey key : list) {
            key.setValue(mask(key.getValue()));
            key.setSecret(mask(key.getSecret()));
        }
    }

    /**
    * 脱敏单值，保留首尾各 2 位，中间以星号替代。
    *
    * @param value 明文
    * @return 脱敏结果
    */
    private String mask(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    /**
    * 查询全部厂商 API Key，Key 与 Secret 脱敏返回。
    *
    * @return API Key 列表
    */
    @Permission("admin")
    @Operation(summary = "查询全部厂商 API Key")
    @GetMapping("list")
    public ReturnResult<List<SysAiManufacturerKey>> list() {
        List<SysAiManufacturerKey> list = manufacturerKeyService.list();
        /**
        * mask密钥。
        * @param list 列表
        */
        maskSecrets(list);
        return ReturnResult.ok(list);
    }

    /**
    * 按厂商编码查询 API Key，Key 与 Secret 脱敏返回。
    *
    * @param manufacturerCode 厂商编码
    * @return API Key 列表
    */
    @Permission("admin")
    @Operation(summary = "按厂商编码查询 API Key")
    @GetMapping("list-by-code")
    public ReturnResult<List<SysAiManufacturerKey>> listByManufacturerCode(
            @Parameter(description = "厂商编码") @RequestParam String manufacturerCode) {
        List<SysAiManufacturerKey> list = manufacturerKeyService.listByManufacturerCode(manufacturerCode);
        /**
        * mask密钥。
        * @param list 列表
        */
        maskSecrets(list);
        return ReturnResult.ok(list);
    }

    /**
    * 新增厂商 API Key。
    *
    * @param manufacturerKey API Key 实体
    * @return 保存后的实体
    */
    @Permission("admin")
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
    @Permission("admin")
    @Operation(summary = "更新厂商 API Key")
    @PutMapping("update")
    public ReturnResult<Boolean> update(
            @Parameter(description = "API Key 实体") @RequestBody SysAiManufacturerKey manufacturerKey) {
        return ReturnResult.of(manufacturerKeyService.update(manufacturerKey));
    }

    /**
    * 删除厂商 API Key。
    *
    * @param id 主键 标识
    * @return 是否成功
    */
    @Permission("admin")
    @Operation(summary = "删除厂商 API Key")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(
            @Parameter(description = "主键 ID") @RequestParam Long id) {
        return ReturnResult.of(manufacturerKeyService.delete(id));
    }

    /**
    * 切换 API Key 启停状态。
    *
    * @param id      主键 标识
    * @param enabled 是否启用
    * @return 是否成功
    */
    @Permission("admin")
    @Operation(summary = "切换 API Key 启停状态")
    @PutMapping("toggle")
    public ReturnResult<Boolean> toggle(
            @Parameter(description = "主键 ID") @RequestParam Long id,
            @Parameter(description = "是否启用") @RequestParam Boolean enabled) {
        return ReturnResult.of(manufacturerKeyService.toggle(id, enabled));
    }
}
