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
    public void recordWrongQuestion(String studentId, Long questionId, Long examId, String ownerUserId) {
        recordWrongQuestion(studentId, questionId, examId, ownerUserId, null);
    }

    /**
     * 记录错题（带试卷归因）。
     *
     * <p>同一 (学生, 题目) 只保留一行并累加错误次数（唯一键口径），
     * 因此调用方需保证同一份卷只登记一次（见阅卷「完成」的幂等标记）。</p>
     *
     * @param studentId   学生标识
     * @param questionId  题目标识
     * @param examId      考试标识
     * @param ownerUserId 归属用户标识
     * @param paperId     试卷标识（可空：任意卷没有对应试卷）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordWrongQuestion(String studentId, Long questionId, Long examId, String ownerUserId, Long paperId) {
        EduWrongQuestion existing = baseMapper.selectOneByStudentQuestionOwner(studentId, questionId, ownerUserId);

        if (existing == null) {
            // 兜底：唯一键是 (student_id, question_id) 两列，不含归属；
            // 若既有行的 owner 与本次不同（例如历史数据 owner 为 NULL），
            // 上面的三条件查询会查不到，直接 INSERT 会撞唯一键
            // （实测：Duplicate entry 'anonymous-327' for key 'edu_wrong_question_student_question'）。
            // 因此按唯一键口径再查一次，命中则累加而非插入。
            existing = baseMapper.selectByStudentAndQuestion(studentId, questionId);
        }

        if (existing != null) {
            existing.setWrongQuestionWrongCount(existing.getWrongQuestionWrongCount() + 1);
            existing.setWrongQuestionLastWrongTime(LocalDateTime.now());
            existing.setWrongQuestionMasteryLevel(0); // 重置为未掌握
            // 历史行可能没有试卷归因：本次带了就补上（已有值不覆盖）
            if (existing.getWrongQuestionPaperId() == null && paperId != null) {
                existing.setWrongQuestionPaperId(paperId);
            }
            existing.setUpdateTime(LocalDateTime.now());
            baseMapper.updateById(existing);
        } else {
            EduWrongQuestion entity = new EduWrongQuestion();
            entity.setOwnerUserId(ownerUserId);
            entity.setWrongQuestionStudentId(studentId);
            entity.setWrongQuestionQuestionId(questionId);
            entity.setWrongQuestionExamId(examId);
            entity.setWrongQuestionPaperId(paperId);
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
        int count = dto.count() != null ? dto.count() : 10;
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        List<EduWrongQuestion> list = baseMapper.selectReviewQuestions(dto.studentId(), count, scope);
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
        return new WrongQuestionVO(
                entity.getWrongQuestionId(),
                entity.getWrongQuestionStudentId(),
                entity.getWrongQuestionQuestionId(),
                entity.getWrongQuestionWrongCount(),
                entity.getWrongQuestionMasteryLevel(),
                entity.getWrongQuestionLastWrongTime(),
                entity.getWrongQuestionLastReviewTime(),
                entity.getWrongQuestionReviewCount(),
                entity.getWrongQuestionPaperId());
    }
}
