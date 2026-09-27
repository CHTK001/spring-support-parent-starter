package com.chua.starter.ai.support.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.image.ImageClient;
import com.chua.common.support.ai.image.ImageResponse;
import com.chua.common.support.ai.video.VideoClient;
import com.chua.common.support.ai.video.VideoResponse;
import com.chua.starter.ai.support.manage.mapper.SysAiGenerateTaskMapper;
import com.chua.starter.ai.support.manage.store.SysAiGenerateTask;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 生成任务服务：文生图 / 文生视频的任务创建、轮询与历史。
 *
 * <p>调用链：请求 → {@link GenerateClientResolver} 按模型名解析出
 * {@link ImageClient} / {@link VideoClient}（SPI 按供应商切换实现）→ 提交异步任务 →
 * 落库 {@code sys_ai_generate_task} → 前端轮询本服务查询时透传 provider 侧状态。</p>
 *
 * <p>轮询语义：{@link #query(String)} 每次都向 provider 侧查一次真实状态并回写，
 * 保证页面刷新或换设备后仍能拿到正确进度，而不是依赖服务端内存态。</p>
 *
 * <p>命名说明：本类与 {@code com.chua.starter.ai.support.service.GenerateTaskService}
 * （历史遗留的空接口）刻意不同名，避免同包外同名类型混淆。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateService {

    /** 本地线程池执行的任务号前缀：这类任务状态由本进程写库，不回源 provider */
    private static final String LOCAL_SYNC_PREFIX = "sync-";

    /** 进度阶段：线程已接手，排队完成 */
    private static final int PROGRESS_QUEUED = 5;

    /** 进度阶段：已向 provider 发出请求，等待返回（心跳维持） */
    private static final int PROGRESS_GENERATING = 30;

    /** 进度阶段：已拿到图，正在 base64 编码 */
    private static final int PROGRESS_ENCODING = 85;

    /**
     * 生成任务映射器
     */
    private final SysAiGenerateTaskMapper taskMapper;

    /**
     * 生成客户端解析器
     */
    private final GenerateClientResolver clientResolver;

    /**
     * 同步实现的图像生成线程池。
     *
     * <p>只有「provider 无异步任务口径」的模型会走这里：任务先落 RUNNING 返回，
     * 真正的生图调用放线程池，避免 HTTP 请求阻塞十几秒。
     */
    private final ExecutorService imageExecutor = createImageExecutor();

    /** 已标记取消的本地任务号：回写终态时据此跳过，避免把取消覆盖成成功 */
    private final Set<String> canceledTaskIds = ConcurrentHashMap.newKeySet();

    /**
     * 创建图像生成线程池。
     *
     * @return 固定 4 线程的守护线程池
     */
    private static ExecutorService createImageExecutor() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "ai-generate-image");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 应用关闭时回收生成线程池。
     */
    @PreDestroy
    public void shutdownImageExecutor() {
        imageExecutor.shutdown();
    }

    /**
     * 启动时收敛僵死任务。
     *
     * <p>本地线程池执行的任务随进程退出而中断，库里会留下永远 RUNNING 的记录。
     * 每次重启都要把这些行判为失败，否则前端轮询会一直停在进行中。
     *
     * <p>用 ApplicationReadyEvent 而不是 {@code @PostConstruct}：
     * 后者执行时 Flyway 迁移可能尚未完成，收敛查询会撞上缺表。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void failStaleLocalTasks() {
        try {
            List<SysAiGenerateTask> stale = taskMapper.selectList(
                    new LambdaQueryWrapper<SysAiGenerateTask>()
                            .in(SysAiGenerateTask::getStatus,
                                    SysAiGenerateTask.STATUS_PENDING, SysAiGenerateTask.STATUS_RUNNING)
                            .likeRight(SysAiGenerateTask::getTaskId, LOCAL_SYNC_PREFIX));
            if (stale.isEmpty()) {
                return;
            }
            for (SysAiGenerateTask task : stale) {
                SysAiGenerateTask patch = new SysAiGenerateTask();
                patch.setTaskId(task.getTaskId());
                patch.setStatus(SysAiGenerateTask.STATUS_FAILED);
                patch.setProgress(100);
                patch.setErrorMessage("服务重启，任务已中断");
                patch.setUpdatedAt(System.currentTimeMillis());
                taskMapper.updateById(patch);
            }
            log.warn("[Generate] 启动收敛僵死任务 {} 条（服务重启导致）", stale.size());
        } catch (Exception e) {
            log.warn("[Generate] 收敛僵死任务失败: {}", e.getMessage());
        }
    }

    /**
     * 生成请求入参
     *
     * @param type           任务类型（image / video）
     * @param model          模型名
     * @param prompt         正向提示词
     * @param negativePrompt 负面提示词
     * @param size           分辨率（WxH）
     * @param count          请求张数
     * @param duration       视频时长（秒）
     * @param seed           随机种子
     * @param style          风格
     * @param quality        质量档
     * @param steps          推理步数
     * @param referenceImage 参考图（data URL 或 base64）
     * @param userId         创建人
     */
    public record Request(
            String type,
            String model,
            String prompt,
            String negativePrompt,
            String size,
            Integer count,
            Integer duration,
            Long seed,
            String style,
            String quality,
            Integer steps,
            String referenceImage,
            String userId
    ) {
    }

    /**
     * 创建生成任务。
     *
     * @param request 生成请求
     * @return 已落库的任务行
     * @throws IllegalArgumentException 参数非法或未配置可用的生成实现
     */
    public SysAiGenerateTask create(Request request) {
        if (request == null || request.model() == null || request.model().isBlank()) {
            throw new IllegalArgumentException("请选择生成模型");
        }
        if (request.prompt() == null || request.prompt().isBlank()) {
            throw new IllegalArgumentException("请输入提示词");
        }
        String type = request.type() == null || request.type().isBlank()
                ? GenerateConfigSupport.TYPE_IMAGE : request.type();

        SysAiGenerateTask task = buildTask(request, type);
        if (GenerateConfigSupport.TYPE_VIDEO.equalsIgnoreCase(type)) {
            submitVideo(task, request);
            task.setCreatedAt(System.currentTimeMillis());
            task.setUpdatedAt(task.getCreatedAt());
            taskMapper.insert(task);
            log.info("[Generate] 任务已创建 type={} model={} taskId={}", type, request.model(), task.getTaskId());
            return task;
        }

        // 图像：先落库再执行，让前端能立刻拿到 taskId 轮询。
        // 不能沿用「生成完再 insert」——那会让 HTTP 阻塞十几秒，前端拿不到任何中间态。
        SyncImage pending = prepareImage(task, request);
        task.setCreatedAt(System.currentTimeMillis());
        task.setUpdatedAt(task.getCreatedAt());
        taskMapper.insert(task);
        log.info("[Generate] 任务已创建 type={} model={} taskId={} status={}",
                type, request.model(), task.getTaskId(), task.getStatus());

        if (pending != null) {
            // 同步实现：交线程池执行，HTTP 线程立即返回
            runImageAsync(task, pending.client(), request.prompt());
        }
        return task;
    }

    /**
     * 判断是否为本地同步执行的任务。
     *
     * @param taskId 任务ID
     * @return true 表示本地执行，不应回源
     */
    private static boolean isLocalSyncTask(String taskId) {
        return taskId != null && taskId.startsWith(LOCAL_SYNC_PREFIX);
    }

    /**
     * 图像任务准备：优先走 provider 原生异步提交；不支持则返回待执行的同步客户端。
     *
     * @param task    任务行
     * @param request 生成请求
     * @return 需要在线程池内同步生成的客户端；返回 null 表示已原生异步提交
     */
    private SyncImage prepareImage(SysAiGenerateTask task, Request request) {
        GenerateClientResolver.Resolved<ImageClient> resolved = clientResolver.resolveImage(request.model());
        if (!resolved.ok()) {
            throw new IllegalStateException("该模型未配置可用的图像生成实现，请在模型管理中检查供应商与密钥");
        }
        SysAiProviderModel modelCfg = resolved.modelCfg();
        GenerateConfigSupport.View view = GenerateConfigSupport.read(modelCfg, GenerateConfigSupport.TYPE_IMAGE);
        int count = clamp(request.count(), 1, view.maxCount());
        ImageClient client = resolved.client()
                .size(splitWidth(request.size()), splitHeight(request.size()))
                .negativePrompt(blankToNull(request.negativePrompt()))
                .style(blankToNull(request.style()))
                .quality(blankToNull(request.quality()))
                .seed(request.seed())
                .steps(request.steps() != null ? request.steps() : view.stepsDefault());
        applyReference(client, request.referenceImage());
        task.setProviderId(resolved.provider() == null ? null : resolved.provider().getProviderId());
        task.setSize(request.size());
        // 异步优先：多数国产厂商是「提交任务 + 轮询」
        try {
            task.setTaskId(client.createTask(request.prompt()));
            task.setCount(count);
            task.setStatus(SysAiGenerateTask.STATUS_PENDING);
            return null;
        } catch (UnsupportedOperationException e) {
            // 同步实现没有异步任务口径：先落 RUNNING 让前端能轮询到进度，
            // 真正生成放线程池，避免 HTTP 请求阻塞十几秒。
            task.setTaskId(LOCAL_SYNC_PREFIX + UUID.randomUUID());
            task.setCount(1);
            task.setStatus(SysAiGenerateTask.STATUS_RUNNING);
            task.setProgress(PROGRESS_QUEUED);
            return new SyncImage(client);
        }
    }

    /** 待在线程池内同步执行的图像客户端 */
    private record SyncImage(ImageClient client) {
    }

    /**
     * 异步执行同步图像生成。
     *
     * <p>进度按「真实可观测的阶段」推进，不做时间插值：
     * <ol>
     *   <li>{@link #PROGRESS_QUEUED} 线程已接手，排队完成</li>
     *   <li>{@link #PROGRESS_GENERATING} 已向 provider 发出请求，等待返回（期间心跳续 updatedAt）</li>
     *   <li>{@link #PROGRESS_ENCODING} 已拿到图，正在 base64 编码（这一步对 1024 图约需数百毫秒，是真耗时）</li>
     *   <li>100 终态</li>
     * </ol>
     * 同步 provider 的 generate() 是单次阻塞调用，内部没有更细的中间量，
     * 所以阶段之间不再细分——凭空插值出来的百分比是假进度。
     *
     * @param task   任务行
     * @param client 图像客户端
     * @param prompt 提示词
     */
    private void runImageAsync(SysAiGenerateTask task, ImageClient client, String prompt) {
        imageExecutor.execute(() -> {
            long startedAt = System.currentTimeMillis();
            // 生成期间被取消：不再回写成功，直接收敛
            if (canceledTaskIds.contains(task.getTaskId())) {
                log.info("[Generate] 任务已取消，跳过回写 taskId={}", task.getTaskId());
                return;
            }
            AtomicBoolean finished = new AtomicBoolean(false);
            // 心跳：provider 计算期间无法报进度，但要让前端看得出任务还活着
            Thread heartbeat = startHeartbeat(task, finished);
            try {
                markProgress(task, PROGRESS_GENERATING);
                BufferedImage image = client.generate(prompt);
                markProgress(task, PROGRESS_ENCODING);
                String url = imageDataUrl(image);
                task.setElapsedMillis(System.currentTimeMillis() - startedAt);
                task.setStatus(SysAiGenerateTask.STATUS_SUCCESS);
                task.setProgress(100);
                Map<String, Object> result = new LinkedHashMap<>(4);
                result.put("urls", List.of(url));
                result.put("mediaType", "image");
                task.setResult(result);
                log.info("[Generate] 图像生成完成 taskId={} elapsed={}ms",
                        task.getTaskId(), task.getElapsedMillis());
            } catch (Exception e) {
                task.setElapsedMillis(System.currentTimeMillis() - startedAt);
                task.setStatus(SysAiGenerateTask.STATUS_FAILED);
                task.setProgress(100);
                task.setErrorMessage(e.getMessage());
                log.warn("[Generate] 图像生成失败 taskId={}: {}", task.getTaskId(), e.getMessage());
            } finally {
                finished.set(true);
                if (heartbeat != null) {
                    heartbeat.interrupt();
                }
            }
            task.setUpdatedAt(System.currentTimeMillis());
            taskMapper.updateById(task);
        });
    }

    /**
     * 启动进度心跳线程：只续 updatedAt，不改 progress。
     *
     * @param task     任务行
     * @param finished 完成标记，置位后心跳退出
     * @return 心跳线程
     */
    private Thread startHeartbeat(SysAiGenerateTask task, AtomicBoolean finished) {
        Thread thread = new Thread(() -> {
            while (!finished.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (finished.get() || canceledTaskIds.contains(task.getTaskId())) {
                    return;
                }
                try {
                    SysAiGenerateTask patch = new SysAiGenerateTask();
                    patch.setTaskId(task.getTaskId());
                    patch.setUpdatedAt(System.currentTimeMillis());
                    taskMapper.updateById(patch);
                } catch (Exception e) {
                    log.debug("[Generate] 心跳续期失败 taskId={}: {}", task.getTaskId(), e.getMessage());
                }
            }
        }, "ai-generate-heartbeat");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 回写进行中任务的进度。
     *
     * @param task     任务行
     * @param progress 进度百分比
     */
    private void markProgress(SysAiGenerateTask task, int progress) {
        SysAiGenerateTask patch = new SysAiGenerateTask();
        patch.setTaskId(task.getTaskId());
        patch.setProgress(progress);
        patch.setUpdatedAt(System.currentTimeMillis());
        taskMapper.updateById(patch);
        task.setProgress(progress);
    }

    /**
     * 查询任务：向 provider 侧取实时状态并回写。
     *
     * @param taskId 任务ID
     * @return 任务行，不存在返回 null
     */
    public SysAiGenerateTask query(String taskId) {
        SysAiGenerateTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }
        // 终态不再回源，避免重复计费与无谓请求
        if (isTerminal(task.getStatus())) {
            return task;
        }
        // sync- 前缀是「同步实现 + 本地线程池执行」的任务，没有 provider 任务号可回源。
        // 必须直接返回库里的状态，否则每次轮询都会回源失败并被 catch 吞掉，
        // 任务会永远停在 RUNNING，前端看不到进度推进。
        if (isLocalSyncTask(taskId)) {
            return task;
        }
        try {
            if (GenerateConfigSupport.TYPE_VIDEO.equalsIgnoreCase(task.getType())) {
                refreshVideo(task);
            } else {
                refreshImage(task);
            }
            task.setUpdatedAt(System.currentTimeMillis());
            taskMapper.updateById(task);
        } catch (Exception e) {
            log.warn("[Generate] 刷新任务状态失败 taskId={}: {}", taskId, e.getMessage());
        }
        return task;
    }

    /**
     * 分页查询当前用户的任务历史。
     *
     * @param userId 用户ID
     * @param type   任务类型，可为空表示全部
     * @param limit  返回条数上限
     * @return 任务列表，按创建时间倒序
     */
    public List<SysAiGenerateTask> listRecent(String userId, String type, int limit) {
        LambdaQueryWrapper<SysAiGenerateTask> wrapper = new LambdaQueryWrapper<SysAiGenerateTask>()
                .eq(SysAiGenerateTask::getUserId, userId)
                .orderByDesc(SysAiGenerateTask::getCreatedAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        if (type != null && !type.isBlank()) {
            wrapper.eq(SysAiGenerateTask::getType, type);
        }
        return taskMapper.selectList(wrapper);
    }

    /**
     * 取消任务（仅标记，终态由轮询收敛）。
     *
     * @param taskId 任务ID
     * @return true 表示已标记取消
     */
    public boolean cancel(String taskId) {
        SysAiGenerateTask task = taskMapper.selectById(taskId);
        if (task == null || isTerminal(task.getStatus())) {
            return false;
        }
        task.setStatus(SysAiGenerateTask.STATUS_CANCELED);
        task.setUpdatedAt(System.currentTimeMillis());
        taskMapper.updateById(task);
        // 本地线程池任务无法真正中断，但要让回写看到取消态、不再覆盖成 SUCCESS
        if (isLocalSyncTask(taskId)) {
            canceledTaskIds.add(taskId);
        }
        return true;
    }


    /**
     * 提交视频生成任务。
     *
     * @param task    任务行
     * @param request 生成请求
     */
    private void submitVideo(SysAiGenerateTask task, Request request) {
        GenerateClientResolver.Resolved<VideoClient> resolved = clientResolver.resolveVideo(request.model());
        if (!resolved.ok()) {
            throw new IllegalStateException("该模型未配置可用的视频生成实现，请在模型管理中检查供应商与密钥");
        }
        SysAiProviderModel modelCfg = resolved.modelCfg();
        GenerateConfigSupport.View view = GenerateConfigSupport.read(modelCfg, GenerateConfigSupport.TYPE_VIDEO);
        VideoClient client = resolved.client()
                .size(splitWidth(request.size()), splitHeight(request.size()))
                .duration(request.duration())
                .negativePrompt(blankToNull(request.negativePrompt()))
                .style(blankToNull(request.style()))
                .quality(blankToNull(request.quality()))
                .seed(request.seed());
        byte[] reference = decodeReference(request.referenceImage());
        if (reference != null) {
            client = client.referenceImage(reference);
        }
        String taskId = client.createTask(request.prompt());
        task.setTaskId(taskId);
        task.setProviderId(resolved.provider() == null ? null : resolved.provider().getProviderId());
        task.setDuration(request.duration());
        task.setSize(request.size());
        task.setStatus(SysAiGenerateTask.STATUS_PENDING);
    }

    /**
     * 向 provider 刷新图像任务状态。
     *
     * @param task 任务行
     */
    private void refreshImage(SysAiGenerateTask task) {
        GenerateClientResolver.Resolved<ImageClient> resolved = clientResolver.resolveImage(task.getModel());
        if (!resolved.ok()) {
            task.setStatus(SysAiGenerateTask.STATUS_FAILED);
            task.setErrorMessage("生成实现不可用");
            return;
        }
        ImageResponse resp = resolved.client().queryTask(task.getTaskId());
        if (resp == null) {
            return;
        }
        applyStatus(task, resp.getStatus() == null ? null : resp.getStatus().name(),
                resp.getProgress(), resp.getErrorMessage());
        if (resp.getImageUrl() != null && !resp.getImageUrl().isBlank()) {
            Map<String, Object> result = new LinkedHashMap<>(4);
            result.put("urls", List.of(resp.getImageUrl()));
            result.put("mediaType", "image");
            task.setResult(result);
        }
    }

    /**
     * 向 provider 刷新视频任务状态。
     *
     * @param task 任务行
     */
    private void refreshVideo(SysAiGenerateTask task) {
        GenerateClientResolver.Resolved<VideoClient> resolved = clientResolver.resolveVideo(task.getModel());
        if (!resolved.ok()) {
            task.setStatus(SysAiGenerateTask.STATUS_FAILED);
            task.setErrorMessage("生成实现不可用");
            return;
        }
        VideoResponse resp = resolved.client().queryTask(task.getTaskId());
        if (resp == null) {
            return;
        }
        applyStatus(task, resp.getStatus() == null ? null : resp.getStatus().name(),
                resp.getProgress(), resp.getErrorMessage());
        if (resp.getVideoUrl() != null && !resp.getVideoUrl().isBlank()) {
            Map<String, Object> result = new LinkedHashMap<>(4);
            result.put("urls", List.of(resp.getVideoUrl()));
            result.put("mediaType", "video");
            task.setResult(result);
        }
    }

    /**
     * 把 provider 状态映射到本地状态并收尾。
     *
     * @param task     任务行
     * @param status   provider 状态名
     * @param progress 进度
     * @param error    失败原因
     */
    private static void applyStatus(SysAiGenerateTask task, String status, Integer progress, String error) {
        if (status != null) {
            task.setStatus(status);
        }
        if (progress != null) {
            task.setProgress(progress);
        }
        if (error != null && !error.isBlank()) {
            task.setErrorMessage(error);
        }
        // 异步实现走到终态时补记耗时：从创建到本轮轮询拿到终态
        if (isTerminal(task.getStatus())
                && task.getElapsedMillis() == null
                && task.getCreatedAt() != null
                && task.getCreatedAt() > 0) {
            task.setElapsedMillis(Math.max(1L, System.currentTimeMillis() - task.getCreatedAt()));
        }
    }

    /**
     * 构造任务行的公共字段。
     *
     * @param request 生成请求
     * @param type    任务类型
     * @return 任务行（taskId 待提交后回填）
     */
    private static SysAiGenerateTask buildTask(Request request, String type) {
        SysAiGenerateTask task = new SysAiGenerateTask();
        task.setType(type);
        task.setPrompt(request.prompt());
        task.setNegativePrompt(blankToNull(request.negativePrompt()));
        task.setModel(request.model());
        task.setSeed(request.seed());
        task.setStyle(blankToNull(request.style()));
        task.setQuality(blankToNull(request.quality()));
        task.setSteps(request.steps());
        task.setUserId(request.userId());
        return task;
    }

    /**
     * 判断是否终态。
     *
     * @param status 状态
     * @return true 表示终态
     */
    private static boolean isTerminal(String status) {
        return SysAiGenerateTask.STATUS_SUCCESS.equals(status)
                || SysAiGenerateTask.STATUS_FAILED.equals(status)
                || SysAiGenerateTask.STATUS_CANCELED.equals(status);
    }

    /**
     * 解析分辨率宽度。
     *
     * @param size 分辨率（WxH）
     * @return 宽度，无法解析返回 null
     */
    private static Integer splitWidth(String size) {
        return splitSize(size, 0);
    }

    /**
     * 解析分辨率高度。
     *
     * @param size 分辨率（WxH）
     * @return 高度，无法解析返回 null
     */
    private static Integer splitHeight(String size) {
        return splitSize(size, 1);
    }

    /**
     * 解析分辨率的指定分量。
     *
     * @param size  分辨率（WxH）
     * @param index 0=宽 1=高
     * @return 分量值，无法解析返回 null
     */
    private static Integer splitSize(String size, int index) {
        if (size == null || size.isBlank()) {
            return null;
        }
        String[] parts = size.toLowerCase().split("[x*×]");
        if (parts.length < 2) {
            return null;
        }
        try {
            return Integer.valueOf(parts[index].trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 限制张数到模型允许的区间。
     *
     * @param count 请求张数
     * @param min   下限
     * @param max   上限
     * @return 收敛后的张数
     */
    private static int clamp(Integer count, int min, int max) {
        int value = count == null ? min : count;
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 空白转 null。
     *
     * @param value 原始值
     * @return 去空后的值，空白返回 null
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 解析参考图：支持 data URL 与裸 base64。
     *
     * @param reference 参考图字符串
     * @return 字节数组；为空或非法返回 null
     */
    private static byte[] decodeReference(String reference) {
        String raw = blankToNull(reference);
        if (raw == null) {
            return null;
        }
        try {
            int comma = raw.indexOf(",");
            String payload = raw.startsWith("data:") && comma > 0 ? raw.substring(comma + 1) : raw;
            return Base64.getDecoder().decode(payload.trim());
        } catch (IllegalArgumentException e) {
            log.warn("[Generate] 参考图 base64 解析失败");
            return null;
        }
    }

    /**
     * 应用参考图：实现不支持图生图时忽略该参数，不阻断主流程。
     *
     * <p>{@code OpenAiImageClient} 等标准实现未开放 img2img，{@code referenceImage}
     * 会抛 {@link UnsupportedOperationException}；模型侧是否开放参考图由
     * {@code capabilities[].generate.referenceImage} 声明，本方法只做兜底，
     * 避免用户多传一张参考图就把整个任务打成 501。</p>
     *
     * @param client         图像客户端
     * @param referenceImage 参考图（data URL 或裸 base64）
     */
    private static void applyReference(ImageClient client, String referenceImage) {
        byte[] reference = decodeReference(referenceImage);
        if (reference == null) {
            return;
        }
        try {
            client.referenceImage(reference);
        } catch (UnsupportedOperationException e) {
            log.warn("[Generate] 当前图像实现不支持参考图，已忽略该参数");
        }
    }

    /**
     * 把同步生成的图片编码为 data URL。
     *
     * <p>前端以 {@code <img src>} 直接渲染结果，data URL 无需额外鉴权与静态资源端点；
     * 异步实现返回的是厂商公网 URL，两者在结果结构上统一为 {@code urls} 列表。</p>
     *
     * @param image 同步生成的图片
     * @return PNG 格式 data URL
     */
    private static String imageDataUrl(BufferedImage image) {
        if (image == null) {
            throw new IllegalStateException("图像实现返回了空结果");
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("生成结果编码失败: " + e.getMessage());
        }
    }

    /**
     * 汇总任务的媒体地址（供接口层使用）。
     *
     * @param task 任务行
     * @return 媒体地址列表
     */
    public static List<String> mediaUrls(SysAiGenerateTask task) {
        return task == null ? List.of() : new ArrayList<>(task.resultUrls());
    }
}
