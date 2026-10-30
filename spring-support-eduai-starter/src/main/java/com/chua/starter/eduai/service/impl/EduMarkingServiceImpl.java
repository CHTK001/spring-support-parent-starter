package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.ai.EduPaperScanner;
import com.chua.starter.eduai.ai.MarkingBlankRule;
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
import com.chua.starter.eduai.model.dto.MarkingToBankDTO;
import com.chua.starter.eduai.model.dto.BatchMarkingRequest;
import com.chua.starter.eduai.model.dto.QuestionSaveDTO;
import com.chua.starter.eduai.model.vo.MarkingProgressVO;
import com.chua.starter.eduai.model.vo.MarkingResultVO;
import com.chua.starter.eduai.model.vo.MarkingFinishResultVO;
import com.chua.starter.eduai.model.vo.MarkingFinishPreviewVO;
import com.chua.starter.eduai.model.vo.MarkingPreviewQuestionVO;
import com.chua.starter.eduai.model.vo.MarkingToBankResultVO;
import com.chua.starter.eduai.model.vo.MarkingUndoResultVO;
import com.chua.starter.eduai.model.vo.BatchMarkingResultVO;
import com.chua.starter.eduai.queue.MarkingJob;
import com.chua.starter.eduai.queue.MarkingMessageListener;
import com.chua.starter.eduai.service.IEduMarkingService;
import com.chua.starter.eduai.service.IEduQuestionService;
import com.chua.starter.eduai.service.IEduWrongQuestionService;
import com.chua.starter.eduai.service.MarkingSettingService;
import com.chua.starter.eduai.support.util.AnswerSimilarity;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.GradeLevels;
import com.chua.starter.eduai.support.util.MarkingScanIntentResolver;
import com.chua.starter.eduai.stream.EduMarkingProgressHub;
import com.chua.starter.queue.MessageTemplate;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
     * 判定「近似灰度」时单个像素允许的最大通道差（0~255）。
     *
     * <p>扫描件的白纸往往带一点点色彩偏移，取 12 既能容忍轻微偏色，又不会把
     * 真正的彩色照片判成灰度。</p>
     */
    private static final int GRAY_TOLERANCE = 12;

    /**
     * 卷面缩略图的最长边（像素）。
     *
     * <p>详情页左栏实际显示宽度只有 600~700px，取 800 既能 1:1 呈现，
     * 又不会让老师为了看清题目去放大 —— 缩放后版面框是归一化坐标，叠加不受影响。</p>
     */
    private static final int THUMB_MAX_EDGE = 800;

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
     * 题库服务：外来卷「加入题库」复用既有的保存校验与审核态口径，
     * 不另写一套落库逻辑（否则唯一键、来源类型、审核态会与题库页脱节）
     */
    private final IEduQuestionService questionService;

    /**
     * 阅卷运行时设置（匹配模式）：严谨模式下填空题的作答必须落在题干空位内。
     *
     * <p>可选项：bean 缺失（精简测试上下文）时按宽松处理 —— 门禁缺失只会少判负，
     * 不会因为拿不到配置而把正常作答判 0。</p>
     */
    @Autowired(required = false)
    private MarkingSettingService markingSettingService;

    /**
     * 外来卷入库时的来源类型：与「手动录入 / 老师上传」区分，便于题库里筛出来单独复核
     */
    private static final String SOURCE_TYPE_SCAN = "SCAN";

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
        validateSubmitContent(dto.content());
        rejectDuplicateSheet(dto.examId(), dto.studentId());
        // 0.5 试卷校验：考试必须存在、关联的试卷必须存在且已发布，答案题号必须落在试卷题项上
        validateExamPaper(dto.examId(), dto.answers());

        // 1. 持久化答题卡
        EduAnswerSheet sheet = new EduAnswerSheet();
        sheet.setOwnerUserId(EduUserContext.requireUserId());
        sheet.setAnswerSheetExamId(dto.examId());
        sheet.setAnswerSheetStudentId(dto.studentId());
        sheet.setAnswerSheetContent(dto.content());
        sheet.setAnswerSheetSubmitTime(LocalDateTime.now());
        sheet.setAnswerSheetStatus(1); // 已提交
        sheet.setCreateTime(LocalDateTime.now());
        sheet.setUpdateTime(LocalDateTime.now());
        answerSheetMapper.insert(sheet);

 // 2. 生成 任务标识 并预占 marking_结果
        String taskId = UUID.randomUUID().toString().replace("-", "");
        for (MarkingSubmitDTO.AnswerItem item : dto.answers()) {
            EduMarkingResult result = new EduMarkingResult();
            result.setOwnerUserId(sheet.getOwnerUserId());
            result.setMarkingResultAnswerSheetId(sheet.getAnswerSheetId());
            result.setMarkingResultQuestionId(item.questionId());
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
            dispatchAsync(taskId, sheet.getAnswerSheetId(), dto.answers());
        } else {
            /**
             * mark同步。
             * @param taskId 任务标识
             * @param dto.getAnswers( dto.获取answers(
             */
            markSync(taskId, dto.answers());
        }

        log.info("[EduAi] 答题卡已提交: taskId={}, student={}, async={}",
                taskId, dto.studentId(), properties.getMarking().isAsync());
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
                if (item != null && item.questionId() != null && !onPaper.contains(item.questionId())) {
                    throw new BusinessException("题目 " + item.questionId() + " 不在试卷上，无法阅卷");
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
            log.warn("[EduAi] 答题内容不是合法 JSON，已拒绝（内容已脱敏）");
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
     * 扫码路径的防重复提交：同一考试同一学生已存在排队/处理中/完成的阅卷任务时，
     * 拒绝再次创建新任务；失败任务允许重传，避免 OCR 失败后无法补扫。
     *
     * @param examId    考试 ID，可为空（未绑定考试时不参与判定）
     * @param studentId 学生标识
     */
    private void rejectDuplicateScanTask(Long examId, String studentId) {
        if (examId == null || StringUtils.isBlank(studentId)) {
            return;
        }
        Long exists = taskMapper.selectCount(new LambdaQueryWrapper<EduMarkingTask>()
                .eq(EduMarkingTask::getMarkingTaskExamId, examId)
                .eq(EduMarkingTask::getMarkingTaskStudentId, studentId)
                .ne(EduMarkingTask::getMarkingTaskStatus, EduMarkingTask.STATUS_FAILED));
        if (exists != null && exists > 0) {
            log.warn("[EduAi] 扫码重复提交阅卷任务已拦截: examId={}, studentId={}", examId, studentId);
            throw new BusinessException("该考试下该学生已有阅卷任务，请勿重复扫描提交");
        }
    }

    /**
     * 把阅卷任务投递到队列，由 {@link MarkingMessageListener} 异步消费。
     * @param taskId 任务标识
     * @param answerSheetId answersheetid
     * @param answers answers
     */
    private void dispatchAsync(String taskId, Long answerSheetId, List<MarkingSubmitDTO.AnswerItem> answers) {
        MarkingJob job = new MarkingJob(taskId, answerSheetId, answers);
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
            EduQuestion question = questionMapper.selectById(item.questionId());
            if (question == null) {
                continue;
            }
            int maxScore = fullScores.getOrDefault(item.questionId(), fallbackScore);
            EduAiService.MarkingOutcome outcome = eduAiService.markQuestionWithAi(
                    question.getQuestionType(),
                    question.getQuestionStem(),
                    question.getQuestionAnswer(),
                    item.answer(),
                    maxScore
            );
            applyOutcome(taskId, question, outcome);
        }
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
        List<byte[]> validImages = images == null ? List.of()
                : images.stream().filter(v -> v != null && v.length > 0).toList();
        if (validImages.isEmpty()) {
            throw new BusinessException("上传图片为空");
        }
        if (validImages.size() > MAX_SCAN_IMAGES) {
            throw new BusinessException("单次扫描最多 " + MAX_SCAN_IMAGES + " 张图片");
        }
        // 整卷二维码识别：卷面页脚印有 PAPER:{试卷ID}，解码命中后外来卷自动归回内置卷，
        // 并按试卷 ID + 题号回数据库取标准答案判分（与手动选「自有卷」等价）。
        // 二维码还可携带 ;STUDENT:学号 与 ;EXAM:考试ID，用于自动绑定学生与考试。
        MarkingScanIntentResolver.Resolution intent =
                MarkingScanIntentResolver.resolve(mode, validImages.get(0), paperId, studentId, examId);
        anyMode = intent.anyMode();
        paperId = intent.paperId();
        examId = intent.examId();
        if (intent.fromQr()) {
            log.info("[EduAi] 扫码识别到整卷二维码，自动归为内置卷: paperId={}", paperId);
        }
        if (intent.identityConflict()) {
            throw new BusinessException("替考告警：卷面二维码学生与提交学生不一致，请核对后重新上传");
        }
        if (StringUtils.isBlank(studentId) && StringUtils.isNotBlank(intent.studentNo())) {
            studentId = intent.studentNo();
            log.info("[EduAi] 扫码识别到卷面学生，自动绑定: studentId={}", studentId);
        }
        rejectDuplicateScanTask(examId, studentId);
        if (!anyMode && paperId == null) {
            throw new BusinessException("自有卷必须指定试卷 ID");
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
            // 错题登记已收敛到老师点「完成」时（按最终分 + 学生 + 试卷归因），
            // 判分阶段不再登记，避免同一题被「判分」与「完成」登记两次而虚增错误次数。
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
        // 按页保留原始 blocks/text：SELF 按页抽取时页面间重新开始题号递进，
        // 避免「上一页末题号 + 下一页头题号不递进」导致整页文本被吞进上一题作答。
        var pageBlocksList = new ArrayList<List<EduPaperScanner.ScanBlock>>();
        var pageTextsList = new ArrayList<String>();
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
            // 按页保留原始坐标块与文本：供 SELF 按页抽取（页际重置题号递进）
            if (pageScan.blocks() != null && !pageScan.blocks().isEmpty()) {
                pageBlocksList.add(List.copyOf(pageScan.blocks()));
            }
            if (StringUtils.isNotBlank(pageScan.text())) {
                pageTextsList.add(pageScan.text());
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
            // 按页抽取（页际重置题号递进）：多页合并后统一抽取会把「下一页头题号」
            // 因不递进而吞进上一题作答（实测第 43 题整页文本灌入），
            // 改为每页独立 extractSelfQuestions 再拼接，页内仍走版面块归属→行规则→模型。
            scanned = new ArrayList<>();
            for (int pi = 0; pi < pageBlocksList.size(); pi++) {
                List<EduPaperScanner.ScanBlock> pb = pageBlocksList.get(pi);
                String pt = pi < pageTextsList.size() ? pageTextsList.get(pi) : null;
                scanned.addAll(paperScanner.extractSelfQuestions(pb, pt));
            }
            if (scanned.isEmpty()) {
                scanned = paperScanner.extractSelfQuestions(mergedBlocks, text);
            }
        }
        if (scanned.isEmpty()) {
            // any卷识别全失败时，用 OCR 文字块兜底（防止完全无法阅卷）
            if (anyScan != null && !text.isEmpty()) {
                EduPaperScanner.ScannedQuestion fallback = new EduPaperScanner.ScannedQuestion(
                        "未知题目",
                        null,
                        null,
                        null
                );
                scanned = List.of(fallback);
                log.warn("[EduAi] any卷识别失败，降级为 OCR 文字块兜底: taskId={}", taskId);
            }
            if (scanned.isEmpty()) {
                throw new BusinessException("未能从图片中解析出题目，请检查图片质量或改用另一种模式");
            }
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
                // SELF 卷：题库题干已确定，把行规则误收进作答的题干前缀剥掉
                // （实测 42/43 简答只截到「翻译句子：…」，题干下一行才是学生翻译，
                // 不剥会把题干整段当真作答，学生实际写的部分被相似度稀释成 0 分）。
                EduQuestion question = null;
                if (!anyMode && row.getMarkingResultQuestionId() != null) {
                    question = questionMapper.selectById(row.getMarkingResultQuestionId());
                    if (question != null) {
                        String cleanedAnswer;
                        String qType = String.valueOf(question.getQuestionType()).trim();
                        if ("FILL".equalsIgnoreCase(qType)) {
                            cleanedAnswer = EduPaperScanner.stripFillStem(item.answer(), question.getQuestionStem());
                        } else if ("JUDGE".equalsIgnoreCase(qType)) {
                            // 判断题：题目整段（含尾部页脚污染）一旦被规则切题收进作答，
                            // stripLeadingStem 的 startsWith 会因尾注不匹配而剥离失败，导致
                            // 整段题干冒充作答 → 「与标准答案不一致」。判断题的实质作答只有
                            // 对/错/√/× 符号：剥离题干后仍提取不到判定符号（题干尾段、页脚
                            // 或空），一律按未作答置空——不依赖题干互含判断（OCR 题干与题库
                            // 标点/空格常有差异，互含判断会漏）。
                            cleanedAnswer = EduPaperScanner.stripLeadingStem(item.answer(), question.getQuestionStem());
                            if (EduAiService.extractObjectiveAnswer("JUDGE", cleanedAnswer) == null) {
                                cleanedAnswer = "";
                            }
                        } else {
                            cleanedAnswer = EduPaperScanner.stripLeadingStem(item.answer(), question.getQuestionStem());
                        }
                        item = new EduPaperScanner.ScannedQuestion(
                                item.no(), item.stem(), cleanedAnswer, item.score());
                    }
                }
                EduAiService.MarkingOutcome outcome;
                if (anyMode) {
                    // 切分修复：版面把印刷尾段切进了作答（实测第 15 题题干「…它的面积是」、
                    // 作答「平方厘米。24」）。先把印刷片段回填到题干、原位补一个空位，
                    // 学生填的 24 才会落回空内；修复后的题干也让严谨模式的空位门禁有据可依。
                    MarkingBlankRule.Repair repaired =
                            MarkingBlankRule.repairPrintedSplit(item.stem(), item.answer());
                    if (!Objects.equals(repaired.stem(), item.stem())
                            || !Objects.equals(repaired.answer(), item.answer())) {
                        log.info("[EduAi] 第 {} 题切分修复: 作答「{}」→「{}」，题干「{}」→「{}」",
                                item.no(), item.answer(), repaired.answer(), item.stem(), repaired.stem());
                        item = new EduPaperScanner.ScannedQuestion(
                                item.no(), repaired.stem(), repaired.answer(), item.score());
                    }
                    outcome = paperScanner.markAnyQuestion(item, maxScore);
                } else {
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

    /**
     * new Parse Row
     * @param kind 字符串
     * @param questionNo 字符串
     * @param task 参数
     * @return 返回值
    */
    private EduMarkingSheetParse newParseRow(EduMarkingTask task, String kind, String questionNo) {
        var row = new EduMarkingSheetParse();
        row.setParseOwnerUserId(task.getOwnerUserId());
        row.setParseTaskId(task.getMarkingTaskTaskId());
        row.setParseAnswerSheetId(task.getMarkingTaskAnswerSheetId());
        row.setParseKind(kind);
        row.setParseQuestionNo(questionNo == null ? "" : questionNo.trim());
        return row;
    }

    /**
     * anchor Index By No
     * @param Map<String 参数
     * @param content 参数
     * @return 返回值
    */
    private Map<String, Map<String, Object>> anchorIndexByNo(Map<String, Object> content) {
        var index = new java.util.HashMap<String, Map<String, Object>>();
        for (Map<String, Object> anchor : toMapList(content.get("questionAnchors"))) {
            index.putIfAbsent(normalizeNo(String.valueOf(anchor.get("no"))), anchor);
        }
        return index;
    }

    /**
     * truncate
     * @param max 整数
     * @param value 对象
     * @return 返回值
    */
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

    /**
     * trim Or Null
     * @param value 对象
     * @return 返回值
    */
    private static String trimOrNull(Object value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    /**
     * as Int
     * @param value 对象
     * @return 返回值
    */
    private static Integer asInt(Object value) {
        try {
            return value == null ? null : Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * as Double
     * @param value 对象
     * @return 返回值
    */
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
        return new MarkingProgressVO(
                taskId,
                status,
                stage,
                total,
                done,
                percentOf(total, done),
                error,
                false,
                null,
                null,
                null,  // paperTotalScore：需关联 exam 补全
                LocalDateTime.now());
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
        Integer percent = percentOf(task.getMarkingTaskTotal(), task.getMarkingTaskDoneCount());
        // 注意：三元分支含 int 字面量时会自动拆箱 percent，percent 为 null 且非终态会 NPE —— 全部保持 Integer 装箱
        Integer resolvedPercent = percent != null ? percent : (terminal ? Integer.valueOf(100) : null);
        return new MarkingProgressVO(
                task.getMarkingTaskTaskId(),
                status,
                task.getMarkingTaskStage(),
                task.getMarkingTaskTotal(),
                task.getMarkingTaskDoneCount(),
                resolvedPercent,
                task.getMarkingTaskError(),
                terminal,
                task.getMarkingTaskMode(),
                task.getMarkingTaskStudentId(),
                null,  // paperTotalScore：需关联 exam 补全
                LocalDateTime.now());
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
     * 判断题作答是否「只剩题干」（即学生没有作答）。
     *
     * <p>判断题的题干整段（含「判断正误：…（　　）」与可能的页脚尾注）被规则切题收进
     * 作答时，两端都去掉空白与标点后比较：作答净文本与题干净文本重合（互为子串）即
     * 视为「作答=题干」，学生实际没有写判定符号。</p>
     *
     * @param answer 已剥过题干前缀的作答文本（可能仍残留题干尾段/页脚）
     * @param stem   题库题干
     * @return true 表示作答只剩题干，应按未作答处理
     */
    private static boolean judgeAnswerIsStemLike(String answer, String stem) {
        if (StringUtils.isBlank(answer) || StringUtils.isBlank(stem)) {
            return false;
        }
        String a = normalizeText(answer);
        String s = normalizeText(stem);
        if (a.isEmpty() || s.isEmpty()) {
            return false;
        }
        // 作答净文本与题干净文本重合（互含或相等）→ 作答就是题干
        return a.equals(s) || a.contains(s) || s.contains(a);
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
        // 「学生作答」统一保存清洗 + 提取 + 剥题干后的实质作答：客观题从「题干+选项+作答C」
        // 整块 OCR 文本里提取 C/√/×/对错，简答去掉水印/分值/题号后保留学生文字；
        // 外来卷（题干也是抽出来的）还要剥掉混在作答里的题干片段。
        // 这既让前端展示不再把整段题干当作答，也让判分直接面对真实作答。
        // SELF 卷 item.stem() 为 null，normalizeAnyAnswer 退化为原「清洗+提取」行为。
        detail.put("studentAnswer", EduPaperScanner.normalizeAnyAnswer(item.answer(), item.stem()));
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
        detail.put("studentAnswer", EduPaperScanner.normalizeAnyAnswer(item.answer(), item.stem()));
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
        // 严谨模式：填空题的作答必须落在题干空位内。传入的是剥离题干碎片后的作答，
        // 剥不掉的碎片说明学生把答案写在了括号外（或整段印刷文字被收进了作答）。
        // 「宽松」模式不做这道门禁，仍按文本内容判分。
        if (markingSettingService != null && markingSettingService.strictMatch()
                && MarkingBlankRule.outsideBlank(question.getQuestionStem(), studentAnswer)) {
            var outside = new EduAiService.MarkingOutcome();
            outside.setSource("AI");
            outside.setScore(0);
            outside.setComment("未填写在题目空格内（严谨模式），本题不给分");
            return outside;
        }
        if (isObjectiveType(question.getQuestionType())) {
            // 判断/选择先做符号与选项归一化（√→对、(C)→C）：学生作答里常混着题干
            // （OCR 把「（√）」连题干一起识别），LCS 对符号不敏感，直接比对会误判。
            String type = question.getQuestionType().trim().toUpperCase();
            String extracted = EduAiService.extractObjectiveAnswer(type, studentAnswer);
            // 判断题作答为空（stripLeadingStem 剥离题干后学生没有写判定符号）→ 未作答
            // 判断作答提取不到判定符号（"·二次验收专用 1" 之类页脚残留）同样按未作答：判断题
            // 的实质作答只有 对/错/√/× 符号，OCR 残留的题干尾段/页脚不构成作答。
            if ("JUDGE".equals(type) && (StringUtils.isBlank(studentAnswer) || extracted == null)) {
                var outcome = new EduAiService.MarkingOutcome();
                outcome.setSource("AI");
                outcome.setScore(0);
                outcome.setComment("未作答");
                return outcome;
            }
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
        // no(归一化) → 干净作答：锚点补「作答」文本，前端把「题号·作答」标记贴到卷面对应位置
        var anchorAnswers = new java.util.HashMap<String, String>();
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
            // 切分修复（只读兜底）：修复上线前入库的行仍是「题干断在『…它的面积是』、
            // 作答是『平方厘米。24』」。这里按判分时同一套规则修一次再展示，老任务
            // 不用重新阅卷也能看到正常题面；新任务在判分时已按修复后的文本落库。
            MarkingBlankRule.Repair repaired = MarkingBlankRule.repairPrintedSplit(
                    detail.get("stem") == null ? null : String.valueOf(detail.get("stem")),
                    detail.get("studentAnswer") == null ? null : String.valueOf(detail.get("studentAnswer")));
            item.put("stem", repaired.stem());
            String rawAnswer = repaired.answer();
            item.put("studentAnswer", EduPaperScanner.extractInlineMark(rawAnswer));
            if (detail.get("recognizedNo") != null) {
                String no = normalizeNo(String.valueOf(detail.get("recognizedNo")));
                String answerText = EduPaperScanner.extractInlineMark(
                        EduPaperScanner.cleanScannedAnswer(rawAnswer));
                if (StringUtils.isNotBlank(answerText)) {
                    anchorAnswers.put(no, answerText);
                }
            }
            item.put("maxScore", detail.get("maxScore"));
            item.put("source", detail.get("source"));
            if (row.getMarkingResultQuestionId() != null) {
                EduQuestion question = questionMapper.selectById(row.getMarkingResultQuestionId());
                if (question != null) {
                    item.put("knowledgePointIds", question.getQuestionKnowledgePointIds());
                    item.put("knowledgePoints", resolveKnowledgePoints(question.getQuestionKnowledgePointIds()));
                    // 标准答案：SELF 内置卷判分与「参考答案」展示都回题库取值
                    item.put("standardAnswer", question.getQuestionAnswer());
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
        // 锚点补「作答」：前端把每题的干净作答贴到卷面对应位置，作答应显示在正确区域
        for (Map<String, Object> anchor : displayAnchors) {
            if (anchor.get("no") == null) {
                continue;
            }
            String no = normalizeNo(String.valueOf(anchor.get("no")));
            String answer = anchorAnswers.get(no);
            if (StringUtils.isNotBlank(answer)) {
                anchor.put("answer", answer);
            }
        }
        // 识别失败题（有题号锚点、但 OCR 没产出结果行）：补一行「待老师协助」占位，
        // 让老师在详情页可见、可补录，而不是静默缺行。self(自有卷)与 any(外来卷)
        // 用不同提示文案，便于老师按卷型处理。
        boolean anyMode = "ANY".equalsIgnoreCase(out.get("mode") == null ? "" : String.valueOf(out.get("mode")));
        Set<String> resultNos = new HashSet<>();
        for (Map<String, Object> q : displayQuestions) {
            Object qNo = q.get("no");
            if (qNo != null) {
                resultNos.add(normalizeNo(String.valueOf(qNo)));
            }
        }
        if (displayAnchors != null) {
            Set<String> filled = new HashSet<>();
            var unrecognized = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> anchor : displayAnchors) {
                if (anchor.get("no") == null) {
                    continue;
                }
                String no = normalizeNo(String.valueOf(anchor.get("no")));
                if (no.isEmpty() || resultNos.contains(no) || !filled.add(no)) {
                    continue;
                }
                var placeholder = new LinkedHashMap<String, Object>();
                placeholder.put("resultId", -1);
                placeholder.put("questionId", null);
                placeholder.put("no", anchor.get("no"));
                placeholder.put("stem", anchor.get("stem") == null ? null : anchor.get("stem"));
                placeholder.put("studentAnswer", "");
                placeholder.put("maxScore", anchor.get("score") == null ? null : anchor.get("score"));
                placeholder.put("source", "UNRECOGNIZED");
                // 自有卷有题库可核对题号；外来卷需老师人工按卷面补录
                placeholder.put("comment", anyMode
                        ? "识别失败，请老师对照卷面人工补录本题"
                        : "识别失败，请核对题库题号后补录本题");
                placeholder.put("unrecognized", true);
                unrecognized.add(placeholder);
            }
            if (!unrecognized.isEmpty()) {
                var merged = new ArrayList<Map<String, Object>>();
                merged.addAll(displayQuestions);
                merged.addAll(unrecognized);
                displayQuestions = merged;
                log.info("[EduMarking] 识别失败题补占位行: taskId={}, count={}, mode={}", taskId, unrecognized.size(), out.get("mode"));
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
        return getSheetImage(taskId, page, null);
    }

    @Override
    public byte[] getSheetImage(String taskId, int page, String variant) {
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
            byte[] bytes = shrinkSheetImage(file, Files.readAllBytes(file));
            return isThumb(variant) ? thumbSheetImage(file, bytes) : bytes;
        } catch (Exception e) {
            throw new BusinessException("读取答卷图片失败: " + e.getMessage());
        }
    }

    /**
     * is Thumb
     * @param variant 字符串
     * @return 返回值
    */
    private static boolean isThumb(String variant) {
        return "thumb".equalsIgnoreCase(variant == null ? null : variant.trim());
    }

    /**
     * 把卷面图缩到 {@value #THUMB_MAX_EDGE}px 以内的灰度 PNG（详情页左栏用）。
     *
     * <p>详情页左栏显示宽度只有 600~700px，直接下发 1240px 原图等于多传一倍字节
     * （实测经网关 73KB ≈ 2.2s，缩略图约 30KB ≈ 0.9s）。缩略图按需生成并落盘缓存
     * （{@code xxx.thumb.png}），源图更新后按修改时间自动失效。</p>
     *
     * <p>任何一步失败都退回原图 —— 图片能看比图片更小重要。</p>
     *
     * @param file   原始图片路径（用于定位缓存）
     * @param source 已做过灰度处理的图片字节
     * @return 缩略图字节（失败时返回 source）
     */
    private static byte[] thumbSheetImage(Path file, byte[] source) {
        String name = file.getFileName().toString();
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            return source;
        }
        Path cache = file.resolveSibling(name.substring(0, name.length() - 4) + ".thumb.png");
        try {
            if (Files.isRegularFile(cache)
                    && Files.getLastModifiedTime(cache).compareTo(Files.getLastModifiedTime(file)) >= 0) {
                return Files.readAllBytes(cache);
            }
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(source));
            if (image == null) {
                return source;
            }
            int maxEdge = Math.max(image.getWidth(), image.getHeight());
            if (maxEdge <= THUMB_MAX_EDGE) {
                return source;
            }
            double ratio = (double) THUMB_MAX_EDGE / maxEdge;
            int width = Math.max(1, (int) Math.round(image.getWidth() * ratio));
            int height = Math.max(1, (int) Math.round(image.getHeight() * ratio));
            BufferedImage small = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D graphics = small.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(small, "png", out)) {
                return source;
            }
            byte[] bytes = out.toByteArray();
            try {
                Files.write(cache, bytes);
            } catch (Exception ignored) {
                // 缓存写不进去不影响本次响应，下次再算一遍即可
            }
            return bytes.length < source.length ? bytes : source;
        } catch (Exception e) {
            log.debug("[EduMarking] 答卷图片缩略图生成跳过: {}", file.getFileName(), e);
            return source;
        }
    }

    /**
     * 把答卷图片压缩成灰度 PNG（无损，只在该图本来就是黑白扫描件时生效）。
     *
     * <p>扫描/拍照得到的答卷基本都是「白纸黑字」，像素的 R/G/B 三个通道几乎相同，
     * 但保存 PNG 时用的是真彩色（colorType=2），每个像素白占 3 倍空间。实测
     * 1240x1800 的一页：原图 171KB，转 8 位灰度 PNG 后 85KB，肉眼无差别。</p>
     *
     * <p>转换结果落盘缓存（{@code xxx.gray.png}），后续请求直接读缓存，不再解码；
     * 源图更新后按修改时间自动失效。任何一步失败都原样返回原始字节 —— 图片能看
     * 比图片更小重要。</p>
     *
     * @param file 原始图片路径
     * @param raw  原始图片字节
     * @return 用于响应的图片字节（可能已换成灰度版）
     */
    private static byte[] shrinkSheetImage(Path file, byte[] raw) {
        String name = file.getFileName().toString();
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            return raw;
        }
        Path cache = file.resolveSibling(name.substring(0, name.length() - 4) + ".gray.png");
        try {
            if (Files.isRegularFile(cache)
                    && Files.getLastModifiedTime(cache).compareTo(Files.getLastModifiedTime(file)) >= 0) {
                return Files.readAllBytes(cache);
            }
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null || !isNearGray(image)) {
                return raw;
            }
            BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(),
                    BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D graphics = gray.createGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(gray, "png", out)) {
                return raw;
            }
            byte[] bytes = out.toByteArray();
            // 灰度版反而更大说明这张图不是真正的黑白扫描件，保留原图
            if (bytes.length >= raw.length) {
                return raw;
            }
            try {
                Files.write(cache, bytes);
            } catch (Exception ignored) {
                // 缓存写不进去不影响本次响应，下次再算一遍即可
            }
            return bytes;
        } catch (Exception e) {
            log.debug("[EduMarking] 答卷图片灰度压缩跳过: {}", file.getFileName(), e);
            return raw;
        }
    }

    /**
     * 判断图片是否近似灰度（黑白扫描件）。
     *
     * <p>按网格抽样，只统计「最亮通道与最暗通道相差不超过 {@value #GRAY_TOLERANCE}」的像素；
     * 抽样中 97% 以上满足才算灰度。留 3% 余量是为了容忍纸面上的彩色印章、红笔批注 ——
     * 少量彩色笔迹转灰也不会影响阅卷，而整页彩色照片（偏差远超阈值）不会被误转。</p>
     *
     * @param image 图片
     * @return true 表示可以安全地转成灰度
     */
    static boolean isNearGray(BufferedImage image) {
        int stepX = Math.max(1, image.getWidth() / 200);
        int stepY = Math.max(1, image.getHeight() / 300);
        long total = 0;
        long gray = 0;
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                total++;
                if (max - min <= GRAY_TOLERANCE) {
                    gray++;
                }
            }
        }
        return total > 0 && gray * 100 >= total * 97;
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

    /**
     * float Value
     * @param value 对象
     * @return 返回值
    */
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
     * @param taskId 字符串
     * @return 返回值
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
            return new MarkingResultVO(
                    taskId,
                    task.getMarkingTaskStatus(),
                    0,
                    0,
                    taskFailed,
                    new ArrayList<>(),
                    task.getMarkingTaskStatus(),
                    task.getMarkingTaskStage(),
                    task.getMarkingTaskError(),
                    isTeacherFinished(task));
        }

        int fallbackScore = properties.getMarking().getMaxScore();
        Map<Long, Integer> fullScores = resolveFullScores(
                answerSheetMapper.selectById(results.getFirst().getMarkingResultAnswerSheetId()));

        List<MarkingResultVO.QuestionResult> list = new ArrayList<>();
        int totalScore = 0;
        boolean allDone = true;
        for (EduMarkingResult r : results) {
            // 任意卷无题库题号（questionId=0），教师要靠卷面题号 + 学生作答对位修正
            Map<String, Object> detail = parseJsonMap(r.getMarkingResultDetail());
            Object no = detail.get("recognizedNo");
            Object answer = detail.get("studentAnswer");
            // 与 getStructure 同口径：旧行里「印刷尾段 + 作答」按切分规则修正后再返回，
            // 老师在工作台与详情页看到的是同一份作答。
            answer = MarkingBlankRule.repairPrintedSplit(
                    detail.get("stem") == null ? null : String.valueOf(detail.get("stem")),
                    answer == null ? null : String.valueOf(answer)).answer();
            // 扫描链路 detail 里存的是判分实际采用的卷面满分，优先用它，
            // 避免「得分 10 / 满分 5」这类满分口径错位（教师修正上限同源）
            Integer detailMax = parseDetailMaxScoreOrNull(r.getMarkingResultDetail());
            MarkingResultVO.QuestionResult qr = new MarkingResultVO.QuestionResult(
                    r.getMarkingResultId(),
                    r.getMarkingResultQuestionId(),
                    no == null ? null : String.valueOf(no),
                    answer == null ? null : String.valueOf(answer),
                    detailMax != null ? detailMax
                            : fullScores.getOrDefault(r.getMarkingResultQuestionId(), fallbackScore),
                    r.getMarkingResultAiScore() == null ? 0 : r.getMarkingResultAiScore(),
                    r.getMarkingResultAiComment(),
                    r.getMarkingResultTeacherScore(),
                    r.getMarkingResultTeacherComment());
            list.add(qr);
            totalScore += qr.teacherScore() != null ? qr.teacherScore() : qr.score();
            if (r.getMarkingResultStatus() == null || r.getMarkingResultStatus() < STATUS_AI_DONE) {
                allDone = false;
            }
        }
        // 完成判定：任务已完结，或所有结果行都已批改（兼容手工提交链路）
        return new MarkingResultVO(
                taskId,
                results.getFirst().getMarkingResultStatus(),
                totalScore,
                results.size(),
                taskDone || allDone,
                list,
                task == null ? null : task.getMarkingTaskStatus(),
                task == null ? null : task.getMarkingTaskStage(),
                task == null ? null : task.getMarkingTaskError(),
                isTeacherFinished(task));
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
        persistStudentCorrection(dto.taskId(), dto.studentName(), dto.studentNo());
        if (dto.adjustments() == null || dto.adjustments().isEmpty()) {
            return true;
        }
        Map<Long, Integer> fullScores = resolveFullScores(findSheet(dto.taskId()));
        int fallbackScore = properties.getMarking().getMaxScore();
        for (MarkingConfirmDTO.ScoreAdjustment adj : dto.adjustments()) {
            if (adj.resultId() == null || adj.resultId() <= 0) {
                // 识别失败补录：resultId 为负/空且带题号 → 为老师补录的新增结果行落库
                if (StringUtils.isNotBlank(adj.no())) {
                    upsertUnrecognizedResult(dto.taskId(), adj);
                }
                continue;
            }
            EduMarkingResult result = markingResultMapper.selectById(adj.resultId());
            if (result == null) {
                continue;
            }
            EduUserContext.checkWritable(result.getOwnerUserId());
            // 教师修正上限：与结果行一致，优先用 detail 里判分实际采用的卷面满分，
            // 否则 SELF 卷（卷面 10 分）上教师只能改到题库默认的 5 分
            Integer detailMax = parseDetailMaxScoreOrNull(result.getMarkingResultDetail());
            Integer maxScore = detailMax != null ? detailMax
                    : fullScores.getOrDefault(result.getMarkingResultQuestionId(), fallbackScore);
            Integer score = clampScore(adj.score(), maxScore);
            if (score != null && !score.equals(adj.score())) {
                log.warn("[EduAi] 教师修正分数超出卷面满分，已收敛: resultId={}, questionId={}, 提交={}, 满分={}, 采用={}",
                        adj.resultId(), result.getMarkingResultQuestionId(), adj.score(), maxScore, score);
            }
            result.setMarkingResultTeacherScore(score);
            result.setMarkingResultScore(score);
            // 评语只在调用方显式给出时才覆盖：同版式解析只改对错（不传评语），
            // 若无条件写入会把教师已写好的评语冲成 AI 评语或清空
            if (adj.comment() != null) {
                result.setMarkingResultTeacherComment(adj.comment());
                result.setMarkingResultComment(adj.comment());
            }
            result.setMarkingResultStatus(STATUS_TEACHER_DONE);
            result.setUpdateTime(LocalDateTime.now());
            markingResultMapper.updateById(result);
        }
        log.info("[EduAi] 教师确认阅卷: taskId={}, items={}", dto.taskId(), dto.adjustments().size());
        // 持久化满意度（1-5）
        if (dto.satisfaction() != null) {
            taskMapper.updateSatisfaction(dto.taskId(), dto.satisfaction(), LocalDateTime.now());
            log.info("[EduAi] 教师满意度已记录: taskId={}, satisfaction={}", dto.taskId(), dto.satisfaction());
        }
        return true;
    }

    /**
     * 识别失败题的教师补录：按题号新建/更新一条结果行，落老师分与评语。
     *
     * <p>OCR 对个别题（版面文字缺失等）没产出结果行，结构接口补了
     * {@code unrecognized=true} 的占位行（{@code resultId=-1}）。老师在该行上
     * 打分/写评语后保存，这里把它真正落库：SELF 卷尽量回题库取题型/题干/满分，
     * 拿不到也以老师提交的分与整卷兜底满分落一条「人工补录」结果行。</p>
     *
     * @param taskId 任务标识
     * @param adj    包含题号的补录调整
     */
    private void upsertUnrecognizedResult(String taskId, MarkingConfirmDTO.ScoreAdjustment adj) {
        EduMarkingTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            return;
        }
        String no = StringUtils.trimToNull(adj.no());
        if (no == null) {
            return;
        }
        String norm = normalizeNo(no);
        // 已有按同题号的结果行（可能先前补录过）：更新而不是重复插入
        List<EduMarkingResult> existing = markingResultMapper.selectByTaskId(taskId);
        for (EduMarkingResult row : existing) {
            Map<String, Object> detail = parseJsonMap(row.getMarkingResultDetail());
            String rowNo = detail.get("recognizedNo") == null ? "" : String.valueOf(detail.get("recognizedNo"));
            if (normalizeNo(rowNo).equals(norm)
                    && row.getMarkingResultQuestionId() != null && row.getMarkingResultQuestionId() > 0) {
                int maxScore = Math.max(1, safeScore(parseDetailMaxScoreOrNull(row.getMarkingResultDetail())));
                Integer score = clampScore(adj.score(), maxScore);
                row.setMarkingResultTeacherScore(score);
                row.setMarkingResultScore(score);
                row.setMarkingResultAiScore(0);
                row.setMarkingResultAiComment("识别失败，由老师人工补录");
                if (adj.comment() != null) {
                    row.setMarkingResultTeacherComment(adj.comment());
                    row.setMarkingResultComment(adj.comment());
                }
                row.setMarkingResultStatus(STATUS_TEACHER_DONE);
                row.setUpdateTime(LocalDateTime.now());
                markingResultMapper.updateById(row);
                log.info("[EduAi] 识别失败题更新补录: taskId={}, no={}, score={}", taskId, norm, score);
                return;
            }
        }

        EduAnswerSheet sheet = task.getMarkingTaskAnswerSheetId() == null
                ? null : answerSheetMapper.selectById(task.getMarkingTaskAnswerSheetId());
        Long questionId = 0L;
        int maxScore = 0;
        String stem = null;
        var detailMap = new LinkedHashMap<String, Object>();
        detailMap.put("scan", true);
        detailMap.put("unrecognized", true);
        detailMap.put("recognizedNo", no);
        if (task.getMarkingTaskPaperId() != null && !"ANY".equalsIgnoreCase(task.getMarkingTaskMode())) {
            List<EduPaperQuestion> relations = paperQuestionMapper.selectByPaperId(task.getMarkingTaskPaperId());
            for (EduPaperQuestion relation : relations) {
                if (StringUtils.isNotBlank(relation.getPaperQuestionNo())
                        && normalizeNo(relation.getPaperQuestionNo()).equals(norm)) {
                    questionId = relation.getPaperQuestionQuestionId();
                    maxScore = relation.getPaperQuestionScore() == null ? 0 : relation.getPaperQuestionScore();
                    EduQuestion question = questionId != null && questionId > 0
                            ? questionMapper.selectById(questionId) : null;
                    if (question != null) {
                        stem = question.getQuestionStem();
                    }
                    detailMap.put("maxScore", maxScore);
                    break;
                }
            }
        }
        if (maxScore <= 0) {
            maxScore = Math.max(1, properties.getMarking().getMaxScore());
        }
        detailMap.put("maxScore", maxScore);
        if (StringUtils.isNotBlank(stem)) {
            detailMap.put("stem", stem);
        }
        detailMap.put("studentAnswer", "");
        detailMap.put("source", "MANUAL");
        EduMarkingResult row = new EduMarkingResult();
        row.setOwnerUserId(task.getOwnerUserId());
        row.setMarkingResultAnswerSheetId(sheet == null ? null : sheet.getAnswerSheetId());
        row.setMarkingResultQuestionId(questionId);
        row.setMarkingResultTaskId(taskId);
        Integer score = clampScore(adj.score(), maxScore);
        row.setMarkingResultTeacherScore(score);
        row.setMarkingResultScore(score);
        row.setMarkingResultAiScore(0);
        row.setMarkingResultAiComment("识别失败，由老师人工补录");
        if (adj.comment() != null) {
            row.setMarkingResultTeacherComment(adj.comment());
            row.setMarkingResultComment(adj.comment());
        }
        row.setMarkingResultStatus(STATUS_TEACHER_DONE);
        row.setMarkingResultDetail(Json.toJson(detailMap));
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        markingResultMapper.insert(row);
        log.info("[EduAi] 识别失败题人工补录落库: taskId={}, no={}, score={}", taskId, norm, score);
    }

    /**
     * safe Score
     * @param s 整数
     * @return 返回值
    */
    private static int safeScore(Integer s) {
        return s == null || s < 0 ? 0 : s;
    }

    /**
     * 阅卷完成：把该卷错题按最终分归入对应学生的错题库。
     *
     * <p>「最终分」= 老师分优先、未改则用 AI 分。判错口径由
     * {@code plugin.eduai.marking.wrong-score-rate} 决定：
     * 得分率低于该比例即记为错（默认 0.6 = 不及格；配 0 退回「只有 0 分才算错」）。
     * 这样满分 10 分的主观题拿 3 分也会进错题本，不再被「分数 &gt; 0 就跳过」漏掉。</p>
     *
     * <p>登记时带上试卷标识与来源任务，让错题能回答「来自哪张卷」，
     * 并让「撤销完成」能精确回退本次登记。</p>
     *
     * <p>幂等：任务已置「老师已完成」时直接返回，不重复累加错题次数
     * （错题表按 (学生, 题目) 唯一，重复登记会虚增错误次数）。</p>
     *
     * @param taskId 任务业务标识
     * @return 完成结果（学生 / 试卷 / 本次登记的错题数 / 登记失败数 / 是否此前已完成）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarkingFinishResultVO finishMarking(String taskId) {
        return finishMarking(taskId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * finish Marking
     * @param excludeQuestionIds 参数
     * @param taskId 字符串
     * @return 返回值
    */
    public MarkingFinishResultVO finishMarking(String taskId, List<Long> excludeQuestionIds) {
        EduMarkingTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        EduUserContext.checkWritable(task.getOwnerUserId());
        EduAnswerSheet sheet = task.getMarkingTaskAnswerSheetId() == null
                ? findSheet(taskId)
                : answerSheetMapper.selectById(task.getMarkingTaskAnswerSheetId());

        // 学生：任务上的学号优先（卷面识别与人工纠正都会写它），其次答题卡
        String studentId = firstNonBlank(task.getMarkingTaskStudentId(),
                sheet == null ? null : sheet.getAnswerSheetStudentId());
        // 未识别学生 = 空 或 占位 anonymous（上传未指定学生且卷面识别不到时服务端写入的值）。
        // 此时拒绝完成：错题必须落到「对应学生」，否则会生成无主错题。
        if (isUnidentifiedStudent(studentId)) {
            throw new BusinessException("该卷还没有识别到学生，请先用上方「修改学生」设置学号后再点完成");
        }

        Long paperId = resolvePaperId(task, sheet);

        // 幂等：已完成过就不再重复登记错题
        if (Integer.valueOf(1).equals(task.getMarkingTaskTeacherFinished())) {
            return new MarkingFinishResultVO(taskId, studentId, paperId, 0, 0, true);
        }

        Long examId = task.getMarkingTaskExamId() != null ? task.getMarkingTaskExamId()
                : (sheet == null ? null : sheet.getAnswerSheetExamId());
        String ownerUserId = sheet != null && StringUtils.isNotBlank(sheet.getOwnerUserId())
                ? sheet.getOwnerUserId() : task.getOwnerUserId();

        // 判错要按每题满分算得分率，满分口径与结果面板一致：结果 detail 里记的优先，
        // 其次按试卷题目分值，最后退到配置的兜底单题分。
        Map<Long, Integer> fullScores = resolveFullScores(sheet);
        double wrongRate = properties.getMarking().getWrongScoreRate();
        Set<Long> excluded = excludeQuestionIds == null || excludeQuestionIds.isEmpty()
                ? Set.of() : new HashSet<>(excludeQuestionIds);

        int recorded = 0;
        int failed = 0;
        for (EduMarkingResult result : listByTask(taskId)) {
            Long questionId = result.getMarkingResultQuestionId();
            // 任意卷没有题库记录（题目标识记 0），错题本只登记真实题目标识
            if (questionId == null || questionId <= 0 || excluded.contains(questionId)) {
                continue;
            }
            int finalScore = result.getMarkingResultTeacherScore() != null
                    ? result.getMarkingResultTeacherScore()
                    : (result.getMarkingResultAiScore() == null ? 0 : result.getMarkingResultAiScore());
            Integer maxScore = parseDetailMaxScoreOrNull(result.getMarkingResultDetail());
            if (maxScore == null || maxScore <= 0) {
                maxScore = fullScores.getOrDefault(questionId, properties.getMarking().getMaxScore());
            }
            if (!isWrongByFinalScore(finalScore, maxScore, wrongRate)) {
                continue;
            }
            try {
                wrongQuestionService.recordWrongQuestion(studentId, questionId, examId, ownerUserId, paperId, taskId);
                recorded++;
            } catch (Exception e) {
                // 错题登记是完成动作的附加步骤：单条失败不能让整个「完成」回滚，
                // 但必须如实计数回报给老师，不能静默吞成「全部成功」。
                failed++;
                log.warn("[EduAi] 完成时错题登记失败(已计入失败数): taskId={}, questionId={}, err={}",
                        taskId, questionId, e.getMessage());
            }
        }

        task.setMarkingTaskTeacherFinished(1);
        task.setMarkingTaskTeacherFinishTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        log.info("[EduAi] 阅卷完成: taskId={}, student={}, paperId={}, 本轮错题={}, 失败={}, 阈值={}, 剔除={}",
                taskId, studentId, paperId, recorded, failed, wrongRate, excluded.size());
        return new MarkingFinishResultVO(taskId, studentId, paperId, recorded, failed, false);
    }

    @Override
    public MarkingFinishPreviewVO previewFinishMarking(String taskId) {
        EduMarkingTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        // 预览是只读操作：不能复用 checkWritable（预置数据会误报「不允许修改」）
        EduUserContext.checkReadable(task.getOwnerUserId());
        EduAnswerSheet sheet = task.getMarkingTaskAnswerSheetId() == null
                ? findSheet(taskId)
                : answerSheetMapper.selectById(task.getMarkingTaskAnswerSheetId());
        String studentId = firstNonBlank(task.getMarkingTaskStudentId(),
                sheet == null ? null : sheet.getAnswerSheetStudentId());
        if (isUnidentifiedStudent(studentId)) {
            throw new BusinessException("该卷还没有识别到学生，请先用上方「修改学生」设置学号后再点完成");
        }
        Map<Long, Integer> fullScores = resolveFullScores(sheet);
        double wrongRate = properties.getMarking().getWrongScoreRate();
        List<MarkingPreviewQuestionVO> questions = new ArrayList<>();
        int wrongCount = 0;
        for (EduMarkingResult result : listByTask(taskId)) {
            Long questionId = result.getMarkingResultQuestionId();
            if (questionId == null || questionId <= 0) {
                continue;
            }
            int finalScore = result.getMarkingResultTeacherScore() != null
                    ? result.getMarkingResultTeacherScore()
                    : (result.getMarkingResultAiScore() == null ? 0 : result.getMarkingResultAiScore());
            Integer maxScore = parseDetailMaxScoreOrNull(result.getMarkingResultDetail());
            if (maxScore == null || maxScore <= 0) {
                maxScore = fullScores.getOrDefault(questionId, properties.getMarking().getMaxScore());
            }
            int safeMax = maxScore == null || maxScore <= 0 ? properties.getMarking().getMaxScore() : maxScore;
            boolean wrong = isWrongByFinalScore(finalScore, safeMax, wrongRate);
            String stem = null;
            EduQuestion question = questionMapper.selectById(questionId);
            if (question != null) {
                stem = question.getQuestionStem();
            }
            java.math.BigDecimal rate = safeMax <= 0 ? java.math.BigDecimal.ZERO
                    : java.math.BigDecimal.valueOf(Math.min(1.0, (double) finalScore / safeMax))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            questions.add(new MarkingPreviewQuestionVO(questionId, stem, finalScore, safeMax, rate, wrong));
            if (wrong) {
                wrongCount++;
            }
        }
        return new MarkingFinishPreviewVO(taskId, studentId, wrongCount, wrongRate, questions);
    }

    @Override
    public List<BatchMarkingResultVO> batchFinishMarking(BatchMarkingRequest request) {
        if (request == null || request.taskIds() == null || request.taskIds().isEmpty()) {
            throw new BusinessException("请选择至少一个任务");
        }
        if (request.taskIds().size() > 50) {
            throw new BusinessException("单次批量操作最多 50 个任务");
        }
        Map<String, List<Long>> excludes = request.excludeQuestionIdsByTask() == null
                ? Map.of() : request.excludeQuestionIdsByTask();
        List<BatchMarkingResultVO> out = new ArrayList<>();
        for (String taskId : request.taskIds()) {
            try {
                MarkingFinishResultVO r = finishMarking(taskId, excludes.get(taskId));
                out.add(new BatchMarkingResultVO(taskId, true, "完成成功",
                        r.wrongCount() == null ? 0 : r.wrongCount(), 0));
            } catch (Exception e) {
                log.warn("[EduAi] 批量完成单任务失败: taskId={}, err={}", taskId, e.getMessage());
                out.add(new BatchMarkingResultVO(taskId, false, e.getMessage(), 0, 0));
            }
        }
        return out;
    }

    @Override
    public List<BatchMarkingResultVO> batchUnfinishMarking(BatchMarkingRequest request) {
        if (request == null || request.taskIds() == null || request.taskIds().isEmpty()) {
            throw new BusinessException("请选择至少一个任务");
        }
        if (request.taskIds().size() > 50) {
            throw new BusinessException("单次批量操作最多 50 个任务");
        }
        List<BatchMarkingResultVO> out = new ArrayList<>();
        for (String taskId : request.taskIds()) {
            try {
                MarkingUndoResultVO r = unfinishMarking(taskId);
                out.add(new BatchMarkingResultVO(taskId, true, "撤销成功", 0, r.removed()));
            } catch (Exception e) {
                log.warn("[EduAi] 批量撤销单任务失败: taskId={}, err={}", taskId, e.getMessage());
                out.add(new BatchMarkingResultVO(taskId, false, e.getMessage(), 0, 0));
            }
        }
        return out;
    }

    /**
     * 撤销「完成」：删掉本次登记的错题并复位完成标记。
     *
     * <p>用途：老师误点完成，或事后改了老师分想按新分数重新归位。
     * 只删 {@code wrong_question_source_task_id = taskId} 的行 —— 同一道题若
     * 也被别的任务登记过，那次记录保留，避免一处撤销连带丢历史。</p>
     *
     * <p>未完成的任务调用它返回 {@code wasFinished=false} 且不删任何东西（幂等）。</p>
     *
     * @param taskId 任务业务标识
     * @return 撤销结果（删除了多少条错题 / 之前是否处于已完成状态）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarkingUndoResultVO unfinishMarking(String taskId) {
        EduMarkingTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        EduUserContext.checkWritable(task.getOwnerUserId());
        EduAnswerSheet sheet = task.getMarkingTaskAnswerSheetId() == null
                ? findSheet(taskId)
                : answerSheetMapper.selectById(task.getMarkingTaskAnswerSheetId());
        String studentId = firstNonBlank(task.getMarkingTaskStudentId(),
                sheet == null ? null : sheet.getAnswerSheetStudentId());

        boolean wasFinished = Integer.valueOf(1).equals(task.getMarkingTaskTeacherFinished());
        int removed = 0;
        if (wasFinished) {
            removed = wrongQuestionService.removeBySourceTask(taskId);
            task.setMarkingTaskTeacherFinished(0);
            task.setMarkingTaskTeacherFinishTime(null);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        log.info("[EduAi] 撤销阅卷完成: taskId={}, student={}, 删除错题={}, 之前已完成={}",
                taskId, studentId, removed, wasFinished);
        return new MarkingUndoResultVO(taskId, studentId, removed, wasFinished);
    }

    /**
     * 按最终分与满分判定是否算错题。
     *
     * <p>口径：0 分一定算错；{@code wrongRate <= 0} 时只有 0 分算错（旧行为）；
     * 否则得分率严格低于阈值才算错 —— 正好等于阈值（如 6/10 对 0.6）不算错，
     * 避免边界上把「刚好及格」的题塞进错题本。</p>
     *
     * @param finalScore 最终分（老师分优先）
     * @param maxScore   该题满分
     * @param wrongRate  判错得分率阈值（0 表示关闭阈值）
     * @return 是否记为错题
     */
    private static boolean isWrongByFinalScore(int finalScore, Integer maxScore, double wrongRate) {
        if (finalScore <= 0) {
            return true;
        }
        if (wrongRate <= 0 || maxScore == null || maxScore <= 0) {
            return false;
        }
        return finalScore < maxScore * wrongRate;
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
     * 未识别学生的占位标识。
     *
     * <p>上传时未指定学生、且卷面也识别不到学号/姓名时，服务端把任务学生写成
     * {@code anonymous}（见 {@code createTask}）。它不是一个真实学生，
     * 「阅卷完成」必须把它当「没识别到学生」对待，否则错题会变成无主记录。</p>
     */
    private static final String ANONYMOUS_STUDENT = "anonymous";

    /**
     * 学生标识是否表示「未识别到学生」。
     *
     * @param studentId 学生标识，可为 null
     * @return 空、或等于占位 {@code anonymous} 时返回 true
     */
    private static boolean isUnidentifiedStudent(String studentId) {
        return StringUtils.isBlank(studentId) || ANONYMOUS_STUDENT.equalsIgnoreCase(studentId.trim());
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

    /**
     * 把外来卷识别出的题目加入题库。
     *
     * <p>逐条处理、单条失败不影响其余：题库侧有 {@code uk_edu_question_stem} 唯一键，
     * 同一题干重复提交时必须给出「已存在」而不是整体报错。</p>
     *
     * @param taskId 任务业务标识
     * @param dto    入题库请求
     * @return 逐条容错的入库结果
     */
    @Override
    public MarkingToBankResultVO addToQuestionBank(String taskId, MarkingToBankDTO dto) {
        if (dto == null || dto.resultIds() == null || dto.resultIds().isEmpty()) {
            throw new BusinessException("请先勾选要加入题库的题目");
        }
        if (!StringUtils.hasText(dto.questionSubject())) {
            throw new BusinessException("请填写学科");
        }
        if (!StringUtils.hasText(dto.questionGradeLevel())) {
            throw new BusinessException("请选择学段");
        }
        if (!StringUtils.hasText(dto.questionGrade())) {
            throw new BusinessException("请填写年级");
        }
        EduMarkingTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        // 自有卷本就是题库来的：再入库只会造重复题目，服务端直接拒绝（前端也不给入口）
        if (!isAnyMode(task)) {
            throw new BusinessException("自有卷的题目来自题库，无需再次加入题库");
        }
        EduUserContext.checkWritable(task.getOwnerUserId());

        String subject = dto.questionSubject().trim();
        String gradeLevel = GradeLevels.normalize(dto.questionGradeLevel().trim());
        String grade = dto.questionGrade().trim();
        int difficulty = dto.questionDifficulty() == null ? 3 : Math.min(5, Math.max(1, dto.questionDifficulty()));
        Map<Long, String> suppliedAnswers = dto.answers() == null ? Map.of() : dto.answers();

        List<String> errors = new ArrayList<>();
        List<String> needAnswerNos = new ArrayList<>();
        int success = 0;
        int duplicate = 0;
        Set<Long> seen = new HashSet<>();
        for (Long resultId : dto.resultIds()) {
            if (resultId == null || !seen.add(resultId)) {
                continue;
            }
            EduMarkingResult row = markingResultMapper.selectById(resultId);
            if (row == null || !taskId.equals(row.getMarkingResultTaskId())) {
                // 只接受本任务的题目：避免拿别的任务的 resultId 越权写入题库
                errors.add("结果行 " + resultId + " 不属于该任务");
                continue;
            }
            Map<String, Object> detail = parseJsonMap(row.getMarkingResultDetail());
            String no = displayNo(detail.get("recognizedNo"));
            String stem = detail.get("stem") == null ? null : String.valueOf(detail.get("stem")).trim();
            if (!StringUtils.hasText(stem)) {
                errors.add(label(no) + "没有识别出题干，无法入库");
                continue;
            }
            int maxScore = safeScore(parseDetailMaxScoreOrNull(row.getMarkingResultDetail()));
            if (maxScore <= 0) {
                maxScore = properties.getMarking().getMaxScore();
            }
            int finalScore = row.getMarkingResultTeacherScore() != null
                    ? row.getMarkingResultTeacherScore()
                    : safeScore(row.getMarkingResultAiScore());
            boolean full = finalScore >= maxScore;

            // 标准答案：老师补写优先；没补写时只有「本次判满分」才用学生作答代替
            // （答对即正确解），判错的题不能把错误作答当标准答案写进题库。
            String answer = suppliedAnswers.get(resultId);
            if (!StringUtils.hasText(answer)) {
                if (!full) {
                    needAnswerNos.add(no);
                    continue;
                }
                answer = detail.get("studentAnswer") == null
                        ? null : String.valueOf(detail.get("studentAnswer")).trim();
            }
            if (!StringUtils.hasText(answer)) {
                needAnswerNos.add(no);
                continue;
            }

            String type = guessQuestionType(stem, answer);
            String options = "SINGLE_CHOICE".equals(type) || "MULTIPLE_CHOICE".equals(type)
                    ? toJsonOptions(stem) : null;
            String bankStem = options == null ? stem : stripOptionsFromStem(stem);
            int score = dto.questionScore() != null && dto.questionScore() > 0
                    ? dto.questionScore() : maxScore;

            QuestionSaveDTO saveDto = new QuestionSaveDTO(
                    null, type, subject, gradeLevel, grade, null, difficulty, score,
                    null, bankStem, null, options, answer,
                    null,
                    "外来卷识别入库（任务 " + taskId + " 第 " + (StringUtils.hasText(no) ? no : resultId) + " 题）",
                    SOURCE_TYPE_SCAN, null, null, false, 1, task.getOwnerUserId());
            try {
                if (questionMapper.countSameQuestion(bankStem, subject, task.getOwnerUserId()) > 0) {
                    duplicate++;
                    continue;
                }
                questionService.saveQuestion(saveDto);
                success++;
            } catch (Exception e) {
                String msg = rootCauseMessage(e);
                log.warn("[EduAi] 外来卷入题库失败: taskId={}, no={}, err={}", taskId, no, msg);
                // 唯一键冲突（并发重复提交）按「已存在」处理，不算失败
                if (msg.contains("uk_edu_question_stem") || msg.contains("Duplicate entry")
                        || msg.contains("question_stem")) {
                    duplicate++;
                } else {
                    errors.add(label(no) + "：" + msg);
                }
            }
        }
        log.info("[EduAi] 外来卷入题库: taskId={}, 选中={}, 成功={}, 重复={}, 失败={}, 缺答案={}",
                taskId, dto.resultIds().size(), success, duplicate, errors.size(), needAnswerNos.size());
        return new MarkingToBankResultVO(success, duplicate, errors.size(), errors, needAnswerNos);
    }

    /**
     * 判断任务是否为「任意卷」（外来卷）。
     *
     * @param task 阅卷任务
     * @return true 表示外来卷
     */
    private static boolean isAnyMode(EduMarkingTask task) {
        return "ANY".equalsIgnoreCase(task.getMarkingTaskMode() == null ? "" : task.getMarkingTaskMode());
    }

    /**
     * 题号展示用：去掉结尾的 {@code .} / {@code 、} 等分隔符。
     *
     * @param raw 识别出的原始题号
     * @return 展示题号；为空时返回空串
     */
    private static String displayNo(Object raw) {
        if (raw == null) {
            return "";
        }
        return String.valueOf(raw).trim().replaceAll("[.、．:：]+$", "").trim();
    }

    /**
     * 拼「第 N 题」前缀，没有题号时退回中性描述。
     *
     * @param no 展示题号
     * @return 逐条结果用的题目标签
     */
    private static String label(String no) {
        return StringUtils.hasText(no) ? "第 " + no + " 题" : "该题";
    }

    /**
     * 取异常根因的单行消息。
     *
     * <p>MyBatis 的异常消息是「### Error updating database.」这类多行摘要，
     * 真正的根因（如 {@code CONSTRAINT xxx failed}）在 {@code getCause()} 里，
     * 此前直接取 {@code e.getMessage()} 会把真因吞掉、只剩一个空白的「第 1 题」。</p>
     *
     * @param e 异常
     * @return 根因单行消息（超长截断）；取不到时退回异常类名
     */
    private static String rootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (!StringUtils.hasText(msg)) {
            msg = e.getMessage();
        }
        if (!StringUtils.hasText(msg)) {
            return root.getClass().getSimpleName();
        }
        String line = msg.lines().filter(StringUtils::hasText).findFirst().orElse(msg).trim();
        return line.length() > 120 ? line.substring(0, 120) + "…" : line;
    }

    /**
     * 从题干与作答特征推断题型。
     *
     * <p>外来卷没有题型字段，只能按形态判断：带成组选项 → 选择；作答是判定符号/「判断」字样 → 判断；
     * 题干含填空横线 → 填空；其余按简答。推断只影响题库里的分类与后续组卷筛选，
     * 老师在题库管理里可随时改正。</p>
     *
     * @param stem   题干
     * @param answer 标准答案（用于判断题判定）
     * @return 题型码值
     */
    static String guessQuestionType(String stem, String answer) {
        String text = stem == null ? "" : stem;
        boolean hasA = text.matches("(?s).*(^|[\\s；;\\n])[AＡ][.、．].*");
        boolean hasB = text.matches("(?s).*(^|[\\s；;\\n])[BＢ][.、．].*");
        boolean hasC = text.matches("(?s).*(^|[\\s；;\\n])[CＣ][.、．].*");
        if (hasA && hasB) {
            // 选项超过一个「多选」判据：题干出现「多选」「选出所有」等字样才认为是多选
            boolean multi = text.contains("多选") || text.contains("所有正确") || text.contains("不止一项");
            return multi && hasC ? "MULTIPLE_CHOICE" : "SINGLE_CHOICE";
        }
        String ans = answer == null ? "" : answer.trim();
        if (ans.matches("[√✓×✗对错TFtf]") || ans.matches("(正确|错误|对|错)")) {
            return "JUDGE";
        }
        if (text.contains("____") || text.contains("＿＿") || text.contains("（  ）") || text.contains("(  )")) {
            return "FILL";
        }
        return "SHORT_ANSWER";
    }

    /**
     * 从题干里抽出选项，转成题库要求的 JSON 数组文本。
     *
     * <p>{@code edu_question.question_options} 是 JSON 列且带
     * {@code CHECK (json_valid(...))}：纯文本（如「A.甲\nB.乙」）会被约束拒绝。
     * 题库页保存选项时也是 {@code JSON.stringify(数组)}，这里保持同一口径。</p>
     *
     * @param stem 题干原文
     * @return 选项 JSON 数组文本；无成组选项时返回 {@code null}（列为 NULL 才过约束）
     */
    static String toJsonOptions(String stem) {
        if (StringUtils.isBlank(stem)) {
            return null;
        }
        List<String> options = extractOptions(stem);
        return options.size() >= 2 ? Json.toJson(options) : null;
    }

    /**
     * 去掉题干里的选项，只留问题本身（选项单独存题库选项字段）。
     *
     * <p>识别口径与 {@link #extractOptions} 一致：行首 `A.…` 整行、或题干内
     * 空格分隔的 `A.… B.… C.…` 行内选项一并移除。无法剔出干净题干时退回原文。</p>
     *
     * @param stem 题干原文
     * @return 去掉选项后的题干；结果为空时退回原文
     */
    static String stripOptionsFromStem(String stem) {
        String out = stem.replaceAll("(?<![A-Za-z0-9])\\s*[A-DＡ-Ｄ][.、．][^\\s；;\\n]*", "");
        String trimmed = out.replaceAll("\\s{2,}", " ").trim();
        return StringUtils.hasText(trimmed) ? trimmed : out.trim();
    }

    /**
     * 从题干里抽出选项列表，兼容两种卷面形态：
     * <ul>
     *   <li>成行选项（OCR 通常把每个选项拆成独立一行）：`A.完壁归赵\nB.守株待兔\nC.画龙点精`；</li>
     *   <li>行内选项（整题一行、选项间空格分隔）：`（） A.90 B.95 C.100 D.110`。</li>
     * </ul>
     * 判断口径与 {@link #guessQuestionType} 一致：选项字母前必须是行首或空白/分号，
     * 避免把题干里的「A.」引用当成选项。
     *
     * @param stem 题干原文
     * @return 匹配到的选项条目；不足 2 个返回空列表（题库要求至少两个选项）
     */
    private static List<String> extractOptions(String stem) {
        List<String> options = new ArrayList<>();
        if (StringUtils.isBlank(stem)) {
            return options;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)(^|[\\s；;\\n])[A-DＡ-Ｄ][.、．][^\\s；;\\n]*(?=[\\s；;\\n]|$)")
                .matcher(stem);
        while (m.find()) {
            String item = m.group().replaceAll("^[\\s；;\\n]+", "").trim();
            if (!item.isEmpty() && options.size() < 10) {
                options.add(item);
            }
        }
        return options;
    }
}
