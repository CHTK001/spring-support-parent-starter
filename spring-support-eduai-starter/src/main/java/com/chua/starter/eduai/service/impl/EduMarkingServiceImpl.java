package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.ai.EduPaperScanner;
import com.chua.starter.eduai.ai.PaperScanPipeline;
import com.chua.starter.eduai.model.EduMarkingTask;
import com.chua.starter.eduai.mapper.EduMarkingTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.chua.starter.eduai.configuration.EduAiProperties;
import com.chua.starter.eduai.mapper.EduAnswerSheetMapper;
import com.chua.starter.eduai.mapper.EduExamMapper;
import com.chua.starter.eduai.mapper.EduMarkingResultMapper;
import com.chua.starter.eduai.mapper.EduMarkingSheetParseMapper;
import com.chua.starter.eduai.mapper.EduPaperMapper;
import com.chua.starter.eduai.mapper.EduPaperQuestionMapper;
import com.chua.starter.eduai.mapper.EduQuestionMapper;
import com.chua.starter.eduai.mapper.EduKnowledgePointMapper;
import com.chua.starter.eduai.model.EduKnowledgePoint;
import com.chua.starter.eduai.model.EduAnswerSheet;
import com.chua.starter.eduai.model.EduExam;
import com.chua.starter.eduai.model.EduMarkingResult;
import com.chua.starter.eduai.model.EduMarkingSheetParse;
import com.chua.starter.eduai.model.EduPaper;
import com.chua.starter.eduai.model.EduPaperQuestion;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.MarkingConfirmDTO;
import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;
import com.chua.starter.eduai.model.vo.MarkingProgressVO;
import com.chua.starter.eduai.model.vo.MarkingResultVO;
import com.chua.starter.eduai.model.vo.MarkingFinishResultVO;
import com.chua.starter.eduai.queue.MarkingJob;
import com.chua.starter.eduai.queue.MarkingMessageListener;
import com.chua.starter.eduai.service.IEduMarkingService;
import com.chua.starter.eduai.service.IEduWrongQuestionService;
import com.chua.starter.eduai.support.util.AnswerSimilarity;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.stream.EduMarkingProgressHub;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
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
 *   <li>异步路径（{@link #dispatchAsync}）通过 {@link MessageTemplate} 派发到
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
     * 文字块最前面的题号 token。必须紧随块文本起始，且紧跟分隔符，
     * 否则「10题·满分」这类页眉不会误取成题号 1。
     */
    private static final java.util.regex.Pattern QUESTION_NO_HEAD = java.util.regex.Pattern.compile(
            "^[\\[【（(]?\\s*(?:第\\s*)?([0-9lI]+)(?:\\s*[-–—]\\s*[0-9]+)?(\\s*[.。、,，．：:；;]\\s*)?",
            java.util.regex.Pattern.CASE_INSENSITIVE);

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
     * 知识点 Mapper：结构化解析里把题目的知识点 id 解析成名称与描述
     */
    private final EduKnowledgePointMapper knowledgePointMapper;
    /**
     * 考试映射器
    */
    private final EduExamMapper examMapper;
    /**
     * 试卷映射器（阅卷前做试卷校验：存在 / 已发布 / 卷下有题）
     */
    private final EduPaperMapper paperMapper;
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
     * Paper 图片扫描管线（可选布局矫正 -> 原预处理 -> 扫描）
     */
    private final PaperScanPipeline paperScanPipeline;
    /**
     * 阅卷任务映射器（异步任务的状态载体，支撑重启续跑）
     */
    private final EduMarkingTaskMapper taskMapper;

    /**
     * 阅卷进度 SSE 推送注册表（判分过程中把阶段推给正在看这单的老师）
     */
    private final EduMarkingProgressHub progressHub;

    /**
     * 试卷解析结果映射器（姓名/学号/总分/逐题的结构化解析结果落库）
     */
    private final EduMarkingSheetParseMapper sheetParseMapper;

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
        // 0. 入参体检：答题内容列带 json_valid 检查约束、(考试, 学生) 上有唯一键，
        //    直接 insert 会以 500 + 数据库原文（含复合主键值）暴露给用户，这里先转成业务提示
        validateSubmitContent(dto.getContent());
        rejectDuplicateSheet(dto.getExamId(), dto.getStudentId());
        // 0.5 试卷校验：考试必须存在、关联的试卷必须存在且已发布，答案题号必须落在试卷题项上
        validateExamPaper(dto.getExamId(), dto.getAnswers());

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
     * 校验考试关联的试卷：考试存在、试卷存在、试卷已发布、答案题号全部落在试卷题项上。
     *
     * <p>手工提交链路只传 {@code examId} 不传 {@code paperId}，此前直接落库判分，
     * 草稿卷/空卷/卷外题都会静默混入成绩。</p>
     *
     * @param examId  考试标识
     * @param answers 作答列表
     */
    private void validateExamPaper(Long examId,
                                   List<MarkingSubmitDTO.AnswerItem> answers) {
        if (examId == null) {
            return;
        }
        EduExam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在，无法阅卷");
        }
        if (exam.getExamPaperId() == null) {
            throw new BusinessException("该考试未关联试卷，无法阅卷");
        }
        List<EduPaperQuestion> relations = paperQuestionMapper.selectByPaperId(exam.getExamPaperId());
        requirePublishedPaper(exam.getExamPaperId(), relations);
        Set<Long> onPaper = new HashSet<>();
        for (EduPaperQuestion relation : relations) {
            onPaper.add(relation.getPaperQuestionQuestionId());
        }
        if (answers != null) {
            for (MarkingSubmitDTO.AnswerItem item : answers) {
                if (item != null && item.getQuestionId() != null && !onPaper.contains(item.getQuestionId())) {
                    throw new BusinessException("题目 " + item.getQuestionId() + " 不在试卷上，无法阅卷");
                }
            }
        }
    }

    /**
     * 校验单张试卷：存在、已发布（status=1）、卷下有题。
     *
     * @param paperId   试卷标识
     * @param relations 该卷题项（为空时内部重查一次，避免调用方传空集合绕过“有题”检查）
     * @return 试卷题项
     */
    private List<EduPaperQuestion> requirePublishedPaper(Long paperId, List<EduPaperQuestion> relations) {
        EduPaper paper = paperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在，无法阅卷");
        }
        if (!Integer.valueOf(1).equals(paper.getPaperStatus())) {
            throw new BusinessException("试卷未发布（仅已发布试卷可阅卷）");
        }
        List<EduPaperQuestion> rows = relations;
        if (rows == null || rows.isEmpty()) {
            rows = paperQuestionMapper.selectByPaperId(paperId);
        }
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("该试卷下没有题目，无法阅卷");
        }
        return rows;
    }

    /**
     * 校验答题内容：{@code edu_answer_sheet.answer_sheet_content} 上有 {@code CHECK (json_valid(...))}，
     * 空串或非 JSON 会让 insert 抛数据库约束异常（HTTP 500，且报文带 SQL 原文），这里提前给出可读提示。
     *
     * @param content 答题内容，允许为空（为空时落库 NULL，绕过检查约束）
     */
    private void validateSubmitContent(String content) {
        if (StringUtils.isBlank(content)) {
            return;
        }
        try {
            MAPPER.readTree(content);
        } catch (Exception e) {
            log.warn("[EduAi] 答题内容不是合法 JSON，已拒绝: {}", content);
            throw new BusinessException("答题内容须为合法 JSON");
        }
    }

    /**
     * 同一考试同一学生只允许一份答题卡：唯一键 {@code uk(answer_sheet_exam_id, answer_sheet_student_id)}
     * 冲突会以 500 + 复合键值暴露，这里先查后插，转成业务提示。
     *
     * @param examId    考试 ID，可为空（未绑定考试时不参与唯一键判定）
     * @param studentId 学生 ID
     */
    private void rejectDuplicateSheet(Long examId, String studentId) {
        if (examId == null || StringUtils.isBlank(studentId)) {
            return;
        }
        Long exists = answerSheetMapper.selectCount(new LambdaQueryWrapper<EduAnswerSheet>()
                .eq(EduAnswerSheet::getAnswerSheetExamId, examId)
                .eq(EduAnswerSheet::getAnswerSheetStudentId, studentId));
        if (exists != null && exists > 0) {
            log.warn("[EduAi] 重复提交答题卡已拦截: examId={}, studentId={}", examId, studentId);
            throw new BusinessException("该考试下该学生已提交过答题卡，请勿重复提交");
        }
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
        // 试卷校验：自有卷必须存在、已发布、卷下有题；任意卷不校验
        if (!anyMode) {
            requirePublishedPaper(paperId, null);
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
            log.warn("[EduAi] 阅卷任务失败(异常): taskId={}, msg={}", taskId, e.toString());
        } catch (Throwable t) {
            // Error（如 UnsatisfiedLinkError/OOM）不是 Exception，上面两个 catch 兜不住；
            // 不处理会让任务永久停在 PROCESSING（runWithTimeout 的 ExecutionException 只记日志）。
            if (isInterruptedByShutdown(t)) {
                keepResumable(taskId, t);
                return;
            }
            changeStage(taskId, EduMarkingTask.STATUS_FAILED, "失败", null, null,
                    t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage(), true);
            log.warn("[EduAi] 阅卷任务失败(未捕获): taskId={}, msg={}", taskId, t.toString());
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
     * 把题号锚定到「哪个 OCR 文字块」上。
     *
     * <p>优先按题号前缀定位；题号可能被检测框切出文字块（实测外来卷大字版式如此），
     * 此时改用题干前 8 字做兜底特征。定位不到时返回 {@code null}，前端退化为列表展示。</p>
     *
     * @param no    题号（如 "1" / "1."），可为空
     * @param stem  题干（兜底定位用），可为空
     * @param pages 逐页结构化数据（含 blocks）
     * @return 锚点（no/page/x/y/width/height）；定位不到时返回 {@code null}
     */
    private static Map<String, Object> locateAnchor(String no, String stem, List<Map<String, Object>> pages) {
        if (pages == null) {
            return null;
        }
        String wantNo = StringUtils.isBlank(no) ? "" : normalizeNo(no);
        // 注意：题干的归一化必须保留中文。normalizeNo 只留 [0-9a-z]，会把题干汉字全剥掉，
        // 「题干特征」就恒为空、兜底永不生效（这正是第一版没生效的原因）。
        String wantStem = normalizeText(stem);
        // 题号可能根本没进文字块：实测外来卷大字版式下，检测框把「1.」与题干切成两块，
        // 文字块里只剩题干，于是按题号前缀永远匹配不到。这里用题干前 8 字做兜底特征。
        String stemProbe = wantStem.length() > 8 ? wantStem.substring(0, 8) : wantStem;
        if (wantNo.isEmpty() && stemProbe.isEmpty()) {
            return null;
        }
        // 两轮定位：**先题号、后题干**。
        // 单轮把两种信号混在一起会被「同名句」骗到别的页：26 题「晴川历历汉阳树，……」的题干特征，
        // 和第 8 题 B 选项「晴川历历汉阳树，芳草萋萋鹦鹉洲」在同一个块里对得上，
        // 一旦先命中选项行，这道题就被定位到第 2 页，右栏「第 6~10 题」里就冒出第 26 题。
        // 题号是强特征（题号段必然在题首），先穷尽它，再用题干特征兜底 OCR 没读出题号的题。
        for (int round = 0; round < 2; round++) {
            boolean byNo = round == 0;
            for (Map<String, Object> pageNode : pages) {
                if (!(pageNode.get("blocks") instanceof List<?> list)) {
                    continue;
                }
                for (int blockIndex = 0; blockIndex < list.size(); blockIndex++) {
                    Object item = list.get(blockIndex);
                    if (!(item instanceof Map<?, ?> block)) {
                        continue;
                    }
                    Object text = block.get("text");
                    if (text == null) {
                        continue;
                    }
                    String rawText = String.valueOf(text);
                    boolean hit;
                    if (byNo) {
                        String blockNo = leadingQuestionNo(rawText);
                        hit = !wantNo.isEmpty() && !blockNo.isEmpty()
                                && fixOcrDigits(blockNo).equals(fixOcrDigits(wantNo));
                    } else {
                        // 题干轮只认题目行：选项行常整句复述题干，命中它等于定位到隔壁题。
                        hit = !stemProbe.isEmpty() && !isOptionLine(rawText)
                                && normalizeText(rawText).contains(stemProbe);
                    }
                    if (hit) {
                        var anchor = new LinkedHashMap<String, Object>();
                        anchor.put("no", no);
                        anchor.put("page", pageNode.get("page"));
                        anchor.put("x", block.get("x"));
                        anchor.put("y", block.get("y"));
                        anchor.put("width", block.get("width"));
                        anchor.put("height", block.get("height"));
                        // 命中块原文 + 其后 2 块：题干常被 OCR 拆成多块（「16.树树皆秋色，」
                        // 与「。（王绩《野望》）（2分）」两块），分值标注落在续块上，
                        // 只看命中块会漏掉分值而回落到整卷兜底分。调用方用完会移除本键。
                        anchor.put("_blockText", joinBlockText(list, blockIndex, 2));
                        return anchor;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 把命中块与其后 {@code lookAhead} 个块拼成一段文本（供解析「（N分）」标注）。
     *
     * @param list       当前页的文字块列表（存储顺序即识别顺序）
     * @param from       命中块下标
     * @param lookAhead  向后多看几块
     * @return 拼接文本；块缺失时跳过
     */
    private static String joinBlockText(List<?> list, int from, int lookAhead) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= Math.min(from + lookAhead, list.size() - 1); i++) {
            Object node = list.get(i);
            if (node instanceof Map<?, ?> block && block.get("text") != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(block.get("text"));
            }
        }
        return sb.toString();
    }

    /**
     * 是否选项行（{@code A.}/{@code B、} 这类）。
     *
     * <p>题干特征定位要跳过它们：选择题的选项经常把题干原句抄一遍，
     * 命中选项行会让锚点落到别的题上。</p>
     *
     * @param rawText 文字块文本
     * @return true 表示该块是选项行
     */
    private static boolean isOptionLine(String rawText) {
        if (rawText == null) {
            return false;
        }
        String text = rawText.trim().toLowerCase();
        if (text.length() < 3) {
            return false;
        }
        char head = text.charAt(0);
        if (head < 'a' || head > 'd') {
            return false;
        }
        char sep = text.charAt(1);
        return sep == '.' || sep == '、' || sep == '．' || sep == ' ';
    }

    /**
     * 卷面每题的「（N分）」分值标注。
     *
     * <p>分值写在卷面上是确定性信息，不该指望模型每次都能抽出来：
     * ANY 抽取的 prompt 只让模型认「每题X分/共X分」这类就近归属标注，
     * 一整卷每题尾标「（2分）」时常被返回 {@code null}，
     * 于是每题都掉到整卷兜底分（配置默认 100），出现「卷面明明 2 分，AI 分与老师分却都是 100」。
     * 这里按命中题块原文兜底解析，解析不到才用兜底分。</p>
     */
    private static final java.util.regex.Pattern SCORE_MARKER =
            java.util.regex.Pattern.compile("[（(]\\s*(\\d{1,3})\\s*分\\s*[）)]");

    /**
     * 从题块原文里解析「（N分）」分值。
     *
     * @param rawText 题块原文
     * @return 分值；无标注或超出合理范围返回 null
     */
    private static Integer parseQuestionScore(String rawText) {
        if (rawText == null) {
            return null;
        }
        var matcher = SCORE_MARKER.matcher(rawText);
        if (!matcher.find()) {
            return null;
        }
        try {
            int value = Integer.parseInt(matcher.group(1));
            return value > 0 && value <= 100 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 把版面区域坐标归一化到 0~1。
     *
     * <p>扫描用的是预处理图（可能被缩放），展示用的是原图，两者尺寸不一定相同；
     * 归一化后与展示尺寸解耦，前端按渲染尺寸相乘即可。</p>
     *
     * @param boxes  版面区域（像素空间）
     * @param width  该页像素宽
     * @param height 该页像素高
     * @return 归一化后的区域列表
     */
    private static List<Map<String, Object>> normalizeLayout(List<EduPaperScanner.LayoutBox> boxes,
                                                             int width, int height) {
        var out = new ArrayList<Map<String, Object>>();
        if (boxes == null) {
            return out;
        }
        for (EduPaperScanner.LayoutBox box : boxes) {
            var node = new LinkedHashMap<String, Object>();
            node.put("label", box.label());
            node.put("x", ratio(box.x(), width));
            node.put("y", ratio(box.y(), height));
            node.put("width", ratio(box.width(), width));
            node.put("height", ratio(box.height(), height));
            out.add(node);
        }
        return out;
    }

    /**
     * 把文字块坐标归一化到 0~1（保留文本与置信度）。
     *
     * @param blocks 文字块（像素空间）
     * @param width  该页像素宽
     * @param height 该页像素高
     * @return 归一化后的文字块列表
     */
    private static List<Map<String, Object>> normalizeBlocks(List<EduPaperScanner.ScanBlock> blocks,
                                                             int width, int height) {
        var out = new ArrayList<Map<String, Object>>();
        if (blocks == null) {
            return out;
        }
        for (EduPaperScanner.ScanBlock block : blocks) {
            var node = new LinkedHashMap<String, Object>();
            node.put("text", block.text());
            node.put("confidence", block.confidence());
            node.put("x", ratio(block.x(), width));
            node.put("y", ratio(block.y(), height));
            node.put("width", ratio(block.width(), width));
            node.put("height", ratio(block.height(), height));
            out.add(node);
        }
        return out;
    }

    /**
     * 归一化单个坐标分量（裁剪到 0~1）。
     *
     * @param value 像素值
     * @param total 该轴总像素
     * @return 0~1 的比值；{@code total} 非法时返回 0
     */
    private static double ratio(float value, int total) {
        if (total <= 0) {
            return 0d;
        }
        double value0 = value / total;
        if (value0 < 0d) {
            return 0d;
        }
        return Math.min(value0, 1d);
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
        var pages = new ArrayList<Map<String, Object>>();
        var mergedBlocks = new ArrayList<EduPaperScanner.ScanBlock>();
        // 版面区域同样跨页合并并加页偏移：region 策略要按区域归属文字块（坐标必须与 blocks 同空间）
        var mergedLayoutBoxes = new ArrayList<EduPaperScanner.LayoutBox>();
        int blockCount = 0;
        int pageNo = 0;
        for (byte[] imageData : images) {
            pageNo++;
            // 图片级扫描管线：可选 ONNX 布局矫正 -> 原预处理 -> 版面+OCR。
            // 矫正默认关闭；开启时未检出或失败也回退原图，不改变原有阅卷路径。
            PaperScanPipeline.ScanOutcome pageOutcome = paperScanPipeline.scan(imageData);
            EduPaperScanner.ScanResult pageScan = pageOutcome.scan();
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
            // 版面块跨页合并：抽取时按题号块归属，题号可能跨页连续，合并后统一结构化才能保题序。
            // 多页 y 坐标会撞车，加页偏移区分（页高未知时用 100000 兜底，只保相对顺序）。
            int pageH = pageScan.pageHeight() > 0 ? pageScan.pageHeight() : 100000;
            if (pageScan.blocks() != null) {
                for (EduPaperScanner.ScanBlock b : pageScan.blocks()) {
                    mergedBlocks.add(new EduPaperScanner.ScanBlock(b.text(), b.confidence(),
                            b.x(), b.y() + (float) (pageNo - 1) * pageH, b.width(), b.height()));
                }
            }
            if (pageScan.layoutBoxes() != null) {
                for (EduPaperScanner.LayoutBox box : pageScan.layoutBoxes()) {
                    mergedLayoutBoxes.add(new EduPaperScanner.LayoutBox(box.label(), box.x(),
                            box.y() + (float) (pageNo - 1) * pageH, box.width(), box.height()));
                }
            }
            // 结构化页面：坐标原样落库，供前端按原图等比叠放。
            // 只存数量的话前端无法还原版式，这正是「同版式展示」缺的那一段数据。
            // 坐标在扫描器里是「预处理图」的像素空间，而展示用的是原图（可能被缩放），
            // 因此这里统一归一化到 0~1，与展示尺寸解耦，避免整体错位。
            int pw = pageScan.pageWidth();
            int ph = pageScan.pageHeight();
            var pageNode = new LinkedHashMap<String, Object>();
            pageNode.put("page", pageNo);
            pageNode.put("width", pw);
            pageNode.put("height", ph);
            pageNode.put("layout", normalizeLayout(pageScan.layoutBoxes(), pw, ph));
            pageNode.put("blocks", normalizeBlocks(pageScan.blocks(), pw, ph));
            pages.add(pageNode);
            log.info("[EduScan] 第 {}/{} 页识别完成，累计文本长度={}", pageNo, images.size(), mergedText.length());
        }
        String text = mergedText.toString();
        if (StringUtils.isBlank(text)) {
            throw new BusinessException("未从图片识别到文字，请确认图片清晰、方向正确后重试");
        }
        changeStage(taskId, EduMarkingTask.STATUS_PROCESSING, "解析题目", null, null, null, false);

        List<EduPaperScanner.ScannedQuestion> scanned;
        EduPaperScanner.AnyScan anyScan = null;
        if (anyMode) {
            anyScan = paperScanner.extractAnyScan(mergedBlocks, mergedLayoutBoxes, text);
            scanned = anyScan.questions();
        } else {
            scanned = paperScanner.extractSelfQuestions(mergedBlocks, text);
        }
        if (scanned.isEmpty()) {
            throw new BusinessException("未能从图片中解析出题目，请检查图片质量或改用另一种模式");
        }

        // 匿名任务的学生回填：上传时没指定学生（anonymous）时，
        // ANY 卷用模型抽取的卷面学生、SELF 卷用正则从卷头「姓名/学号」栏抽取，
        // 学号优先、姓名兜底，回填任务学生标识 —— 老师在记录列表里能看到「这份卷是谁的」；
        // 识别到学号且与学生账号学号一致时，学生端也能按学号精确匹配到这份卷。
        String studentName = null;
        String studentNo = null;
        if (anyMode) {
            studentName = anyScan.studentName();
            studentNo = anyScan.studentNo();
        } else {
            // 自有卷：抽题不调模型（分值取数据库），学生信息用正则从卷头抽
            String[] byLines = paperScanner.extractStudentByLines(text);
            studentName = byLines[0];
            studentNo = byLines[1];
        }
        if ("anonymous".equals(task.getMarkingTaskStudentId())
                && (studentNo != null || studentName != null)) {
            String recognized = studentNo != null ? studentNo : studentName;
            task.setMarkingTaskStudentId(recognized);
            taskMapper.updateById(task);
            log.info("[EduScan] 匿名任务回填卷面学生: taskId={}, student={}", taskId, recognized);
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

        // 题号锚点：抽取出的题目来自合并文本，这里回到逐页文字块按题号前缀定位，
        // 前端据此把分数标记贴到原图的对应位置。
        var questionAnchors = new ArrayList<Map<String, Object>>();
        // 与 scanned 同序的「卷面（N分）标注」提示：模型没给出 score 时的兜底分值来源
        var scanScoreHints = new ArrayList<Integer>();
        for (EduPaperScanner.ScannedQuestion q : scanned) {
            Map<String, Object> anchor = locateAnchor(q.no(), q.stem(), pages);
            if (anchor != null) {
                scanScoreHints.add(parseQuestionScore(String.valueOf(anchor.remove("_blockText"))));
                questionAnchors.add(anchor);
            } else {
                scanScoreHints.add(null);
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
        content.put("pages", pages);
        content.put("questionAnchors", questionAnchors);
        // 卷面识别到的学生（ANY=模型抽取 / SELF=卷头正则）：前端在详情头部展示
        // 「卷面识别：姓名 学号」，与任务学生标识（可能被回填为学号）互补。
        if (studentName != null || studentNo != null) {
            var recognized = new java.util.LinkedHashMap<String, Object>();
            recognized.put("name", studentName);
            recognized.put("no", studentNo);
            content.put("recognizedStudent", recognized);
        }
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
        for (int scannedIndex = 0; scannedIndex < scanned.size(); scannedIndex++) {
            EduPaperScanner.ScannedQuestion item = scanned.get(scannedIndex);
            Integer paperScoreHint = scannedIndex < scanScoreHints.size()
                    ? scanScoreHints.get(scannedIndex) : null;
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
            // 分值优先级：自有卷 DB 为准；DB 无/任意卷用抽取分；最后兜底表单值
            int maxScore;
            if (relation != null && relation.getPaperQuestionScore() != null
                    && relation.getPaperQuestionScore() > 0) {
                maxScore = relation.getPaperQuestionScore();
            } else if (item.score() != null && item.score() > 0) {
                maxScore = item.score();
                log.info("[EduAi] 题号 {} 采用卷面抽取分值 {}", item.no(), maxScore);
            } else if (paperScoreHint != null) {
                // 模型没给 score 时，用题块原文里的「（N分）」标注兜底，
                // 不再一律回落到整卷默认分（否则卷面 2 分会显示成 100）
                maxScore = paperScoreHint;
                log.info("[EduAi] 题号 {} 采用卷面「（N分）」标注分值 {}", item.no(), maxScore);
            } else {
                maxScore = fallbackScore;
            }

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
        // 判分完成后再落解析结果：这样姓名/学号/总分/逐题得分一次写全（扫描阶段只有满分、无得分）
        persistParseResults(taskId);
        return done;
    }

    /**
     * 把试卷解析结果结构化落库（姓名/学号/总分/逐题）。
     *
     * <p>原本这些结果只散在 {@code edu_answer_sheet.answer_sheet_content} 与
     * {@code edu_marking_result.marking_result_detail} 两个 JSON 列里，无法按字段检索；
     * 这里按解析项（STUDENT / TOTAL / QUESTION）逐行落到 {@code edu_marking_sheet_parse}。</p>
     *
     * <p>解析失败绝不能影响阅卷结果：整段 try/catch，只记 warn。</p>
     *
     * @param taskId 任务标识
     */
    private void persistParseResults(String taskId) {
        try {
            EduMarkingTask task = taskMapper.selectByTaskId(taskId);
            if (task == null) {
                return;
            }
            List<EduMarkingResult> rows = markingResultMapper.selectByTaskId(taskId);
            Map<String, Object> content = Map.of();
            if (task.getMarkingTaskAnswerSheetId() != null) {
                EduAnswerSheet sheet = answerSheetMapper.selectById(task.getMarkingTaskAnswerSheetId());
                if (sheet != null) {
                    content = parseJsonMap(sheet.getAnswerSheetContent());
                }
            }
            Map<String, Object> recognized = content.get("recognizedStudent") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();

            sheetParseMapper.deleteByTaskId(taskId);

            // 1) 姓名 / 学号
            var student = newParseRow(task, EduMarkingSheetParse.KIND_STUDENT, "");
            student.setParseStudentName(trimOrNull(recognized.get("name")));
            student.setParseStudentNo(trimOrNull(recognized.get("no")));
            sheetParseMapper.insert(student);

            // 2) 卷面总分：优先卷面标注（「满分：100分」），否则用逐题满分合计
            Integer paperTotal = parsePaperTotalScore(String.valueOf(content.getOrDefault("text", "")));
            int fullSum = 0;
            for (EduMarkingResult row : rows) {
                fullSum += parseDetailMaxScore(row.getMarkingResultDetail());
            }
            var totalRow = newParseRow(task, EduMarkingSheetParse.KIND_TOTAL, "");
            totalRow.setParseTotalScore(paperTotal != null ? paperTotal : (fullSum > 0 ? fullSum : null));
            totalRow.setParseSource(paperTotal != null ? "RULE" : "CALC");
            sheetParseMapper.insert(totalRow);

            // 3) 逐题：题号 / 题干 / 学生作答 / 满分 / 得分 / 对错
            Map<String, Map<String, Object>> anchors = anchorIndexByNo(content);
            int order = 0;
            for (EduMarkingResult row : rows) {
                var detail = parseJsonMap(row.getMarkingResultDetail());
                String no = String.valueOf(detail.getOrDefault("recognizedNo", ""));
                var question = newParseRow(task, EduMarkingSheetParse.KIND_QUESTION, no);
                String stem = truncate(detail.get("stem"), 4000);
                String answer = truncate(detail.get("studentAnswer"), 4000);
                if (stem == null || stem.isBlank()) {
                    // 模型「补题干」这一步会因模型不可用而静默失败（实测 43 题 stem 全为 null，
                    // 题干文本全落在 studentAnswer 里）。这里按作答文本确定性地剥出题干，
                    // 保证解析结果表的「题目」列永远有内容。
                    stem = deriveStem(answer);
                }
                question.setParseQuestionStem(stem);
                question.setParseStudentAnswer(answer);
                question.setParseFullScore(parseDetailMaxScore(row.getMarkingResultDetail()));
                int full = question.getParseFullScore() == null ? 0 : question.getParseFullScore();
                int earned = row.getMarkingResultScore() == null ? 0 : row.getMarkingResultScore();
                question.setParseEarnedScore(earned);
                question.setParseCorrect(full > 0 ? (earned >= full ? 1 : 0) : null);
                question.setParseSource(String.valueOf(detail.getOrDefault("source", "")));
                Map<String, Object> anchor = anchors.get(normalizeNo(no));
                if (anchor != null) {
                    question.setParsePage(asInt(anchor.get("page")));
                    question.setParseRegionX(asDouble(anchor.get("x")));
                    question.setParseRegionY(asDouble(anchor.get("y")));
                    question.setParseRegionW(asDouble(anchor.get("width")));
                    question.setParseRegionH(asDouble(anchor.get("height")));
                }
                order++;
                sheetParseMapper.insert(question);
            }
            log.info("[EduAi] 试卷解析结果已落库: taskId={}, 行数={}", taskId, rows.size() + 2);
        } catch (Exception e) {
            log.warn("[EduAi] 试卷解析结果落库失败（不影响阅卷结果）: taskId={}, err={}", taskId, e.getMessage());
        }
    }

    /** 构造一行解析结果（归属任务/答题卡，非逐题行题号用空串以命中唯一键）。 */
    private EduMarkingSheetParse newParseRow(EduMarkingTask task, String kind, String questionNo) {
        var row = new EduMarkingSheetParse();
        row.setParseOwnerUserId(task.getOwnerUserId());
        row.setParseTaskId(task.getMarkingTaskTaskId());
        row.setParseAnswerSheetId(task.getMarkingTaskAnswerSheetId());
        row.setParseKind(kind);
        row.setParseQuestionNo(questionNo == null ? "" : questionNo.trim());
        return row;
    }

    /** 题号归一化 → 锚点，供解析结果记录来源区域。 */
    private Map<String, Map<String, Object>> anchorIndexByNo(Map<String, Object> content) {
        var index = new java.util.HashMap<String, Map<String, Object>>();
        for (Map<String, Object> anchor : toMapList(content.get("questionAnchors"))) {
            index.putIfAbsent(normalizeNo(String.valueOf(anchor.get("no"))), anchor);
        }
        return index;
    }

    /** 截断长文本，避免 TEXT 列被单题超长内容撑爆。 */
    private static String truncate(Object value, int max) {
        String text = String.valueOf(value == null ? "" : value).trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    /**
     * 从「学生作答」文本里确定性地剥出题干。
     *
     * <p>规则切题路径只切出「题号 + 作答」，题干靠模型补；模型不可用时那道补题干的调用
     * 会静默失败，题干文本就只剩在 studentAnswer 里。这里去掉题号前缀与「（N分）」分值标注。</p>
     *
     * <p><b>不再在第一个选项行处截断</b>：选择题的 A./B./C./D. 是题干的组成部分，
     * 早先在此截断导致题干只剩问句。判分时模型既看不到选项、又（任意卷）没有标准答案，
     * 只能回「无法判断」并给 0 分 —— 实测 5 题里 4 题被这样误判为 0。
     * 唯一「带上选项」的那题判对了，反证选项必须留在题干里。</p>
     *
     * @param studentAnswer 识别到的作答文本（含题干 + 选项）
     * @return 题干（含选项）；无法得出返回 null
     */
    private static String deriveStem(String studentAnswer) {
        if (studentAnswer == null || studentAnswer.isBlank()) {
            return null;
        }
        String text = studentAnswer.trim()
                .replaceFirst("^\\s*\\d+\\s*[.、．]\\s*", "")
                .replaceAll("[（(]\\s*\\d{1,3}\\s*分\\s*[）)]", "")
                .trim();
        return text.isEmpty() ? null : truncate(text, 4000);
    }

    private static String trimOrNull(Object value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    private static Integer asInt(Object value) {
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double asDouble(Object value) {
        try {
            return value == null ? null : Double.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析卷面标注的总分（「满分：100分」「总分 100」这类）。
     *
     * <p>任意卷的满分此前只能由上传表单手填，卷面自己印的「满分：100分」没被识别；
     * 这里从版面全文里抽，作为结构化解析结果的「总分」字段。</p>
     *
     * @param text 版面全文
     * @return 抽到的总分；没有标注返回 null
     */
    private static Integer parsePaperTotalScore(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        var matcher = java.util.regex.Pattern
                .compile("(满分|总分|总得分|得分)\\s*[:：]?\\s*(\\d{1,4})\\s*分?")
                .matcher(text);
        while (matcher.find()) {
            Integer value = asInt(matcher.group(2));
            if (value != null && value > 0 && value <= 1000) {
                return value;
            }
        }
        return null;
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
        // 落库之后再推流：断线重订阅的老师能从库里拿到与推送一致的状态
        publishProgress(taskId, status, stage, total, done, error);
    }

    /**
     * 把阶段变化推给订阅了该任务的 SSE 连接。
     *
     * <p>推送失败绝不能影响阅卷本身：整段包在 try/catch 里，只记 debug。</p>
     *
     * @param taskId 任务业务标识
     * @param status 任务状态
     * @param stage  阶段描述
     * @param total  题目总数（可为空）
     * @param done   已判分数（可为空）
     * @param error  失败原因（可为空）
     */
    private void publishProgress(String taskId, int status, String stage,
                                 Integer total, Integer done, String error) {
        try {
            boolean terminal = status == EduMarkingTask.STATUS_DONE || status == EduMarkingTask.STATUS_FAILED;
            MarkingProgressVO vo;
            if (terminal) {
                // 终态读库：失败路径传的 total/done 都是 null，而库里保留着最后一次的
                // 题目数 / 已判分数，直接拿库里的行拼一个准确的收尾快照
                EduMarkingTask latest = taskMapper.selectByTaskId(taskId);
                vo = latest != null ? toProgressVo(latest) : buildProgressVo(taskId, status, stage, total, done, error);
            } else {
                vo = buildProgressVo(taskId, status, stage, total, done, error);
            }
            if (terminal) {
                // 终态：广播 done 并主动收流，前端不必等下一次轮询或超时
                progressHub.complete(taskId, "done", vo);
            } else {
                progressHub.broadcast(taskId, "progress", vo);
            }
        } catch (Exception e) {
            log.debug("[EduAi] 推送阅卷进度失败(忽略，不影响阅卷): taskId={}, err={}", taskId, e.getMessage());
        }
    }

    /**
     * 由阶段参数拼进度 VO（非终态路径；库里可能还没写入 total/done）。
     *
     * @param taskId 任务业务标识
     * @param status 任务状态
     * @param stage  阶段描述
     * @param total  题目总数（可为空）
     * @param done   已判分数（可为空）
     * @param error  失败原因（可为空）
     * @return 进度 VO
     */
    private MarkingProgressVO buildProgressVo(String taskId, int status, String stage,
                                              Integer total, Integer done, String error) {
        MarkingProgressVO vo = new MarkingProgressVO();
        vo.setTaskId(taskId);
        vo.setTaskStatus(status);
        vo.setStage(stage);
        vo.setTotal(total);
        vo.setDoneCount(done);
        vo.setError(error);
        vo.setFinished(false);
        vo.setPercent(percentOf(total, done));
        vo.setUpdateTime(LocalDateTime.now());
        return vo;
    }

    /**
     * 由任务实体拼进度 VO（建连快照 / 终态收尾）。
     *
     * @param task 任务实体
     * @return 进度 VO
     */
    private MarkingProgressVO toProgressVo(EduMarkingTask task) {
        Integer status = task.getMarkingTaskStatus();
        boolean terminal = Integer.valueOf(EduMarkingTask.STATUS_DONE).equals(status)
                || Integer.valueOf(EduMarkingTask.STATUS_FAILED).equals(status);
        MarkingProgressVO vo = new MarkingProgressVO();
        vo.setTaskId(task.getMarkingTaskTaskId());
        vo.setTaskStatus(status);
        vo.setStage(task.getMarkingTaskStage());
        vo.setTotal(task.getMarkingTaskTotal());
        vo.setDoneCount(task.getMarkingTaskDoneCount());
        vo.setError(task.getMarkingTaskError());
        vo.setMode(task.getMarkingTaskMode());
        vo.setStudentId(task.getMarkingTaskStudentId());
        vo.setFinished(terminal);
        Integer percent = percentOf(task.getMarkingTaskTotal(), task.getMarkingTaskDoneCount());
        // 注意：三元分支含 int 字面量时会自动拆箱 percent，percent 为 null 且非终态会 NPE —— 全部保持 Integer 装箱
        vo.setPercent(percent != null ? percent : (terminal ? Integer.valueOf(100) : null));
        vo.setUpdateTime(LocalDateTime.now());
        return vo;
    }

    /**
     * 百分比：题目数未知时返回 null（前端用不确定态进度条），已知时按已判分数折算。
     *
     * @param total 题目总数
     * @param done  已判分数
     * @return 0-100 或 null
     */
    private static Integer percentOf(Integer total, Integer done) {
        if (total != null && total > 0 && done != null) {
            return Math.min(100, (int) Math.round(done * 100.0 / total));
        }
        return null;
    }

    /**
     * 取任务当前进度快照（SSE 建连时先推一帧）。
     *
     * @param taskId 任务业务标识
     * @return 进度快照
     */
    @Override
    public MarkingProgressVO getProgress(String taskId) {
        EduMarkingTask task = loadTaskQuietly(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return toProgressVo(task);
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
        Integer v = parseDetailMaxScoreOrNull(detail);
        return v != null ? v : properties.getMarking().getMaxScore();
    }

    /**
     * 从 detail JSON 取本题满分（可空版本）。
     *
     * <p>扫描链路（SELF/ANY）判分实际采用的是卷面分值（SELF 为 paper_question_score，
     * ANY 为卷面抽取分值），结果行 detail 里已经存了真实满分；题库分值/配置默认值
     * 与卷面分值可能不一致（实测 SELF 卷每题 10 分、默认口径 5 分），只信 detail
     * 才不会出现「得分 10 / 满分 5」与教师修正被钳到 5 分的错位。</p>
     *
     * @param detail 详情 JSON
     * @return 满分；缺失/非法时 null
     */
    private Integer parseDetailMaxScoreOrNull(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        try {
            var node = MAPPER.readTree(detail);
            int v = node.path("maxScore").asInt(0);
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
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
     * 取文字块最前面的题号 token（例如 {@code 1.} → {@code 1}、{@code 4.赤道…} → {@code 4}）。
     *
     * <p>不用 {@link #normalizeNo(String)} 的 startsWith：整块文本归一化后可能把后面的
     * 分值或页码也拼进同串（如 {@code 10题·满分57分} → {@code 1057}），会让题号 1 误命中 10 题页眉。</p>
     *
     * @param raw 文字块文本
     * @return 首题号；不是题号开头的块返回空串
     */
    private static String leadingQuestionNo(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.trim().toLowerCase();
        var matcher = QUESTION_NO_HEAD.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        String separator = matcher.group(2);
        boolean hasSeparator = separator != null && !separator.isBlank();
        // 裸题号「2」允许，但「10题·满分…」这类「数字后直接跟汉字」不是题号。
        if (!hasSeparator && matcher.end() < text.length()) {
            return "";
        }
        return matcher.group(1);
    }

    /**
     * 文本归一化：保留中英文与数字，去掉空白与中英标点。
     *
     * <p>用于「按题干特征定位文字块」。不能复用 {@link #normalizeNo}：它只保留 {@code [0-9a-z]}，
     * 会把题干里的汉字全部剥掉，导致特征恒为空。中文题干的匹配必须走这里。</p>
     *
     * @param raw 原始文本
     * @return 归一化文本
     */
    private static String normalizeText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[\\s\\p{Punct}，。、；：？！（）〔〕《》〈〉“”‘’·—…￥【】]", "");
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
        return AnswerSimilarity.similarity(standard, student);
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
        // 存识别原文：题干兜底（deriveStem）与人工复核都需要它。
        // 「学生作答」展示与判分分别由结构化接口与 markAnyQuestion 归一化出实质作答，
        // 不在此处改写，避免把兜底题干也截成单个字母。
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
     * <p>策略：客观题先做「归一化 + 最长公共子序列相似度」比对，达到
     * {@link AnswerSimilarity#SIMILARITY_THRESHOLD} 即给满分；不达标才交回原有链路
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
            // 判断/选择先做符号与选项归一化（√→对、(C)→C）：学生作答里常混着题干
            // （OCR 把「（√）」连题干一起识别），LCS 对符号不敏感，直接比对会误判。
            String type = question.getQuestionType().trim().toUpperCase();
            String extracted = EduAiService.extractObjectiveAnswer(type, studentAnswer);
            if ("JUDGE".equals(type) && extracted != null) {
                boolean right = extracted.equals(EduAiService.normalizeJudgeAnswer(question.getQuestionAnswer()));
                var outcome = new EduAiService.MarkingOutcome();
                outcome.setSource("AI");
                outcome.setScore(right ? maxScore : 0);
                outcome.setComment(right ? "判断正确" : String.format("判断错误，正确答案：%s",
                        EduAiService.normalizeJudgeAnswer(question.getQuestionAnswer())));
                return outcome;
            }
            if ("SINGLE_CHOICE".equals(type) && extracted != null) {
                boolean right = extracted.equalsIgnoreCase(question.getQuestionAnswer() == null
                        ? "" : question.getQuestionAnswer().trim());
                var outcome = new EduAiService.MarkingOutcome();
                outcome.setSource("AI");
                outcome.setScore(right ? maxScore : 0);
                outcome.setComment(right ? "选择正确" : String.format("选择错误，正确答案：%s",
                        question.getQuestionAnswer()));
                return outcome;
            }
            double sim = similarity(question.getQuestionAnswer(), studentAnswer);
            if (sim >= AnswerSimilarity.SIMILARITY_THRESHOLD) {
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
     * 获取「与图片同版式」的结构化解析结果。
     *
     * <p>逐页返回版面区域与文字块的像素坐标、页尺寸，再附题号锚点与判分明细（含知识点），
     * 前端据此把分数标记/对错/知识点按原图位置叠放并支持点击编辑。</p>
     *
     * @param taskId 任务标识
     * @return 结构化数据
     */
    @Override
    public Map<String, Object> getStructure(String taskId) {
        EduMarkingTask task = taskMapper.selectByTaskId(taskId);
        List<EduMarkingResult> rows = markingResultMapper.selectTaskResults(taskId, EduUserContext.ownerScope());
        // 手工提交答题卡的链路只落结果行、不落任务表：这类任务没有答卷图片与版面可解析，
        // 但结果本身仍可展示，若一并报「任务不存在」会让同版式解析整页打不开
        if (task == null && rows.isEmpty()) {
            throw new BusinessException("任务不存在");
        }
        var out = new LinkedHashMap<String, Object>();
        out.put("taskId", taskId);
        out.put("mode", task == null ? null : task.getMarkingTaskMode());
        out.put("paperId", task == null ? null : task.getMarkingTaskPaperId());

        Map<String, Object> content = Map.of();
        if (task != null && task.getMarkingTaskAnswerSheetId() != null) {
            EduAnswerSheet sheet = answerSheetMapper.selectById(task.getMarkingTaskAnswerSheetId());
            if (sheet != null) {
                content = parseJsonMap(sheet.getAnswerSheetContent());
            }
        }
        List<Map<String, Object>> pages = toMapList(content.get("pages"));
        out.put("pageCount", content.getOrDefault("pageCount", 0));
        out.put("pages", pages);
        List<Map<String, Object>> displayAnchors = toMapList(content.get("questionAnchors"));
        // 卷面识别到的学生（ANY 链路扫描时模型/正则抽取）：详情头部展示用
        out.put("recognizedStudent", content.get("recognizedStudent"));

        var questions = new ArrayList<Map<String, Object>>();
        for (EduMarkingResult row : rows) {
            var item = new LinkedHashMap<String, Object>();
            item.put("resultId", row.getMarkingResultId());
            item.put("questionId", row.getMarkingResultQuestionId());
            item.put("score", row.getMarkingResultAiScore());
            item.put("teacherScore", row.getMarkingResultTeacherScore());
            item.put("comment", row.getMarkingResultAiComment());
            // 老师评语单独返回：编辑框初值用它（为空时前端回落 AI 评语展示），
            // 保存时显式传 comment 才会覆盖（见 confirmMarking 的非空判断）
            item.put("teacherComment", row.getMarkingResultTeacherComment());
            Map<String, Object> detail = parseJsonMap(row.getMarkingResultDetail());
            item.put("no", detail.get("recognizedNo"));
            item.put("stem", detail.get("stem"));
            item.put("studentAnswer", EduPaperScanner.extractInlineMark(
                    detail.get("studentAnswer") == null ? null : String.valueOf(detail.get("studentAnswer"))));
            item.put("maxScore", detail.get("maxScore"));
            item.put("source", detail.get("source"));
            if (row.getMarkingResultQuestionId() != null) {
                EduQuestion question = questionMapper.selectById(row.getMarkingResultQuestionId());
                if (question != null) {
                    item.put("knowledgePointIds", question.getQuestionKnowledgePointIds());
                    item.put("knowledgePoints", resolveKnowledgePoints(question.getQuestionKnowledgePointIds()));
                    // 题干回落题库：SELF 链路抽题不调模型（stem 留空），展示与「入题库」预填都靠题库补
                    if (!StringUtils.hasText((String) item.get("stem"))) {
                        item.put("stem", question.getQuestionStem());
                    }
                }
            }
            questions.add(item);
        }
        List<Map<String, Object>> displayQuestions = questions;
        if (shouldSplitLegacyAggregate(questions, pages)) {
            List<Map<String, Object>> repaired = splitLegacyAggregated(questions.getFirst(), pages);
            if (repaired.size() > 1) {
                displayQuestions = repaired;
                displayAnchors = locateSplitAnchors(repaired, pages);
                log.info("[EduMarking] 旧任务整页一题改为版面展示拆分: taskId={}, rows={}", taskId, repaired.size());
            }
        }
        out.put("questionAnchors", displayAnchors);
        out.put("questions", displayQuestions);
        out.put("overallComment", resolveOverallComment(questions, content));
        return out;
    }

    /**
     * 是否需要对「整页一题」结果做只读版面拆分展示。
     *
     * <p>拆分是给**旧的无成绩聚合行**兜底展示用的：早期版本把一整页识别成一题，
     * 拆分后按版面文字块还原出多道虚拟子题，只为让老师看清卷面结构。
     * 虚拟行 {@code resultId} 为负、前端禁用编辑且保存时被过滤，本身不可判分。</p>
     *
     * <p><b>为什么必须排除已判分的行</b>：拆分会把每行的 {@code score} 与
     * {@code teacherScore} 置空。若这一行其实是**真实成绩**（典型场景：整张卷只有
     * 一道大题/作文，AI 已判分或老师已给分），拆分后汇总仍读原始行，
     * 于是出现「AI 总分 50 · 老师总分 50，但逐题 AI 分整列空白、老师也无法逐题打分」
     * 的自相矛盾界面。已判分的单题直接按原始行展示，老师可继续逐题修正。</p>
     *
     * @param questions 结果行装配出的题目（当前仅 1 行时才可能拆分）
     * @param pages     答卷版面页数据
     * @return true 表示这是无成绩的旧聚合行，可按版面拆分展示
     */
    static boolean shouldSplitLegacyAggregate(List<Map<String, Object>> questions,
                                              List<Map<String, Object>> pages) {
        if (questions == null || questions.size() != 1 || pages == null || pages.isEmpty()) {
            return false;
        }
        Map<String, Object> only = questions.getFirst();
        // 有任一成绩（AI 分或老师分）即为真实判分结果，保留原始行，不做会清空分数的只读拆分
        return only.get("score") == null && only.get("teacherScore") == null;
    }

    /**
     * 对历史任务里「整页被收成一题」的结果做只读展示拆分。
     *
     * <p>旧任务已落库，不能直接改判分行；复用仍保存的版面文字块按题号重新切题，
     * 只给前端右侧提供逐题展示与锚点，判分明细仍以原结果行为准。</p>
     *
     * @param original 原单行结果
     * @param pages    页面文字块
     * @return 拆分后的虚拟题目；拆不出多题时返回原单行
     */
    private List<Map<String, Object>> splitLegacyAggregated(Map<String, Object> original,
                                                             List<Map<String, Object>> pages) {
        List<EduPaperScanner.ScanBlock> blocks = new ArrayList<>();
        for (Map<String, Object> page : pages) {
            for (Map<String, Object> item : toMapList(page.get("blocks"))) {
                Object text = item.get("text");
                if (text == null) {
                    continue;
                }
                blocks.add(new EduPaperScanner.ScanBlock(
                        String.valueOf(text),
                        item.get("confidence") == null ? 0f : Float.parseFloat(String.valueOf(item.get("confidence"))),
                        floatValue(item.get("x")),
                        floatValue(item.get("y")),
                        floatValue(item.get("width")),
                        floatValue(item.get("height"))));
            }
        }
        List<EduPaperScanner.ScannedQuestion> split = paperScanner.extractByLayout(blocks);
        if (split.size() <= 1) {
            return List.of(original);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < split.size(); i++) {
            EduPaperScanner.ScannedQuestion q = split.get(i);
            Map<String, Object> row = new LinkedHashMap<>(original);
            row.put("resultId", -1000L - i);
            row.put("questionId", 0);
            row.put("no", q.no());
            row.put("stem", q.stem() == null ? "" : q.stem());
            row.put("studentAnswer", q.answer() == null ? "" : q.answer());
            row.put("maxScore", null);
            row.put("score", null);
            row.put("teacherScore", null);
            row.put("comment", null);
            row.put("teacherComment", null);
            row.put("virtual", true);
            out.add(row);
        }
        return out;
    }

    /**
     * 为只读拆分的虚拟题目重新定位锚点。
     */
    private List<Map<String, Object>> locateSplitAnchors(List<Map<String, Object>> rows,
                                                         List<Map<String, Object>> pages) {
        var anchors = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Object no = row.get("no");
            Object stem = row.get("stem");
            Map<String, Object> anchor = locateAnchor(
                    no == null ? "" : String.valueOf(no),
                    stem == null ? "" : String.valueOf(stem),
                    pages);
            if (anchor != null) {
                anchors.add(anchor);
            }
        }
        return anchors;
    }

    /**
     * 顶部 AI 总体评语：优先取已保存字段，否则由各题 AI 评语汇总。
     *
     * @param questions 题目列表
     * @param content   答题卡结构内容
     * @return 总体评语；没有可展示内容时返回 null
     */
    private String resolveOverallComment(List<Map<String, Object>> questions, Map<String, Object> content) {
        Object saved = content.get("overallComment");
        if (saved instanceof CharSequence s && StringUtils.isNotBlank(s.toString())) {
            return s.toString().trim();
        }
        if (questions.isEmpty()) {
            return null;
        }
        if (questions.size() == 1) {
            Object singleComment = questions.getFirst().get("comment");
            if (singleComment instanceof CharSequence c && StringUtils.isNotBlank(c.toString())) {
                return c.toString().trim();
            }
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> q : questions) {
            Object comment = q.get("comment");
            if (!(comment instanceof CharSequence c) || StringUtils.isBlank(c.toString())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('；');
            }
            String no = q.get("no") == null ? "" : String.valueOf(q.get("no")).trim();
            builder.append("第").append(no).append("题：").append(c.toString().trim());
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    /**
     * 读取答卷图片的指定页（同版式展示的打底图）。
     *
     * <p>只按「配置目录 + 任务号 + 页码」重建路径，不采信数据库里存的原串，
     * 并做目录包含校验，从根上避免路径穿越。</p>
     *
     * @param taskId 任务标识
     * @param page   页码（从 1 开始）
     * @return 图片字节
     */
    @Override
    public byte[] getSheetImage(String taskId, int page) {
        if (StringUtils.isBlank(taskId) || page < 1) {
            throw new BusinessException("图片参数不合法");
        }
        Path dir = Paths.get(properties.getMarking().getImageDir()).toAbsolutePath().normalize();
        Path file = dir.resolve(taskId + "-" + page + ".png").normalize();
        if (!file.startsWith(dir)) {
            throw new BusinessException("非法的图片请求");
        }
        if (!Files.isRegularFile(file)) {
            throw new BusinessException("答卷图片不存在: 第 " + page + " 页");
        }
        try {
            return Files.readAllBytes(file);
        } catch (Exception e) {
            throw new BusinessException("读取答卷图片失败: " + e.getMessage());
        }
    }

    /**
     * 把题目的知识点 id 串解析为「id + 名称 + 描述」列表，供前端展示与点击。
     *
     * <p>只返回 id 串的话前端拿不到名称，按 id 去搜关键字也搜不到，点开只能是空的；
     * 这里一次性取回，前端直接渲染名称、点开即出描述。</p>
     *
     * @param idsCsv 逗号分隔的知识点 id 串，可为空
     * @return 知识点列表；无有效 id 时返回空列表
     */
    private List<Map<String, Object>> resolveKnowledgePoints(String idsCsv) {
        var out = new ArrayList<Map<String, Object>>();
        if (StringUtils.isBlank(idsCsv)) {
            return out;
        }
        var ids = new ArrayList<Long>();
        for (String part : idsCsv.split("[,，\\s]+")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                // 非数字的知识点标识直接忽略
            }
        }
        if (ids.isEmpty()) {
            return out;
        }
        for (EduKnowledgePoint point : knowledgePointMapper.selectBatchIds(ids)) {
            var node = new LinkedHashMap<String, Object>();
            node.put("id", point.getKnowledgePointId());
            node.put("name", point.getKnowledgePointName());
            node.put("description", point.getKnowledgePointDescription());
            out.add(node);
        }
        return out;
    }

    /**
     * 把 JSON 文本解析为 Map。
     *
     * @param json JSON 文本
     * @return 键值映射；为空或解析失败时返回空 Map
     */
    private static Map<String, Object> parseJsonMap(String json) {
        if (StringUtils.isBlank(json)) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 把 JSON 里的对象数组安全转成字符串键 Map 列表。
     *
     * @param value 原始节点
     * @return Map 列表；无值/类型不符时返回空列表
     */
    private static List<Map<String, Object>> toMapList(Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    var node = new LinkedHashMap<String, Object>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        node.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    out.add(node);
                }
            }
        }
        return out;
    }

    private static float floatValue(Object value) {
        if (value instanceof Number n) {
            return n.floatValue();
        }
        if (value != null) {
            try {
                return Float.parseFloat(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // 坐标异常时按 0 处理，不影响文本拆分
            }
        }
        return 0f;
    }

    @Override
    /**
     * 获取结果
    */
    public MarkingResultVO getResult(String taskId) {
        EduMarkingTask task = loadTaskQuietly(taskId);
        List<EduMarkingResult> results = markingResultMapper.selectTaskResults(
                taskId, EduUserContext.ownerScope());
        return buildResultVo(taskId, task, results);
    }

    @Override
    /**
     * 学生查看自己的阅卷结果。
     *
     * <p>归属以「任务上的学生标识」为准，而不是任务归属人：教师上传答卷时把 studentId
     * 记在任务上，学生端只能看 studentId 等于自己学号的任务。此处**不再叠加 ownerScope**
     * ——任务归属人是上传的教师，用学生身份去过滤会一行都查不到。</p>
     *
     * @param taskId    任务标识
     * @param studentId 当前学生学号（登录态 userId）
     * @return 阅卷结果
     */
    public MarkingResultVO getResultForStudent(String taskId, String studentId) {
        EduMarkingTask task = loadTaskQuietly(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        if (!Objects.equals(task.getMarkingTaskStudentId(), studentId)) {
            throw new BusinessException("无权查看该阅卷结果");
        }
        return buildResultVo(taskId, task, markingResultMapper.selectByTaskId(taskId));
    }

    /**
     * 查任务并容错：异步模式下「刚提交、识别尚未产出结果行」是正常状态，
     * 表未建立或查询异常时返回 null，由调用方按「排队中」处理，而不是直接报「任务不存在」。
     *
     * @param taskId 任务标识
     * @return 任务实体或 null
     */
    private EduMarkingTask loadTaskQuietly(String taskId) {
        try {
            return taskMapper.selectByTaskId(taskId);
        } catch (Exception e) {
            log.debug("[EduAi] 查询阅卷任务失败（表可能尚未建立）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 把任务与结果行装配成前端 VO。
     *
     * @param taskId  任务标识
     * @param task    任务实体，可为 null
     * @param results 结果行（已按调用方口径过滤）
     * @return 阅卷结果 VO
     */
    private MarkingResultVO buildResultVo(String taskId, EduMarkingTask task, List<EduMarkingResult> results) {
        boolean taskFailed = task != null && Integer.valueOf(EduMarkingTask.STATUS_FAILED).equals(task.getMarkingTaskStatus());
        boolean taskDone = task != null && Integer.valueOf(EduMarkingTask.STATUS_DONE).equals(task.getMarkingTaskStatus());

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
            pendingVo.setTeacherFinished(isTeacherFinished(task));
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
        vo.setTeacherFinished(isTeacherFinished(task));

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
            // 任意卷无题库题号（questionId=0），教师要靠卷面题号 + 学生作答对位修正
            Map<String, Object> detail = parseJsonMap(r.getMarkingResultDetail());
            Object no = detail.get("recognizedNo");
            qr.setNo(no == null ? null : String.valueOf(no));
            Object answer = detail.get("studentAnswer");
            qr.setStudentAnswer(answer == null ? null : String.valueOf(answer));
            // 扫描链路 detail 里存的是判分实际采用的卷面满分，优先用它，
            // 避免「得分 10 / 满分 5」这类满分口径错位（教师修正上限同源）
            Integer detailMax = parseDetailMaxScoreOrNull(r.getMarkingResultDetail());
            qr.setMaxScore(detailMax != null ? detailMax
                    : fullScores.getOrDefault(r.getMarkingResultQuestionId(), fallbackScore));
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
        if (dto == null) {
            return true;
        }
        persistStudentCorrection(dto.getTaskId(), dto.getStudentName(), dto.getStudentNo());
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
            // 教师修正上限：与结果行一致，优先用 detail 里判分实际采用的卷面满分，
            // 否则 SELF 卷（卷面 10 分）上教师只能改到题库默认的 5 分
            Integer detailMax = parseDetailMaxScoreOrNull(result.getMarkingResultDetail());
            Integer maxScore = detailMax != null ? detailMax
                    : fullScores.getOrDefault(result.getMarkingResultQuestionId(), fallbackScore);
            Integer score = clampScore(adj.getScore(), maxScore);
            if (score != null && !score.equals(adj.getScore())) {
                log.warn("[EduAi] 教师修正分数超出卷面满分，已收敛: resultId={}, questionId={}, 提交={}, 满分={}, 采用={}",
                        adj.getResultId(), result.getMarkingResultQuestionId(), adj.getScore(), maxScore, score);
            }
            result.setMarkingResultTeacherScore(score);
            result.setMarkingResultScore(score);
            // 评语只在调用方显式给出时才覆盖：同版式解析只改对错（不传评语），
            // 若无条件写入会把教师已写好的评语冲成 AI 评语或清空
            if (adj.getComment() != null) {
                result.setMarkingResultTeacherComment(adj.getComment());
                result.setMarkingResultComment(adj.getComment());
            }
            result.setMarkingResultStatus(STATUS_TEACHER_DONE);
            result.setUpdateTime(LocalDateTime.now());
            markingResultMapper.updateById(result);
        }
        log.info("[EduAi] 教师确认阅卷: taskId={}, items={}", dto.getTaskId(), dto.getAdjustments().size());
        return true;
    }

    /**
     * 阅卷完成：把该卷错题按最终分归入对应学生的错题库。
     *
     * <p>「最终分」= 老师分优先、未改则用 AI 分；判为错的题（最终分 &lt;= 0）登记，
     * 并带上试卷标识，让错题能回答「来自哪张卷」。学生取任务 / 答题卡上
     * 卷面识别或人工纠正后的学号；缺学生时拒绝完成，
     * 保证错题一定落到「对应学生」而不是变成无主记录。</p>
     *
     * <p>幂等：任务已置「老师已完成」时直接返回，不重复累加错题次数
     * （错题表按 (学生, 题目) 唯一，重复登记会虚增错误次数）。</p>
     *
     * @param taskId 任务业务标识
     * @return 完成结果（学生 / 试卷 / 本次登记的错题数 / 是否此前已完成）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarkingFinishResultVO finishMarking(String taskId) {
        EduMarkingTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        EduUserContext.checkWritable(task.getOwnerUserId());
        EduAnswerSheet sheet = task.getMarkingTaskAnswerSheetId() == null
                ? findSheet(taskId)
                : answerSheetMapper.selectById(task.getMarkingTaskAnswerSheetId());

        MarkingFinishResultVO vo = new MarkingFinishResultVO();
        vo.setTaskId(taskId);

        // 学生：任务上的学号优先（卷面识别与人工纠正都会写它），其次答题卡
        String studentId = firstNonBlank(task.getMarkingTaskStudentId(),
                sheet == null ? null : sheet.getAnswerSheetStudentId());
        vo.setStudentId(studentId);
        if (StringUtils.isBlank(studentId)) {
            throw new BusinessException("该卷还没有识别到学生，请先用上方「修改学生」设置学号后再点完成");
        }

        Long paperId = resolvePaperId(task, sheet);
        vo.setPaperId(paperId);

        // 幂等：已完成过就不再重复登记错题
        if (Integer.valueOf(1).equals(task.getMarkingTaskTeacherFinished())) {
            vo.setAlreadyFinished(true);
            vo.setWrongCount(0);
            return vo;
        }

        Long examId = task.getMarkingTaskExamId() != null ? task.getMarkingTaskExamId()
                : (sheet == null ? null : sheet.getAnswerSheetExamId());
        String ownerUserId = sheet != null && StringUtils.isNotBlank(sheet.getOwnerUserId())
                ? sheet.getOwnerUserId() : task.getOwnerUserId();

        int recorded = 0;
        for (EduMarkingResult result : listByTask(taskId)) {
            Long questionId = result.getMarkingResultQuestionId();
            // 任意卷没有题库记录（题目标识记 0），错题本只登记真实题目标识
            if (questionId == null || questionId <= 0) {
                continue;
            }
            int finalScore = result.getMarkingResultTeacherScore() != null
                    ? result.getMarkingResultTeacherScore()
                    : (result.getMarkingResultAiScore() == null ? 0 : result.getMarkingResultAiScore());
            if (finalScore > 0) {
                continue;
            }
            try {
                wrongQuestionService.recordWrongQuestion(studentId, questionId, examId, ownerUserId, paperId);
                recorded++;
            } catch (Exception e) {
                // 错题登记是完成动作的附加步骤：单条失败不能让整个「完成」回滚
                log.warn("[EduAi] 完成时错题登记失败(忽略): taskId={}, questionId={}, err={}",
                        taskId, questionId, e.getMessage());
            }
        }

        task.setMarkingTaskTeacherFinished(1);
        task.setMarkingTaskTeacherFinishTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        vo.setWrongCount(recorded);
        vo.setAlreadyFinished(false);
        log.info("[EduAi] 阅卷完成: taskId={}, student={}, paperId={}, 本轮错题={}",
                taskId, studentId, paperId, recorded);
        return vo;
    }

    /**
     * 取错题所属试卷：任务上的试卷优先，其次由答题卡的考试反查。
     *
     * @param task  任务实体
     * @param sheet 答题卡，可为 null
     * @return 试卷标识；链路不全时返回 null
     */
    private Long resolvePaperId(EduMarkingTask task, EduAnswerSheet sheet) {
        if (task != null && task.getMarkingTaskPaperId() != null) {
            return task.getMarkingTaskPaperId();
        }
        if (sheet == null || sheet.getAnswerSheetExamId() == null) {
            return null;
        }
        EduExam exam = examMapper.selectById(sheet.getAnswerSheetExamId());
        return exam == null ? null : exam.getExamPaperId();
    }

    /**
     * 任务是否已由老师点过「完成」。
     *
     * @param task 任务实体，可为 null
     * @return 已完成返回 true
     */
    private static Boolean isTeacherFinished(EduMarkingTask task) {
        return task != null && Integer.valueOf(1).equals(task.getMarkingTaskTeacherFinished());
    }

    /**
     * 取第一个非空白字符串（去首尾空格）。
     *
     * @param values 候选值
     * @return 第一个非空白值；都为空白时返回 null
     */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 保存管理员/老师手动纠正的卷面学生。
     *
     * <p>只提供学生姓名/学号时也允许（调整列表可能为空）；学号非空时同步任务学生标识，
     * 保证记录列表、学生端匹配与答卷都按最终人工确认值走。</p>
     *
     * @param taskId 任务标识
     * @param name   人工姓名字符串；null 表示不修改，空串表示清空
     * @param no     人工学号字符串；null 表示不修改，空串表示清空
     */
    private void persistStudentCorrection(String taskId, String name, String no) {
        if (StringUtils.isBlank(taskId) || (name == null && no == null)) {
            return;
        }
        EduMarkingTask task = taskMapper.selectByTaskId(taskId);
        EduAnswerSheet sheet = task == null ? findSheet(taskId) : null;
        String owner = task != null ? task.getOwnerUserId()
                : (sheet == null ? null : sheet.getOwnerUserId());
        if (owner != null) {
            EduUserContext.checkWritable(owner);
        } else {
            EduUserContext.requireUserId();
        }
        if (task == null && sheet == null) {
            throw new BusinessException("任务不存在，无法修改学生信息");
        }
        EduAnswerSheet answerSheet = null;
        Map<String, Object> content = new LinkedHashMap<>();
        if (task != null && task.getMarkingTaskAnswerSheetId() != null) {
            answerSheet = answerSheetMapper.selectById(task.getMarkingTaskAnswerSheetId());
            if (answerSheet != null) {
                Map<String, Object> existedContent = parseJsonMap(answerSheet.getAnswerSheetContent());
                content.putAll(existedContent);
            }
        } else if (sheet != null) {
            answerSheet = sheet;
            content = parseJsonMap(sheet.getAnswerSheetContent());
            if (content.isEmpty()) {
                content = new LinkedHashMap<>();
            }
        }
        Map<String, Object> recognized = new LinkedHashMap<>();
        Object existed = content.get("recognizedStudent");
        if (existed instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                recognized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        if (name != null) {
            recognized.put("name", StringUtils.isBlank(name) ? null : name.trim());
        }
        if (no != null) {
            recognized.put("no", StringUtils.isBlank(no) ? null : no.trim());
        }
        if (StringUtils.isBlank((String) recognized.get("name")) && StringUtils.isBlank((String) recognized.get("no"))) {
            content.remove("recognizedStudent");
        } else {
            content.put("recognizedStudent", recognized);
        }
        if (answerSheet != null) {
            answerSheet.setAnswerSheetContent(Json.toJson(content));
            if (no != null && StringUtils.isNotBlank(no.trim())) {
                answerSheet.setAnswerSheetStudentId(no.trim());
            }
            answerSheet.setUpdateTime(LocalDateTime.now());
            answerSheetMapper.updateById(answerSheet);
        }
        if (task != null && no != null && StringUtils.isNotBlank(no.trim())) {
            task.setMarkingTaskStudentId(no.trim());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        log.info("[EduAi] 人工纠正卷面学生: taskId={}, name={}, no={}", taskId, name, no);
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
