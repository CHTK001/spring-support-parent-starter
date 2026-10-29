package com.chua.starter.ai.support.controller.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.common.support.lang.code.ReturnResult;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型定义控制器，提供模型的增删改查和连通性测试。
 *
 * <p><b>按账号隔离，不限管理员</b>：模型清单属于账号自己，与 {@code sys_ai_app_key} /
 * {@code sys_ai_role} 同口径。控制器层不再挂 {@code @Permission("admin")}，
 * 隔离改由服务层的归属守卫实施（{@code AiModelServiceImpl}）：管理员看全量，
 * 普通账号只看自己的，写只认归属人，无归属的历史行谁都不可写。</p>
 *
 * <p>此前这里六个端点整类限 admin，但前端并未同步：侧边栏「模型管理」对所有账号可见
 * （只有「调度」带 {@code adminOnly}），默认模型页写着「请先在『模型管理』中添加对应类型的模型」，
 * 聊天输入框与模型选择器在没模型时也会「跳转到模型管理页面」。
 * 结果普通账号按界面指引点进去必然 403 —— 前后端不一致，属实缺陷。</p>
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
    @Operation(summary = "新建模型")
    @PostMapping
    public AiModelDTO create(@RequestBody AiModelDTO model) {
        return modelService.create(model);
    }

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
    @Operation(summary = "删除模型")
    @DeleteMapping("/{modelId}")
    public void delete(@Parameter(description = "模型主键") @PathVariable Long modelId) {
        modelService.delete(modelId);
    }

    /**
     * 批量删除模型。
     *
     * <p>与 {@link #delete(Long)} 同口径：写出口归属守卫（{@code AiModelServiceImpl}
     * ）整批过完后才删；{@code ids} 为空或无主行直接拦截，不出现部分删除。</p>
     *
     * @param body 请求体，须含 {@code ids} 列表
     * @return 包含 success 与 deletedCount 的结果
     */
    @Operation(summary = "批量删除模型")
    @DeleteMapping("/batch")
    public ReturnResult<Map<String, Object>> batchDelete(@RequestBody Map<String, ?> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = body == null ? null : (List<Long>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "ids 不能为空");
        }
        int deleted = modelService.batchDelete(ids);
        Map<String, Object> result = new HashMap<>(2);
        result.put("success", true);
        result.put("deletedCount", deleted);
        return ReturnResult.ok(result);
    }

    /**
     * 测试：Latency。
     *
     * @param modelId 模型ID，不允许为 null
     * @return Ai模型DTO 对象
     */
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
    @Operation(summary = "切换模型启用状态")
    @Permission("admin")
    @PutMapping("/{compositeId}/toggle-active")
    public Map<String, Object> toggleActive(@Parameter(description = "复合模型ID providerId:modelName")
                                            @PathVariable("compositeId") String compositeId) {
        // ★ 本端点与其余五个**不同**：它不经过 AiModelService，而是直接改
        //   sys_ai_provider_model.enabled —— 那是**全局共享**的模型可用性表，
        //   EffectiveChatModelResolver / ModelClientResolver / RagClientManager
        //   都按 enabled=true 读它来决定「谁能用哪些模型」。
        //   放开它等于让任意账号一键停掉所有人的模型，故仍限 admin。
        //   待该表也按账号隔离后，本端点才能跟着放开。
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
