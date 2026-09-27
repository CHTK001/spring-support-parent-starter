package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduExamMapper;
import com.chua.starter.eduai.model.EduExam;
import com.chua.starter.eduai.service.IEduExamService;
import com.chua.starter.eduai.support.util.EduUserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 考试服务实现
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@Service
public class EduExamServiceImpl extends ServiceImpl<EduExamMapper, EduExam> implements IEduExamService {

    @Override
    /**
     * Page查询
    */
    public PageResult<EduExam> pageQuery(EduExam exam, long page, long pageSize) {
        EduUserContext.OwnerScope scope = EduUserContext.ownerScope();
        Page<EduExam> pageInfo = new Page<>(page, pageSize);
        IPage<EduExam> result = baseMapper.pageSelect(pageInfo, exam, scope);
        return PageResult.<EduExam>builder()
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
     * 创建Exam
     *
     * @param exam exam
     * @return 创建exam的结果
     */
    public Long createExam(EduExam exam) {
        if (exam.getExamPaperId() == null) {
            /**
             * 业务异常。
             * @param "试卷ID不能为空" "试卷标识不能为空"
             * @return 业务异常的结果
             */
            throw new BusinessException("试卷ID不能为空");
        }
        exam.setExamId(null);
        exam.setOwnerUserId(EduUserContext.requireUserId());
        exam.setExamStatus(exam.getExamStatus() != null ? exam.getExamStatus() : 0);
        exam.setCreateTime(LocalDateTime.now());
        exam.setUpdateTime(LocalDateTime.now());;
        baseMapper.insert(exam);
        log.info("创建考试成功，ID: {}", exam.getExamId());
        return exam.getExamId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 更新Exam
     *
     * @param exam exam
     * @return 更新exam的结果
     */
    public boolean updateExam(EduExam exam) {
        if (exam.getExamId() == null) {
            /**
             * 业务异常。
             * @param "考试ID不能为空" "考试标识不能为空"
             * @return 业务异常的结果
             */
            throw new BusinessException("考试ID不能为空");
        }
        EduExam exists = baseMapper.selectById(exam.getExamId());
        if (exists == null) {
            /**
             * 业务异常。
             * @param "考试不存在" "考试不存在"
             * @return 业务异常的结果
             */
            throw new BusinessException("考试不存在");
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        exam.setOwnerUserId(exists.getOwnerUserId());
        exam.setUpdateTime(LocalDateTime.now());;
        int rows = baseMapper.updateById(exam);
        log.info("更新考试成功，ID: {}", exam.getExamId());
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除Exam
     *
     * @param id 标识
     * @return 删除exam的结果
     */
    public boolean deleteExam(Long id) {
        EduExam exists = baseMapper.selectById(id);
        if (exists == null) {
            /**
             * 业务异常。
             * @param "考试不存在" "考试不存在"
             * @return 业务异常的结果
             */
            throw new BusinessException("考试不存在");
        }
        EduUserContext.checkWritable(exists.getOwnerUserId());
        // 走 @TableLogic 的独立删除标记，不复用生命周期列 exam_status
        int rows = baseMapper.deleteById(id);
        log.info("删除考试，ID: {}", id);
        return rows > 0;
    }
}
