package com.chua.starter.eduai.dimension.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chua.starter.eduai.dimension.entity.EduSubject;
import com.chua.starter.eduai.support.util.EduUserContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学科维度 Mapper
 *
 * @author CH
 * @since 4.0.0.42
 */
@Mapper
public interface EduSubjectMapper extends BaseMapper<EduSubject> {

    /**
     * 列表学科（XML 语句 listSubjects：启用状态 + 学段模糊匹配 + 归属隔离，按排序号升序）。
     *
     * @param gradeLevel 学段，为空时不限学段
     * @param scope      归属隔离参数
     * @return 学科列表
     */
    List<EduSubject> listSubjects(@Param("gradeLevel") String gradeLevel,
                                  @Param("scope") EduUserContext.OwnerScope scope);
}
