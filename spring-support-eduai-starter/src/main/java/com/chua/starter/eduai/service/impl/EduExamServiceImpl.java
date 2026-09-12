package com.chua.starter.eduai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.common.support.exception.BusinessException;
import com.chua.starter.eduai.mapper.EduExamMapper;
import com.chua.starter.eduai.model.EduExam;
import com.chua.starter.eduai.service.IEduExamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    /** Page查询 */
    public PageResult<EduExam> pageQuery(EduExam exam, long page, long pageSize) {
        LambdaQueryWrapper<EduExam> wrapper = new LambdaQueryWrapper<>();
        if (exam != null) {
 // 按试卷标识筛选
            if (exam.getExamPaperId() != null) {
                wrapper.eq(EduExam::getExamPaperId, exam.getExamPaperId());
            }
            // 按考试名称筛选（like 匹配）
            if (StringUtils.hasText(exam.getExamName())) {
                wrapper.like(EduExam::getExamName, exam.getExamName());
            }
            // 按班级筛选
            if (StringUtils.hasText(exam.getExamClassId())) {
                wrapper.eq(EduExam::getExamClassId, exam.getExamClassId());
            }
            // 按状态筛选
            if (exam.getExamStatus() != null) {
                wrapper.eq(EduExam::getExamStatus, exam.getExamStatus());
            }
        }
        // 默认按创建时间倒序
        wrapper.orderByDesc(EduExam::getExamCreateTime);

        Page<EduExam> pageInfo = new Page<>(page, pageSize);
        IPage<EduExam> result = baseMapper.selectPage(pageInfo, wrapper);
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
        exam.setExamStatus(exam.getExamStatus() != null ? exam.getExamStatus() : 0);
        exam.setExamCreateTime(LocalDateTime.now());
        exam.setExamUpdateTime(LocalDateTime.now());
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
        exam.setExamUpdateTime(LocalDateTime.now());
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
        // 逻辑删除：状态置为已结束
        exists.setExamStatus(2);
        exists.setExamUpdateTime(LocalDateTime.now());
        int rows = baseMapper.updateById(exists);
        log.info("删除考试，ID: {}", id);
        return rows > 0;
    }
}
