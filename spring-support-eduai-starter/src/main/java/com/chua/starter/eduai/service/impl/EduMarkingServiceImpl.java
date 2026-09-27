package com.chua.starter.eduai.service.impl;

import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.ai.EduAiService;
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
