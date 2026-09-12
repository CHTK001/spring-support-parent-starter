package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduQuestionMapper;
import com.chua.starter.eduai.model.EduQuestion;
import com.chua.starter.eduai.model.dto.QuestionQueryDTO;
import com.chua.starter.eduai.model.dto.QuestionSaveDTO;
import com.chua.starter.eduai.service.IEduQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
* 题目服务实现
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduQuestionServiceImpl extends ServiceImpl<EduQuestionMapper, EduQuestion> implements IEduQuestionService {

    @Override
    /** Page查询 */
    public PageResult<EduQuestion> pageQuery(QuestionQueryDTO query) {
        LambdaQueryWrapper<EduQuestion> wrapper = new LambdaQueryWrapper<>();

        // 按学科筛选
        if (StringUtils.hasText(query.getSubject())) {
            wrapper.eq(EduQuestion::getQuestionSubject, query.getSubject());
        }
        // 按年段筛选
        if (StringUtils.hasText(query.getGradeLevel())) {
            wrapper.eq(EduQuestion::getQuestionGradeLevel, query.getGradeLevel());
        }
        // 按年级筛选
        if (StringUtils.hasText(query.getGrade())) {
            wrapper.eq(EduQuestion::getQuestionGrade, query.getGrade());
        }
        // 按题型筛选
        if (StringUtils.hasText(query.getType())) {
            wrapper.eq(EduQuestion::getQuestionType, query.getType());
        }
        // 按难度筛选
        if (query.getDifficulty() != null) {
            wrapper.eq(EduQuestion::getQuestionDifficulty, query.getDifficulty());
        }
        // 按知识点筛选（like 匹配）
        if (StringUtils.hasText(query.getKnowledgePointIds())) {
            wrapper.like(EduQuestion::getQuestionKnowledgePointIds, query.getKnowledgePointIds());
        }
        // 按审核状态筛选
        if (query.getAuditStatus() != null) {
            wrapper.eq(EduQuestion::getQuestionAuditStatus, query.getAuditStatus());
        }
        // 按状态筛选
        if (query.getStatus() != null) {
            wrapper.eq(EduQuestion::getQuestionStatus, query.getStatus());
        }

        // 默认按创建时间倒序
        wrapper.orderByDesc(EduQuestion::getQuestionCreateTime);

        Page<EduQuestion> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<EduQuestion> result = baseMapper.selectPage(page, wrapper);
        return PageResult.<EduQuestion>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(result.getRecords())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * 保存Question
    *
    * @param dto dto
    * @return 保存question的结果
     */
    public Long saveQuestion(QuestionSaveDTO dto) {
        EduQuestion entity = new EduQuestion();
        entity.setQuestionType(dto.getType());
        entity.setQuestionSubject(dto.getSubject());
        entity.setQuestionGradeLevel(dto.getGradeLevel());
        entity.setQuestionGrade(dto.getGrade());
        entity.setQuestionYear(dto.getYear());
        entity.setQuestionDifficulty(dto.getDifficulty());
        entity.setQuestionKnowledgePointIds(dto.getKnowledgePointIds());
        entity.setQuestionStem(dto.getStem());
        entity.setQuestionStemImages(dto.getStemImages());
        entity.setQuestionOptions(dto.getOptions());
        entity.setQuestionAnswer(dto.getAnswer());
        entity.setQuestionAnalysis(dto.getAnalysis());
        entity.setQuestionSource(dto.getSource());
        entity.setQuestionSourceType(dto.getSourceType());
        entity.setQuestionAuditStatus(dto.getAuditStatus() != null ? dto.getAuditStatus() : 0);
        entity.setQuestionStatus(dto.getStatus() != null ? dto.getStatus() : 1);

        if (dto.getId() != null && dto.getId() > 0) {
            // 更新
            entity.setQuestionId(dto.getId());
            entity.setQuestionUpdateTime(LocalDateTime.now());
            baseMapper.updateById(entity);
            log.info("更新题目成功，ID: {}", dto.getId());
            return dto.getId();
        } else {
            // 新增
            entity.setQuestionUseCount(0);
            entity.setQuestionCreateTime(LocalDateTime.now());
            entity.setQuestionUpdateTime(LocalDateTime.now());
            baseMapper.insert(entity);
            log.info("新增题目成功，ID: {}", entity.getQuestionId());
            return entity.getQuestionId();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * 导入questions
    *
    * @param list 列表
    * @return 导入questions的结果
     */
    public int importQuestions(List<QuestionSaveDTO> list) {
        int count = 0;
        for (QuestionSaveDTO dto : list) {
            try {
                /**
                * 保存question。
                * @param dto dto
                 */
                saveQuestion(dto);
                count++;
            } catch (Exception e) {
                log.warn("导入题目失败: {}", e.getMessage());
            }
        }
        log.info("批量导入完成，成功 {} 条，失败 {} 条", count, list.size() - count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * 删除Question
    *
    * @param id 标识
    * @return 删除question的结果
     */
    public boolean deleteQuestion(Long id) {
        EduQuestion entity = baseMapper.selectById(id);
        if (entity == null) {
            /**
            * 业务异常。
            * @param "题目不存在" "题目不存在"
            * @return 业务异常的结果
             */
            throw new BusinessException("题目不存在");
        }
        // 逻辑删除：将状态置为0
        entity.setQuestionStatus(0);
        entity.setQuestionUpdateTime(LocalDateTime.now());
        int rows = baseMapper.updateById(entity);
        log.info("删除题目，ID: {}", id);
        return rows > 0;
    }
}