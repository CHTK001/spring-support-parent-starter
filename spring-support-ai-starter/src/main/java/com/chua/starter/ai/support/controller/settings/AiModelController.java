package com.chua.starter.ai.support.controller.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.model.AiModelDTO;
import com.chua.starter.ai.support.service.settings.AiModelService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型定义控制器，提供模型的增删改查和连通性测试。
 *
 * @author CH
 * @since 4.0.0
 */
@RestController
@RequestMapping("/v2/models")
@RequiredArgsConstructor
@Tag(name = "AI 设置 - 模型定义")
public class AiModelController {

    private final AiModelService modelService;
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * 列出。
     *
     * @return 结果列表，无数据时为空列表
     */
    @Permission("admin")
    @Operation(summary = "查询所有模型")
    @GetMapping
    public List<AiModelDTO> list() {
        return modelService.list();
    }

    /**
     * 创建。
     *
     * @param model 模型，不允许为 null
     * @return Ai模型DTO 对象
     */
    @Permission("admin")
    @Operation(summary = "新建模型")
    @PostMapping
    public AiModelDTO create(@RequestBody AiModelDTO model) {
        return modelService.create(model);
    }

    @Permission("admin")
    @Operation(summary = "更新模型")
    @PutMapping("/{modelId}")
    public AiModelDTO update(@Parameter(description = "模型主键") @PathVariable Long modelId,
                             @RequestBody AiModelDTO model) {
        return modelService.update(modelId, model);
    }

    /**
     * 删除。
     *
     * @param modelId 模型ID，不允许为 null
     */
    @Permission("admin")
    @Operation(summary = "删除模型")
    @DeleteMapping("/{modelId}")
    public void delete(@Parameter(description = "模型主键") @PathVariable Long modelId) {
        modelService.delete(modelId);
    }

    /**
     * 测试：Latency。
     *
     * @param modelId 模型ID，不允许为 null
     * @return Ai模型DTO 对象
     */
    @Permission("admin")
    @Operation(summary = "测试模型连通性延迟（非本地模型）")
    @PostMapping("/{modelId}/test-latency")
    public AiModelDTO testLatency(@Parameter(description = "模型主键") @PathVariable Long modelId) {
        return modelService.testLatency(modelId);
    }

    /**
     * 切换某提供商下某模型的启用状态。
     *
     * <p>guada 模型管理页使用复合 id {@code providerId:modelName}（如
     * {@code 1789547286889:agnes-2.5-flash}），启停结果持久化到
     * {@code sys_ai_provider_model} 表。</p>
     *
     * @param compositeId 复合模型标识 providerId:modelName
     * @return 包含最新启用状态的对象 {@code {isActive:boolean}}
     */
    @Permission("admin")
    @Operation(summary = "切换模型启用状态")
    @PutMapping("/{compositeId}/toggle-active")
    public Map<String, Object> toggleActive(@Parameter(description = "复合模型ID providerId:modelName")
                                            @PathVariable("compositeId") String compositeId) {
        int separator = compositeId.indexOf(':');
        if (separator <= 0 || separator >= compositeId.length() - 1) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "模型ID格式非法，应为 providerId:modelName");
        }
        String providerId = compositeId.substring(0, separator);
        String modelName = compositeId.substring(separator + 1);

        SysAiProviderModel entity = providerModelMapper.selectOne(
                new LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getProviderId, providerId)
                        .eq(SysAiProviderModel::getModel, modelName));
        if (entity == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "模型不存在: " + modelName);
        }

        boolean active = !Boolean.TRUE.equals(entity.getEnabled());
        entity.setEnabled(active);
        entity.setUpdatedAt(System.currentTimeMillis());
        providerModelMapper.update(entity, new LambdaUpdateWrapper<SysAiProviderModel>()
                .eq(SysAiProviderModel::getProviderId, providerId)
                .eq(SysAiProviderModel::getModel, modelName));

        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("isActive", active);
        return result;
    }
}
