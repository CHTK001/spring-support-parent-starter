package com.chua.starter.ai.support.generate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.AiProviderException;
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

    /**
     * 任务多久没有推进就判定为僵死（毫秒）：5 分钟。
     *
     * <p>超时的非终态任务不会再自愈——没有任何在跑的线程会回写它，只能由服务端
     * 主动判失败。过短会误杀正在正常生成的任务（图像生成实测约 30 秒，视频可到数分钟），
     * 故取一个明显长于常见生成时长的值。</p>
     */
    private static final long STALE_TASK_MILLIS = 5 * 60 * 1000L;

    /**
     * 参考图入库长度上限（字符数，data URI 约为二进制字节数的 1.33 倍）。
     *
     * <p>前端已在浏览器里按长边 1024 缩放，正常请求到这里只有几百 KB；
     * 4M 字符 ≈ 3MB 二进制是给「绕过前端直接调接口」的兜底，
     * 超限时只放弃持久化，不影响生成本身。</p>
     */
    private static final int REFERENCE_IMAGE_MAX_CHARS = 4 * 1024 * 1024;

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
     * 参考图发布器（把 base64 参考图换成公网限时地址，供只收 URL 的协议使用）
     */
    private final ReferenceImagePublisher referenceImagePublisher;

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
        // 重启遗留：只覆盖 PENDING/RUNNING。
        // 若不限定状态，已完成的行会被无差别改写成失败——这是本方法唯一要防的破坏。
        failStale("服务重启，任务已中断");
    }

    /**
     * 把僵死任务收敛为失败。
     *
     * <p>用于两种都会让前端永久停在「排队中 / 0%」的情形：</p>
     * <ul>
     *   <li><b>服务重启</b>：本地线程池执行的任务随进程退出而中断，库里的行永远停在
     *       PENDING/RUNNING；</li>
     *   <li><b>异步提交丢失</b>：记录已入库，但提交上游的线程在回写前丢失（或提交前就异常），
     *       同样没有机会落终态。此类任务再等下去也不会有结果，因为没有任何在跑的线程会回写它。</li>
     * </ul>
     * <p>两者都无法在前端或轮询里察觉——只能查库，而查库发现「它还在非终态」时，
     * 唯一合理的处置就是判失败：与其让用户对着一个永不推进的 0% 发呆，
     * 不如明确告诉他这条没跑成。</p>
     *
     * @param reason 写入 errorMessage 的原因
     */
    private void failStale(String reason) {
        try {
            List<SysAiGenerateTask> stale = taskMapper.selectList(
                    new LambdaQueryWrapper<SysAiGenerateTask>()
                            .in(SysAiGenerateTask::getStatus,
                                    SysAiGenerateTask.STATUS_PENDING, SysAiGenerateTask.STATUS_RUNNING));
            if (stale.isEmpty()) {
                return;
            }
            for (SysAiGenerateTask task : stale) {
                SysAiGenerateTask patch = new SysAiGenerateTask();
                patch.setTaskId(task.getTaskId());
                patch.setStatus(SysAiGenerateTask.STATUS_FAILED);
                patch.setProgress(100);
                patch.setErrorMessage(reason);
                patch.setUpdatedAt(System.currentTimeMillis());
                taskMapper.updateById(patch);
            }
            log.warn("[Generate] 收敛僵死任务 {} 条：{}", stale.size(), reason);
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

        // 先把僵死任务收敛掉，再查「有没有在跑」：否则一条卡住的任务会永远占着名额，
        // 让用户既等不到结果也发不出新任务。
        failStaleBeforeSubmit();
        // 一次只允许一个未完成任务。
        // 前端禁用了发送按钮，但那只是体验；并发提交、多标签页、直接调接口都能绕开，
        // 故服务端必须自己拦一道。共用额度被打爆、结果互相穿插都源于并发。
        requireNoRunningTask(request.userId());

        SysAiGenerateTask task = buildTask(request, type);
        // 视频原先是「先提交上游、成功后才 insert」，主键由上游任务号回填；
        // 改为先落库后主键必须在 insert 之前就有，否则 sysAiGenerateTaskId 为空。
        // 这里先给一个本地占位号，submitVideoAsync 提交成功后会被上游任务号覆盖。
        if (task.getTaskId() == null || task.getTaskId().isBlank()) {
            task.setTaskId(LOCAL_SYNC_PREFIX + UUID.randomUUID());
        }
        task.setCreatedAt(System.currentTimeMillis());
        task.setUpdatedAt(task.getCreatedAt());
        // 图像与视频一律「先落库再执行」，理由与图像一致：
        // ① 让 HTTP 立即返回，前端拿得到 taskId 去轮询，不被上游十几秒的调用阻塞；
        // ② 上游失败（限流 / 队列满 / Key 失效）时记录仍然存在，用户能在历史里看到
        //    失败原因并在刷新时看到状态变化，而不是只拿到一个 500 且查无此事。
        // 视频此前是「先同步打上游、成功才 insert」，限流时一条记录都不留，与图像不一致。
        taskMapper.insert(task);
        log.info("[Generate] 任务已创建 type={} model={} taskId={} status={}",
                type, request.model(), task.getTaskId(), task.getStatus());

        if (GenerateConfigSupport.TYPE_VIDEO.equalsIgnoreCase(type)) {
            // 视频是上游异步接口，提交本身只是一次 HTTP 调用；失败回写 FAILED 即可，
            // 不必重试——重试由「刷新时回源」与「换 API Key」两条路径各自负责。
            submitVideoAsync(task, request);
            return task;
        }

        // 图像：先落库再执行，让前端能立刻拿到 taskId 轮询。
        SyncImage pending = prepareImage(task, request);
        if (pending != null) {
            // 同步实现：交线程池执行，HTTP 线程立即返回
            runImageAsync(task, pending.client(), request.prompt());
        }
        return task;
    }

    /**
     * 校验该用户没有未完成的任务，有则拒绝。
     *
     * @param userId 用户标识
     * @throws IllegalStateException 存在未完成任务时抛出
     */
    private void requireNoRunningTask(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        List<SysAiGenerateTask> running = taskMapper.selectList(
                new LambdaQueryWrapper<SysAiGenerateTask>()
                        .eq(SysAiGenerateTask::getUserId, userId)
                        .in(SysAiGenerateTask::getStatus,
                                SysAiGenerateTask.STATUS_PENDING, SysAiGenerateTask.STATUS_RUNNING)
                        .last("LIMIT 1"));
        if (running != null && !running.isEmpty()) {
            throw new IllegalStateException("已有生成中的任务，请等它完成后再提交");
        }
    }

    /**
     * 异步提交视频任务，失败回写 FAILED。
     *
     * <p>记录已先落库，故提交失败也要把状态与原因写回去——用户刷新时看到的是
     * 一条带原因的失败记录，而不是凭空消失。</p>
     *
     * @param task    任务行（已 insert）
     * @param request 生成请求
     */
    private void submitVideoAsync(SysAiGenerateTask task, Request request) {
        imageExecutor.execute(() -> {
            long startedAt = System.currentTimeMillis();
            try {
                submitVideo(task, request);
                task.setElapsedMillis(System.currentTimeMillis() - startedAt);
                task.setUpdatedAt(System.currentTimeMillis());
                taskMapper.updateById(task);
                log.info("[Generate] 视频任务已提交 taskId={} elapsed={}ms",
                        task.getTaskId(), task.getElapsedMillis());
            } catch (Exception e) {
                task.setElapsedMillis(System.currentTimeMillis() - startedAt);
                task.setStatus(SysAiGenerateTask.STATUS_FAILED);
                task.setProgress(100);
                task.setErrorMessage(e.getMessage());
                task.setUpdatedAt(System.currentTimeMillis());
                taskMapper.updateById(task);
                log.warn("[Generate] 视频任务提交失败 taskId={}: {}", task.getTaskId(), e.getMessage());
            }
        });
    }

    /**
     * 提交前兜底：把「长时间没有进展」的非终态任务判为失败。
     *
     * <p>为什么必须有这一步：一旦某条任务卡在 PENDING/RUNNING，它会一直占着
     * 「一次只能有一个在跑」的名额，让后续所有提交都被 409 拒掉——用户既等不到结果、
     * 也发不出新任务，整页等于不可用。而这种僵死在轮询里看不出破绽，因为前端能正常
     * 查到这条记录，只是它自己不再变化。</p>
     *
     * <p>判据用「创建至今已超过阈值」而不是「距上次更新超过阈值」：本地线程池任务
     * 执行期间根本不回写进度，用 updatedAt 判断会把正在正常生成的任务误杀。</p>
     */
    private void failStaleBeforeSubmit() {
        try {
            long cutoff = System.currentTimeMillis() - STALE_TASK_MILLIS;
            List<SysAiGenerateTask> stale = taskMapper.selectList(
                    new LambdaQueryWrapper<SysAiGenerateTask>()
                            .in(SysAiGenerateTask::getStatus,
                                    SysAiGenerateTask.STATUS_PENDING, SysAiGenerateTask.STATUS_RUNNING)
                            .lt(SysAiGenerateTask::getCreatedAt, cutoff));
            for (SysAiGenerateTask task : stale) {
                SysAiGenerateTask patch = new SysAiGenerateTask();
                patch.setTaskId(task.getTaskId());
                patch.setStatus(SysAiGenerateTask.STATUS_FAILED);
                patch.setProgress(100);
                patch.setErrorMessage("任务长时间没有进展，已终止，请重新提交");
                patch.setUpdatedAt(System.currentTimeMillis());
                taskMapper.updateById(patch);
            }
            if (!stale.isEmpty()) {
                log.warn("[Generate] 提交前收敛超时任务 {} 条", stale.size());
            }
        } catch (Exception e) {
            log.warn("[Generate] 提交前收敛超时任务失败: {}", e.getMessage());
        }
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
                // style 不再作为上游参数下发（见 OpenAiImageClient 注释），
                // 风格语义由前端拼进 prompt；这里保留透传以兼容仍支持该参数的实现。
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

    /**
     * 待在线程池内同步执行的图像客户端。
     *
     * <p>只作为「该 provider 不支持异步提交任务、必须本地同步出图」这一分支的返回值载体：
     * {@link #prepareImage} 捕获 {@code ImageClient#createTask} 抛出的
     * {@code UnsupportedOperationException} 后，返回非空 {@code SyncImage} 表示改走
     * {@link #runImageAsync}；返回 {@code null} 则表示任务已由 provider 原生异步受理。
     * 任务行先落库再执行，HTTP 线程不阻塞。</p>
     *
     * @param client 已完成参数装配、待在线程池内同步出图的图像客户端。取值来源为
     *               {@code clientResolver.resolveImage(request.model())} 解析出的实例，
     *               并已依次设置尺寸（按请求 size 拆分宽高）、负向提示词、风格、质量、随机种子、
     *               步数（缺省取模型配置默认值）与参考图，因此这里无需再做参数处理；
     *               后续由 {@link #runImageAsync} 调用其 {@code generate(prompt)} 同步出图。
     *               该 record 不做校验，实例生命周期由 {@link GenerateClientResolver} 管理，
     *               调用方不得提前 {@code close}；实际不会为 {@code null}（仅在
     *               {@code prepareImage} 校验失败时抛异常而非返回包装）
     */
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
     * @param offset 跳过条数，用于滚动分页
     * @return 任务列表，按创建时间倒序
     */
    public List<SysAiGenerateTask> listRecent(String userId, String type, int limit, int offset) {
        int size = Math.max(1, Math.min(limit, 200));
        int skip = Math.max(0, offset);
        LambdaQueryWrapper<SysAiGenerateTask> wrapper = new LambdaQueryWrapper<SysAiGenerateTask>()
                .eq(SysAiGenerateTask::getUserId, userId)
                .orderByDesc(SysAiGenerateTask::getCreatedAt)
                // size / skip 均为已钳制的 int，无注入面；MyBatis-Plus 不支持在 wrapper 上直接分页
                .last("LIMIT " + skip + "," + size);
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
     * 删除任务记录。
     *
     * <p>进行中的任务先收敛为取消态再删：否则行没了但上游还在算，
     * 线程回写时 {@code updateById} 命中 0 行，用户看到的是「记录消失但额度被消耗」。
     * 本地线程池任务登记进 {@link #canceledTaskIds}，让回写主动跳过。</p>
     *
     * @param userId 当前用户
     * @param taskId 任务ID
     * @return true 表示已删除
     */
    public boolean delete(String userId, String taskId) {
        SysAiGenerateTask task = taskMapper.selectById(taskId);
        // 归属校验：任务表按 sys_user_id 隔离，不能凭 taskId 删别人的记录
        if (task == null || userId == null || !userId.equals(task.getUserId())) {
            return false;
        }
        if (!isTerminal(task.getStatus())) {
            task.setStatus(SysAiGenerateTask.STATUS_CANCELED);
            task.setUpdatedAt(System.currentTimeMillis());
            taskMapper.updateById(task);
            if (isLocalSyncTask(taskId)) {
                canceledTaskIds.add(taskId);
            }
        }
        return taskMapper.deleteById(taskId) > 0;
    }

    /**
     * 批量清理失败任务。
     *
     * <p>失败记录没有产出、留着只会一直占列表，所以给一个专门的清理入口；
     * 与逐条删除一样逐条校验归属，绝不批量删别人的数据。</p>
     *
     * @param userId 当前用户
     * @return 删除条数
     */
    public int deleteFailed(String userId) {
        List<SysAiGenerateTask> failed = taskMapper.selectList(new LambdaQueryWrapper<SysAiGenerateTask>()
                .eq(SysAiGenerateTask::getUserId, userId)
                .eq(SysAiGenerateTask::getStatus, SysAiGenerateTask.STATUS_FAILED));
        int removed = 0;
        for (SysAiGenerateTask task : failed) {
            if (taskMapper.deleteById(task.getTaskId()) > 0) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * 提交视频生成任务。
     *
     * @param task    任务行
     * @param request 生成请求
     */
    private void submitVideo(SysAiGenerateTask task, Request request) {
        GenerateClientResolver.Resolved<VideoClient> first = clientResolver.resolveVideo(request.model());
        if (!first.ok()) {
            throw new IllegalStateException("该模型未配置可用的视频生成实现，请在模型管理中检查供应商与密钥");
        }
        int keyCount = clientResolver.keyCount(first.provider());
        AiProviderException last = null;
        // 遇限流换下一个 API Key 重试：模型与全部参数保持不变
        for (int i = 0; i < Math.max(1, keyCount); i++) {
            GenerateClientResolver.Resolved<VideoClient> resolved =
                    i == 0 ? first : clientResolver.resolveVideo(request.model(), i);
            if (!resolved.ok()) {
                continue;
            }
            try {
                submitVideoWith(task, request, resolved);
                return;
            } catch (AiProviderException e) {
                if (!worthRetryWithNextKey(e) || i >= keyCount - 1) {
                    throw e;
                }
                log.warn("[Generate] 视频接口被限流/鉴权拒绝，模型不变、换 API Key 重试 {} -> {}", i, i + 1);
                last = e;
            }
        }
        throw last == null ? new IllegalStateException("该模型可用的 API Key 均已失效") : last;
    }

    /**
     * 判断该失败是否值得换 API Key 重试。
     *
     * <p>只认 429/401/403：限流与 Key 失效都是<em>单 Key</em>问题，换一个 Key 就能继续，
     * 而模型不变。5xx 是供应商整体故障（换 Key 同样不通，重试只会放大流量），
     * 404 是接口不存在（换 Key 无意义），这些必须原样抛出。</p>
     *
     * @param e 上游失败异常
     * @return true 表示应换 Key 重试
     */
    private static boolean worthRetryWithNextKey(AiProviderException e) {
        Integer status = e.getStatus();
        return status != null && (status == 429 || status == 401 || status == 403);
    }

    /**
     * 用给定客户端提交视频任务。
     *
     * @param task     任务行
     * @param request  生成请求
     * @param resolved 已解析的客户端与供应商
     */
    private void submitVideoWith(SysAiGenerateTask task, Request request,
            GenerateClientResolver.Resolved<VideoClient> resolved) {
        VideoClient client = resolved.client()
                .size(splitWidth(request.size()), splitHeight(request.size()))
                .duration(request.duration())
                .negativePrompt(blankToNull(request.negativePrompt()))
                // 同图像：style 由前端拼进 prompt，这里保留透传以兼容支持的实现
                .style(blankToNull(request.style()))
                .quality(blankToNull(request.quality()))
                .seed(request.seed());
        // 这些字段必须在 createTask 之前写进任务行。
        // createTask 是唯一会抛异常的一步（限流 429 / 队列满 503 / Key 失效），
        // 写在它后面就等于「只在上游受理成功时才记录本任务的参数」——
        // 而失败恰恰是最常见的情形（实测视频行 19/19 的 size 与 duration 全为空），
        // 结果是失败卡片不显示分辨率与时长，且「再次生成」会丢掉这两个参数。
        task.setProviderId(resolved.provider() == null ? null : resolved.provider().getProviderId());
        task.setDuration(request.duration());
        task.setSize(request.size());
        byte[] reference = decodeReference(request.referenceImage());
        if (reference != null) {
            client = clientReference(client, reference);
        }
        String taskId = client.createTask(request.prompt());
        task.setTaskId(taskId);
        task.setStatus(SysAiGenerateTask.STATUS_PENDING);
    }

    /**
     * 把参考图交给视频客户端。
     *
     * <p>协议对参考图的形态要求分两类：</p>
     * <ul>
     *   <li>收<b>字节</b>的（如 {@code ZhipuVideoClient} 走 base64 字段）→ 直接给字节；</li>
     *   <li>收<b>公网可访问 URL</b>的（如 {@code AgnesVideoClient} 的 {@code first_frame}）
     *       → 先经 {@link ReferenceImagePublisher} 落到文件存储服务，换一条限时访问链接。</li>
     * </ul>
     *
     * <p>两条都拿不到时抛异常终止提交，<b>不静默降级为文生视频</b>——用户以为用上了参考图，
     * 实际生成的是无关内容并照常计费，比报错坏得多。</p>
     *
     * @param client 视频客户端
     * @param bytes  参考图字节
     * @return 装配好参考图的客户端
     */
    private VideoClient clientReference(VideoClient client, byte[] bytes) {
        if (client.supportsReferenceImageUrl()) {
            String url = referenceImagePublisher.publish(bytes, "image/jpeg");
            if (url != null) {
                return client.referenceImageUrl(url);
            }
            log.warn("[Generate] 参考图发布失败，且 {} 只接受公网地址，终止本次提交", client.getClass().getSimpleName());
            throw new IllegalStateException("图生视频需要可公网访问的参考图，当前文件存储服务不可用，请改用文生视频");
        }
        try {
            return client.referenceImage(bytes);
        } catch (UnsupportedOperationException e) {
            throw new IllegalStateException("该模型暂不支持图生视频，请改用文生视频", e);
        }
    }

    /**
     * 向 provider 刷新图像任务状态。
     *
     * @param task 任务行
     */
    private void refreshImage(SysAiGenerateTask task) {        GenerateClientResolver.Resolved<ImageClient> resolved = clientResolver.resolveImage(task.getModel());
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
        // 参考图必须落库：历史卡片靠它还原「参考图 → 箭头 → 结果」的对应关系，
        // 不存的话每条图生图记录都退化成纯文本卡，事后完全看不出用了哪张图。
        task.setReferenceImage(capReference(request.referenceImage()));
        return task;
    }

    /**
     * 参考图入库前的长度裁剪。
     *
     * <p>参考图以 data URI 原文落库。过大的原图（如 1200 万像素手机照片）会把每行撑到十几 MB，
     * 而历史列表接口每次都要把这些长字段读回来，代价与收益不成比例；
     * 超限时放弃持久化（卡片退化成纯文本卡），但生成本身不受影响，仍按内存里的字节下发给上游。</p>
     *
     * @param referenceImage data URI 或裸 base64
     * @return 可安全入库的文本；空白或超限时返回 null
     */
    private static String capReference(String referenceImage) {
        if (referenceImage == null || referenceImage.isBlank()) {
            return null;
        }
        if (referenceImage.length() > REFERENCE_IMAGE_MAX_CHARS) {
            log.warn("[Generate] 参考图 {} 字符超过上限 {}，本次不落库（卡片将不显示参考图）",
                    referenceImage.length(), REFERENCE_IMAGE_MAX_CHARS);
            return null;
        }
        return referenceImage;
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
