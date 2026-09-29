package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.mapper.EduWrongQuestionMapper;
import com.chua.starter.eduai.model.EduWrongQuestion;
import com.chua.starter.eduai.model.dto.ReviewPaperDTO;
import com.chua.starter.eduai.model.dto.WrongQueryDTO;
import com.chua.starter.eduai.model.vo.WrongQuestionVO;
import com.chua.starter.eduai.service.IEduWrongQuestionService;
import com.chua.starter.eduai.support.util.EduUserContext;
import com.chua.starter.eduai.support.util.PageBounds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
public class EduWrongQuestionServiceImpl extends ServiceImpl<EduWrongQuestionMapper, EduWrongQuestion> implements IEduWrongQuestionService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * recordwrongquestion
     *
     * @param studentId studentid
     * @param questionId questionid
     * @param examId examid
     * @param ownerUserId 归属用户标识
     */
    public void recordWrongQuestion(String studentId, Long questionId, Long examId, String ownerUserId) {
        EduWrongQuestion existing = baseMapper.selectOneByStudentQuestionOwner(studentId, questionId, ownerUserId);

        if (existing != null) {
            existing.setWrongQuestionWrongCount(existing.getWrongQuestionWrongCount() + 1);
            existing.setWrongQuestionLastWrongTime(LocalDateTime.now());
            existing.setWrongQuestionMasteryLevel(0); // 重置为未掌握
            existing.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(existing);
        } else {
            EduWrongQuestion entity = new EduWrongQuestion();
            entity.setOwnerUserId(ownerUserId);
            entity.setWrongQuestionStudentId(studentId);
            entity.setWrongQuestionQuestionId(questionId);
            entity.setWrongQuestionExamId(examId);
            entity.setWrongQuestionWrongCount(1);
            entity.setWrongQuestionMasteryLevel(0);
            entity.setWrongQuestionLastWrongTime(LocalDateTime.now());
            entity.setWrongQuestionStatus(1);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            baseMapper.insert(entity);
        }
        log.info("记录错题: student={}, question={}", studentId, questionId);
    }

    @Override
    /**
     * Page查询
    */
    public PageResult<WrongQuestionVO> pageQuery(WrongQueryDTO query) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        // 分页参数来自请求，必须收敛边界（页码<1 归 1；pageSize 封顶，否则 pageSize=1000000 会拉全表）
        Page<EduWrongQuestion> page = new Page<>(PageBounds.pageNum(query.getPageNum()),
                PageBounds.pageSize(query.getPageSize()));
        IPage<EduWrongQuestion> result = baseMapper.pageSelect(page, query, scope);

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
    /**
     * generate评审paper
    */
    public List<Long> generateReviewPaper(ReviewPaperDTO dto) {
        int count = dto.getCount() != null ? dto.getCount() : 10;
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        List<EduWrongQuestion> list = baseMapper.selectReviewQuestions(dto.getStudentId(), count, scope);
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
        EduUserContext.checkWritable(entity.getOwnerUserId());
        entity.setWrongQuestionMasteryLevel(masteryLevel);
        entity.setWrongQuestionLastReviewTime(LocalDateTime.now());
        entity.setWrongQuestionReviewCount(entity.getWrongQuestionReviewCount() + 1);
        entity.setUpdateTime(LocalDateTime.now());
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
