package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.AiProviderException;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.generate.GenerateConfigSupport;
import com.chua.starter.ai.support.generate.GenerateMediaStore;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
         *
         * <p>前端已在浏览器里按长边缩放过，这里收到的是缩放后的图，
         * 用于减少请求体与落库体积；图生图只需看清构图，不影响质量。</p>
         */
        private String referenceImage;

        /**
         * 参考图的原始像素宽（缩放前）
         *
         * <p>缩放后的 data URL 里已经没有原始尺寸，而历史卡片要按原比例渲染，
         * 所以由前端单独带上。缺省时按缩放后图的比例兜底。</p>
         */
        private Integer referenceWidth;

        /**
         * 参考图的原始像素高（缩放前）
         */
        private Integer referenceHeight;
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
            GenerateService.Request request = new GenerateService.Request(
                    body.getType(), body.getModel(), body.getPrompt(), body.getNegativePrompt(),
                    body.getSize(), body.getCount(), body.getDuration(), body.getSeed(),
                    body.getStyle(), body.getQuality(), body.getSteps(), body.getReferenceImage(), userId);
            SysAiGenerateTask task = generateService.create(request);
            // 原始尺寸只在响应里回带，不落库：它只服务于「渲染还原比例」，
            // 存成两个数字列不值当，而落进参考图列会与缩放后的图对不上。
            Map<String, Object> view = toView(task);
            if (task.getReferenceImage() != null) {
                if (body.getReferenceWidth() != null) {
                    view.put("referenceWidth", body.getReferenceWidth());
                }
                if (body.getReferenceHeight() != null) {
                    view.put("referenceHeight", body.getReferenceHeight());
                }
            }
            return ReturnResult.success(view);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ApiException(ApiErrorCode.NO_COMPLETER, e.getMessage());
        } catch (AiProviderException e) {
            // 供应商侧的失败（限流 / 队列满 / 鉴权 / 不支持）必须把上游原文透出去：
            // 这两类都是用户能据此行动的（等额度恢复、稍后重试、换 Key），
            // 压成「系统繁忙」等于让用户面对一句废话。
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
     * 删除单条生成记录。
     *
     * <p>进行中的任务会先收敛为取消态再删除：直接删会让行消失但上游仍在计算，
     * 用户看到的是「记录没了、额度却花了」。归属在服务层校验，非本人记录一律不删。</p>
     *
     * @param taskId 任务ID
     * @return 是否删除成功
     */
    @DeleteMapping("/tasks/{taskId}")
    @Operation(summary = "删除生成记录")
    public ReturnResult<Boolean> delete(@PathVariable String taskId) {
        String userId = AiUserContext.requireUserId();
        return ReturnResult.success(generateService.delete(userId, taskId));
    }

    /**
     * 清理当前用户的全部失败记录。
     *
     * <p>失败记录没有产出、留着只会持续占列表。逐条按归属删除，不做跨用户批量操作。</p>
     *
     * @return 删除条数
     */
    @DeleteMapping("/tasks/failed")
    @Operation(summary = "清理失败记录")
    public ReturnResult<Integer> deleteFailed() {
        String userId = AiUserContext.requireUserId();
        return ReturnResult.success(generateService.deleteFailed(userId));
    }

    /**
     * 生成任务历史。
     *
     * @param type   任务类型，可为空
     * @param limit  条数上限
     * @param offset 跳过条数，用于滚动分页
     * @return 任务视图列表
     */
    @GetMapping("/tasks")
    @Operation(summary = "生成任务历史")
    public ReturnResult<List<Map<String, Object>>> history(
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "30") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {
        String userId = AiUserContext.requireUserId();
        List<SysAiGenerateTask> tasks = generateService.listRecent(userId, type, limit, offset);
        return ReturnResult.success(tasks.stream().map(GenerateController::toView).toList());
    }

    /**
     * 批量删除指定任务（勾选后删除）。
     *
     * @param body 待删除的任务号列表
     * @return 实际删除条数
     */
    @PostMapping("/tasks/batch-delete")
    @Operation(summary = "批量删除生成记录")
    public ReturnResult<Integer> batchDelete(@RequestBody BatchDeleteBody body) {
        String userId = AiUserContext.requireUserId();
        return ReturnResult.success(generateService.deleteBatch(userId, body == null ? null : body.getTaskIds()));
    }

    /**
     * 清空当前用户的全部生成记录。
     *
     * <p>不可恢复，会连已生成的作品一起删；前端须在用户输入确认词后才调用。</p>
     *
     * @return 实际删除条数
     */
    @DeleteMapping("/tasks/all")
    @Operation(summary = "清空全部生成记录")
    public ReturnResult<Integer> deleteAll() {
        String userId = AiUserContext.requireUserId();
        return ReturnResult.success(generateService.deleteAll(userId));
    }

    /**
     * 批量删除的请求体。
     */
    @Data
    public static class BatchDeleteBody {

        /**
         * 待删除的任务号
         */
        private List<String> taskIds;
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
        map.put("styles", view.styles());
        map.put("style", view.style());
        return map;
    }

    /**
     * 任务行转前端视图。
     *
     * <p><b>内联 base64 一律换成可寻址路径。</b>本方法同时服务「创建响应」「单任务查询」
     * 与「历史列表」三个出口，历史上把 {@code referenceImage} 与 {@code urls} 原样吐出，
     * 于是只读的历史列表会带着几十 MB 纯文本回来（实测 30 条 = 77.5MB / 5.2 秒，
     * 全部来自 1 条带 28 张图 + 1 张 2.38MB 参考图的记录）。改成返回
     * {@code /v2/ai/generate/tasks/{taskId}/media/{i}} 后，列表只带元数据，
     * 字节流由 {@link GenerateMediaController} 在 {@code <img>} 真正要显示时才吐一次。</p>
     *
     * <p><b>「怎么区分」在服务端做完。</b>判据只有一条：内联 base64 以
     * {@code data:} 开头（见 {@link GenerateMediaController#isInline}），
     * 供应商本来就给的 http/https 地址原样透传。两种情况对前端都是
     * 「一个字符串塞进 img.src」，浏览器都能加载，所以前端不需要判断、不需要分支。</p>
     *
     * <p>路径是相对路径（不含主机名）：开发态前端与后端不同源，生产态经 nginx 同源，
     * 两种拓扑下都由前端按自己的 API 基址补全，写死主机名反而会在其中一种下出错。</p>
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
        map.put("referenceImage", referenceView(task));
        map.put("elapsedMillis", task.getElapsedMillis());
        map.put("urls", mediaView(task));
        map.put("errorMessage", task.getErrorMessage());
        map.put("createdAt", task.getCreatedAt());
        map.put("updatedAt", task.getUpdatedAt());
        return map;
    }

    /**
     * 参考图转前端视图：内联的换成可寻址路径，其余原样。
     *
     * <p>「再次生成」会把这里的值回填进提交参数，所以前端拿到路径后需要
     * 先按地址取回字节再转成 data URI，见前端 {@code regenerate} 的处理。</p>
     *
     * @param task 任务行
     * @return 参考图地址，无参考图时返回 null
     */
    private static String referenceView(SysAiGenerateTask task) {
        String raw = task.getReferenceImage();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return GenerateMediaStore.isStored(raw)
                ? GenerateMediaController.INLINE_PATH_PREFIX + task.getTaskId() + "/reference"
                : raw;
    }

    /**
     * 结果地址列表转前端视图：逐项判断，内联的换成可寻址路径，其余原样。
     *
     * <p>下标必须与库里 {@code urls} 的位置严格一致，因为
     * {@code /media/{i}} 就是按下标取字节的；这里按下标替换而不是过滤，
     * 正是为了不打乱对应关系（过滤会让后面所有图错位一张）。</p>
     *
     * @param task 任务行
     * @return 结果地址列表
     */
    private static List<String> mediaView(SysAiGenerateTask task) {
        List<String> raw = task.resultUrls();
        if (raw.isEmpty()) {
            return raw;
        }
        List<String> view = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String item = raw.get(i);
            view.add(GenerateMediaStore.isStored(item)
                    ? GenerateMediaController.INLINE_PATH_PREFIX + task.getTaskId() + "/media/" + i
                    : item);
        }
        return view;
    }
}
