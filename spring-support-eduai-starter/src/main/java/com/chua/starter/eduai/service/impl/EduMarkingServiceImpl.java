package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.configuration.EduAiProperties;
import com.chua.starter.eduai.mapper.EduAnswerSheetMapper;
import com.chua.starter.eduai.mapper.EduMarkingResultMapper;
import com.chua.starter.eduai.mapper.EduQuestionMapper;
import com.chua.starter.eduai.model.EduAnswerSheet;
import com.chua.starter.eduai.model.EduMarkingResult;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.MarkingConfirmDTO;
import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;
import com.chua.starter.eduai.model.vo.MarkingResultVO;
import com.chua.starter.eduai.queue.MarkingJob;
import com.chua.starter.eduai.queue.MarkingMessageListener;
import com.chua.starter.eduai.service.IEduMarkingService;
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

    /** Answersheetmapper */
    private final EduAnswerSheetMapper answerSheetMapper;
    /** Marking结果映射器 */
    private final EduMarkingResultMapper markingResultMapper;
    /** Questionmapper */
    private final EduQuestionMapper questionMapper;
    /** EDUAI服务 */
    private final EduAiService eduAiService;
    /** 属性 */
    private final EduAiProperties properties;
    /** 消息模板 */
    private final MessageTemplate messageTemplate;

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
        sheet.setAnswerSheetExamId(dto.getExamId());
        sheet.setAnswerSheetStudentId(dto.getStudentId());
        sheet.setAnswerSheetContent(dto.getContent());
        sheet.setAnswerSheetSubmitTime(LocalDateTime.now());
        sheet.setAnswerSheetStatus(1); // 已提交
        sheet.setAnswerSheetCreateTime(LocalDateTime.now());
        sheet.setAnswerSheetUpdateTime(LocalDateTime.now());
        answerSheetMapper.insert(sheet);

 // 2. 生成 任务标识 并预占 marking_结果
        String taskId = UUID.randomUUID().toString().replace("-", "");
        for (MarkingSubmitDTO.AnswerItem item : dto.getAnswers()) {
            EduMarkingResult result = new EduMarkingResult();
            result.setMarkingResultAnswerSheetId(sheet.getAnswerSheetId());
            result.setMarkingResultQuestionId(item.getQuestionId());
            result.setMarkingResultTaskId(taskId);
            result.setMarkingResultStatus(STATUS_PENDING);
            result.setMarkingResultCreateTime(LocalDateTime.now());
            result.setMarkingResultUpdateTime(LocalDateTime.now());
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
     * 同步阅卷：阻塞直到全部题目批改完成。
     * @param taskId 任务标识
     * @param answers answers
     */
    public void markSync(String taskId, List<MarkingSubmitDTO.AnswerItem> answers) {
        int maxScore = properties.getMarking().getMaxScore();
        for (MarkingSubmitDTO.AnswerItem item : answers) {
            EduQuestion question = questionMapper.selectById(item.getQuestionId());
            if (question == null) {
                continue;
            }
            EduAiService.MarkingOutcome outcome = eduAiService.markQuestionWithAi(
                    question.getQuestionType(),
                    question.getQuestionStem(),
                    question.getQuestionAnswer(),
                    item.getAnswer(),
                    maxScore
            );
            /**
             * applyoutcome。
             * @param taskId 任务标识
             * @param question question
             * @param outcome outcome
             */
            applyOutcome(taskId, question, outcome);
        }
    }

    /**
     * 把单题批改结果写入 DB。
     * @param taskId 任务标识
     * @param question question
     * @param outcome outcome
     */
    public void applyOutcome(String taskId, EduQuestion question, EduAiService.MarkingOutcome outcome) {
        LambdaUpdateWrapper<EduMarkingResult> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(EduMarkingResult::getMarkingResultTaskId, taskId)
                .eq(EduMarkingResult::getMarkingResultQuestionId, question.getQuestionId())
                .set(EduMarkingResult::getMarkingResultAiScore, outcome.getScore())
                .set(EduMarkingResult::getMarkingResultAiComment, outcome.getComment())
                .set(EduMarkingResult::getMarkingResultStatus, STATUS_AI_DONE)
                .set(EduMarkingResult::getMarkingResultUpdateTime, LocalDateTime.now());
        markingResultMapper.update(null, wrapper);
    }

    @Override
    /** 获取结果 */
    public MarkingResultVO getResult(String taskId) {
        LambdaQueryWrapper<EduMarkingResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EduMarkingResult::getMarkingResultTaskId, taskId);
        List<EduMarkingResult> results = markingResultMapper.selectList(wrapper);

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

        List<MarkingResultVO.QuestionResult> list = new ArrayList<>();
        int totalScore = 0;
        boolean allDone = true;
        for (EduMarkingResult r : results) {
            MarkingResultVO.QuestionResult qr = new MarkingResultVO.QuestionResult();
            qr.setResultId(r.getMarkingResultId());
            qr.setQuestionId(r.getMarkingResultQuestionId());
            qr.setScore(r.getMarkingResultAiScore() == null ? 0 : r.getMarkingResultAiScore());
            qr.setTeacherScore(r.getMarkingResultTeacherScore());
            qr.setComment(r.getMarkingResultAiComment());
            qr.setTeacherComment(r.getMarkingResultTeacherComment());
            list.add(qr);
            totalScore += qr.getScore();
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
        for (MarkingConfirmDTO.ScoreAdjustment adj : dto.getAdjustments()) {
            EduMarkingResult result = markingResultMapper.selectById(adj.getResultId());
            if (result == null) {
                continue;
            }
            result.setMarkingResultTeacherScore(adj.getScore());
            result.setMarkingResultTeacherComment(adj.getComment());
            result.setMarkingResultStatus(STATUS_TEACHER_DONE);
            result.setMarkingResultUpdateTime(LocalDateTime.now());
            markingResultMapper.updateById(result);
        }
        log.info("[EduAi] 教师确认阅卷: taskId={}, items={}", dto.getTaskId(),
                dto.getAdjustments() == null ? 0 : dto.getAdjustments().size());
        return true;
    }
}
