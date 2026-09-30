package com.chua.starter.eduai.service.impl;

import com.chua.starter.common.support.exception.BusinessException;
import com.chua.common.support.lang.json.Json;
import com.chua.common.support.utils.StringUtils;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.ai.EduPaperScanner;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            wrongQuestionService.recordWrongQuestion(sheet.getAnswerSheetStudentId(),
                    result.getMarkingResultQuestionId(), sheet.getAnswerSheetExamId(), sheet.getOwnerUserId());
            recorded++;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String scanAndMark(byte[] imageData, String mode, Long paperId, String studentId,
                              Long examId, Integer maxScore) {
        // 1. 版面还原 + 文字提取（本地 ONNX：版面 pp-doc-layout、文字 paddleocrv6 检测+识别）
        EduPaperScanner.ScanResult scan = paperScanner.scan(imageData);
        if (StringUtils.isBlank(scan.text())) {
            throw new BusinessException("未从图片识别到文字，请确认图片清晰、方向正确后重试");
        }

        boolean anyMode = !EduPaperScanner.MODE_SELF.equalsIgnoreCase(mode);
        // 2. 结构化：自有卷只抽「题号 + 作答」，任意卷还要抽题干
        List<EduPaperScanner.ScannedQuestion> scanned = anyMode
                ? paperScanner.extractAnyQuestions(scan.text())
                : paperScanner.extractSelfQuestions(scan.text());
        if (scanned.isEmpty()) {
            throw new BusinessException("未能从图片中解析出题目，请检查图片质量或改用另一种模式");
        }

        // 3. 自有卷：题号 → 试卷题项索引。分值与题目 ID 一律取自数据库，这是「准确」的来源
        Map<String, EduPaperQuestion> byNo = new HashMap<>();
        Map<String, EduPaperQuestion> byOrder = new HashMap<>();
        if (!anyMode) {
            if (paperId == null) {
                throw new BusinessException("自有卷必须指定试卷 ID");
            }
            List<EduPaperQuestion> relations = paperQuestionMapper.selectByPaperId(paperId);
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

        // 4. 落答题卡（复用既有表；content 是 JSON 列，必须存合法 JSON，故包成对象，
        //    顺带记录模式与版面区域统计，便于事后核对识别质量）
        // 学生 ID 兜底：前端允许留空（先扫描、后绑定学生是合理场景），
        // 但 answer_sheet_student_id 是 NOT NULL，缺值会让 INSERT 直接失败
        // （实测前端不填该字段时报 "Field 'answer_sheet_student_id' doesn't have a default value"）。
        // 这里统一落一个可检索的占位值，而不是要求前端必填。
        String effectiveStudentId = StringUtils.isBlank(studentId) ? "anonymous" : studentId;

        var content = new java.util.LinkedHashMap<String, Object>();
        content.put("mode", anyMode ? EduPaperScanner.MODE_ANY : EduPaperScanner.MODE_SELF);
        content.put("paperId", paperId);
        content.put("text", scan.text());
        content.put("layoutRegions", scan.layoutRegions());
        content.put("blockCount", scan.blocks().size());
        EduAnswerSheet sheet = new EduAnswerSheet();
        sheet.setOwnerUserId(EduUserContext.requireUserId());
        sheet.setAnswerSheetExamId(examId);
        sheet.setAnswerSheetStudentId(effectiveStudentId);
        sheet.setAnswerSheetContent(Json.toJson(content));
        sheet.setAnswerSheetSubmitTime(LocalDateTime.now());
        sheet.setAnswerSheetStatus(1);
        sheet.setCreateTime(LocalDateTime.now());
        sheet.setUpdateTime(LocalDateTime.now());
        answerSheetMapper.insert(sheet);

        String taskId = UUID.randomUUID().toString().replace("-", "");
        int fallbackScore = maxScore != null && maxScore > 0
                ? maxScore : properties.getMarking().getMaxScore();

        // 先建全部结果行（PENDING），把「需要 LLM 的判分」交给后台线程。
        // 为什么必须异步：扫描阅卷每道题要调一次大模型，串行 3 题实测耗时 37 秒，
        // 同步会让 HTTP 请求长时间挂住（前端表现为「点了没反应」而非报错）。
        // 这与既有手工提交链路一致（plugin.eduai.marking.async 默认 true，先返回 taskId 再轮询）。
        var pending = new ArrayList<PendingItem>();
        for (EduPaperScanner.ScannedQuestion item : scanned) {
            EduPaperQuestion relation = anyMode ? null : matchRelation(item.no(), byNo, byOrder);
            if (!anyMode && relation == null) {
                log.warn("[EduAi] 扫描阅卷：题号 {} 在试卷 {} 中找不到对应题项，跳过", item.no(), paperId);
                continue;
            }
            Long questionId = relation == null ? 0L : relation.getPaperQuestionQuestionId();
            int score = relation == null || relation.getPaperQuestionScore() == null
                    || relation.getPaperQuestionScore() <= 0
                    ? fallbackScore : relation.getPaperQuestionScore();

            // 预占结果行（保持与手工提交一致的状态机：先 PENDING，再写 AI 结果）
            EduMarkingResult row = new EduMarkingResult();
            row.setOwnerUserId(sheet.getOwnerUserId());
            row.setMarkingResultAnswerSheetId(sheet.getAnswerSheetId());
            row.setMarkingResultQuestionId(questionId);
            row.setMarkingResultTaskId(taskId);
            row.setMarkingResultStatus(STATUS_PENDING);
            row.setCreateTime(LocalDateTime.now());
            row.setUpdateTime(LocalDateTime.now());
            markingResultMapper.insert(row);
            pending.add(new PendingItem(row.getMarkingResultId(), questionId, score, item));
        }
        if (pending.isEmpty()) {
            throw new BusinessException("识别到题目但未能匹配到可判分的题项，请确认试卷选择是否正确");
        }

        Thread.ofVirtual().name("edu-scan-mark-" + taskId).start(() -> {
            try {
                gradePending(pending, anyMode);
                log.info("[EduAi] 扫描阅卷后台判分完成: taskId={}, 已判分={}", taskId, pending.size());
            } catch (Exception e) {
                log.error("[EduAi] 扫描阅卷后台判分失败: taskId={}", taskId, e);
            }
        });
        recordWrongQuestions(taskId, sheet);
        log.info("[EduAi] 扫描阅卷已提交: taskId={}, mode={}, 识别题目={}, 待判分={}, 版面={}",
                taskId, anyMode ? EduPaperScanner.MODE_ANY : EduPaperScanner.MODE_SELF,
                scanned.size(), pending.size(), scan.layoutRegions());
        return taskId;
    }

    /**
     * 一条待判分的题目。
     *
     * @param resultId   结果行主键（按主键更新，避免任意卷 question_id 全为 0 时互相覆盖）
     * @param questionId 题目标识（任意卷为 0）
     * @param maxScore   本题满分
     * @param item       识别出的题目（题号 / 题干 / 作答）
     */
    private record PendingItem(Long resultId, Long questionId, int maxScore,
                               EduPaperScanner.ScannedQuestion item) {
    }

    /**
     * 后台逐题判分并写库。
     *
     * @param pending 待判分列表
     * @param anyMode 是否任意卷（任意卷全靠模型判定）
     */
    private void gradePending(List<PendingItem> pending, boolean anyMode) {
        for (PendingItem p : pending) {
            try {
                EduAiService.MarkingOutcome outcome;
                if (anyMode) {
                    outcome = paperScanner.markAnyQuestion(p.item(), p.maxScore());
                } else {
                    EduQuestion question = questionMapper.selectById(p.questionId());
                    if (question == null) {
                        log.warn("[EduAi] 扫描阅卷：题目 {} 不在题库，跳过", p.questionId());
                        markingResultMapper.updateScanOutcomeById(p.resultId(), 0,
                                "题目不在题库", buildScanDetail(p.item(), null, p.maxScore()),
                                LocalDateTime.now());
                        continue;
                    }
                    outcome = tolerantObjectiveOutcome(question, p.item().answer(), p.maxScore());
                }
                // 按【行主键】写结果：任意卷 question_id 统一为 0，
                // 若按 (taskId, questionId) 更新会命中该任务下所有 0 号行、互相覆盖（实测 3 行内容完全相同）
                markingResultMapper.updateScanOutcomeById(p.resultId(), outcome.getScore(),
                        outcome.getComment(), buildScanDetail(p.item(), outcome, p.maxScore()),
                        LocalDateTime.now());
            } catch (Exception e) {
                // 单题失败不能让它永久停在 PENDING：既会卡住轮询的 finished 判定，
                // 也让老师看不到是哪一题出了问题。写入失败原因后继续处理其余题目。
                log.error("[EduAi] 扫描阅卷单题判分失败: resultId={}, no={}", p.resultId(), p.item().no(), e);
                try {
                    markingResultMapper.updateScanOutcomeById(p.resultId(), 0,
                            "判分失败：" + e.getMessage(), buildScanDetail(p.item(), null, p.maxScore()),
                            LocalDateTime.now());
                } catch (Exception ex) {
                    log.error("[EduAi] 写入判分失败结果也失败: resultId={}", p.resultId(), ex);
                }
            }
        }
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
     * 答案相似度（0~1），用于容忍 OCR 个别错字。
     *
     * <p>先归一化（去空白与中英标点、统一小写），再用编辑距离折算：
     * {@code 1 - 距离 / 较长串长度}。例如「做了湖涂事」与「做了糊涂事」
     * 只差 1 个字，相似度 0.8。</p>
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
        int maxLen = Math.max(a.length(), b.length());
        return 1d - (double) editDistance(a, b) / maxLen;
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
     * 编辑距离（Levenshtein），用于答案容错比对。
     *
     * @param a 串 a
     * @param b 串 b
     * @return 编辑距离
     */
    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }

    /**
     * 组装扫描阅卷的批改详情 JSON。
     *
     * <p>现有表没有「学生作答」列，用闲置的 {@code marking_result_detail} 承载，
     * 同时记录对错判定与来源，便于前端与后续统计。</p>
     *
     * @param item    识别出的题目
     * @param outcome 判分结果
     * @param maxScore 本题满分
     * @return 详情 JSON
     */
    private static String buildScanDetail(EduPaperScanner.ScannedQuestion item,
                                          EduAiService.MarkingOutcome outcome,
                                          int maxScore) {
        var detail = new java.util.LinkedHashMap<String, Object>();
        detail.put("scan", true);
        detail.put("recognizedNo", item.no());
        detail.put("stem", item.stem());
        detail.put("studentAnswer", item.answer());
        detail.put("maxScore", maxScore);
        detail.put("score", outcome.getScore());
        detail.put("correct", maxScore > 0 && outcome.getScore() >= maxScore);
        detail.put("source", outcome.getSource());
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
        List<EduMarkingResult> results = markingResultMapper.selectTaskResults(
                taskId, EduUserContext.ownerScope());

        if (results.isEmpty()) {
            /**
             * 业务异常。
             * @param "任务不存在" "任务不存在"
             * @return 业务异常的结果
             */
            throw new BusinessException("任务不存在");
        }

        MarkingResultVO vo = new MarkingResultVO();
        vo.setTaskId(taskId);
        vo.setStatus(results.getFirst().getMarkingResultStatus());
        vo.setTotal(results.size());

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
        vo.setFinished(allDone);
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
