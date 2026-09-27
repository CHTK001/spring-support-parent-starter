package com.chua.starter.eduai.dimension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.dimension.entity.EduStudent;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学生档案 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduStudentMapper extends BaseMapper<EduStudent> {

    /**
     * 列表学生（XML 语句 listStudents：在读状态 + 班级/学段/年级筛选 + 归属隔离，按学生ID升序）。
     *
     * @param classId    班级标识，为空时不限定
     * @param gradeLevel 学段，为空时不限学段
     * @param grade      年级，为空时不限年级
     * @param scope      归属隔离参数
     * @return 学生列表
     */
    List<EduStudent> listStudents(@Param("classId") Long classId,
                                  @Param("gradeLevel") String gradeLevel,
                                  @Param("grade") String grade,
                                  @Param("scope") EduUserContext.OwnerScope scope);
}
