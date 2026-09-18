package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduIdiom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 成语 Mapper
 *
 * @since 4.0.0.42
 * @author CH
 */
@Mapper
public interface EduIdiomMapper extends BaseMapper<EduIdiom> {

    /**
    * 根据学段获取成语列表
    *
    * @param gradeLevel 学段
    * @param count      数量
    * @return 成语列表
    */
    @Select("<script>" +
            "SELECT i.* FROM edu_idiom i " +
            "INNER JOIN edu_idiom_grade g ON i.idiom_id = g.idiom_id " +
            "WHERE g.grade_level = #{gradeLevel} " +
            "<if test='grade != null and grade != \"\"'>AND g.grade = #{grade}</if> " +
            "ORDER BY RAND() " +
            "LIMIT #{count}" +
            "</script>")
    List<EduIdiom> selectByGradeLevel(@Param("gradeLevel") String gradeLevel,
                                       @Param("grade") String grade,
                                       @Param("count") int count);

    /**
    * 随机获取指定数量的成语
    *
    * @param count 数量
    * @return 成语列表
    */
    @Select("SELECT * FROM edu_idiom ORDER BY RAND() LIMIT #{count}")
    List<EduIdiom> selectRandom(@Param("count") int count);
}
