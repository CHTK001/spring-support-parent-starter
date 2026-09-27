package com.chua.starter.eduai.dimension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.dimension.entity.EduGrade;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 年级维度 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduGradeMapper extends BaseMapper<EduGrade> {

    /**
     * 列表年级（XML 语句 listGrades：学段等值 + 归属隔离，按学段内顺序升序）。
     *
     * @param gradeLevel 学段，为空时不限学段
     * @param scope      归属隔离参数
     * @return 年级列表
     */
    List<EduGrade> listGrades(@Param("gradeLevel") String gradeLevel,
                              @Param("scope") EduUserContext.OwnerScope scope);
}
