package com.chua.starter.eduai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.eduai.model.EduExam;

/**
 * 考试服务接口
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface IEduExamService extends IService<EduExam> {

    /**
     * 分页查询考试
     *
     * @param exam    查询条件
     * @param page    页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<EduExam> pageQuery(EduExam exam, long page, long pageSize);

    /**
     * 创建考试
     *
     * @param exam 考试信息
     * @return 考试ID
     */
    Long createExam(EduExam exam);

    /**
     * 更新考试
     *
     * @param exam 考试信息
     * @return 是否成功
     */
    boolean updateExam(EduExam exam);

    /**
     * 删除考试（逻辑删除）
     *
     * @param id 考试标识
     * @return 是否成功
     */
    boolean deleteExam(Long id);
}
