package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.generate.GenerateConfigSupport;
import com.chua.starter.ai.support.generate.GenerateSpiRouter;
import com.chua.starter.ai.support.generate.GenerateService;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiGenerateTask;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.util.AiUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成任务控制器：文生图 / 文生视频。
 *
 * <p>与对话侧一致，按模型名路由到对应供应商的 SPI 实现（见
 * {@link GenerateSpiRouter} 与 {@code GenerateClientResolver}），前端不需要知道
 * 背后是通义万相、智谱还是 OpenAI。</p>
 *
 * <p>任务为异步模式：创建后立即返回 taskId，前端轮询 {@code /tasks/{taskId}} 取进度，
 * 状态每次都向 provider 侧回源查询，因此刷新页面或换设备后进度依然正确。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/ai/generate")
@RequiredArgsConstructor
@Tag(name = "AI 生成 - 文生图/文生视频")
public class GenerateController {

    /**
     * 生成服务
     */
    private final GenerateService generateService;

    /**
     * 提供者映射器
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 提供者模型映射器
     */
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * 创建生成任务的请求体
     */
    @Data
    public static class CreateBody {

        /**
         * 任务类型（image / video），为空按 image 处理
         */
        private String type;

        /**
         * 模型名（必填）
         */
        private String model;

        /**
         * 正向提示词（必填）
         */
        private String prompt;

        /**
         * 负面提示词
         */
        private String negativePrompt;

        /**
         * 分辨率，如 1024x1024
         */
        private String size;

        /**
         * 请求张数，超出模型上限由服务端收敛
         */
        private Integer count;

        /**
         * 视频时长（秒）
         */
        private Integer duration;

        /**
         * 随机种子
         */
        private Long seed;

        /**
         * 风格
         */
        private String style;

        /**
         * 质量档
         */
        private String quality;

        /**
         * 推理步数
         */
        private Integer steps;

        /**
         * 参考图（data URL 或 base64）
         */
        private String referenceImage;
    }

