package com.chua.starter.eduai.service.impl;

import com.chua.starter.common.support.exception.BusinessException;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.ai.EduPaperScanner;
import com.chua.starter.eduai.model.EduMarkingTask;
import com.chua.starter.eduai.mapper.EduMarkingTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chua.starter.eduai.configuration.EduAiProperties;
import com.chua.starter.eduai.mapper.EduAnswerSheetMapper;
import com.chua.starter.eduai.mapper.EduExamMapper;
import com.chua.starter.eduai.mapper.EduMarkingResultMapper;
import com.chua.starter.eduai.mapper.EduPaperQuestionMapper;
import com.chua.starter.eduai.mapper.EduQuestionMapper;
import com.chua.starter.eduai.model.EduAnswerSheet;
import com.chua.starter.eduai.model.EduExam;
import com.chua.starter.eduai.model.EduMarkingResult;
import com.chua.starter.eduai.model.EduPaperQuestion;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.MarkingConfirmDTO;
import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;
import com.chua.starter.eduai.model.vo.MarkingResultVO;
import com.chua.starter.eduai.queue.MarkingJob;
import com.chua.starter.eduai.queue.MarkingMessageListener;
import com.chua.starter.eduai.service.IEduMarkingService;
import com.chua.starter.eduai.service.IEduWrongQuestionService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.queue.MessageTemplate;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

