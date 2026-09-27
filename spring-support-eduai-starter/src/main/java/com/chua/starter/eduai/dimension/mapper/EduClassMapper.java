package com.chua.starter.eduai.dimension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.dimension.entity.EduClass;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 班级维度 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduClassMapper extends BaseMapper<EduClass> {

    /**
     * 列表班级（XML 语句 listClasses：启用状态 + 学段/班主任筛选 + 归属隔离，按班级ID升序）。
     *
     * @param gradeLevel 学段，为空时不限学段
     * @param teacherId  班主任用户标识，为空时不限定
     * @param scope      归属隔离参数
     * @return 班级列表
     */
    List<EduClass> listClasses(@Param("gradeLevel") String gradeLevel,
                               @Param("teacherId") Long teacherId,
                               @Param("scope") EduUserContext.OwnerScope scope);
}
