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
     * result JSON 里存上游任务号的键。
     *
     * <p>上游任务号不能占用主键：主键在 insert 时已定并已作为响应返回前端，
     * 后续再改它，前端就会拿着一个库里不存在的 id 去轮询（卡片永远 0%，
     * 按该 id 取消还会返回 200 却什么都没取消）。放这里不需要改表结构。</p>
     */
    private static final String PROVIDER_TASK_ID_KEY = "providerTaskId";

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
    /**
     * 参考图在存储里的下标名。
     *
     * <p>与结果图共用一套「按任务号分目录、按下标命名」的落盘规则，
     * 便于整个任务被删除时一次性清理。</p>
     */
    private static final String REFERENCE_INDEX = "reference";

    /**
     * 判定「列里还存着整张图」的内容片段
     */
    private static final String INLINE_MEDIA_MARKER = "data:image";

    /**
     * 单次回填最多扫描多少行。
     *
     * <p>给个上限：一次全表扫描会把 80MB 级的 LONGTEXT 全部读进 JVM，
     * 分批执行更稳；方法幂等，重复调用即可推进到全部完成。</p>
     */
    private static final int INLINE_MEDIA_SCAN_LIMIT = 200;

    /**
     * 参考图请求原文的字符上限。
     *
     * <p>参考图现在落盘、库里只留句柄，不再受列长度限制；这个上限只用于挡住
     * 「请求原文本身大得离谱」的情况，避免绕过存储层的体积上限。</p>
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
     * 生成媒体落盘存储：把内联 base64 挡在库外，列表体积与图片大小脱钩
     */
    private final GenerateMediaStore mediaStore;

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
        // 这里先给一个本地占位号。注意它此后**不再被上游任务号覆盖**：
        // 上游号存进 result 的 providerTaskId，主键一旦落库与返回就必须保持不变。
        if (task.getTaskId() == null || task.getTaskId().isBlank()) {
            task.setTaskId(LOCAL_SYNC_PREFIX + UUID.randomUUID());
        }
        // 参考图落盘：库里只留句柄，不再把整张图的 base64 塞进参考图列。
        // 必须在此处做而不是 buildTask 里——句柄要按任务号分目录，而任务号刚定下来。
        task.setReferenceImage(persistReference(task.getTaskId(), request.referenceImage()));
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
            // 排队态要落库。prepareImage 只把 PROGRESS_QUEUED 写进了内存对象，
            // 而这一行早已 insert 完毕，于是前端第一眼看到的就是「模型生成中 30%」——
            // 看不到「已入队」。这一步是真实可观测的（线程还没接手），因此补一次持久化。
            markProgress(task, PROGRESS_QUEUED);
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
     * @param task 任务行
     * @return true 表示本地执行，不应回源
     */
    private static boolean isLocalSyncTask(SysAiGenerateTask task) {
        // 判据从「主键是否带 sync- 前缀」换成「有没有上游任务号」。
        // 用前缀判断曾经是可行的，因为只有异步受理成功的行才会把主键改成上游号；
        // 现在主键一律不再改动，前缀对每一行都成立，用它判断会让所有任务都不再回源。
        // 没有上游任务号就是没有可回源的对象，这才是真正要表达的意思。
        return task != null && providerTaskIdOf(task) == null;
    }

    /**
     * 记录上游任务号。
     *
     * <p>写进 result JSON 的同名键，而不是主键——主键在 insert 时已定并已返回前端，
     * 任何后续改动都会让前端拿着一个库里不存在的 id 去轮询。选择 result 是因为
     * 它已经是这一行的自由字段，不需要改表结构。</p>
     *
     * @param task       任务行
     * @param providerId 上游任务号
     */
    private static void putProviderTaskId(SysAiGenerateTask task, String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return;
        }
        Map<String, Object> result = task.getResult();
        if (result == null) {
            result = new LinkedHashMap<>(4);
            task.setResult(result);
        }
        result.put(PROVIDER_TASK_ID_KEY, providerId);
    }

    /**
     * 读取上游任务号。
     *
     * @param task 任务行
     * @return 上游任务号；无从回源时返回 null
     */
    private static String providerTaskIdOf(SysAiGenerateTask task) {
        if (task == null || task.getResult() == null) {
            return null;
        }
        Object value = task.getResult().get(PROVIDER_TASK_ID_KEY);
        return value == null ? null : value.toString();
    }

    /**
     * 把存量行里的内联 base64 回填到磁盘。
     *
     * <p>落盘改造只对新行生效，改造前入库的 32 条结果行与 4 条参考图行仍把整张图
     * 存在 LONGTEXT 里（实测 result 83.2MB + reference 9.1MB），只读的历史列表
     * 每次都要把页内这些行读回来——列表慢的账全在这里，不回填就等于没修。</p>
     *
     * <p><b>顺序保证不丢数据</b>：先写文件、确认写成功，再把库里那一项换成句柄。
     * 写失败的行原样保留 base64，宁可它继续重，也不能出现「库里的值与磁盘上的文件
     * 两边都没有」的状态。因此本方法是幂等的，可以反复执行，中断后重跑会跳过已完成的行。</p>
     *
     * @param dryRun 只清点不写库，用于先看清会影响多少行
     * @return 统计：扫描行数、已转换项数、跳过项数、失败项数、影响行数
     */
    public Map<String, Object> backfillInlineMedia(boolean dryRun) {
        List<String> ids = taskMapper.selectIdsWithInlineMedia(INLINE_MEDIA_MARKER, INLINE_MEDIA_SCAN_LIMIT);
        List<SysAiGenerateTask> candidates = new ArrayList<>(ids.size());
        for (String id : ids) {
            SysAiGenerateTask task = taskMapper.selectById(id);
            if (task != null) {
                candidates.add(task);
            }
        }
        int scanned = candidates.size();
        int converted = 0;
        int skipped = 0;
        int failed = 0;
        int touchedRows = 0;
        for (SysAiGenerateTask task : candidates) {
            boolean changed = false;
            List<String> raw = task.resultUrls();
            if (!raw.isEmpty()) {
                List<String> stored = new ArrayList<>(raw.size());
                for (int i = 0; i < raw.size(); i++) {
                    String url = raw.get(i);
                    if (!GenerateMediaStore.isInlineData(url)) {
                        stored.add(url);
                        skipped++;
                        continue;
                    }
                    if (dryRun) {
                        stored.add(url);
                        converted++;
                        changed = true;
                        continue;
                    }
                    String handle = mediaStore.save(task.getTaskId(), String.valueOf(i), url);
                    if (handle == null) {
                        stored.add(url);
                        failed++;
                        continue;
                    }
                    stored.add(handle);
                    converted++;
                    changed = true;
                }
                if (changed && !dryRun) {
                    putResult(task, stored, String.valueOf(task.getResult().get("mediaType")));
                }
            }
            String reference = task.getReferenceImage();
            if (GenerateMediaStore.isInlineData(reference)) {
                if (dryRun) {
                    converted++;
                    changed = true;
                } else {
                    // 同一任务重复回填时直接覆盖同名文件，不先删——
                    // 先删后写会在两次操作之间留下「文件没了、库里还是 base64」的空窗
                    String handle = mediaStore.save(task.getTaskId(), REFERENCE_INDEX, reference);
                    if (handle == null) {
                        failed++;
                    } else {
                        task.setReferenceImage(handle);
                        converted++;
                        changed = true;
                    }
                }
            }
            if (changed) {
                touchedRows++;
                if (!dryRun) {
                    task.setUpdatedAt(System.currentTimeMillis());
                    taskMapper.updateById(task);
                }
            }
        }
        Map<String, Object> report = new LinkedHashMap<>(8);
        report.put("dryRun", dryRun);
        report.put("candidateRows", scanned);
        report.put("touchedRows", touchedRows);
        report.put("convertedItems", converted);
        report.put("skippedItems", skipped);
        report.put("failedItems", failed);
        log.warn("[Generate] 存量媒体回填 dryRun={} 候选 {} 行，影响 {} 行，转换 {} 项，跳过 {} 项，失败 {} 项",
                dryRun, scanned, touchedRows, converted, skipped, failed);
        return report;
    }

    /**
     * 写回结果，同时保住上游任务号。
     *
     * <p>结果地图在生成完成与回源刷新两处会被整体重建。若重建时直接
     * {@code new LinkedHashMap} 放 urls，上游任务号会被静默抹掉，
     * 下一次回源就查不到自己的上游任务——表现为任务卡在非终态再不动。
     * 故所有结果写入都必须经此处。</p>
     *
     * @param task      任务行
     * @param urls      媒体地址列表
     * @param mediaType 媒体类型
     */
    private void putResult(SysAiGenerateTask task, List<String> urls, String mediaType) {
        String providerId = providerTaskIdOf(task);
        Map<String, Object> result = new LinkedHashMap<>(4);
        if (providerId != null) {
            result.put(PROVIDER_TASK_ID_KEY, providerId);
        }
        result.put("urls", persistUrls(task, urls));
        result.put("mediaType", mediaType);
        task.setResult(result);
    }

    /**
     * 把内联 base64 换成本存储句柄，其余值原样保留。
     *
     * <p>落盘失败时退回存原文：宁可这一行像改造前一样重，也不能让已经生成出来的产物
     * 在库里凭空消失——列表变慢是可以接受的，丢结果不是。</p>
     *
     * @param task 任务行
     * @param urls 媒体值列表
     * @return 可入库的媒体值列表
     */
    private List<String> persistUrls(SysAiGenerateTask task, List<String> urls) {
        List<String> stored = new ArrayList<>(urls.size());
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            if (!GenerateMediaStore.isInlineData(url)) {
                stored.add(url);
                continue;
            }
            String handle = mediaStore.save(task.getTaskId(), String.valueOf(i), url);
            stored.add(handle == null ? url : handle);
        }
        return stored;
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
        // 异步优先：多数厂商是「提交任务 + 轮询」
        try {
            // 上游任务号另存，绝不写回主键：主键在 insert 时就已定下并已返回给前端，
            // 再改它等于让前端拿着一个库里不存在的 id 去轮询（表现为卡片永远 0%）。
            putProviderTaskId(task, client.createTask(request.prompt()));
            task.setCount(count);
            task.setStatus(SysAiGenerateTask.STATUS_PENDING);
            return null;
        } catch (UnsupportedOperationException e) {
            // 同步实现没有异步任务口径：先落 RUNNING 让前端能轮询到进度，
            // 真正生成放线程池，避免 HTTP 请求阻塞十几秒。
            // 这里不再另起一个 UUID 覆盖主键 —— 主键已落库、已返回，改了就是同一个 bug。
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
     * 判断该任务此刻是否已被取消。
     *
     * <p>两处取或，缺一不可：</p>
     * <ul>
     *   <li><b>本进程的取消登记</b>：同一代次内取消得最快，不必回库；</li>
     *   <li><b>库里当前状态</b>：覆盖「进程重启后才取消」「被别的路径（删除/批量删除）取消」，
     *       这些情况下内存集合里没有它。行已被删除也视为取消——
     *       用户把记录删了，不该让一个已完成的 worker 把它复活。</li>
     * </ul>
     *
     * @param task 任务行
     * @return true 表示已被取消或记录已不存在
     */
    private boolean isCanceledNow(SysAiGenerateTask task) {
        if (canceledTaskIds.contains(task.getTaskId())) {
            return true;
        }
        SysAiGenerateTask fresh = taskMapper.selectById(task.getTaskId());
        return fresh == null || SysAiGenerateTask.STATUS_CANCELED.equals(fresh.getStatus());
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
                // 末态回写前必须再确认一次是否已被取消。
                // 只在开工前查一次是不够的：generate() 会阻塞十几秒，用户在这期间点取消，
                // 下面那句 setStatus(SUCCESS) 会把 CANCELED 冲成成功——实测「已取消」的记录
                // 15 秒后自己变成 SUCCESS，于是「清理失败」点下去只能报「没有可清理的记录」。
                if (isCanceledNow(task)) {
                    log.info("[Generate] 任务在生成期间被取消，丢弃结果 taskId={}", task.getTaskId());
                    return;
                }
                task.setElapsedMillis(System.currentTimeMillis() - startedAt);
                task.setStatus(SysAiGenerateTask.STATUS_SUCCESS);
                task.setProgress(100);
                putResult(task, List.of(url), "image");
                log.info("[Generate] 图像生成完成 taskId={} elapsed={}ms",
                        task.getTaskId(), task.getElapsedMillis());
            } catch (Exception e) {
                // 取消同样不该被失败冲掉：用户主动停的任务保持取消态，重试由用户决定
                if (isCanceledNow(task)) {
                    log.info("[Generate] 任务已取消，失败原因不再回写 taskId={}: {}", task.getTaskId(), e.getMessage());
                    return;
                }
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
        // 没有上游任务号就没有可回源的对象（同步实现 + 本地线程池执行）。
        // 必须直接返回库里的状态，否则每次轮询都会回源失败并被 catch 吞掉，
        // 任务会永远停在 RUNNING，前端看不到进度推进。
        if (isLocalSyncTask(task)) {
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
        if (isLocalSyncTask(task)) {
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
            if (isLocalSyncTask(task)) {
                canceledTaskIds.add(taskId);
            }
        }
        boolean removed = taskMapper.deleteById(taskId) > 0;
        if (removed) {
            // 记录删了、磁盘上的图片也要跟着删，否则留下没有归属的字节
            mediaStore.deleteAll(taskId);
        }
        return removed;
    }

    /**
     * 批量删除指定任务（勾选后删除）。
     *
     * <p>逐条校验归属：只删属于当前用户的行，不属于的静默跳过而不是报错——
     * 前端可能拿着一个过期的选择集（其间记录已被别处删掉或换了归属），
     * 为其中一条失败把整批回滚反而更差。有产物的行一并清磁盘。</p>
     *
     * @param userId  当前用户
     * @param taskIds 待删除的任务号
     * @return 实际删除条数
     */
    public int deleteBatch(String userId, List<String> taskIds) {
        if (userId == null || taskIds == null || taskIds.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (String taskId : taskIds) {
            if (taskId == null || taskId.isBlank()) {
                continue;
            }
            SysAiGenerateTask task = taskMapper.selectById(taskId);
            if (task == null || !userId.equals(task.getUserId())) {
                continue;
            }
            if (!isTerminal(task.getStatus())) {
                // 进行中的先收敛为取消态，避免「记录没了但上游还在算」
                task.setStatus(SysAiGenerateTask.STATUS_CANCELED);
                task.setUpdatedAt(System.currentTimeMillis());
                taskMapper.updateById(task);
                if (isLocalSyncTask(task)) {
                    canceledTaskIds.add(taskId);
                }
            }
            if (taskMapper.deleteById(taskId) > 0) {
                mediaStore.deleteAll(taskId);
                removed++;
            }
        }
        return removed;
    }

    /**
     * 清空当前用户的全部任务记录。
     *
     * <p>这是一个不可恢复的破坏性操作（会删掉已生成的作品），
     * 归属校验逐条做，绝不跨用户批量删。前端要求输入确认词后才调用。</p>
     *
     * @param userId 当前用户
     * @return 实际删除条数
     */
    public int deleteAll(String userId) {
        if (userId == null) {
            return 0;
        }
        List<SysAiGenerateTask> mine = taskMapper.selectList(new LambdaQueryWrapper<SysAiGenerateTask>()
                .eq(SysAiGenerateTask::getUserId, userId));
        int removed = 0;
        for (SysAiGenerateTask task : mine) {
            if (taskMapper.deleteById(task.getTaskId()) > 0) {
                mediaStore.deleteAll(task.getTaskId());
                removed++;
            }
        }
        log.warn("[Generate] 清空全部记录 userId={} 删除 {} 条", userId, removed);
        return removed;
    }

    /**
     * 批量清理失败与已取消的任务。
     *
     * <p>这两类都没有产出、留着只会一直占列表，所以给一个专门的清理入口；
     * 与逐条删除一样逐条校验归属，绝不批量删别人的数据。</p>
     *
     * <p><b>为什么必须一起清 CANCELED。</b>前端那个按钮的徽标统计的是
     * FAILED + CANCELED，标题也写着「清理失败与已取消的记录」；此前这里只筛 FAILED，
     * 于是当页内剩余的都是取消记录时，徽标明明显示有 N 条，点下去却回
     * 「没有可清理的记录」——数字与行为对不上，用户会以为功能坏了。实测复现：
     * 当页 SUCCESS=17 / CANCELED=5 / FAILED=0，徽标 5，可删 0。</p>
     *
     * @param userId 当前用户
     * @return 删除条数
     */
    public int deleteFailed(String userId) {
        List<SysAiGenerateTask> voided = taskMapper.selectList(new LambdaQueryWrapper<SysAiGenerateTask>()
                .eq(SysAiGenerateTask::getUserId, userId)
                .in(SysAiGenerateTask::getStatus,
                        SysAiGenerateTask.STATUS_FAILED, SysAiGenerateTask.STATUS_CANCELED));
        int removed = 0;
        for (SysAiGenerateTask task : voided) {
            if (taskMapper.deleteById(task.getTaskId()) > 0) {
                // 失败记录一般没有产物，但视频失败行仍可能留有已落盘的参考图，一并清理
                mediaStore.deleteAll(task.getTaskId());
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
        // 同图像：上游任务号另存，主键自 insert 起不再改动
        putProviderTaskId(task, taskId);
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
        ImageResponse resp = resolved.client().queryTask(providerTaskIdOf(task));
        if (resp == null) {
            return;
        }
        applyStatus(task, resp.getStatus() == null ? null : resp.getStatus().name(),
                resp.getProgress(), resp.getErrorMessage());
        if (resp.getImageUrl() != null && !resp.getImageUrl().isBlank()) {
            putResult(task, List.of(resp.getImageUrl()), "image");
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
        VideoResponse resp = resolved.client().queryTask(providerTaskIdOf(task));
        if (resp == null) {
            return;
        }
        applyStatus(task, resp.getStatus() == null ? null : resp.getStatus().name(),
                resp.getProgress(), resp.getErrorMessage());
        if (resp.getVideoUrl() != null && !resp.getVideoUrl().isBlank()) {
            putResult(task, List.of(resp.getVideoUrl()), "video");
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
        // 参考图不在此处落库：句柄要按任务号分目录，而任务号在 create 里才定下来，
        // 由 create 调 persistReference 写入。这里留空。
        return task;
    }

    /**
     * 参考图落盘并返回句柄。
     *
     * <p>改造前参考图以 data URI 原文落库，一张 1200 万像素手机照片就能把单行撑到十几 MB，
     * 而历史列表每次都要把它读回来。这里只保留字节在磁盘上的句柄，
     * 列表因此与参考图大小无关；字节仍按需经 {@code /reference} 吐出，前端无需改动。</p>
     *
     * <p>落盘失败或超限时返回 null（卡片退化成纯文本卡），但生成本身不受影响：
     * 下发给上游的仍是请求里的原始字节，与持久化无关。</p>
     *
     * @param taskId         任务号
     * @param referenceImage data URI 或裸 base64
     * @return 句柄；无参考图或落盘失败时返回 null
     */
    private String persistReference(String taskId, String referenceImage) {
        String capped = capReference(referenceImage);
        if (capped == null) {
            return null;
        }
        if (!GenerateMediaStore.isInlineData(capped)) {
            // 裸 base64（无 data: 前缀）：补一个自述头再走同一条落盘路径
            return mediaStore.save(taskId, REFERENCE_INDEX, "data:image/png;base64," + capped);
        }
        return mediaStore.save(taskId, REFERENCE_INDEX, capped);
    }

    /**
     * 参考图入库前的长度裁剪。
     *
     * <p>参考图现在落盘、库里只留句柄，不再受列长度限制；本方法仅在
     * 「请求原文本身大得离谱」时兜底拒绝持久化，避免绕过存储层的体积上限。
     * 超限时放弃持久化（卡片退化成纯文本卡），但生成本身不受影响，
     * 仍按内存里的字节下发给上游。</p>
     *
     * @param referenceImage data URI 或裸 base64
     * @return 可安全处理的文本；空白或超限时返回 null
     */
    private static String capReference(String referenceImage) {
        if (referenceImage == null || referenceImage.isBlank()) {
            return null;
        }
        if (referenceImage.length() > REFERENCE_IMAGE_MAX_CHARS) {
            log.warn("[Generate] 参考图 {} 字符超过上限 {}，本次不落盘（卡片将不显示参考图）",
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
