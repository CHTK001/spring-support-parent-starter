package com.chua.starter.eduai.dimension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.dimension.entity.EduTeacher;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 教师档案 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduTeacherMapper extends BaseMapper<EduTeacher> {

    /**
     * 列表教师（XML 语句 listTeachers：在职状态 + 学科筛选 + 归属隔离，按教师ID升序）。
     *
     * @param subjectCode 任教学科编码，为空时不限学科
     * @param scope       归属隔离参数
     * @return 教师列表
     */
    List<EduTeacher> listTeachers(@Param("subjectCode") String subjectCode,
                                  @Param("scope") EduUserContext.OwnerScope scope);
}
