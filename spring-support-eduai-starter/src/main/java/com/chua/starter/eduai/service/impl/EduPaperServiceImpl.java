package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.ai.EduAiService;
import com.chua.starter.eduai.configuration.EduAiProperties;
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
 * <p>AI 组卷流程：
 * <ol>
 *   <li>先按学科/学段/年级/题型从题库中筛出候选题目（兜底数据）</li>
 *   <li>调用 {@link EduAiService#generatePaperOutline} 让模型挑选合适的题目 ID</li>
 *   <li>AI 不可用/返回为空时回退到候选前 N 条</li>
 *   <li>写入 {@code edu_paper} 与 {@code edu_paper_question} 关联表</li>
 * </ol>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduPaperServiceImpl extends ServiceImpl<EduPaperMapper, EduPaper> implements IEduPaperService {

    /**
     * Paperquestionmapper
    */
    private final EduPaperQuestionMapper paperQuestionMapper;
    /**
     * Questionmapper
    */
    private final EduQuestionMapper questionMapper;
    /**
     * EDUAI服务
    */
    private final EduAiService eduAiService;
    /**
     * 属性
    */
    private final EduAiProperties properties;

    @Override
    /**
     * Page查询
    */
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
    /**
     * 获取paperdetail
    */
    public PaperVO getPaperDetail(Long paperId) {
        EduPaper paper = baseMapper.selectById(paperId);
        if (paper == null) {
            /**
             * 业务异常。
             * @param "试卷不存在" "试卷不存在"
             * @return 业务异常的结果
             */
            throw new BusinessException("试卷不存在");
        }
        PaperVO vo = toVO(paper);

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
    /**
     * generatepaper
     *
     * @param dto dto
     * @return generatePaper的结果
     */
    public Long generatePaper(PaperGenerateDTO dto) {
        log.info("[EduAi] AI 组卷: subject={}, knowledgePoints={}", dto.getSubject(), dto.getKnowledgePointIds());

        // 1) 创建试卷草稿
        EduPaper paper = new EduPaper();
        paper.setPaperName(dto.getName());
        paper.setPaperSubject(dto.getSubject());
        paper.setPaperGradeLevel(dto.getGradeLevel());
        paper.setPaperGrade(dto.getGrade());
        paper.setPaperTotalScore(dto.getTotalScore() != null ? dto.getTotalScore() : properties.getPaper().getMaxTotalScore());
        paper.setPaperDuration(dto.getDuration());
        paper.setPaperStatus(0); // 草稿
        paper.setPaperSource("AI");
        paper.setPaperCreateTime(LocalDateTime.now());
        paper.setPaperUpdateTime(LocalDateTime.now());
        baseMapper.insert(paper);

        int count = dto.getQuestionCount() != null ? dto.getQuestionCount() : properties.getPaper().getDefaultQuestionCount();
        int perScore = dto.getScorePerQuestion() != null ? dto.getScorePerQuestion() : properties.getPaper().getDefaultScorePerQuestion();

        // 2) 候选题目（兜底数据）
        LambdaQueryWrapper<EduQuestion> candidateWrapper = new LambdaQueryWrapper<EduQuestion>()
                .eq(EduQuestion::getQuestionSubject, dto.getSubject())
                .eq(EduQuestion::getQuestionStatus, 1)
                .orderByAsc(EduQuestion::getQuestionId)
                .last("limit " + Math.max(count * 3, count));
        List<EduQuestion> candidates = questionMapper.selectList(candidateWrapper);
        if (candidates.isEmpty()) {
            log.warn("[EduAi] 题库为空，无法组卷: subject={}", dto.getSubject());
            return paper.getPaperId();
        }

        // 3) 知识点评分过滤（如果指定）
        List<Long> candidateIds = new ArrayList<>();
        if (dto.getKnowledgePointIds() != null && !dto.getKnowledgePointIds().isEmpty()) {
            for (EduQuestion q : candidates) {
                String kpIds = q.getQuestionKnowledgePointIds();
                if (!StringUtils.hasText(kpIds)) {
                    continue;
                }
                for (Long kp : dto.getKnowledgePointIds()) {
                    if (kpIds.contains(String.valueOf(kp))) {
                        candidateIds.add(q.getQuestionId());
                        break;
                    }
                }
            }
            if (candidateIds.isEmpty()) {
                candidateIds = candidates.stream().map(EduQuestion::getQuestionId).collect(Collectors.toList());
            }
        } else {
            candidateIds = candidates.stream().map(EduQuestion::getQuestionId).collect(Collectors.toList());
        }

        // 4) AI 挑选
        List<String> types = null;
        if (dto.getTypeConfigs() != null && !dto.getTypeConfigs().isEmpty()) {
            types = dto.getTypeConfigs().stream()
                    .map(PaperGenerateDTO.QuestionTypeConfig::getType)
                    .collect(Collectors.toList());
        }
        List<Long> knowledgeIds = dto.getKnowledgePointIds();
        List<Long> aiIds = eduAiService.generatePaperOutline(
                dto.getSubject(), dto.getGradeLevel(), dto.getGrade(),
                types, knowledgeIds, count, candidateIds);
        if (aiIds.size() > count) {
            aiIds = aiIds.subList(0, count);
        }

        // 5) 入库关联
        int order = 1;
        int totalScore = 0;
        for (Long qid : aiIds) {
            EduPaperQuestion pq = new EduPaperQuestion();
            pq.setPaperQuestionPaperId(paper.getPaperId());
            pq.setPaperQuestionQuestionId(qid);
            pq.setPaperQuestionOrder(order++);
            pq.setPaperQuestionScore(perScore);
            pq.setPaperQuestionCreateTime(LocalDateTime.now());
            paperQuestionMapper.insert(pq);
            totalScore += perScore;
        }

        // 6) 回写试卷总分/题目数
        paper.setPaperTotalScore(totalScore);
        paper.setPaperQuestionCount(aiIds.size());
        paper.setPaperUpdateTime(LocalDateTime.now());
        baseMapper.updateById(paper);

        log.info("[EduAi] AI 组卷完成: paperId={}, questionCount={}", paper.getPaperId(), aiIds.size());
        return paper.getPaperId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 替换question
     *
     * @param paperId paperid
     * @param oldQuestionId 旧questionid
     * @param newQuestionId 新questionid
     * @return 替换question的结果
     */
    public boolean replaceQuestion(Long paperId, Long oldQuestionId, Long newQuestionId) {
        LambdaQueryWrapper<EduPaperQuestion> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(EduPaperQuestion::getPaperQuestionPaperId, paperId)
                .eq(EduPaperQuestion::getPaperQuestionQuestionId, oldQuestionId);
        paperQuestionMapper.delete(delWrapper);

        EduPaperQuestion pq = new EduPaperQuestion();
        pq.setPaperQuestionPaperId(paperId);
        pq.setPaperQuestionQuestionId(newQuestionId);
        pq.setPaperQuestionOrder(0);
        pq.setPaperQuestionScore(properties.getPaper().getDefaultScorePerQuestion());
        pq.setPaperQuestionCreateTime(LocalDateTime.now());
        paperQuestionMapper.insert(pq);

        log.info("[EduAi] 替换题目成功: paperId={}, oldId={}, newId={}", paperId, oldQuestionId, newQuestionId);
        return true;
    }

    /**
     * 转为vo
     *
     * @param paper paper
     * @return 转为vo的结果
     */
    private PaperVO toVO(EduPaper paper) {
        PaperVO vo = new PaperVO();
        vo.setPaperId(paper.getPaperId());
        vo.setPaperName(paper.getPaperName());
        vo.setPaperSubject(paper.getPaperSubject());
        vo.setPaperGradeLevel(paper.getPaperGradeLevel());
        vo.setPaperGrade(paper.getPaperGrade());
        vo.setPaperTotalScore(paper.getPaperTotalScore());
        vo.setPaperDuration(paper.getPaperDuration());
        vo.setPaperQuestionCount(paper.getPaperQuestionCount());
        vo.setPaperStatus(paper.getPaperStatus());
        vo.setPaperSource(paper.getPaperSource());
        vo.setPaperCreateTime(paper.getPaperCreateTime());
        return vo;
    }
}
