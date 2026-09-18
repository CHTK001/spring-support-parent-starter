package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.mapper.EduWrongQuestionMapper;
import com.chua.starter.eduai.model.EduWrongQuestion;
import com.chua.starter.eduai.model.dto.ReviewPaperDTO;
import com.chua.starter.eduai.model.dto.WrongQueryDTO;
import com.chua.starter.eduai.model.vo.WrongQuestionVO;
import com.chua.starter.eduai.service.IEduWrongQuestionService;
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
* 错题库服务实现
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduWrongQuestionServiceImpl extends ServiceImpl<EduWrongQuestionMapper, EduWrongQuestion> implements IEduWrongQuestionService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * recordwrongquestion
    *
    * @param studentId studentid
    * @param questionId questionid
    * @param examId examid
    */
    public void recordWrongQuestion(String studentId, Long questionId, Long examId) {
        LambdaQueryWrapper<EduWrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EduWrongQuestion::getWrongQuestionStudentId, studentId)
                .eq(EduWrongQuestion::getWrongQuestionQuestionId, questionId);
        EduWrongQuestion existing = baseMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setWrongQuestionWrongCount(existing.getWrongQuestionWrongCount() + 1);
            existing.setWrongQuestionLastWrongTime(LocalDateTime.now());
            existing.setWrongQuestionMasteryLevel(0); // 重置为未掌握
            existing.setWrongQuestionUpdateTime(LocalDateTime.now());
            baseMapper.updateById(existing);
        } else {
            EduWrongQuestion entity = new EduWrongQuestion();
            entity.setWrongQuestionStudentId(studentId);
            entity.setWrongQuestionQuestionId(questionId);
            entity.setWrongQuestionExamId(examId);
            entity.setWrongQuestionWrongCount(1);
            entity.setWrongQuestionMasteryLevel(0);
            entity.setWrongQuestionLastWrongTime(LocalDateTime.now());
            entity.setWrongQuestionStatus(1);
            entity.setWrongQuestionCreateTime(LocalDateTime.now());
            entity.setWrongQuestionUpdateTime(LocalDateTime.now());
            baseMapper.insert(entity);
        }
        log.info("记录错题: student={}, question={}", studentId, questionId);
    }

    @Override
    /** Page查询 */
    public PageResult<WrongQuestionVO> pageQuery(WrongQueryDTO query) {
        LambdaQueryWrapper<EduWrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EduWrongQuestion::getWrongQuestionStudentId, query.getStudentId());
        if (StringUtils.hasText(query.getSubject())) {
            // 需要关联查询，简化处理
        }
        if (query.getMasteryLevel() != null) {
            wrapper.eq(EduWrongQuestion::getWrongQuestionMasteryLevel, query.getMasteryLevel());
        }
        wrapper.orderByDesc(EduWrongQuestion::getWrongQuestionLastWrongTime);

        Page<EduWrongQuestion> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<EduWrongQuestion> result = baseMapper.selectPage(page, wrapper);

        List<WrongQuestionVO> voList = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.<WrongQuestionVO>builder()
                .pageNo((int) result.getCurrent())
                .pageSize((int) result.getSize())
                .totalPages((int) result.getPages())
                .total(result.getTotal())
                .data(voList)
                .build();
    }

    @Override
    /** generate评审paper */
    public List<Long> generateReviewPaper(ReviewPaperDTO dto) {
        LambdaQueryWrapper<EduWrongQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EduWrongQuestion::getWrongQuestionStudentId, dto.getStudentId())
                .eq(EduWrongQuestion::getWrongQuestionStatus, 1)
                .orderByAsc(EduWrongQuestion::getWrongQuestionMasteryLevel)
                .orderByDesc(EduWrongQuestion::getWrongQuestionLastWrongTime)
                .last("limit " + (dto.getCount() != null ? dto.getCount() : 10));
        List<EduWrongQuestion> list = baseMapper.selectList(wrapper);
        return list.stream()
                .map(EduWrongQuestion::getWrongQuestionQuestionId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
    * 更新Mastery
    *
    * @param wrongId wrongid
    * @param masteryLevel mastery水平仪
    * @return 更新mastery的结果
    */
    public boolean updateMastery(Long wrongId, Integer masteryLevel) {
        EduWrongQuestion entity = baseMapper.selectById(wrongId);
        if (entity == null) {
            return false;
        }
        entity.setWrongQuestionMasteryLevel(masteryLevel);
        entity.setWrongQuestionLastReviewTime(LocalDateTime.now());
        entity.setWrongQuestionReviewCount(entity.getWrongQuestionReviewCount() + 1);
        entity.setWrongQuestionUpdateTime(LocalDateTime.now());
        baseMapper.updateById(entity);
        log.info("更新掌握度: wrongId={}, mastery={}", wrongId, masteryLevel);
        return true;
    }

    /**
    * 转为vo
    *
    * @param entity 实体
    * @return 转为vo的结果
    */
    private WrongQuestionVO toVO(EduWrongQuestion entity) {
        WrongQuestionVO vo = new WrongQuestionVO();
        vo.setWrongQuestionId(entity.getWrongQuestionId());
        vo.setWrongQuestionStudentId(entity.getWrongQuestionStudentId());
        vo.setWrongQuestionQuestionId(entity.getWrongQuestionQuestionId());
        vo.setWrongQuestionWrongCount(entity.getWrongQuestionWrongCount());
        vo.setWrongQuestionMasteryLevel(entity.getWrongQuestionMasteryLevel());
        vo.setWrongQuestionLastWrongTime(entity.getWrongQuestionLastWrongTime());
        vo.setWrongQuestionLastReviewTime(entity.getWrongQuestionLastReviewTime());
        vo.setWrongQuestionReviewCount(entity.getWrongQuestionReviewCount());
        return vo;
    }
}
