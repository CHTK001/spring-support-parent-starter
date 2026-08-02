package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduPaperMapper;
import com.chua.starter.eduai.mapper.EduPaperQuestionMapper;
import com.chua.starter.eduai.mapper.EduQuestionMapper;
import com.chua.starter.eduai.model.EduPaper;
import com.chua.starter.eduai.model.EduPaperQuestion;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.PaperGenerateDTO;
import com.chua.starter.eduai.model.dto.PaperQueryDTO;
import com.chua.starter.eduai.model.vo.PaperVO;
import com.chua.starter.eduai.model.vo.QuestionVO;
import com.chua.starter.eduai.service.IEduPaperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 试卷服务实现
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduPaperServiceImpl extends ServiceImpl<EduPaperMapper, EduPaper> implements IEduPaperService {

    private final EduPaperQuestionMapper paperQuestionMapper;
    private final EduQuestionMapper questionMapper;

    @Override
    public PageResult<PaperVO> pageQuery(PaperQueryDTO query) {
        LambdaQueryWrapper<EduPaper> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getSubject())) {
            wrapper.eq(EduPaper::getPaperSubject, query.getSubject());
        }
        if (StringUtils.hasText(query.getGradeLevel())) {
            wrapper.eq(EduPaper::getPaperGradeLevel, query.getGradeLevel());
        }
        if (query.getStatus() != null) {
            wrapper.eq(EduPaper::getPaperStatus, query.getStatus());
        }
        wrapper.orderByDesc(EduPaper::getPaperCreateTime);

        Page<EduPaper> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<EduPaper> result = baseMapper.selectPage(page, wrapper);

        List<PaperVO> voList = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.<PaperVO>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(voList)
                .build();
    }

    @Override
    public PaperVO getPaperDetail(Long paperId) {
        EduPaper paper = baseMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        PaperVO vo = toVO(paper);

        // 查询关联题目
        LambdaQueryWrapper<EduPaperQuestion> pqWrapper = new LambdaQueryWrapper<>();
        pqWrapper.eq(EduPaperQuestion::getPaperQuestionPaperId, paperId)
                .orderByAsc(EduPaperQuestion::getPaperQuestionOrder);
        List<EduPaperQuestion> relations = paperQuestionMapper.selectList(pqWrapper);

        if (!relations.isEmpty()) {
            List<Long> questionIds = relations.stream()
                    .map(EduPaperQuestion::getPaperQuestionQuestionId)
                    .collect(Collectors.toList());
            List<EduQuestion> questions = questionMapper.selectBatchIds(questionIds);
            List<QuestionVO> questionVOs = questions.stream().map(q -> {
                QuestionVO qv = new QuestionVO();
                qv.setId(q.getQuestionId());
                qv.setType(q.getQuestionType());
                qv.setStem(q.getQuestionStem());
                qv.setOptions(q.getQuestionOptions());
                qv.setAnswer(q.getQuestionAnswer());
                qv.setAnalysis(q.getQuestionAnalysis());
                // 获取分值
                relations.stream()
                        .filter(r -> r.getPaperQuestionQuestionId().equals(q.getQuestionId()))
                        .findFirst()
                        .ifPresent(r -> qv.setScore(r.getPaperQuestionScore()));
                return qv;
            }).collect(Collectors.toList());
            vo.setQuestions(questionVOs);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generatePaper(PaperGenerateDTO dto) {
        // TODO: 调用AI服务根据知识点、难度、题型生成题目列表
        // 当前为模拟实现：随机选取若干题目
        log.info("AI组卷: subject={}, knowledgePoints={}", dto.getSubject(), dto.getKnowledgePointIds());

        // 创建试卷
        EduPaper paper = new EduPaper();
        paper.setPaperName(dto.getName());
        paper.setPaperSubject(dto.getSubject());
        paper.setPaperGradeLevel(dto.getGradeLevel());
        paper.setPaperGrade(dto.getGrade());
        paper.setPaperTotalScore(dto.getTotalScore() != null ? dto.getTotalScore() : 100);
        paper.setPaperDuration(dto.getDuration());
        paper.setPaperStatus(0); // 草稿
        paper.setPaperSource("AI");
        paper.setPaperCreateTime(LocalDateTime.now());
        paper.setPaperUpdateTime(LocalDateTime.now());
        baseMapper.insert(paper);

        // 模拟：从题库中随机取题（实际应调用AI）
        List<EduQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<EduQuestion>()
                        .eq(EduQuestion::getQuestionSubject, dto.getSubject())
                        .eq(EduQuestion::getQuestionStatus, 1)
                        .last("limit " + (dto.getQuestionCount() != null ? dto.getQuestionCount() : 10))
        );

        int order = 1;
        int totalScore = 0;
        for (EduQuestion q : questions) {
            EduPaperQuestion pq = new EduPaperQuestion();
            pq.setPaperQuestionPaperId(paper.getPaperId());
            pq.setPaperQuestionQuestionId(q.getQuestionId());
            pq.setPaperQuestionOrder(order++);
            int score = dto.getScorePerQuestion() != null ? dto.getScorePerQuestion() : 5;
            pq.setPaperQuestionScore(score);
            pq.setPaperQuestionCreateTime(LocalDateTime.now());
            paperQuestionMapper.insert(pq);
            totalScore += score;
        }

        // 更新试卷总分和题目数
        paper.setPaperTotalScore(totalScore);
        paper.setPaperQuestionCount(questions.size());
        baseMapper.updateById(paper);

        log.info("AI组卷完成，试卷ID: {}, 题目数: {}", paper.getPaperId(), questions.size());
        return paper.getPaperId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean replaceQuestion(Long paperId, Long oldQuestionId, Long newQuestionId) {
        // 删除旧关联
        LambdaQueryWrapper<EduPaperQuestion> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(EduPaperQuestion::getPaperQuestionPaperId, paperId)
                .eq(EduPaperQuestion::getPaperQuestionQuestionId, oldQuestionId);
        paperQuestionMapper.delete(delWrapper);

        // 添加新关联
        EduPaperQuestion pq = new EduPaperQuestion();
        pq.setPaperQuestionPaperId(paperId);
        pq.setPaperQuestionQuestionId(newQuestionId);
        pq.setPaperQuestionOrder(0);
        pq.setPaperQuestionScore(5);
        pq.setPaperQuestionCreateTime(LocalDateTime.now());
        paperQuestionMapper.insert(pq);

        log.info("替换题目成功，试卷: {}, 旧题: {}, 新题: {}", paperId, oldQuestionId, newQuestionId);
        return true;
    }

    private PaperVO toVO(EduPaper paper) {
        PaperVO vo = new PaperVO();
        vo.setId(paper.getPaperId());
        vo.setName(paper.getPaperName());
        vo.setSubject(paper.getPaperSubject());
        vo.setGradeLevel(paper.getPaperGradeLevel());
        vo.setGrade(paper.getPaperGrade());
        vo.setTotalScore(paper.getPaperTotalScore());
        vo.setDuration(paper.getPaperDuration());
        vo.setQuestionCount(paper.getPaperQuestionCount());
        vo.setStatus(paper.getPaperStatus());
        vo.setSource(paper.getPaperSource());
        vo.setCreateTime(paper.getPaperCreateTime());
        return vo;
    }
}