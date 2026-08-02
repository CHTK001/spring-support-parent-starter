package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduAnswerSheetMapper;
import com.chua.starter.eduai.mapper.EduMarkingResultMapper;
import com.chua.starter.eduai.mapper.EduQuestionMapper;
import com.chua.starter.eduai.model.EduAnswerSheet;
import com.chua.starter.eduai.model.EduMarkingResult;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.MarkingConfirmDTO;
import com.chua.starter.eduai.model.dto.MarkingSubmitDTO;
import com.chua.starter.eduai.model.vo.MarkingResultVO;
import com.chua.starter.eduai.service.IEduMarkingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 阅卷服务实现
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduMarkingServiceImpl implements IEduMarkingService {

    private final EduAnswerSheetMapper answerSheetMapper;
    private final EduMarkingResultMapper markingResultMapper;
    private final EduQuestionMapper questionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitAnswerSheet(MarkingSubmitDTO dto) {
        // 保存答题卡
        EduAnswerSheet sheet = new EduAnswerSheet();
        sheet.setAnswerSheetExamId(dto.getExamId());
        sheet.setAnswerSheetStudentId(dto.getStudentId());
        sheet.setAnswerSheetContent(dto.getContent());
        sheet.setAnswerSheetSubmitTime(LocalDateTime.now());
        sheet.setAnswerSheetStatus(1); // 已提交
        sheet.setAnswerSheetCreateTime(LocalDateTime.now());
        sheet.setAnswerSheetUpdateTime(LocalDateTime.now());
        answerSheetMapper.insert(sheet);

        // 生成任务ID
        String taskId = UUID.randomUUID().toString().replace("-", "");

        // TODO: 调用AI阅卷服务（当前为模拟）
        // 模拟批改每个题目
        List<MarkingSubmitDTO.AnswerItem> answers = dto.getAnswers();
        for (MarkingSubmitDTO.AnswerItem item : answers) {
            EduQuestion question = questionMapper.selectById(item.getQuestionId());
            if (question == null) {
                continue;
            }
            EduMarkingResult result = new EduMarkingResult();
            result.setMarkingResultAnswerSheetId(sheet.getAnswerSheetId());
            result.setMarkingResultQuestionId(item.getQuestionId());
            result.setMarkingResultTaskId(taskId);
            result.setMarkingResultStatus(1); // AI已批改

            // 模拟判断：简单比较答案
            boolean correct = StringUtils.hasText(item.getAnswer()) &&
                    item.getAnswer().trim().equalsIgnoreCase(question.getQuestionAnswer().trim());
            int score = correct ? 5 : 0;
            result.setMarkingResultAiScore(score);
            result.setMarkingResultAiComment(correct ? "回答正确" : "回答有误");
            result.setMarkingResultCreateTime(LocalDateTime.now());
            result.setMarkingResultUpdateTime(LocalDateTime.now());
            markingResultMapper.insert(result);
        }

        log.info("提交答题卡成功，任务ID: {}, 学生: {}", taskId, dto.getStudentId());
        return taskId;
    }

    @Override
    public MarkingResultVO getResult(String taskId) {
        LambdaQueryWrapper<EduMarkingResult> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EduMarkingResult::getMarkingResultTaskId, taskId);
        List<EduMarkingResult> results = markingResultMapper.selectList(wrapper);

        if (results.isEmpty()) {
            throw new BusinessException("任务不存在");
        }

        MarkingResultVO vo = new MarkingResultVO();
        vo.setTaskId(taskId);
        vo.setStatus(results.get(0).getMarkingResultStatus());

        List<MarkingResultVO.QuestionResult> list = new ArrayList<>();
        int totalScore = 0;
        for (EduMarkingResult r : results) {
            MarkingResultVO.QuestionResult qr = new MarkingResultVO.QuestionResult();
            qr.setQuestionId(r.getMarkingResultQuestionId());
            qr.setScore(r.getMarkingResultAiScore());
            qr.setComment(r.getMarkingResultAiComment());
            list.add(qr);
            totalScore += r.getMarkingResultAiScore() != null ? r.getMarkingResultAiScore() : 0;
        }
        vo.setResults(list);
        vo.setTotalScore(totalScore);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmMarking(MarkingConfirmDTO dto) {
        for (MarkingConfirmDTO.ScoreAdjustment adj : dto.getAdjustments()) {
            EduMarkingResult result = markingResultMapper.selectById(adj.getResultId());
            if (result == null) {
                continue;
            }
            result.setMarkingResultTeacherScore(adj.getScore());
            result.setMarkingResultTeacherComment(adj.getComment());
            result.setMarkingResultStatus(2); // 教师已确认
            result.setMarkingResultUpdateTime(LocalDateTime.now());
            markingResultMapper.updateById(result);
        }
        log.info("教师确认阅卷完成，任务ID: {}", dto.getTaskId());
        return true;
    }
}