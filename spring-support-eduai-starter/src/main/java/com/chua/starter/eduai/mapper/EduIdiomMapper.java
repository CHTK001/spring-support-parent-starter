package com.chua.starter.eduai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.model.EduIdiom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
     * @param grade      年级，可为空，为空时不参与过滤
     * @param count      数量
     * @param userId     归属隔离：非空时只返回系统成语与该用户的成语；管理员传空看全部
     * @return 成语列表
     */
    List<EduIdiom> selectByGradeLevel(@Param("gradeLevel") String gradeLevel,
                                       @Param("grade") String grade,
                                       @Param("count") int count,
                                       @Param("userId") String userId);

    /**
     * 随机获取指定数量的成语
     *
     * @param count  数量
     * @param userId 归属隔离：非空时只返回系统成语与该用户的成语；管理员传空看全部
     * @return 成语列表
     */
    List<EduIdiom> selectRandom(@Param("count") int count, @Param("userId") String userId);
}