    /**
     * 可选生成模型列表（含模型级生成参数）。
     *
     * <p>只返回已启用、且其供应商已具备对应生成实现的模型，避免前端把不可用模型
     * 渲染出来让用户提交后才失败。</p>
     *
     * @param type 任务类型（image / video），为空返回全部
     * @return 模型列表
     */
    @GetMapping("/models")
    @Operation(summary = "可选生成模型", description = "返回已启用且供应商具备对应生成实现的模型及其生成参数")
    public ReturnResult<List<Map<String, Object>>> models(
            @RequestParam(required = false) String type) {
        String kind = type == null || type.isBlank() ? GenerateConfigSupport.TYPE_IMAGE : type;
        boolean video = GenerateConfigSupport.TYPE_VIDEO.equalsIgnoreCase(kind);
        List<Map<String, Object>> result = new ArrayList<>();
        List<SysAiProviderModel> rows = providerModelMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAiProviderModel>()
                        .eq(SysAiProviderModel::getEnabled, true)
                        .orderByAsc(SysAiProviderModel::getSortOrder));
        for (SysAiProviderModel row : rows) {
            if (!matchesTask(row, kind)) {
                continue;
            }
            SysAiProvider provider = providerMapper.selectById(row.getProviderId());
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
                continue;
            }
            boolean supported = video
                    ? GenerateSpiRouter.supportsVideo(provider.getPlatform())
                    : GenerateSpiRouter.supportsImage(provider.getPlatform());
            if (!supported) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("model", row.getModel());
            item.put("alias", row.getAlias());
            item.put("providerId", row.getProviderId());
            item.put("providerName", provider.getName());
            item.put("type", kind);
            item.put("config", toConfigView(GenerateConfigSupport.read(row, kind)));
            result.add(item);
        }
        return ReturnResult.success(result);
    }

    /**
     * 创建生成任务。
     *
     * @param body 创建入参
     * @return 任务视图（含 taskId）
     */
    @PostMapping("/tasks")
    @Operation(summary = "创建生成任务", description = "按模型名路由到对应供应商的生成实现，异步返回 taskId")
    public ReturnResult<Map<String, Object>> create(@RequestBody CreateBody body) {
        String userId = AiUserContext.requireUserId();
        try {
            SysAiGenerateTask task = generateService.create(new GenerateService.Request(
                    body.getType(), body.getModel(), body.getPrompt(), body.getNegativePrompt(),
                    body.getSize(), body.getCount(), body.getDuration(), body.getSeed(),
                    body.getStyle(), body.getQuality(), body.getSteps(), body.getReferenceImage(), userId));
            return ReturnResult.success(toView(task));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ApiException(ApiErrorCode.NO_COMPLETER, e.getMessage());
        }
    }

    /**
     * 查询生成任务状态与结果。
     *
     * @param taskId 任务ID
     * @return 任务视图
     */
    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询生成任务", description = "向 provider 侧回源查询实时状态并回写")
    public ReturnResult<Map<String, Object>> query(@PathVariable String taskId) {
        AiUserContext.requireUserId();
        SysAiGenerateTask task = generateService.query(taskId);
        if (task == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "任务不存在");
        }
        return ReturnResult.success(toView(task));
    }

    /**
     * 取消生成任务。
     *
     * @param taskId 任务ID
     * @return 是否取消成功
     */
    @PostMapping("/tasks/{taskId}/cancel")
    @Operation(summary = "取消生成任务")
    public ReturnResult<Boolean> cancel(@PathVariable String taskId) {
        AiUserContext.requireUserId();
        return ReturnResult.success(generateService.cancel(taskId));
    }

    /**
     * 生成任务历史。
     *
     * @param type  任务类型，可为空
     * @param limit 条数上限
     * @return 任务视图列表
     */
    @GetMapping("/tasks")
    @Operation(summary = "生成任务历史")
    public ReturnResult<List<Map<String, Object>>> history(
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "30") int limit) {
        String userId = AiUserContext.requireUserId();
        List<SysAiGenerateTask> tasks = generateService.listRecent(userId, type, limit);
        return ReturnResult.success(tasks.stream().map(GenerateController::toView).toList());
    }

    /**
     * 判断模型行是否属于指定生成任务。
     *
     * @param row  模型行
     * @param kind 任务类型
     * @return true 表示匹配
     */
    private static boolean matchesTask(SysAiProviderModel row, String kind) {
        List<Map<String, Object>> caps = row.getCapabilities();
        if (caps == null || caps.isEmpty()) {
            return false;
        }
        String wanted = GenerateConfigSupport.TYPE_VIDEO.equalsIgnoreCase(kind)
                ? "video_generation" : "image_generation";
        for (Map<String, Object> cap : caps) {
            if (cap == null) {
                continue;
            }
            Object task = cap.get("task");
            if (task == null) {
                continue;
            }
            String name = task.toString();
            if (wanted.equals(name) || (wanted.equals("image_generation") && "text-to-image".equals(name))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成参数视图转前端可读结构。
     *
     * @param view 生成参数视图
     * @return 前端结构
     */
    private static Map<String, Object> toConfigView(GenerateConfigSupport.View view) {
        Map<String, Object> map = new LinkedHashMap<>(10);
        map.put("referenceImage", view.referenceImage());
        map.put("negativePrompt", view.negativePrompt());
        map.put("seed", view.seed());
        map.put("maxCount", view.maxCount());
        map.put("sizes", view.sizes());
        map.put("durations", view.durations());
        map.put("stepsMin", view.stepsMin());
        map.put("stepsMax", view.stepsMax());
        map.put("stepsDefault", view.stepsDefault());
        return map;
    }

    /**
     * 任务行转前端视图。
     *
     * @param task 任务行
     * @return 前端结构
     */
    private static Map<String, Object> toView(SysAiGenerateTask task) {
        Map<String, Object> map = new LinkedHashMap<>(16);
        map.put("taskId", task.getTaskId());
        map.put("type", task.getType());
        map.put("status", task.getStatus());
        map.put("progress", task.getProgress());
        map.put("prompt", task.getPrompt());
        map.put("negativePrompt", task.getNegativePrompt());
        map.put("model", task.getModel());
        map.put("size", task.getSize());
        map.put("duration", task.getDuration());
        map.put("count", task.getCount());
        map.put("seed", task.getSeed());
        map.put("style", task.getStyle());
        map.put("referenceImage", task.getReferenceImage());
        map.put("elapsedMillis", task.getElapsedMillis());
        map.put("urls", task.resultUrls());
        map.put("errorMessage", task.getErrorMessage());
        map.put("createdAt", task.getCreatedAt());
        map.put("updatedAt", task.getUpdatedAt());
        return map;
    }
}
