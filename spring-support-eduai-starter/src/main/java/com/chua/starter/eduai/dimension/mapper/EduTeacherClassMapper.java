package com.chua.starter.eduai.dimension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.dimension.entity.EduTeacherClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 教师班级配置 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduTeacherClassMapper extends BaseMapper<EduTeacherClass> {

    /**
     * 教师已配置的班级标识列表（老师端数据范围）。
     *
     * @param teacherId 教师标识
     * @return 班级标识列表
     */
    List<Long> selectClassIdsByTeacher(@Param("teacherId") Long teacherId);

    /**
     * 清空某教师的全部班级配置（全量替换的前半步）。
     *
     * @param teacherId 教师标识
     * @return 删除行数
     */
    int deleteByTeacherId(@Param("teacherId") Long teacherId);
}
