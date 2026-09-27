package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.eduai.model.EduExam;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 考试 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduExamMapper extends BaseMapper<EduExam> {

    /**
     * 分页查询考试（XML 语句 pageSelect，按考试实体字段做动态筛选）。
     *
     * @param page  分页参数
     * @param query 筛选条件（仅使用 examPaperId/examName/examClassId/examStatus 字段）
     * @param scope 归属隔离参数
     * @return 考试分页
     */
    IPage<EduExam> pageSelect(Page<EduExam> page, @Param("query") EduExam query,
                              @Param("scope") EduUserContext.OwnerScope scope);
}