/**
 * 阅卷服务实现
 *
 * <p>同步路径（{@link #submitAnswerSheet}）：
 * <ol>
 *   <li>持久化 {@code edu_answer_sheet} 答题卡</li>
 *   <li>生成 taskId，写入 {@code edu_marking_result} 状态 = PENDING</li>
 *   <li>异步路径（{@link #markAsync}）通过 {@link MessageTemplate} 派发到
 *       {@code edu.marking} 队列，由 {@link MarkingMessageListener} 异步消费</li>
 *   <li>同步路径立即执行阅卷并把结果落库</li>
 * </ol>
 *
 * <p>队列消费由 {@link MessageTemplate} Bean 驱动（默认实现来自 queue-starter 的 LocalMessageTemplate），
 * 失败时记录错误到 {@code ai_comment} 列，前端轮询可观察到。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduMarkingServiceImpl implements IEduMarkingService {

    /**
     * 阅卷队列名称。
     */
    public static final String QUEUE_NAME = "edu.marking";

    /**
     * 阅卷结果状态：待批改
     */
    private static final int STATUS_PENDING = 0;
    /**
     * 阅卷结果状态：AI 已批改
     */
    private static final int STATUS_AI_DONE = 1;
    /**
     * 阅卷结果状态：教师已确认
     */
    private static final int STATUS_TEACHER_DONE = 2;

    /**
     * 题号里的首个数字（用于把「第1题」「1.」「(2)」这类写法折算成卷内顺序）
     */
    private static final java.util.regex.Pattern FIRST_DIGITS = java.util.regex.Pattern.compile("\\d+");

    /**
     * Answersheetmapper
    */
    private final EduAnswerSheetMapper answerSheetMapper;
    /**
     * Marking结果映射器
    */
    private final EduMarkingResultMapper markingResultMapper;
    /**
     * Questionmapper
    */
    private final EduQuestionMapper questionMapper;
    /**
     * 考试映射器
    */
    private final EduExamMapper examMapper;
    /**
     * 试卷题目关联映射器
    */
    private final EduPaperQuestionMapper paperQuestionMapper;
    /**
     * EDUAI服务
    */
    private final EduAiService eduAiService;
    /**
     * 属性
    */
    private final EduAiProperties properties;
    /**
     * 消息模板
    */
    private final MessageTemplate messageTemplate;
    /**
     * 错题服务
    */
    private final IEduWrongQuestionService wrongQuestionService;
    /**
     * 试卷扫描器（版面还原 + 文字提取 + 结构化，内部复用 ai-starter 全局 Bean）
     */
    private final EduPaperScanner paperScanner;
    /**
     * 阅卷任务映射器（异步任务的状态载体，支撑重启续跑）
     */
    private final EduMarkingTaskMapper taskMapper;

    /**
     * JSON 解析器（还原识别详情，供判分与续跑使用）
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 提交answersheet
     *
     * @param dto dto
     * @return submitAnswerSheet的结果
     */
    public String submitAnswerSheet(MarkingSubmitDTO dto) {
        // 1. 持久化答题卡
        EduAnswerSheet sheet = new EduAnswerSheet();
        sheet.setOwnerUserId(EduUserContext.requireUserId());
        sheet.setAnswerSheetExamId(dto.getExamId());
        sheet.setAnswerSheetStudentId(dto.getStudentId());
        sheet.setAnswerSheetContent(dto.getContent());
        sheet.setAnswerSheetSubmitTime(LocalDateTime.now());
        sheet.setAnswerSheetStatus(1); // 已提交
        sheet.setCreateTime(LocalDateTime.now());
        sheet.setUpdateTime(LocalDateTime.now());
        answerSheetMapper.insert(sheet);

 // 2. 生成 任务标识 并预占 marking_结果
        String taskId = UUID.randomUUID().toString().replace("-", "");
        for (MarkingSubmitDTO.AnswerItem item : dto.getAnswers()) {
            EduMarkingResult result = new EduMarkingResult();
            result.setOwnerUserId(sheet.getOwnerUserId());
            result.setMarkingResultAnswerSheetId(sheet.getAnswerSheetId());
            result.setMarkingResultQuestionId(item.getQuestionId());
            result.setMarkingResultTaskId(taskId);
            result.setMarkingResultStatus(STATUS_PENDING);
            result.setCreateTime(LocalDateTime.now());
            result.setUpdateTime(LocalDateTime.now());
            markingResultMapper.insert(result);
        }

        if (properties.getMarking().isAsync()) {
            /**
             * dispatch异步。
             * @param taskId 任务标识
             * @param sheet.getAnswerSheetId( sheet.获取answersheetid(
             */
            dispatchAsync(taskId, sheet.getAnswerSheetId(), dto.getAnswers());
        } else {
            /**
             * mark同步。
             * @param taskId 任务标识
             * @param dto.getAnswers( dto.获取answers(
             */
            markSync(taskId, dto.getAnswers());
        }

        log.info("[EduAi] 答题卡已提交: taskId={}, student={}, async={}",
                taskId, dto.getStudentId(), properties.getMarking().isAsync());
        return taskId;
    }

    /**
     * 把阅卷任务投递到队列，由 {@link MarkingMessageListener} 异步消费。
     * @param taskId 任务标识
     * @param answerSheetId answersheetid
     * @param answers answers
     */
    private void dispatchAsync(String taskId, Long answerSheetId, List<MarkingSubmitDTO.AnswerItem> answers) {
        MarkingJob job = new MarkingJob();
        job.setTaskId(taskId);
        job.setAnswerSheetId(answerSheetId);
        job.setItems(answers);
        try {
            Map<String, Object> headers = new HashMap<>();
            headers.put("submittedAt", System.currentTimeMillis());
            messageTemplate.sendAsync(QUEUE_NAME, job, headers);
        } catch (Exception e) {
            log.error("[EduAi] 队列投递失败，回退到同步阅卷: taskId={}, err={}", taskId, e.getMessage(), e);
            /**
             * mark同步。
             * @param taskId 任务标识
             * @param answers answers
             */
            markSync(taskId, answers);
        }
    }

    /**
     * 逐题批改并落库，同步分支与队列消费线程共用。
     *
     * <p>每题满分取卷面分值（{@code edu_paper_question.paper_question_score}），
     * 题目不在试卷上时才回退 {@code plugin.eduai.marking.max-score}，
     * 否则得分会与试卷总分口径不一致。</p>
     *
     * @param taskId  任务标识
     * @param answers answers
     */
    public void markSync(String taskId, List<MarkingSubmitDTO.AnswerItem> answers) {
        EduAnswerSheet sheet = findSheet(taskId);
        Map<Long, Integer> fullScores = resolveFullScores(sheet);
        int fallbackScore = properties.getMarking().getMaxScore();
        for (MarkingSubmitDTO.AnswerItem item : answers) {
            EduQuestion question = questionMapper.selectById(item.getQuestionId());
            if (question == null) {
                continue;
            }
            int maxScore = fullScores.getOrDefault(item.getQuestionId(), fallbackScore);
            EduAiService.MarkingOutcome outcome = eduAiService.markQuestionWithAi(
                    question.getQuestionType(),
                    question.getQuestionStem(),
                    question.getQuestionAnswer(),
                    item.getAnswer(),
                    maxScore
            );
            applyOutcome(taskId, question, outcome);
        }
        recordWrongQuestions(taskId, sheet);
    }

    /**
     * 取阅卷任务对应答题卡
     * @param taskId 任务标识
     * @return 查找答题卡的结果，无阅卷结果时返回 {@code null}
     */
    private EduAnswerSheet findSheet(String taskId) {
        List<EduMarkingResult> results = markingResultMapper.selectByTaskId(taskId);
        if (results.isEmpty()) {
            return null;
        }
        return answerSheetMapper.selectById(results.getFirst().getMarkingResultAnswerSheetId());
    }

    /**
     * 答题卡所在试卷的每题分值映射
     * @param sheet 答题卡
     * @return 解析满分试卷的结果，链路不全时返回空映射
     */
    private Map<Long, Integer> resolveFullScores(EduAnswerSheet sheet) {
        if (sheet == null || sheet.getAnswerSheetExamId() == null) {
            return Map.of();
        }
        EduExam exam = examMapper.selectById(sheet.getAnswerSheetExamId());
        if (exam == null || exam.getExamPaperId() == null) {
            return Map.of();
        }
        Map<Long, Integer> scores = new HashMap<>();
        for (EduPaperQuestion relation : paperQuestionMapper.selectByPaperIdForScores(exam.getExamPaperId())) {
            if (relation.getPaperQuestionScore() != null) {
                scores.put(relation.getPaperQuestionQuestionId(), relation.getPaperQuestionScore());
            }
        }
        return scores;
    }

    /**
     * 按任务标识取阅卷结果行
     * @param taskId 任务标识
     * @return 按任务标识取阅卷结果行的结果
     */
    private List<EduMarkingResult> listByTask(String taskId) {
        return markingResultMapper.selectByTaskId(taskId);
    }

    /**
     * 把该阅卷任务下未得分的题目登记到错题本。
     *
     * <p>队列消费线程没有请求上下文，归属用户从答题卡行继承，与
     * {@link EduUserContext} 对异步路径的约定一致。</p>
     *
     * @param taskId 阅卷任务标识
     * @param sheet  答题卡
     */
    private void recordWrongQuestions(String taskId, EduAnswerSheet sheet) {
        List<EduMarkingResult> results = listByTask(taskId);
        if (results.isEmpty() || sheet == null) {
            log.warn("[EduAi] 阅卷结果无对应答题卡，跳过错题登记: taskId={}", taskId);
            return;
        }
        int recorded = 0;
        for (EduMarkingResult result : results) {
            Integer score = result.getMarkingResultAiScore();
            if (score != null && score > 0) {
                continue;
            }
            // 任意卷没有题库记录（题目标识记 0），错题本只登记真实题目标识
            if (result.getMarkingResultQuestionId() == null || result.getMarkingResultQuestionId() <= 0) {
                continue;
            }
            try {
                wrongQuestionService.recordWrongQuestion(sheet.getAnswerSheetStudentId(),
                        result.getMarkingResultQuestionId(), sheet.getAnswerSheetExamId(), sheet.getOwnerUserId());
                recorded++;
            } catch (Exception e) {
                // 错题登记是「判分之后的附加动作」，单条失败不能让整个阅卷任务判为失败
                // （实测曾因唯一键冲突把已判分完成的任务标成 FAILED）
                log.warn("[EduAi] 错题登记失败(忽略): taskId={}, questionId={}, err={}",
                        taskId, result.getMarkingResultQuestionId(), e.getMessage());
            }
        }
        log.info("[EduAi] 错题登记完成: taskId={}, 未得分题数={}", taskId, recorded);
    }

    /**
     * 把单题批改结果写入 DB。
     * @param taskId 任务标识
     * @param question question
     * @param outcome outcome
     */
    public void applyOutcome(String taskId, EduQuestion question, EduAiService.MarkingOutcome outcome) {
        markingResultMapper.updateAiOutcome(taskId, question.getQuestionId(),
                outcome.getScore(), outcome.getComment(), LocalDateTime.now());
    }

    /**
     * 单次扫描允许的最大图片张数（防止误传整册导致无限识别）。
     */
    private static final int MAX_SCAN_IMAGES = 20;

    /**
     * 后台处理并发闸门：同时只允许 N 个任务进入识别 + 判分（默认 1，见
     * {@link EduAiProperties.Marking#getMaxConcurrent()}）。防止多路 ONNX 并发把整机内存打满。
     */
    private Semaphore markingPermits;

    /**
     * 初始化并发闸门（按配置的并发数）。
     */
    @PostConstruct
    public void initMarkingPermits() {
        int permits = Math.max(1, properties.getMarking().getMaxConcurrent());
        this.markingPermits = new Semaphore(permits, true);
        log.info("[EduAi] 阅卷后台并发闸门初始化: permits={}", permits);
    }

    /**
     * 进程是否正在关闭。关闭期中断不能写成业务失败，否则任务成终态、启动续跑不会再捞起。
     */
    private volatile boolean shuttingDown;

    /**
     * 标记进程进入关闭（SIGTERM / 优雅停机时由 Spring 调用）。
     */
    @PreDestroy
    public void markShuttingDown() {
        this.shuttingDown = true;
    }

    @Override
    public String scanAndMark(List<byte[]> images, String mode, Long paperId, String studentId,
                              Long examId, Integer maxScore) {
        // 入参快速校验（不落库）
        boolean anyMode = !EduPaperScanner.MODE_SELF.equalsIgnoreCase(mode);
        if (!anyMode && paperId == null) {
            throw new BusinessException("自有卷必须指定试卷 ID");
        }
        List<byte[]> validImages = images == null ? List.of()
                : images.stream().filter(v -> v != null && v.length > 0).toList();
        if (validImages.isEmpty()) {
            throw new BusinessException("上传图片为空");
        }
        if (validImages.size() > MAX_SCAN_IMAGES) {
            throw new BusinessException("单次扫描最多 " + MAX_SCAN_IMAGES + " 张图片");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        // 1. 图片落盘：任务可重启续跑，输入必须还能取到（内存里的字节在重启后不复存在）
        String imagePath = saveTaskImages(taskId, validImages);

        // 2. 落任务（待处理）后【立即返回】taskId。
        //    不在此处做识别与判分：版面+OCR 为本地推理（实测约 13 秒）、判分还要多次调用大模型，
        //    放在请求线程会超过前端 HTTP 客户端的 10 秒超时（实测前端报 timeout of 10000ms exceeded，
        //    表现为「点了没反应」），且进程重启会丢掉全部中间状态。
        EduMarkingTask task = new EduMarkingTask();
        task.setOwnerUserId(EduUserContext.requireUserId());
        task.setMarkingTaskTaskId(taskId);
        task.setMarkingTaskStatus(EduMarkingTask.STATUS_PENDING);
        task.setMarkingTaskMode(anyMode ? EduPaperScanner.MODE_ANY : EduPaperScanner.MODE_SELF);
        task.setMarkingTaskPaperId(paperId);
        task.setMarkingTaskExamId(examId);
        task.setMarkingTaskStudentId(StringUtils.isBlank(studentId) ? "anonymous" : studentId);
        task.setMarkingTaskMaxScore(maxScore);
        task.setMarkingTaskImagePath(imagePath);
        task.setMarkingTaskStage("排队中");
        task.setMarkingTaskTotal(0);
        task.setMarkingTaskDoneCount(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("[EduAi] 阅卷任务已创建: taskId={}, mode={}, paperId={}",
                taskId, task.getMarkingTaskMode(), paperId);

        // 3. 唤醒后台处理；此处即使失败也不影响任务本身（下次启动续跑会捡起 PENDING）
        kickTask(taskId);
        return taskId;
    }

    /**
     * 唤醒后台处理一个任务（虚拟线程，任务状态已落库，故线程丢失可恢复）。
     *
     * @param taskId 任务业务标识
     */
    private void kickTask(String taskId) {
        Thread.ofVirtual().name("edu-mark-" + taskId).start(() -> {
            // 并发闸门：同时只跑 N 个任务（默认串行）。
            // 每个任务都要跑 ONNX 版面 + OCR，原生内存不受 -Xmx 约束；多路并发会触发内核
            // OOM 杀掉本服务，重启后同样续跑多路 → 崩溃循环、任务永远卡在「识别中」。
            try {
                markingPermits.acquire();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[EduAi] 阅卷任务等待并发闸门被中断: taskId={}", taskId);
                return;
            }
            try {
                runWithTimeout(taskId);
            } finally {
                markingPermits.release();
            }
        });
    }

    /**
     * 带硬超时地处理一个任务。
     *
     * <p>OCR 与大模型调用都可能长时间不返回；单任务挂死会永久占住并发闸门的唯一许可，
     * 把后续任务全部堵死（实测 ANY 任务在「识别中」卡 10 分钟以上，整个队列停摆 ——
     * 限流闸门反而成了单点堵死）。故用 {@link FutureTask} 施加超时：到点中断并判失败，
     * 闸门照常释放，队列继续。</p>
     *
     * @param taskId 任务业务标识
     */
    private void runWithTimeout(String taskId) {
        long timeoutSeconds = Math.max(30, properties.getMarking().getTimeout());
        var future = new FutureTask<Void>(() -> {
            processTask(taskId);
            return null;
        });
        Thread.ofVirtual().name("edu-mark-run-" + taskId).start(future);
        try {
            future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            log.error("[EduAi] 阅卷任务处理超时（>{}s），已中止: taskId={}", timeoutSeconds, taskId);
            changeStage(taskId, EduMarkingTask.STATUS_FAILED, "失败", null, null,
                    "处理超时（>" + timeoutSeconds + "s），已中止", true);
        } catch (ExecutionException ee) {
            log.error("[EduAi] 阅卷任务处理异常: taskId={}", taskId, ee.getCause());
        } catch (InterruptedException ie) {
            future.cancel(true);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 启动续跑：把上次进程遗留的「待处理 / 处理中」任务重新入队。
     *
     * <p>这是「重启会继续处理」的落地：处理中状态可能因进程被杀而中断，
     * 因此处理流程本身设计为可重入（识别产物落库、判分按结果行状态跳过已完成项）。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinishedTasks() {
        List<EduMarkingTask> tasks;
        try {
            tasks = taskMapper.selectResumable(properties.getMarking().getResumeLimit());
        } catch (Exception e) {
            log.warn("[EduAi] 续跑查询失败（阅卷任务表可能尚未建立）: {}", e.getMessage());
            return;
        }
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        log.info("[EduAi] 发现 {} 个未完成的阅卷任务，开始续跑", tasks.size());
        for (EduMarkingTask t : tasks) {
            kickTask(t.getMarkingTaskTaskId());
        }
    }

    /**
     * 处理一个阅卷任务（可重入：重启续跑会再次进入）。
     *
     * <p>分两个阶段，各自可独立续跑：</p>
     * <ol>
     *   <li>识别阶段：读图片 → 版面+OCR → 结构化 → 落答题卡与结果行（PENDING）</li>
     *   <li>判分阶段：对仍为 PENDING 的结果行逐个判分（已完成的跳过）</li>
     * </ol>
     *
     * @param taskId 任务业务标识
     */
    public void processTask(String taskId) {
        EduMarkingTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            log.warn("[EduAi] 阅卷任务不存在: taskId={}", taskId);
            return;
        }
        if (Integer.valueOf(EduMarkingTask.STATUS_DONE).equals(task.getMarkingTaskStatus())) {
            return;
        }
        // 乐观抢占：正常提交是 0→1；续跑时可能已是 1，claim 影响 0 行但仍需继续处理
        taskMapper.claimPending(taskId, LocalDateTime.now());
        changeStage(taskId, EduMarkingTask.STATUS_PROCESSING, "识别中", null, null, null, false);
        try {
            boolean anyMode = !EduPaperScanner.MODE_SELF.equalsIgnoreCase(task.getMarkingTaskMode());
            // 识别阶段：仅在尚未产出答题卡时执行（续跑时跳过，直接进判分）
            if (task.getMarkingTaskAnswerSheetId() == null) {
                recognizeAndPrepare(task, anyMode);
            }
            int graded = gradePendingOfTask(taskId, anyMode);
            EduMarkingTask latest = taskMapper.selectByTaskId(taskId);
            changeStage(taskId, EduMarkingTask.STATUS_DONE, "已完成",
                    latest == null ? null : latest.getMarkingTaskTotal(), graded, null, true);
            // 错题登记放在判分完成后（未得分题目），归属用户从答题卡继承
            if (latest != null && latest.getMarkingTaskAnswerSheetId() != null) {
                recordWrongQuestions(taskId, answerSheetMapper.selectById(latest.getMarkingTaskAnswerSheetId()));
            }
            log.info("[EduAi] 阅卷任务完成: taskId={}, 已判分={}", taskId, graded);
        } catch (BusinessException be) {
            if (isInterruptedByShutdown(be)) {
                keepResumable(taskId, be);
                return;
            }
            changeStage(taskId, EduMarkingTask.STATUS_FAILED, "失败", null, null, be.getMessage(), true);
            log.warn("[EduAi] 阅卷任务失败(业务): taskId={}, msg={}", taskId, be.getMessage());
        } catch (Exception e) {
            // 进程关闭引起的异常不能写成终态：写成 FAILED 后，启动续跑只捞 PENDING/PROCESSING，
            // 任务就永远不会再被处理，与「重启会继续处理」的要求冲突。
            if (isInterruptedByShutdown(e)) {
                keepResumable(taskId, e);
                return;
            }
            changeStage(taskId, EduMarkingTask.STATUS_FAILED, "失败", null, null,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), true);
            log.error("[EduAi] 阅卷任务失败: taskId={}", taskId, e);
        }
    }

    /**
     * 判断异常是否由「进程关闭」引起，而不是业务失败。
     *
     * <p>优雅关闭时 Spring 先销毁 Bean，ONNX Runtime 运行环境随之释放；此刻仍在跑 OCR 的后台
     * 虚拟线程会抛 {@code ORT_FAIL ... Attempt to use DefaultLogger but none has been registered}。
     * 实测这正是「提交任务后立刻重启」时的表现。</p>
     *
     * @param e 捕获到的异常
     * @return 是否属于关闭期中断
     */
    private boolean isInterruptedByShutdown(Throwable e) {
        if (shuttingDown) {
            return true;
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("DefaultLogger but none has been registered")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关闭期中断：保持任务为可续跑状态，不写终态。
     *
     * <p>任务此前已置为 PROCESSING，启动时由 {@code resumeUnfinishedTasks} 捞起重跑；
     * 识别阶段可重入（未产出答题卡则重做），因此不需在这里做任何回滚。</p>
     *
     * @param taskId 任务标识
     * @param e      中断异常
     */
    private void keepResumable(String taskId, Throwable e) {
        log.warn("[EduAi] 进程关闭中断，任务保持可续跑（下次启动续跑）: taskId={}, msg={}",
                taskId, e.getMessage());
    }

    /**
     * 识别阶段：图片 → 版面+OCR → 结构化 → 落答题卡与全部结果行（PENDING）。
     *
     * @param task    任务
     * @param anyMode 是否任意卷
     */
    private void recognizeAndPrepare(EduMarkingTask task, boolean anyMode) {
        String taskId = task.getMarkingTaskTaskId();
        List<byte[]> images = readTaskImages(task.getMarkingTaskImagePath());
        // 多页支持：一份试卷常跨多页，逐张做版面还原 + OCR，再按页序拼接文本后统一结构化。
        // 拼接（而不是分别抽取）的原因：题号可能跨页连续，一起解析才能保证题序与去重正确。
        var mergedText = new StringBuilder();
        var mergedLayoutRegions = new HashMap<String, Integer>();
        int blockCount = 0;
        int pageNo = 0;
        for (byte[] imageData : images) {
            pageNo++;
            EduPaperScanner.ScanResult pageScan = paperScanner.scan(imageData);
            if (pageScan == null) {
                continue;
            }
            if (StringUtils.isNotBlank(pageScan.text())) {
                if (mergedText.length() > 0) {
                    mergedText.append('\n');
                }
                mergedText.append(pageScan.text());
            }
            if (pageScan.layoutRegions() != null) {
                pageScan.layoutRegions().forEach((k, v) -> mergedLayoutRegions.merge(k, v == null ? 0 : v, Integer::sum));
            }
            blockCount += pageScan.blocks() == null ? 0 : pageScan.blocks().size();
            log.info("[EduScan] 第 {}/{} 页识别完成，累计文本长度={}", pageNo, images.size(), mergedText.length());
        }
        String text = mergedText.toString();
        if (StringUtils.isBlank(text)) {
            throw new BusinessException("未从图片识别到文字，请确认图片清晰、方向正确后重试");
        }
        changeStage(taskId, EduMarkingTask.STATUS_PROCESSING, "解析题目", null, null, null, false);

        List<EduPaperScanner.ScannedQuestion> scanned = anyMode
                ? paperScanner.extractAnyQuestions(text)
                : paperScanner.extractSelfQuestions(text);
        if (scanned.isEmpty()) {
            throw new BusinessException("未能从图片中解析出题目，请检查图片质量或改用另一种模式");
        }

        // 自有卷：题号 → 试卷题项索引；分值与题目标识一律取自数据库（「准确」的来源）
        Map<String, EduPaperQuestion> byNo = new HashMap<>();
        Map<String, EduPaperQuestion> byOrder = new HashMap<>();
        List<EduPaperQuestion> relations = List.of();
        if (!anyMode) {
            if (task.getMarkingTaskPaperId() == null) {
                throw new BusinessException("自有卷必须指定试卷 ID");
            }
            relations = paperQuestionMapper.selectByPaperId(task.getMarkingTaskPaperId());
            if (relations.isEmpty()) {
                throw new BusinessException("该试卷下没有题目，无法按题号取准确分值");
            }
            int order = 1;
            for (EduPaperQuestion relation : relations) {
                if (StringUtils.isNotBlank(relation.getPaperQuestionNo())) {
                    byNo.put(normalizeNo(relation.getPaperQuestionNo()), relation);
                }
                byOrder.put(String.valueOf(order), relation);
                order++;
            }
        }

        // 答题卡（content 是 JSON 列，必须存合法 JSON）
        var content = new java.util.LinkedHashMap<String, Object>();
        content.put("mode", task.getMarkingTaskMode());
        content.put("paperId", task.getMarkingTaskPaperId());
        content.put("text", text);
        content.put("layoutRegions", mergedLayoutRegions);
        content.put("blockCount", blockCount);
        content.put("pageCount", images.size());
        EduAnswerSheet sheet = new EduAnswerSheet();
        sheet.setOwnerUserId(task.getOwnerUserId());
        sheet.setAnswerSheetExamId(task.getMarkingTaskExamId());
        sheet.setAnswerSheetStudentId(task.getMarkingTaskStudentId());
        sheet.setAnswerSheetContent(Json.toJson(content));
        sheet.setAnswerSheetSubmitTime(LocalDateTime.now());
        sheet.setAnswerSheetStatus(1);
        sheet.setCreateTime(LocalDateTime.now());
        sheet.setUpdateTime(LocalDateTime.now());
        answerSheetMapper.insert(sheet);
        taskMapper.updateArtifacts(taskId, sheet.getAnswerSheetId(), null, LocalDateTime.now());

        int fallbackScore = task.getMarkingTaskMaxScore() != null && task.getMarkingTaskMaxScore() > 0
                ? task.getMarkingTaskMaxScore() : properties.getMarking().getMaxScore();
        int total = 0;
        // 同一道题只允许产出一行：真实卷里「两位数题号被 OCR 读成个位」很常见（11.→1.），
        // 会把另一处的作答再次配到第 1 题，导致同一题多行互相覆盖、总题数虚高（实测 13 题产出 15 行）。
        Set<Long> usedQuestionIds = new HashSet<>();
        for (EduPaperScanner.ScannedQuestion item : scanned) {
            EduPaperQuestion relation = anyMode ? null : matchRelation(item.no(), byNo, byOrder);
            if (!anyMode && relation != null && usedQuestionIds.contains(relation.getPaperQuestionQuestionId())) {
                EduPaperQuestion reassigned = findNextUnusedByPrefix(item.no(), relations, usedQuestionIds);
                if (reassigned == null) {
                    log.warn("[EduAi] 题号 {} 重复命中已用题项且无可改派目标，跳过（避免同题多行）", item.no());
                    continue;
                }
                log.warn("[EduAi] 题号 {} 命中已用题项，改派到未使用题项 {}",
                        item.no(), reassigned.getPaperQuestionNo());
                relation = reassigned;
            }
            if (!anyMode && relation == null) {
                log.warn("[EduAi] 阅卷任务：题号 {} 在试卷 {} 中找不到对应题项，跳过",
                        item.no(), task.getMarkingTaskPaperId());
                continue;
            }
            if (!anyMode) {
                usedQuestionIds.add(relation.getPaperQuestionQuestionId());
            }
            Long questionId = relation == null ? 0L : relation.getPaperQuestionQuestionId();
            int maxScore = relation == null || relation.getPaperQuestionScore() == null
                    || relation.getPaperQuestionScore() <= 0
                    ? fallbackScore : relation.getPaperQuestionScore();

            EduMarkingResult row = new EduMarkingResult();
            row.setOwnerUserId(task.getOwnerUserId());
            row.setMarkingResultAnswerSheetId(sheet.getAnswerSheetId());
            row.setMarkingResultQuestionId(questionId);
            row.setMarkingResultTaskId(taskId);
            row.setMarkingResultStatus(STATUS_PENDING);
            // 识别信息先落到 detail：判分阶段（乃至重启后续跑）都要用它，
            // 不能只放在内存里，否则续跑时无从得知这道题识别出了什么
            row.setMarkingResultDetail(buildScanBaseDetail(item, maxScore));
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            markingResultMapper.insert(row);
            total++;
        }
        if (total == 0) {
            throw new BusinessException("识别到题目但未能匹配到可判分的题项，请确认试卷选择是否正确");
        }
        changeStage(taskId, EduMarkingTask.STATUS_PROCESSING,
                "判分中 0/" + total, total, 0, null, false);
    }

    /**
     * 判分阶段：对仍为 PENDING 的结果行逐题判分（可重入，已完成的跳过）。
     *
     * @param taskId  任务标识
     * @param anyMode 是否任意卷
     * @return 已判分题数
     */
    private int gradePendingOfTask(String taskId, boolean anyMode) {
        List<EduMarkingResult> rows = markingResultMapper.selectByTaskId(taskId);
        int total = rows.size();
        int done = 0;
        for (EduMarkingResult row : rows) {
            if (row.getMarkingResultStatus() != null && row.getMarkingResultStatus() != STATUS_PENDING) {
                done++;
                continue;
            }
            EduPaperScanner.ScannedQuestion item = parseScanDetail(row.getMarkingResultDetail());
            int maxScore = parseDetailMaxScore(row.getMarkingResultDetail());
            try {
                EduAiService.MarkingOutcome outcome;
                if (anyMode) {
                    outcome = paperScanner.markAnyQuestion(item, maxScore);
                } else {
                    EduQuestion question = questionMapper.selectById(row.getMarkingResultQuestionId());
                    if (question == null) {
                        markingResultMapper.updateScanOutcomeById(row.getMarkingResultId(), 0,
                                "题目不在题库", buildScanFinalDetail(item, null, maxScore),
                                LocalDateTime.now());
                        done++;
                        continue;
                    }
                    outcome = tolerantObjectiveOutcome(question, item.answer(), maxScore);
                }
                // 按【行主键】更新：任意卷 question_id 统一为 0，按 (taskId,questionId) 会命中多行互相覆盖
                markingResultMapper.updateScanOutcomeById(row.getMarkingResultId(), outcome.getScore(),
                        outcome.getComment(), buildScanFinalDetail(item, outcome, maxScore),
                        LocalDateTime.now());
            } catch (Exception e) {
                // 单题失败不能让该行永久停在 PENDING：否则 finished 永远为 false、老师也看不到哪题出错
                log.error("[EduAi] 单题判分失败: resultId={}, no={}", row.getMarkingResultId(), item.no(), e);
                try {
                    markingResultMapper.updateScanOutcomeById(row.getMarkingResultId(), 0,
                            "判分失败：" + e.getMessage(), buildScanFinalDetail(item, null, maxScore),
                            LocalDateTime.now());
                } catch (Exception ex) {
                    log.error("[EduAi] 写入判分失败结果也失败: resultId={}", row.getMarkingResultId(), ex);
                }
            }
            done++;
            changeStage(taskId, EduMarkingTask.STATUS_PROCESSING,
                    "判分中 " + done + "/" + total, total, done, null, false);
        }
        return done;
    }

    /**
     * 更新任务状态 / 阶段 / 进度。
     *
     * @param taskId 任务标识
     * @param status 状态
     * @param stage  阶段描述
     * @param total  题目总数（可为空）
     * @param done   已判分数（可为空）
     * @param error  失败原因（可为空）
     * @param finish 是否写完成时间
     */
    private void changeStage(String taskId, int status, String stage,
                             Integer total, Integer done, String error, boolean finish) {
        try {
            taskMapper.updateProgress(taskId, status, stage, total, done, error, finish, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[EduAi] 更新任务进度失败: taskId={}, stage={}, err={}", taskId, stage, e.getMessage());
        }
    }

    /**
     * 保存任务图片到配置目录（多页按页序编号，路径以逗号连接后落库）。
     *
     * @param taskId 任务标识
     * @param images 图片字节（按页顺序）
     * @return 逗号连接的落盘路径
     */
    private String saveTaskImages(String taskId, List<byte[]> images) {
        try {
            Path dir = Paths.get(properties.getMarking().getImageDir());
            Files.createDirectories(dir);
            var paths = new ArrayList<String>(images.size());
            for (int i = 0; i < images.size(); i++) {
                Path file = dir.resolve(taskId + "-" + (i + 1) + ".png");
                Files.write(file, images.get(i));
                paths.add(file.toAbsolutePath().toString());
            }
            return String.join(",", paths);
        } catch (Exception e) {
            throw new BusinessException("保存试卷图片失败: " + e.getMessage());
        }
    }

    /**
     * 读取任务图片（续跑时用），支持多页（路径以逗号分隔）。
     *
     * @param imagePaths 逗号连接的图片路径
     * @return 图片字节列表（按原页序）
     */
    private List<byte[]> readTaskImages(String imagePaths) {
        if (StringUtils.isBlank(imagePaths)) {
            throw new BusinessException("任务缺少图片路径，无法继续处理");
        }
        var result = new ArrayList<byte[]>();
        for (String item : imagePaths.split(",")) {
            String path = item.trim();
            if (path.isEmpty()) {
                continue;
            }
            try {
                Path p = Paths.get(path);
                if (!Files.isRegularFile(p)) {
                    throw new BusinessException("任务图片已不存在: " + path);
                }
                result.add(Files.readAllBytes(p));
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                throw new BusinessException("读取任务图片失败: " + e.getMessage());
            }
        }
        if (result.isEmpty()) {
            throw new BusinessException("任务缺少图片路径，无法继续处理");
        }
        return result;
    }

    /**
     * 从 detail JSON 还原识别到的题目（判分与续跑阶段使用）。
     *
     * @param detail 详情 JSON
     * @return 题目（题号 / 题干 / 作答）
     */
    private EduPaperScanner.ScannedQuestion parseScanDetail(String detail) {
        if (StringUtils.isBlank(detail)) {
            return new EduPaperScanner.ScannedQuestion("", null, "");
        }
        try {
            var node = MAPPER.readTree(detail);
            return new EduPaperScanner.ScannedQuestion(
                    node.path("recognizedNo").asText(""),
                    node.hasNonNull("stem") ? node.path("stem").asText("") : null,
                    node.path("studentAnswer").asText(""));
        } catch (Exception e) {
            log.warn("[EduAi] 解析识别详情失败: {}", e.getMessage());
            return new EduPaperScanner.ScannedQuestion("", null, "");
        }
    }

    /**
     * 从 detail JSON 取本题满分。
     *
     * @param detail 详情 JSON
     * @return 满分；缺失时回退配置值
     */
    private int parseDetailMaxScore(String detail) {
        try {
            var node = MAPPER.readTree(detail);
            int v = node.path("maxScore").asInt(0);
            return v > 0 ? v : properties.getMarking().getMaxScore();
        } catch (Exception e) {
            return properties.getMarking().getMaxScore();
        }
    }

    /**
     * 题号重复命中时，找一个「未使用且题号数字前缀相容」的题项来改派。
     *
     * <p>针对真实卷高频现象：两位数题号被 OCR 读成个位（{@code 11.} → {@code 1.}）。
     * 此时 {@code 1.} 会先命中第 1 题，但第 1 题已被前页正解占用，于是把这一处改派给
     * 尚未使用、且数字前缀相容（{@code 11} 以 {@code 1} 开头）的题项。
     * 找不到则返回 {@code null}，由调用方跳过 —— 宁可少一行，也不要同题多行互相覆盖。</p>
     *
     * @param no        识别到的题号
     * @param relations 试卷题项（按卷内顺序）
     * @param used      已使用的题目标识
     * @return 可改派的题项；无则 {@code null}
     */
    private static EduPaperQuestion findNextUnusedByPrefix(String no, List<EduPaperQuestion> relations,
                                                           Set<Long> used) {
        String digits = normalizeNo(no).replaceAll("[^0-9]", "");
        if (digits.isEmpty() || relations == null || relations.isEmpty()) {
            return null;
        }
        for (EduPaperQuestion relation : relations) {
            if (relation.getPaperQuestionQuestionId() == null
                    || used.contains(relation.getPaperQuestionQuestionId())) {
                continue;
            }
            String candidate = normalizeNo(relation.getPaperQuestionNo()).replaceAll("[^0-9]", "");
            if (!candidate.isEmpty() && (candidate.startsWith(digits) || digits.startsWith(candidate))) {
                return relation;
            }
        }
        return null;
    }

    /**
     * 把识别出的题号匹配到试卷题项。
     *
     * <p>识别文本里的题号写法很杂（{@code 1.} / {@code 第1题} / {@code (2)} / 二维码编号），
     * 因此依次尝试：题项唯一编号精确匹配 → 去掉标点后匹配 → 取首个数字按卷内顺序匹配。</p>
     *
     * @param no      识别出的题号
     * @param byNo    题项唯一编号 → 关联
     * @param byOrder 卷内顺序（字符串）→ 关联
     * @return 命中的关联；未命中返回 {@code null}
     */
    private EduPaperQuestion matchRelation(String no,
                                           Map<String, EduPaperQuestion> byNo,
                                           Map<String, EduPaperQuestion> byOrder) {
        if (StringUtils.isBlank(no)) {
            return null;
        }
        EduPaperQuestion hit = byNo.get(no.trim());
        if (hit != null) {
            return hit;
        }
        hit = byNo.get(normalizeNo(no));
        if (hit != null) {
            return hit;
        }
        var matcher = FIRST_DIGITS.matcher(no);
        if (matcher.find()) {
            return byOrder.get(matcher.group());
        }
        // 兜底：题号可能被识别成字母（如「1.」→「L.」），折算后再取数字
        var fixed = FIRST_DIGITS.matcher(fixOcrDigits(no));
        if (fixed.find()) {
            return byOrder.get(fixed.group());
        }
        return null;
    }

    /**
     * 题号归一化：只保留字母与数字并转小写，消除 {@code 1.} / {@code 1、} / {@code (1)} 等写法差异。
     *
     * @param raw 原始题号
     * @return 归一化题号
     */
    private static String normalizeNo(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase().replaceAll("[^0-9a-z]", "");
    }

    /**
     * 修正 OCR 常见「字母↔数字」混淆，用于题号兜底匹配。
     *
     * <p>实测：题号 {@code 1.} 会被识别成 {@code L.}（数字 1 与字母 l/L/I 字形相近），
     * 若只按 {@code \d+} 找数字就会整道题漏掉。这里把常见混淆统一折算回数字：
     * {@code l/L/I → 1}、{@code O/o → 0}、{@code S/s → 5}、{@code B → 8}、
     * {@code Z/z → 2}、{@code g → 9}。</p>
     *
     * @param raw 识别出的题号
     * @return 折算后的文本
     */
    private static String fixOcrDigits(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('l', '1').replace('L', '1').replace('I', '1')
                .replace('O', '0').replace('o', '0')
                .replace('S', '5').replace('s', '5')
                .replace('B', '8')
                .replace('Z', '2').replace('z', '2')
                .replace('g', '9');
    }

    /**
     * 该题类型是否为客观题（可精确比对，不依赖模型理解）。
     *
     * @param questionType 题型
     * @return 是否客观题
     */
    public static boolean isObjectiveType(String questionType) {
        if (questionType == null) {
            return false;
        }
        String type = questionType.trim().toUpperCase();
        return "SINGLE_CHOICE".equals(type) || "MULTIPLE_CHOICE".equals(type)
                || "JUDGE".equals(type) || "FILL".equals(type) || "BLANK".equals(type);
    }

    /**
     * 答案相似度（0~1），用于容忍 OCR 个别错字与「答对但啰嗦」。
     *
     * <p>先归一化（去空白与中英标点、统一小写），再取<b>最长公共子序列</b>的对称比率：
     * {@code 2 * LCS / (len(a) + len(b))}。</p>
     *
     * <p>为什么不用编辑距离：编辑距离以「较长串」作分母，正确但啰嗦的作答会被压低。
     * 真实卷实测：「短文主要写丫小兴安岭一年四李的美丽景色」对标准
     * 「小兴安岭一年四季的美丽景色」（含 2 个 OCR 错字）编辑距离口径仅 0.6 判 0 分，
     * LCS 口径 0.77 可判满分；「做工湖涂事」对「做了糊涂事」也从 0.6 升到 0.8。
     * LCS 对插入/删减同样宽容，且是<b>对称量</b>（与参数顺序无关）。</p>
     *
     * @param standard 标准答案
     * @param student  学生作答（可能含 OCR 错字）
     * @return 相似度；任一侧为空返回 0
     */
    public static double similarity(String standard, String student) {
        String a = normalizeText(standard);
        String b = normalizeText(student);
        if (a.isEmpty() || b.isEmpty()) {
            return 0d;
        }
        if (a.equals(b)) {
            return 1d;
        }
        int lcs = lcsLength(a, b);
        return 2d * lcs / (a.length() + b.length());
    }

    /**
     * 归一化文本：去掉空白与中英文标点，统一小写。
     *
     * @param value 原文本
     * @return 归一化文本
     */
    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase()
                .replaceAll("[\\s\\p{Punct}，。、；：？！（）〔〕《》〈〉“”‘’·—…￥【】]", "");
    }

    /**
     * 最长公共子序列长度（字符级，滚动数组实现）。
     *
     * @param a 串 a
     * @param b 串 b
     * @return LCS 长度
     */
    private static int lcsLength(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    curr[j] = prev[j - 1] + 1;
                } else {
                    curr[j] = Math.max(prev[j], curr[j - 1]);
                }
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }

    /**
     * 组装识别信息（判分前写入）。
     *
     * <p>现有表没有「学生作答」列，用闲置的 {@code marking_result_detail} 承载。
     * 识别阶段先写入题号/题干/作答/满分 —— 判分阶段与<b>重启续跑</b>都要靠它还原题目，
     * 因此不能只放在内存里。</p>
     *
     * @param item     识别出的题目
     * @param maxScore 本题满分
     * @return 详情 JSON（尚无得分）
     */
    private static String buildScanBaseDetail(EduPaperScanner.ScannedQuestion item, int maxScore) {
        var detail = new java.util.LinkedHashMap<String, Object>();
        detail.put("scan", true);
        detail.put("recognizedNo", item.no());
        detail.put("stem", item.stem());
        detail.put("studentAnswer", item.answer());
        detail.put("maxScore", maxScore);
        return Json.toJson(detail);
    }

    /**
     * 组装最终批改详情（判分后写入，含得分 / 对错 / 来源）。
     *
     * @param item     识别出的题目
     * @param outcome  判分结果（可为 null，表示判分失败或题目不在题库）
     * @param maxScore 本题满分
     * @return 详情 JSON
     */
    private static String buildScanFinalDetail(EduPaperScanner.ScannedQuestion item,
                                               EduAiService.MarkingOutcome outcome,
                                               int maxScore) {
        var detail = new java.util.LinkedHashMap<String, Object>();
        detail.put("scan", true);
        detail.put("recognizedNo", item.no());
        detail.put("stem", item.stem());
        detail.put("studentAnswer", item.answer());
        detail.put("maxScore", maxScore);
        detail.put("score", outcome == null ? 0 : outcome.getScore());
        detail.put("correct", outcome != null && maxScore > 0 && outcome.getScore() >= maxScore);
        detail.put("source", outcome == null ? "FALLBACK" : outcome.getSource());
        return Json.toJson(detail);
    }

    /**
     * 自有卷客观题的容忍判分。
     *
     * <p>扫描阅卷的作答来自 OCR，<b>必然存在个别错字</b>（实测「糊涂」被识成「湖涂」）。
     * 而自有卷的标准答案就在题库里，若坚持严格相等，正确答案也会被判 0 分。</p>
     *
     * <p>策略：客观题先做「归一化 + 编辑距离相似度」比对，达到
     * {@link #SIMILARITY_THRESHOLD} 即给满分；不达标才交回原有链路
     * （客观题严格比对，主观题交 AI）。这样既容忍识别误差，又不放过真正答错的。</p>
     *
     * @param question      题库题目（提供题型与标准答案）
     * @param studentAnswer 识别出的学生作答
     * @param maxScore      本题满分
     * @return 判分结果
     */
    private EduAiService.MarkingOutcome tolerantObjectiveOutcome(EduQuestion question,
                                                                 String studentAnswer,
                                                                 int maxScore) {
        if (isObjectiveType(question.getQuestionType())) {
            double sim = similarity(question.getQuestionAnswer(), studentAnswer);
            if (sim >= SIMILARITY_THRESHOLD) {
                var outcome = new EduAiService.MarkingOutcome();
                outcome.setSource("AI");
                outcome.setScore(maxScore);
                outcome.setComment(sim >= 1d
                        ? "与标准答案一致"
                        : String.format("与标准答案一致（容忍识别误差，相似度 %.0f%%）", sim * 100));
                return outcome;
            }
        }
        return eduAiService.markQuestionWithAi(question.getQuestionType(),
                question.getQuestionStem(), question.getQuestionAnswer(), studentAnswer, maxScore);
    }

    /**
     * 客观题答案相似度阈值：低于该值视为答错。
     *
     * <p>取 0.7 —— 能容忍 1~2 个字的识别误差（如「做了湖涂事」vs「做了糊涂事」相似度 0.8），
     * 又不会把明显不同的作答判成正确。</p>
     */
    private static final double SIMILARITY_THRESHOLD = 0.7d;

    @Override
    /**
     * 获取结果
    */
    public MarkingResultVO getResult(String taskId) {
        // 先取任务：异步模式下「刚提交、识别尚未产出结果行」是正常状态，
        // 此时不能报「任务不存在」，而要如实回报「排队中 / 处理中」。
        EduMarkingTask task = null;
        try {
            task = taskMapper.selectByTaskId(taskId);
        } catch (Exception e) {
            log.debug("[EduAi] 查询阅卷任务失败（表可能尚未建立）: {}", e.getMessage());
        }
        boolean taskFailed = task != null && Integer.valueOf(EduMarkingTask.STATUS_FAILED).equals(task.getMarkingTaskStatus());
        boolean taskDone = task != null && Integer.valueOf(EduMarkingTask.STATUS_DONE).equals(task.getMarkingTaskStatus());

        List<EduMarkingResult> results = markingResultMapper.selectTaskResults(
                taskId, EduUserContext.ownerScope());

        if (results.isEmpty()) {
            if (task == null) {
                throw new BusinessException("任务不存在");
            }
            // 任务已创建但尚无结果行：回报任务状态与阶段，让前端继续轮询 / 展示失败原因
            MarkingResultVO pendingVo = new MarkingResultVO();
            pendingVo.setTaskId(taskId);
            pendingVo.setStatus(task.getMarkingTaskStatus());
            pendingVo.setTaskStatus(task.getMarkingTaskStatus());
            pendingVo.setStage(task.getMarkingTaskStage());
            pendingVo.setError(task.getMarkingTaskError());
            pendingVo.setTotal(0);
            pendingVo.setTotalScore(0);
            pendingVo.setResults(new ArrayList<>());
            pendingVo.setFinished(taskFailed);
            return pendingVo;
        }

        MarkingResultVO vo = new MarkingResultVO();
        vo.setTaskId(taskId);
        vo.setStatus(results.getFirst().getMarkingResultStatus());
        vo.setTotal(results.size());
        if (task != null) {
            vo.setTaskStatus(task.getMarkingTaskStatus());
            vo.setStage(task.getMarkingTaskStage());
            vo.setError(task.getMarkingTaskError());
        }

        int fallbackScore = properties.getMarking().getMaxScore();
        Map<Long, Integer> fullScores = resolveFullScores(
                answerSheetMapper.selectById(results.getFirst().getMarkingResultAnswerSheetId()));

        List<MarkingResultVO.QuestionResult> list = new ArrayList<>();
        int totalScore = 0;
        boolean allDone = true;
        for (EduMarkingResult r : results) {
            MarkingResultVO.QuestionResult qr = new MarkingResultVO.QuestionResult();
            qr.setResultId(r.getMarkingResultId());
            qr.setQuestionId(r.getMarkingResultQuestionId());
            qr.setMaxScore(fullScores.getOrDefault(r.getMarkingResultQuestionId(), fallbackScore));
            qr.setScore(r.getMarkingResultAiScore() == null ? 0 : r.getMarkingResultAiScore());
            qr.setTeacherScore(r.getMarkingResultTeacherScore());
            qr.setComment(r.getMarkingResultAiComment());
            qr.setTeacherComment(r.getMarkingResultTeacherComment());
            list.add(qr);
            totalScore += qr.getTeacherScore() != null ? qr.getTeacherScore() : qr.getScore();
            if (r.getMarkingResultStatus() == null || r.getMarkingResultStatus() < STATUS_AI_DONE) {
                allDone = false;
            }
        }
        vo.setResults(list);
        vo.setTotalScore(totalScore);
        // 完成判定：任务已完结，或所有结果行都已批改（兼容手工提交链路）
        vo.setFinished(taskDone || allDone);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * confirmmarking
     *
     * @param dto dto
     * @return confirmMarking的结果
     */
    public boolean confirmMarking(MarkingConfirmDTO dto) {
        if (dto.getAdjustments() == null || dto.getAdjustments().isEmpty()) {
            return true;
        }
        Map<Long, Integer> fullScores = resolveFullScores(findSheet(dto.getTaskId()));
        int fallbackScore = properties.getMarking().getMaxScore();
        for (MarkingConfirmDTO.ScoreAdjustment adj : dto.getAdjustments()) {
            EduMarkingResult result = markingResultMapper.selectById(adj.getResultId());
            if (result == null) {
                continue;
            }
            EduUserContext.checkWritable(result.getOwnerUserId());
            Integer maxScore = fullScores.getOrDefault(result.getMarkingResultQuestionId(), fallbackScore);
            Integer score = clampScore(adj.getScore(), maxScore);
            if (score != null && !score.equals(adj.getScore())) {
                log.warn("[EduAi] 教师修正分数超出卷面满分，已收敛: resultId={}, questionId={}, 提交={}, 满分={}, 采用={}",
                        adj.getResultId(), result.getMarkingResultQuestionId(), adj.getScore(), maxScore, score);
            }
            result.setMarkingResultTeacherScore(score);
            result.setMarkingResultTeacherComment(adj.getComment());
            result.setMarkingResultScore(score);
            result.setMarkingResultComment(adj.getComment());
            result.setMarkingResultStatus(STATUS_TEACHER_DONE);
            result.setUpdateTime(LocalDateTime.now());
            markingResultMapper.updateById(result);
        }
        log.info("[EduAi] 教师确认阅卷: taskId={}, items={}", dto.getTaskId(), dto.getAdjustments().size());
        return true;
    }

    /**
     * 把教师修正分数收敛到 [0, 卷面满分] 区间
     *
     * @param score    教师提交的分数，可为空
     * @param maxScore 该题卷面满分
     * @return 收敛后的分数，入参为空时返回空
     */
    private static Integer clampScore(Integer score, Integer maxScore) {
        if (score == null) {
            return null;
        }
        int ceiling = maxScore == null ? score : Math.max(maxScore, 0);
        return Math.min(Math.max(score, 0), ceiling);
    }
}
