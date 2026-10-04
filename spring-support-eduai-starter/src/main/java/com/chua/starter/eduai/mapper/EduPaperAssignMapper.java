package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduPaperAssign;
import com.chua.starter.eduai.model.vo.PaperAssignVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 班级试卷布置 Mapper
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduPaperAssignMapper extends BaseMapper<EduPaperAssign> {

    /**
     * 我（老师）布置过的试卷（XML 语句 selectByTeacher，按布置时间倒序）。
     *
     * @param teacherAccount 老师登录账号
     * @return 布置视图列表
     */
    List<PaperAssignVO> selectByTeacher(@Param("teacherAccount") String teacherAccount);

    /**
     * 某班级当前可见的试卷（XML 语句 selectByClass，仅已布置 + 试卷已发布）。
     *
     * <p>学生端用：按自己的 student_class_id 反查。刻意**不按归属过滤** ——
     * 布置人是老师，学生看不到老师名下的归属行。</p>
     *
     * @param classId 班级标识
     * @return 布置视图列表
     */
    List<PaperAssignVO> selectByClass(@Param("classId") Long classId);
}
